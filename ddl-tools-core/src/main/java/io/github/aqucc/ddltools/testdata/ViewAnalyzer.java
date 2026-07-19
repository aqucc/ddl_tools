package io.github.aqucc.ddltools.testdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;

import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.parse.SqlTextUtil;

/**
 * ビュー定義のSELECT文を解析し、テストデータ生成に使う情報を抽出する。
 *
 * <ul>
 *   <li>等値結合条件 (a.x = b.y) → 両カラムに同じ値を生成するためのペア</li>
 *   <li>リテラル等値条件 (a.status = 'ACTIVE') → その値を固定値として生成</li>
 * </ul>
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
     * ビュー定義を解析する。解析できない場合はnullを返す(呼び出し側で警告)。
     */
    public Analysis analyze(String definitionSql) {
        if (definitionSql == null || definitionSql.trim().isEmpty()) {
            return null;
        }
        try {
            Statement stmt = CCJSqlParserUtil.parse(definitionSql);
            if (!(stmt instanceof PlainSelect)) {
                return null;
            }
            PlainSelect select = (PlainSelect) stmt;
            Map<String, TableMetadata> aliasToTable = new HashMap<String, TableMetadata>();
            Map<String, String> aliasToSchema = new HashMap<String, String>();
            registerFromItem(select.getFromItem(), aliasToTable, aliasToSchema);

            Analysis analysis = new Analysis();
            if (select.getJoins() != null) {
                for (Join join : select.getJoins()) {
                    registerFromItem(join.getRightItem(), aliasToTable, aliasToSchema);
                    for (Expression on : join.getOnExpressions()) {
                        collectConditions(on, aliasToTable, aliasToSchema, analysis);
                    }
                }
            }
            if (select.getWhere() != null) {
                collectConditions(select.getWhere(), aliasToTable, aliasToSchema, analysis);
            }
            return analysis;
        } catch (Exception e) {
            return null;
        }
    }

    private void registerFromItem(Object fromItem, Map<String, TableMetadata> aliasToTable,
            Map<String, String> aliasToSchema) {
        if (!(fromItem instanceof Table)) {
            return;
        }
        Table t = (Table) fromItem;
        String tableName = SqlTextUtil.normalizeIdentifier(t.getName(), dialect);
        String schemaName = t.getSchemaName() == null
                ? null
                : SqlTextUtil.normalizeIdentifier(t.getSchemaName(), dialect);
        TableMetadata resolved = null;
        String resolvedSchema = null;
        if (schemaName != null) {
            SchemaMetadata schema = db.findSchema(schemaName);
            if (schema != null) {
                resolved = schema.findTable(tableName);
                resolvedSchema = schemaName;
            }
        } else {
            for (SchemaMetadata schema : db.getSchemas()) {
                TableMetadata found = schema.findTable(tableName);
                if (found != null) {
                    resolved = found;
                    resolvedSchema = schema.getName();
                    break;
                }
            }
        }
        if (resolved == null) {
            return;
        }
        String alias = t.getAlias() != null
                ? SqlTextUtil.normalizeIdentifier(t.getAlias().getName(), dialect)
                : tableName;
        aliasToTable.put(alias.toLowerCase(Locale.ROOT), resolved);
        aliasToSchema.put(alias.toLowerCase(Locale.ROOT), resolvedSchema);
    }

    private void collectConditions(Expression expr, Map<String, TableMetadata> aliasToTable,
            Map<String, String> aliasToSchema, Analysis analysis) {
        if (expr instanceof AndExpression) {
            AndExpression and = (AndExpression) expr;
            collectConditions(and.getLeftExpression(), aliasToTable, aliasToSchema, analysis);
            collectConditions(and.getRightExpression(), aliasToTable, aliasToSchema, analysis);
            return;
        }
        if (!(expr instanceof EqualsTo)) {
            return;
        }
        EqualsTo eq = (EqualsTo) expr;
        Expression left = eq.getLeftExpression();
        Expression right = eq.getRightExpression();
        if (left instanceof Column && right instanceof Column) {
            String keyL = resolveColumnKey((Column) left, aliasToTable, aliasToSchema);
            String keyR = resolveColumnKey((Column) right, aliasToTable, aliasToSchema);
            if (keyL != null && keyR != null) {
                analysis.getEqualPairs().add(new String[] {keyL, keyR});
            }
        } else if (left instanceof Column) {
            addFixedValue((Column) left, right, aliasToTable, aliasToSchema, analysis);
        } else if (right instanceof Column) {
            addFixedValue((Column) right, left, aliasToTable, aliasToSchema, analysis);
        }
    }

    private void addFixedValue(Column col, Expression literal,
            Map<String, TableMetadata> aliasToTable, Map<String, String> aliasToSchema,
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
        String key = resolveColumnKey(col, aliasToTable, aliasToSchema);
        if (key != null && !analysis.getFixedValues().containsKey(key)) {
            analysis.getFixedValues().put(key, value);
        }
    }

    private String resolveColumnKey(Column col, Map<String, TableMetadata> aliasToTable,
            Map<String, String> aliasToSchema) {
        String columnName = SqlTextUtil.normalizeIdentifier(col.getColumnName(), dialect);
        Table qualifier = col.getTable();
        String alias;
        if (qualifier != null && qualifier.getName() != null) {
            alias = SqlTextUtil.normalizeIdentifier(qualifier.getName(), dialect)
                    .toLowerCase(Locale.ROOT);
        } else if (aliasToTable.size() == 1) {
            alias = aliasToTable.keySet().iterator().next();
        } else {
            return null;
        }
        TableMetadata table = aliasToTable.get(alias);
        String schema = aliasToSchema.get(alias);
        if (table == null || table.findColumn(columnName) == null) {
            return null;
        }
        return columnKey(schema, table.getName(), columnName);
    }
}
