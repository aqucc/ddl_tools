package io.github.aqucc.ddltools.testdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;

import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.model.MaterializedViewMetadata;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.model.ViewMetadata;
import io.github.aqucc.ddltools.parse.SqlTextUtil;

/**
 * ビュー定義のSELECT文を解析し、テストデータ生成に使う情報を抽出する。
 *
 * <ul>
 *   <li>等値結合条件 (a.x = b.y) → 両カラムに同じ値を生成するためのペア</li>
 *   <li>リテラル等値条件 (a.status = 'ACTIVE') → その値を固定値として生成</li>
 * </ul>
 *
 * <p>FROM句が別のビュー・派生表(サブクエリ)・CTE(WITH句)・UNIONを参照している場合でも、
 * 末端が実テーブルになるまで再帰的に辿り、抽出した結合条件・固定値を基底テーブルのカラムへ
 * 伝播させる(循環参照はガードして無限ループを防ぐ)。
 */
public class ViewAnalyzer {

    /** 解析結果。カラムは "schema|table|column" 形式のキーで表す。 */
    public static class Analysis {
        private final List<String[]> equalPairs = new ArrayList<String[]>();
        private final Map<String, Object> fixedValues = new HashMap<String, Object>();

        public List<String[]> getEqualPairs() {
            return equalPairs;
        }

        public Map<String, Object> getFixedValues() {
            return fixedValues;
        }
    }

    /** 基底テーブルのカラムを指す。 */
    private static final class BaseColumn {
        final String schema;
        final String table;
        final String column;

        BaseColumn(String schema, String table, String column) {
            this.schema = schema;
            this.table = table;
            this.column = column;
        }

        String key() {
            return columnKey(schema, table, column);
        }
    }

    /**
     * 1つのFROM要素(テーブル・ビュー・派生表)を、公開カラム名(小文字)→基底カラム のマップとして表す。
     * 解決できないカラムの値はnull。
     */
    private static final class Relation {
        final Map<String, BaseColumn> columns;

        Relation(Map<String, BaseColumn> columns) {
            this.columns = columns;
        }

        BaseColumn resolve(String columnLower) {
            return columns.get(columnLower);
        }
    }

    private final DatabaseMetadata db;
    private final Dialect dialect;

    public ViewAnalyzer(DatabaseMetadata db) {
        this.db = db;
        this.dialect = db.getDialect();
    }

    public static String columnKey(String schema, String table, String column) {
        return (schema + "|" + table + "|" + column).toLowerCase(Locale.ROOT);
    }

    /**
     * ビュー定義を解析する。SQLとして解釈できない場合のみnullを返す(呼び出し側で警告)。
     */
    public Analysis analyze(String definitionSql) {
        if (definitionSql == null || definitionSql.trim().isEmpty()) {
            return null;
        }
        try {
            Statement stmt = CCJSqlParserUtil.parse(definitionSql);
            if (!(stmt instanceof Select)) {
                return null;
            }
            Analysis analysis = new Analysis();
            buildRelation((Select) stmt, new HashMap<String, Relation>(),
                    new HashSet<String>(), analysis);
            return analysis;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 再帰的な解決
    // ------------------------------------------------------------------

    /**
     * SELECT文を解析し、その公開カラム(出力列)→基底カラムのマップを返す。
     * 併せて内部の結合条件・固定値条件を{@code analysis}へ蓄積する。
     *
     * @param cteScope  外側のWITH句で定義済みのCTE名(小文字)→Relation
     * @param visiting  循環参照ガード用の、解決中のビューキー("schema|name")集合
     */
    private Map<String, BaseColumn> buildRelation(Select sel, Map<String, Relation> cteScope,
            Set<String> visiting, Analysis analysis) {
        if (sel instanceof SetOperationList) {
            Map<String, BaseColumn> first = null;
            for (Select arm : ((SetOperationList) sel).getSelects()) {
                Map<String, BaseColumn> m = buildRelation(arm, cteScope, visiting, analysis);
                if (first == null) {
                    first = m;
                }
            }
            return first != null ? first : new LinkedHashMap<String, BaseColumn>();
        }
        if (sel instanceof ParenthesedSelect) {
            return buildRelation(((ParenthesedSelect) sel).getSelect(), cteScope, visiting, analysis);
        }
        if (!(sel instanceof PlainSelect)) {
            return new LinkedHashMap<String, BaseColumn>();
        }
        PlainSelect ps = (PlainSelect) sel;

        // WITH句(CTE)を先に登録する
        Map<String, Relation> scopeCte = new HashMap<String, Relation>(cteScope);
        if (ps.getWithItemsList() != null) {
            for (WithItem wi : ps.getWithItemsList()) {
                String cteName = aliasName(wi.getAlias());
                if (cteName == null) {
                    continue;
                }
                Map<String, BaseColumn> m = buildRelation(wi.getSelect(), scopeCte, visiting, analysis);
                scopeCte.put(cteName.toLowerCase(Locale.ROOT), new Relation(m));
            }
        }

        // FROM + JOIN からローカルスコープ(別名→Relation)を組み立てる
        Map<String, Relation> scope = new LinkedHashMap<String, Relation>();
        registerFrom(ps.getFromItem(), scope, scopeCte, visiting, analysis);
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                registerFrom(join.getRightItem(), scope, scopeCte, visiting, analysis);
            }
        }

        // 結合条件・WHERE条件を収集する
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                if (join.getOnExpressions() != null) {
                    for (Expression on : join.getOnExpressions()) {
                        collectConditions(on, scope, analysis);
                    }
                }
            }
        }
        if (ps.getWhere() != null) {
            collectConditions(ps.getWhere(), scope, analysis);
        }

        return buildOutputColumns(ps.getSelectItems(), scope);
    }

    private void registerFrom(FromItem fromItem, Map<String, Relation> scope,
            Map<String, Relation> cteScope, Set<String> visiting, Analysis analysis) {
        if (fromItem instanceof Table) {
            Table t = (Table) fromItem;
            String name = SqlTextUtil.normalizeIdentifier(t.getName(), dialect);
            String schemaName = t.getSchemaName() == null
                    ? null
                    : SqlTextUtil.normalizeIdentifier(t.getSchemaName(), dialect);
            Relation rel = resolveNamedRelation(schemaName, name, cteScope, visiting, analysis);
            String alias = t.getAlias() != null ? aliasName(t.getAlias()) : name;
            scope.put(alias.toLowerCase(Locale.ROOT), rel);
        } else if (fromItem instanceof ParenthesedSelect) {
            ParenthesedSelect ps = (ParenthesedSelect) fromItem;
            String alias = aliasName(ps.getAlias());
            if (alias == null) {
                return;
            }
            Map<String, BaseColumn> m = buildRelation(ps.getSelect(), cteScope, visiting, analysis);
            scope.put(alias.toLowerCase(Locale.ROOT), new Relation(m));
        }
    }

    /**
     * FROMに現れた名前を、CTE→実テーブル→ビュー(マテビュー)の順に解決する。
     * ビューの場合はその定義を再帰的に解析し、末端の基底カラムまで辿る。
     */
    private Relation resolveNamedRelation(String schemaName, String name,
            Map<String, Relation> cteScope, Set<String> visiting, Analysis analysis) {
        if (schemaName == null && cteScope.containsKey(name.toLowerCase(Locale.ROOT))) {
            return cteScope.get(name.toLowerCase(Locale.ROOT));
        }
        // 実テーブル
        TableResolution table = findTable(schemaName, name);
        if (table != null) {
            return baseTableRelation(table.schema, table.table);
        }
        // ビュー / マテリアライズドビュー
        ViewResolution view = findView(schemaName, name);
        if (view != null && view.definitionSql != null) {
            String viewKey = (view.schema + "|" + name).toLowerCase(Locale.ROOT);
            if (visiting.contains(viewKey)) {
                return emptyRelation();
            }
            visiting.add(viewKey);
            try {
                Statement stmt = CCJSqlParserUtil.parse(view.definitionSql);
                if (stmt instanceof Select) {
                    Map<String, BaseColumn> m = buildRelation((Select) stmt,
                            new HashMap<String, Relation>(), visiting, analysis);
                    return new Relation(m);
                }
            } catch (Exception e) {
                // 解析不能なビューは空Relationとして扱う
            } finally {
                visiting.remove(viewKey);
            }
        }
        return emptyRelation();
    }

    private Relation baseTableRelation(String schema, TableMetadata table) {
        Map<String, BaseColumn> columns = new LinkedHashMap<String, BaseColumn>();
        for (io.github.aqucc.ddltools.model.ColumnMetadata c : table.getColumns()) {
            columns.put(c.getName().toLowerCase(Locale.ROOT),
                    new BaseColumn(schema, table.getName(), c.getName()));
        }
        return new Relation(columns);
    }

    private static Relation emptyRelation() {
        return new Relation(new LinkedHashMap<String, BaseColumn>());
    }

    /** SELECT句の各項目から、出力カラム名(小文字)→基底カラム のマップを組み立てる。 */
    private Map<String, BaseColumn> buildOutputColumns(List<SelectItem<?>> items,
            Map<String, Relation> scope) {
        Map<String, BaseColumn> out = new LinkedHashMap<String, BaseColumn>();
        if (items == null) {
            return out;
        }
        for (SelectItem<?> item : items) {
            Expression expr = item.getExpression();
            Alias alias = item.getAlias();
            if (expr instanceof AllTableColumns) {
                Table t = ((AllTableColumns) expr).getTable();
                String aliasKey = SqlTextUtil.normalizeIdentifier(t.getName(), dialect)
                        .toLowerCase(Locale.ROOT);
                Relation rel = scope.get(aliasKey);
                if (rel != null) {
                    out.putAll(rel.columns);
                }
            } else if (expr instanceof AllColumns) {
                for (Relation rel : scope.values()) {
                    out.putAll(rel.columns);
                }
            } else if (expr instanceof Column) {
                BaseColumn base = resolveColumn((Column) expr, scope);
                String outName = alias != null ? aliasName(alias) : ((Column) expr).getColumnName();
                if (outName != null) {
                    out.put(SqlTextUtil.normalizeIdentifier(outName, dialect).toLowerCase(Locale.ROOT),
                            base);
                }
            } else if (alias != null && aliasName(alias) != null) {
                out.put(SqlTextUtil.normalizeIdentifier(aliasName(alias), dialect)
                        .toLowerCase(Locale.ROOT), null);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 条件収集
    // ------------------------------------------------------------------

    private void collectConditions(Expression expr, Map<String, Relation> scope, Analysis analysis) {
        if (expr instanceof AndExpression) {
            AndExpression and = (AndExpression) expr;
            collectConditions(and.getLeftExpression(), scope, analysis);
            collectConditions(and.getRightExpression(), scope, analysis);
            return;
        }
        if (!(expr instanceof EqualsTo)) {
            return;
        }
        EqualsTo eq = (EqualsTo) expr;
        Expression left = eq.getLeftExpression();
        Expression right = eq.getRightExpression();
        if (left instanceof Column && right instanceof Column) {
            BaseColumn l = resolveColumn((Column) left, scope);
            BaseColumn r = resolveColumn((Column) right, scope);
            if (l != null && r != null) {
                analysis.getEqualPairs().add(new String[] {l.key(), r.key()});
            }
        } else if (left instanceof Column) {
            addFixedValue((Column) left, right, scope, analysis);
        } else if (right instanceof Column) {
            addFixedValue((Column) right, left, scope, analysis);
        }
    }

    private void addFixedValue(Column col, Expression literal, Map<String, Relation> scope,
            Analysis analysis) {
        Object value = null;
        if (literal instanceof StringValue) {
            value = ((StringValue) literal).getValue();
        } else if (literal instanceof LongValue) {
            value = ((LongValue) literal).getValue();
        }
        if (value == null) {
            return;
        }
        BaseColumn base = resolveColumn(col, scope);
        if (base != null && !analysis.getFixedValues().containsKey(base.key())) {
            analysis.getFixedValues().put(base.key(), value);
        }
    }

    /** スコープ内のカラム参照を基底カラムへ解決する。解決できなければnull。 */
    private BaseColumn resolveColumn(Column col, Map<String, Relation> scope) {
        String columnName = SqlTextUtil.normalizeIdentifier(col.getColumnName(), dialect)
                .toLowerCase(Locale.ROOT);
        Table qualifier = col.getTable();
        String alias;
        if (qualifier != null && qualifier.getName() != null) {
            alias = SqlTextUtil.normalizeIdentifier(qualifier.getName(), dialect)
                    .toLowerCase(Locale.ROOT);
        } else if (scope.size() == 1) {
            alias = scope.keySet().iterator().next();
        } else {
            return null;
        }
        Relation rel = scope.get(alias);
        if (rel == null) {
            return null;
        }
        return rel.resolve(columnName);
    }

    // ------------------------------------------------------------------
    // メタ情報からのテーブル/ビュー検索
    // ------------------------------------------------------------------

    private static final class TableResolution {
        final String schema;
        final TableMetadata table;

        TableResolution(String schema, TableMetadata table) {
            this.schema = schema;
            this.table = table;
        }
    }

    private static final class ViewResolution {
        final String schema;
        final String definitionSql;

        ViewResolution(String schema, String definitionSql) {
            this.schema = schema;
            this.definitionSql = definitionSql;
        }
    }

    private TableResolution findTable(String schemaName, String name) {
        if (schemaName != null) {
            SchemaMetadata schema = db.findSchema(schemaName);
            if (schema != null) {
                TableMetadata t = schema.findTable(name);
                if (t != null) {
                    return new TableResolution(schema.getName(), t);
                }
            }
            return null;
        }
        for (SchemaMetadata schema : db.getSchemas()) {
            TableMetadata t = schema.findTable(name);
            if (t != null) {
                return new TableResolution(schema.getName(), t);
            }
        }
        return null;
    }

    private ViewResolution findView(String schemaName, String name) {
        if (schemaName != null) {
            SchemaMetadata schema = db.findSchema(schemaName);
            return schema == null ? null : findViewInSchema(schema, name);
        }
        for (SchemaMetadata schema : db.getSchemas()) {
            ViewResolution v = findViewInSchema(schema, name);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private ViewResolution findViewInSchema(SchemaMetadata schema, String name) {
        for (ViewMetadata v : schema.getViews()) {
            if (v.getName().equalsIgnoreCase(name)) {
                return new ViewResolution(schema.getName(), v.getDefinitionSql());
            }
        }
        for (MaterializedViewMetadata mv : schema.getMaterializedViews()) {
            if (mv.getName().equalsIgnoreCase(name)) {
                return new ViewResolution(schema.getName(), mv.getDefinitionSql());
            }
        }
        return null;
    }

    private static String aliasName(Alias alias) {
        return alias == null ? null : alias.getName();
    }
}
