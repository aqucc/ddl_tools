package io.github.aqucc.ddltools.parse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.CheckConstraint;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;

import io.github.aqucc.ddltools.model.CheckConstraintMetadata;
import io.github.aqucc.ddltools.model.ColumnMetadata;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.model.ForeignKeyMetadata;
import io.github.aqucc.ddltools.model.GenericObjectMetadata;
import io.github.aqucc.ddltools.model.IndexMetadata;
import io.github.aqucc.ddltools.model.MaterializedViewMetadata;
import io.github.aqucc.ddltools.model.PackageMetadata;
import io.github.aqucc.ddltools.model.ParameterMetadata;
import io.github.aqucc.ddltools.model.PrimaryKeyMetadata;
import io.github.aqucc.ddltools.model.RoutineMetadata;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.SequenceMetadata;
import io.github.aqucc.ddltools.model.SynonymMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.model.TriggerMetadata;
import io.github.aqucc.ddltools.model.UniqueConstraintMetadata;
import io.github.aqucc.ddltools.model.ViewMetadata;

/**
 * DDLスクリプトをパースしてメタ情報モデルを構築する。
 *
 * <p>CREATE TABLE はJSqlParserのASTから深く構造化する。
 * プロシージャ/ファンクション/トリガー/パッケージは正規表現でシグネチャを抽出し、
 * 本体はソーステキストのまま保持する。解釈できない文も
 * {@link GenericObjectMetadata} として原文を保持し、情報を失わない。
 */
public class DdlParser {

    private static final String IDENT = "(?:\"[^\"]+\"|[A-Za-z_][\\w$#]*)";
    private static final String QNAME = IDENT + "(?:\\." + IDENT + ")?";

    private static final Pattern P_TABLE = Pattern.compile(
            "^CREATE\\s+((GLOBAL|PRIVATE)\\s+TEMPORARY\\s+|TEMPORARY\\s+|TEMP\\s+|UNLOGGED\\s+)?TABLE\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_VIEW = Pattern.compile(
            "^CREATE\\s+(OR\\s+REPLACE\\s+)?((NO\\s+)?FORCE\\s+)?(EDITIONABLE\\s+|NONEDITIONABLE\\s+)?VIEW\\s+(" + QNAME + ")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_MVIEW = Pattern.compile(
            "^CREATE\\s+MATERIALIZED\\s+VIEW\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(" + QNAME + ")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TRIGGER = Pattern.compile(
            "^CREATE\\s+(OR\\s+REPLACE\\s+)?(CONSTRAINT\\s+)?(EDITIONABLE\\s+|NONEDITIONABLE\\s+)?TRIGGER\\s+(" + QNAME + ")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_ROUTINE = Pattern.compile(
            "^CREATE\\s+(OR\\s+REPLACE\\s+)?(EDITIONABLE\\s+|NONEDITIONABLE\\s+)?(PROCEDURE|FUNCTION)\\s+(" + QNAME + ")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PACKAGE = Pattern.compile(
            "^CREATE\\s+(OR\\s+REPLACE\\s+)?(EDITIONABLE\\s+|NONEDITIONABLE\\s+)?PACKAGE\\s+(BODY\\s+)?(" + QNAME + ")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SEQUENCE = Pattern.compile(
            "^CREATE\\s+SEQUENCE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(" + QNAME + ")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_INDEX = Pattern.compile(
            "^CREATE\\s+(UNIQUE\\s+)?(BITMAP\\s+)?INDEX\\s+(" + QNAME + ")\\s+ON\\s+(" + QNAME + ")\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SYNONYM = Pattern.compile(
            "^CREATE\\s+(OR\\s+REPLACE\\s+)?(PUBLIC\\s+)?SYNONYM\\s+(" + QNAME + ")\\s+FOR\\s+(" + QNAME + ")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_ALTER_ADD = Pattern.compile(
            "^ALTER\\s+TABLE\\s+(ONLY\\s+)?(" + QNAME + ")\\s+ADD\\s+(CONSTRAINT\\s+(" + IDENT + ")\\s+)?"
                    + "(PRIMARY\\s+KEY|FOREIGN\\s+KEY|UNIQUE|CHECK)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern P_COMMENT = Pattern.compile(
            "^COMMENT\\s+ON\\s+(TABLE|COLUMN|MATERIALIZED\\s+VIEW|VIEW)\\s+(\\S+)\\s+IS\\s+('(?:[^']|'')*'|NULL)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern P_CREATE_GENERIC = Pattern.compile(
            "^CREATE\\s+(OR\\s+REPLACE\\s+)?([A-Za-z]+(?:\\s+[A-Za-z]+){0,2}?)\\s+(" + QNAME + ")",
            Pattern.CASE_INSENSITIVE);

    private static final String[] MULTI_WORD_TYPES = {
            "TYPE BODY", "PUBLIC DATABASE LINK", "DATABASE LINK", "FOREIGN TABLE", "EVENT TRIGGER",
            "FOREIGN DATA WRAPPER", "MATERIALIZED VIEW LOG", "USER MAPPING",
    };

    private final ParseOptions options;

    public DdlParser(ParseOptions options) {
        this.options = options;
    }

    public ParseResult parse(String script) {
        DatabaseMetadata db = new DatabaseMetadata(options.getDialect());
        db.setExtractedAt(Instant.now().toString());
        ParseResult result = new ParseResult(db);

        List<String> statements = new DdlScriptSplitter(options.getDialect()).split(script);
        // インデックスやALTERが対象テーブルより先に現れる場合に備えて2パスにする
        List<String> deferred = new ArrayList<String>();
        for (String stmt : statements) {
            if (isDeferredStatement(stmt)) {
                deferred.add(stmt);
            } else {
                parseStatement(stmt, result);
            }
        }
        for (String stmt : deferred) {
            parseStatement(stmt, result);
        }
        return result;
    }

    private boolean isDeferredStatement(String stmt) {
        String head = headOf(stmt);
        return P_INDEX.matcher(head).find()
                || P_ALTER_ADD.matcher(head).find()
                || P_COMMENT.matcher(head).find();
    }

    private void parseStatement(String stmt, ParseResult result) {
        String head = headOf(stmt);
        try {
            if (P_TABLE.matcher(head).find()) {
                parseCreateTable(stmt, result);
            } else if (P_MVIEW.matcher(head).find()) {
                parseMaterializedView(stmt, head, result);
            } else if (P_VIEW.matcher(head).find()) {
                parseView(stmt, head, result);
            } else if (P_TRIGGER.matcher(head).find()) {
                parseTrigger(stmt, head, result);
            } else if (P_ROUTINE.matcher(head).find()) {
                parseRoutine(stmt, head, result);
            } else if (P_PACKAGE.matcher(head).find()) {
                parsePackage(stmt, head, result);
            } else if (P_SEQUENCE.matcher(head).find()) {
                parseSequence(head, result);
            } else if (P_INDEX.matcher(head).find()) {
                parseIndex(head, result);
            } else if (P_SYNONYM.matcher(head).find()) {
                parseSynonym(head, result);
            } else if (P_ALTER_ADD.matcher(head).find()) {
                parseAlterAddConstraint(head, result);
            } else if (P_COMMENT.matcher(head).find()) {
                parseComment(head, result);
            } else if (head.toUpperCase(Locale.ROOT).startsWith("CREATE")) {
                parseGeneric(stmt, head, result);
            } else {
                // GRANT, INSERT, SET などのDDL以外の文はスキップ
                result.addWarning("skipped non-DDL statement: " + summarize(stmt));
            }
        } catch (RuntimeException e) {
            result.addWarning("failed to parse statement (" + e + "): " + summarize(stmt));
        }
    }

    // ------------------------------------------------------------------
    // CREATE TABLE
    // ------------------------------------------------------------------

    private void parseCreateTable(String stmt, ParseResult result) {
        String normalized = P_TABLE.matcher(headOf(stmt)).replaceFirst("CREATE TABLE");
        CreateTable ct = tryParseCreateTable(normalized);
        if (ct == null) {
            // Oracleのストレージ句などで失敗した場合、カラム定義部分のみで再挑戦する
            String reduced = reduceToColumnList(normalized);
            if (reduced != null) {
                ct = tryParseCreateTable(reduced);
            }
        }
        if (ct == null) {
            String name = extractNameAfterKeyword(stmt, "TABLE");
            addGeneric(result, name, "TABLE", stmt);
            result.addWarning("CREATE TABLE could not be fully parsed, kept as generic object: "
                    + summarize(stmt));
            return;
        }

        String schemaName = normalizeOrDefault(ct.getTable().getSchemaName());
        String tableName = normalize(ct.getTable().getName());
        TableMetadata table = new TableMetadata(tableName);

        if (ct.getColumnDefinitions() != null) {
            for (ColumnDefinition cd : ct.getColumnDefinitions()) {
                table.getColumns().add(toColumn(cd, table));
            }
        }
        if (ct.getIndexes() != null) {
            for (Index index : ct.getIndexes()) {
                applyTableConstraint(table, index);
            }
        }
        result.getMetadata().findOrCreateSchema(schemaName).getTables().add(table);
    }

    private CreateTable tryParseCreateTable(String sql) {
        try {
            Statement st = CCJSqlParserUtil.parse(sql);
            if (st instanceof CreateTable) {
                return (CreateTable) st;
            }
        } catch (Exception e) {
            // フォールバックに委ねる
        }
        return null;
    }

    /** CREATE TABLE name ( ... ) のカラムリスト部分までを切り出す。 */
    private String reduceToColumnList(String sql) {
        int open = sql.indexOf('(');
        if (open < 0) {
            return null;
        }
        int close = SqlTextUtil.findMatchingParen(sql, open);
        if (close < 0) {
            return null;
        }
        return sql.substring(0, close + 1);
    }

    private ColumnMetadata toColumn(ColumnDefinition cd, TableMetadata table) {
        ColumnMetadata col = new ColumnMetadata();
        col.setName(normalize(cd.getColumnName()));
        String typeName = cd.getColDataType().getDataType();
        col.setTypeName(typeName.toUpperCase(Locale.ROOT));
        applyTypeArguments(col, cd.getColDataType().getArgumentsStringList());

        List<String> specs = cd.getColumnSpecs();
        String specText = (specs == null) ? "" : join(specs, " ");
        String upperSpec = specText.toUpperCase(Locale.ROOT);

        if (upperSpec.matches(".*\\bNOT\\s+NULL\\b.*")) {
            col.setNullable(false);
        }
        Matcher def = Pattern.compile(
                "\\bDEFAULT\\s+(.+?)(?=\\s+(NOT\\b|NULL\\b|PRIMARY\\b|UNIQUE\\b|CHECK\\b|REFERENCES\\b|CONSTRAINT\\b|ENABLE\\b|GENERATED\\b)|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(specText);
        if (def.find()) {
            col.setDefaultValue(def.group(1).trim());
        }
        if (upperSpec.contains("GENERATED")
                || col.getTypeName().equals("SERIAL")
                || col.getTypeName().equals("BIGSERIAL")
                || col.getTypeName().equals("SMALLSERIAL")) {
            col.setIdentity(Boolean.TRUE);
            col.setNullable(false);
        }
        if (upperSpec.matches(".*\\bPRIMARY\\s+KEY\\b.*")) {
            table.setPrimaryKey(new PrimaryKeyMetadata(null,
                    new ArrayList<String>(Arrays.asList(col.getName()))));
            col.setNullable(false);
        } else if (upperSpec.matches(".*\\bUNIQUE\\b.*")) {
            table.getUniqueConstraints().add(new UniqueConstraintMetadata(null,
                    new ArrayList<String>(Arrays.asList(col.getName()))));
        }
        Matcher chk = Pattern.compile("\\bCHECK\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(specText);
        if (chk.find()) {
            int open = chk.end() - 1;
            int close = SqlTextUtil.findMatchingParen(specText, open);
            if (close > open) {
                table.getCheckConstraints().add(
                        new CheckConstraintMetadata(null, specText.substring(open + 1, close).trim()));
            }
        }
        Matcher ref = Pattern.compile(
                "\\bREFERENCES\\s+(" + QNAME + ")\\s*(\\(([^)]*)\\))?",
                Pattern.CASE_INSENSITIVE).matcher(specText);
        if (ref.find()) {
            ForeignKeyMetadata fk = new ForeignKeyMetadata();
            fk.getColumns().add(col.getName());
            String[] qn = SqlTextUtil.splitQualified(ref.group(1));
            fk.setReferencedSchema(qn[0] == null ? null : normalize(qn[0]));
            fk.setReferencedTable(normalize(qn[1]));
            if (ref.group(3) != null) {
                fk.setReferencedColumns(normalizeList(ref.group(3)));
            }
            table.getForeignKeys().add(fk);
        }
        return col;
    }

    private void applyTypeArguments(ColumnMetadata col, List<String> args) {
        if (args == null || args.isEmpty()) {
            return;
        }
        Integer first = parseLeadingInt(args.get(0));
        if (first == null) {
            return;
        }
        if (isCharacterType(col.getTypeName())) {
            col.setLength(first);
        } else {
            col.setPrecision(first);
            if (args.size() > 1) {
                Integer scale = parseLeadingInt(args.get(1));
                if (scale != null) {
                    col.setScale(scale);
                }
            }
        }
    }

    private static boolean isCharacterType(String typeName) {
        String t = typeName.toUpperCase(Locale.ROOT);
        return t.contains("CHAR") || t.equals("TEXT");
    }

    private static Integer parseLeadingInt(String s) {
        Matcher m = Pattern.compile("^\\s*(\\d+)").matcher(s);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private void applyTableConstraint(TableMetadata table, Index index) {
        String type = index.getType() == null ? "" : index.getType().toUpperCase(Locale.ROOT);
        if (index instanceof ForeignKeyIndex) {
            ForeignKeyIndex fki = (ForeignKeyIndex) index;
            ForeignKeyMetadata fk = new ForeignKeyMetadata();
            fk.setName(normalize(index.getName()));
            fk.setColumns(normalizeAll(index.getColumnsNames()));
            String[] qn = SqlTextUtil.splitQualified(fki.getTable().getFullyQualifiedName());
            fk.setReferencedSchema(qn[0] == null ? null : normalize(qn[0]));
            fk.setReferencedTable(normalize(qn[1]));
            if (fki.getReferencedColumnNames() != null) {
                fk.setReferencedColumns(normalizeAll(fki.getReferencedColumnNames()));
            }
            table.getForeignKeys().add(fk);
        } else if (index instanceof CheckConstraint) {
            CheckConstraint cc = (CheckConstraint) index;
            table.getCheckConstraints().add(new CheckConstraintMetadata(
                    normalize(cc.getName()), String.valueOf(cc.getExpression())));
        } else if (type.contains("PRIMARY")) {
            table.setPrimaryKey(new PrimaryKeyMetadata(
                    normalize(index.getName()), normalizeAll(index.getColumnsNames())));
            for (String pkCol : table.getPrimaryKey().getColumns()) {
                ColumnMetadata c = table.findColumn(pkCol);
                if (c != null) {
                    c.setNullable(false);
                }
            }
        } else if (type.contains("UNIQUE")) {
            table.getUniqueConstraints().add(new UniqueConstraintMetadata(
                    normalize(index.getName()), normalizeAll(index.getColumnsNames())));
        } else {
            table.getIndexes().add(new IndexMetadata(
                    normalize(index.getName()), false, normalizeAll(index.getColumnsNames())));
        }
    }

    // ------------------------------------------------------------------
    // VIEW / MATERIALIZED VIEW
    // ------------------------------------------------------------------

    private void parseView(String stmt, String head, ParseResult result) {
        Matcher m = P_VIEW.matcher(head);
        m.find();
        String rawName = m.group(5);
        String[] qn = SqlTextUtil.splitQualified(rawName);
        ViewMetadata view = new ViewMetadata(normalize(qn[1]), extractViewDefinition(stmt));
        view.setColumns(extractDeclaredColumns(stmt, m.end()));
        schemaOf(result, qn).getViews().add(view);
    }

    private void parseMaterializedView(String stmt, String head, ParseResult result) {
        Matcher m = P_MVIEW.matcher(head);
        m.find();
        String[] qn = SqlTextUtil.splitQualified(m.group(2));
        MaterializedViewMetadata mv = new MaterializedViewMetadata(
                normalize(qn[1]), extractViewDefinition(stmt));
        mv.setColumns(extractDeclaredColumns(stmt, m.end()));
        schemaOf(result, qn).getMaterializedViews().add(mv);
    }

    /** トップレベルの AS 以降(SELECT/WITHで始まる部分)を定義SQLとして取り出す。 */
    private String extractViewDefinition(String stmt) {
        int from = 0;
        while (true) {
            int asPos = SqlTextUtil.indexOfTopLevelWord(stmt.substring(from), "AS");
            if (asPos < 0) {
                return null;
            }
            int abs = from + asPos;
            String after = stmt.substring(abs + 2).trim();
            String upper = after.toUpperCase(Locale.ROOT);
            if (upper.startsWith("SELECT") || upper.startsWith("WITH") || upper.startsWith("(")) {
                return stripViewTrailer(after);
            }
            from = abs + 2;
        }
    }

    private String stripViewTrailer(String definition) {
        return definition
                .replaceAll("(?is)\\s+WITH\\s+(LOCAL\\s+|CASCADED\\s+)?CHECK\\s+OPTION\\s*$", "")
                .replaceAll("(?is)\\s+WITH\\s+READ\\s+ONLY\\s*$", "")
                .trim();
    }

    /** VIEW名直後の (c1, c2, ...) 形式のカラム宣言があれば取り出す。 */
    private List<String> extractDeclaredColumns(String stmt, int afterNamePos) {
        String rest = stmt.substring(afterNamePos);
        Matcher m = Pattern.compile("^\\s*\\(").matcher(rest);
        if (!m.find()) {
            return new ArrayList<String>();
        }
        int open = afterNamePos + m.end() - 1;
        int close = SqlTextUtil.findMatchingParen(stmt, open);
        if (close < 0) {
            return new ArrayList<String>();
        }
        return normalizeList(stmt.substring(open + 1, close));
    }

    // ------------------------------------------------------------------
    // TRIGGER / ROUTINE / PACKAGE
    // ------------------------------------------------------------------

    private void parseTrigger(String stmt, String head, ParseResult result) {
        Matcher m = P_TRIGGER.matcher(head);
        m.find();
        String[] qn = SqlTextUtil.splitQualified(m.group(4));
        TriggerMetadata trigger = new TriggerMetadata(normalize(qn[1]));
        trigger.setBody(stmt);

        int eventsStart = 0;
        Matcher timing = Pattern.compile("\\b(BEFORE|AFTER|INSTEAD\\s+OF)\\b",
                Pattern.CASE_INSENSITIVE).matcher(head);
        if (timing.find()) {
            trigger.setTiming(timing.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+", " "));
            eventsStart = timing.end();
        }
        Matcher on = Pattern.compile("\\bON\\s+(" + QNAME + ")", Pattern.CASE_INSENSITIVE).matcher(head);
        int eventsEnd = head.length();
        if (on.find(eventsStart)) {
            String[] tq = SqlTextUtil.splitQualified(on.group(1));
            trigger.setTargetTable(normalize(tq[1]));
            eventsEnd = on.start();
        }
        String eventsPart = head.substring(Math.min(eventsStart, eventsEnd), eventsEnd);
        Matcher ev = Pattern.compile("\\b(INSERT|UPDATE|DELETE|TRUNCATE)\\b",
                Pattern.CASE_INSENSITIVE).matcher(eventsPart);
        while (ev.find()) {
            String e = ev.group(1).toUpperCase(Locale.ROOT);
            if (!trigger.getEvents().contains(e)) {
                trigger.getEvents().add(e);
            }
        }
        trigger.setLevel(Pattern.compile("\\bFOR\\s+EACH\\s+ROW\\b", Pattern.CASE_INSENSITIVE)
                .matcher(head).find() ? "ROW" : "STATEMENT");
        schemaOf(result, qn).getTriggers().add(trigger);
    }

    private void parseRoutine(String stmt, String head, ParseResult result) {
        Matcher m = P_ROUTINE.matcher(head);
        m.find();
        String routineType = m.group(3).toUpperCase(Locale.ROOT);
        String[] qn = SqlTextUtil.splitQualified(m.group(4));
        RoutineMetadata routine = new RoutineMetadata(normalize(qn[1]), routineType);
        routine.setSourceText(stmt);

        // 名前直後の括弧を引数リストとみなす
        String rest = stmt.substring(indexAfterMatch(stmt, m));
        Matcher paren = Pattern.compile("^\\s*\\(").matcher(rest);
        int afterParams = 0;
        if (paren.find()) {
            int open = paren.end() - 1;
            int close = SqlTextUtil.findMatchingParen(rest, open);
            if (close > open) {
                parseParameters(rest.substring(open + 1, close), routine);
                afterParams = close + 1;
            }
        }
        Matcher ret = Pattern.compile(
                "\\bRETURNS?\\s+((?:SETOF\\s+)?[A-Za-z_][\\w$#.%]*(?:\\s*\\([^)]*\\))?)",
                Pattern.CASE_INSENSITIVE).matcher(rest.substring(afterParams));
        if (ret.find() && RoutineMetadata.TYPE_FUNCTION.equals(routineType)) {
            routine.setReturnType(ret.group(1).trim());
        }
        schemaOf(result, qn).getRoutines().add(routine);
    }

    private void parseParameters(String paramText, RoutineMetadata routine) {
        if (paramText.trim().isEmpty()) {
            return;
        }
        for (String raw : SqlTextUtil.splitTopLevel(paramText, ',')) {
            String p = raw.trim();
            if (p.isEmpty()) {
                continue;
            }
            // DEFAULT値 (:= または DEFAULT) を除去
            p = p.replaceAll("(?is)(:=|\\bDEFAULT\\b).*$", "").trim();
            List<String> tokens = new ArrayList<String>(Arrays.asList(p.split("\\s+")));
            String direction = "IN";
            boolean sawIn = false;
            // 方向キーワードを抽出 (Oracle: name IN OUT type / PG: [mode] name type)
            for (int i = 0; i < tokens.size(); ) {
                String u = tokens.get(i).toUpperCase(Locale.ROOT);
                if (u.equals("IN")) {
                    sawIn = true;
                    direction = "IN";
                    tokens.remove(i);
                } else if (u.equals("OUT")) {
                    direction = sawIn ? "INOUT" : "OUT";
                    tokens.remove(i);
                } else if (u.equals("INOUT")) {
                    direction = "INOUT";
                    tokens.remove(i);
                } else if (u.equals("VARIADIC") || u.equals("NOCOPY")) {
                    tokens.remove(i);
                } else {
                    i++;
                }
            }
            ParameterMetadata param = new ParameterMetadata();
            param.setDirection(direction);
            if (tokens.size() >= 2) {
                param.setName(normalize(tokens.get(0)));
                param.setTypeName(join(tokens.subList(1, tokens.size()), " "));
            } else if (tokens.size() == 1) {
                param.setTypeName(tokens.get(0));
            }
            routine.getParameters().add(param);
        }
    }

    private void parsePackage(String stmt, String head, ParseResult result) {
        Matcher m = P_PACKAGE.matcher(head);
        m.find();
        boolean isBody = m.group(3) != null;
        String[] qn = SqlTextUtil.splitQualified(m.group(4));
        SchemaMetadata schema = schemaOf(result, qn);
        String name = normalize(qn[1]);
        PackageMetadata pkg = null;
        for (PackageMetadata p : schema.getPackages()) {
            if (p.getName().equalsIgnoreCase(name)) {
                pkg = p;
                break;
            }
        }
        if (pkg == null) {
            pkg = new PackageMetadata(name);
            schema.getPackages().add(pkg);
        }
        if (isBody) {
            pkg.setBodySource(stmt);
        } else {
            pkg.setSpecSource(stmt);
        }
    }

    // ------------------------------------------------------------------
    // SEQUENCE / INDEX / SYNONYM
    // ------------------------------------------------------------------

    private void parseSequence(String head, ParseResult result) {
        Matcher m = P_SEQUENCE.matcher(head);
        m.find();
        String[] qn = SqlTextUtil.splitQualified(m.group(2));
        SequenceMetadata seq = new SequenceMetadata(normalize(qn[1]));
        seq.setStartValue(findLong(head, "START\\s+WITH\\s+(-?\\d+)"));
        seq.setIncrementBy(findLong(head, "INCREMENT\\s+BY\\s+(-?\\d+)"));
        seq.setMinValue(findLong(head, "MINVALUE\\s+(-?\\d+)"));
        seq.setMaxValue(findLong(head, "MAXVALUE\\s+(-?\\d+)"));
        String upper = head.toUpperCase(Locale.ROOT);
        if (upper.matches(".*\\bNO\\s*CYCLE\\b.*")) {
            seq.setCycle(Boolean.FALSE);
        } else if (upper.matches(".*\\bCYCLE\\b.*")) {
            seq.setCycle(Boolean.TRUE);
        }
        schemaOf(result, qn).getSequences().add(seq);
    }

    private void parseIndex(String head, ParseResult result) {
        Matcher m = P_INDEX.matcher(head);
        m.find();
        boolean unique = m.group(1) != null;
        String[] idxName = SqlTextUtil.splitQualified(m.group(3));
        String[] tableName = SqlTextUtil.splitQualified(m.group(4));
        int open = m.end() - 1;
        int close = SqlTextUtil.findMatchingParen(head, open);
        List<String> columns = (close > open)
                ? normalizeList(head.substring(open + 1, close))
                : new ArrayList<String>();

        TableMetadata table = findTable(result.getMetadata(), tableName);
        if (table == null) {
            result.addWarning("index target table not found: " + m.group(4)
                    + " (index " + m.group(3) + ")");
            return;
        }
        table.getIndexes().add(new IndexMetadata(normalize(idxName[1]), unique, columns));
        if (unique) {
            table.getUniqueConstraints().add(
                    new UniqueConstraintMetadata(normalize(idxName[1]), columns));
        }
    }

    private void parseSynonym(String head, ParseResult result) {
        Matcher m = P_SYNONYM.matcher(head);
        m.find();
        boolean isPublic = m.group(2) != null;
        String[] qn = SqlTextUtil.splitQualified(m.group(3));
        String[] target = SqlTextUtil.splitQualified(m.group(4));
        SynonymMetadata syn = new SynonymMetadata(
                normalize(qn[1]),
                target[0] == null ? null : normalize(target[0]),
                normalize(target[1]));
        String schemaName = isPublic ? "PUBLIC" : (qn[0] != null ? normalize(qn[0]) : options.getDefaultSchema());
        result.getMetadata().findOrCreateSchema(schemaName).getSynonyms().add(syn);
    }

    // ------------------------------------------------------------------
    // ALTER TABLE ADD CONSTRAINT / COMMENT
    // ------------------------------------------------------------------

    private void parseAlterAddConstraint(String head, ParseResult result) {
        Matcher m = P_ALTER_ADD.matcher(head);
        m.find();
        String[] tq = SqlTextUtil.splitQualified(m.group(2));
        String constraintName = m.group(4) == null ? null : normalize(m.group(4));
        String kind = m.group(5).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");

        TableMetadata table = findTable(result.getMetadata(), tq);
        if (table == null) {
            result.addWarning("ALTER TABLE target not found: " + m.group(2));
            return;
        }
        String rest = head.substring(m.end(5));
        int open = rest.indexOf('(');
        if (open < 0) {
            result.addWarning("could not parse constraint body: " + summarize(head));
            return;
        }
        int close = SqlTextUtil.findMatchingParen(rest, open);
        if (close < 0) {
            result.addWarning("could not parse constraint body: " + summarize(head));
            return;
        }
        String inner = rest.substring(open + 1, close);

        if (kind.equals("PRIMARY KEY")) {
            table.setPrimaryKey(new PrimaryKeyMetadata(constraintName, normalizeList(inner)));
            for (String pkCol : table.getPrimaryKey().getColumns()) {
                ColumnMetadata c = table.findColumn(pkCol);
                if (c != null) {
                    c.setNullable(false);
                }
            }
        } else if (kind.equals("UNIQUE")) {
            table.getUniqueConstraints().add(
                    new UniqueConstraintMetadata(constraintName, normalizeList(inner)));
        } else if (kind.equals("CHECK")) {
            table.getCheckConstraints().add(new CheckConstraintMetadata(constraintName, inner.trim()));
        } else if (kind.equals("FOREIGN KEY")) {
            ForeignKeyMetadata fk = new ForeignKeyMetadata();
            fk.setName(constraintName);
            fk.setColumns(normalizeList(inner));
            Matcher ref = Pattern.compile(
                    "\\bREFERENCES\\s+(" + QNAME + ")\\s*(\\(([^)]*)\\))?",
                    Pattern.CASE_INSENSITIVE).matcher(rest.substring(close + 1));
            if (ref.find()) {
                String[] rq = SqlTextUtil.splitQualified(ref.group(1));
                fk.setReferencedSchema(rq[0] == null ? null : normalize(rq[0]));
                fk.setReferencedTable(normalize(rq[1]));
                if (ref.group(3) != null) {
                    fk.setReferencedColumns(normalizeList(ref.group(3)));
                }
            }
            table.getForeignKeys().add(fk);
        }
    }

    private void parseComment(String head, ParseResult result) {
        Matcher m = P_COMMENT.matcher(head);
        m.find();
        String kind = m.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        String target = m.group(2);
        String literal = m.group(3);
        String comment = literal.equalsIgnoreCase("NULL")
                ? null
                : literal.substring(1, literal.length() - 1).replace("''", "'");

        if (kind.equals("COLUMN")) {
            // schema.table.column または table.column
            String[] parts = target.split("\\.");
            if (parts.length < 2) {
                result.addWarning("could not resolve COMMENT ON COLUMN target: " + target);
                return;
            }
            String columnName = normalize(parts[parts.length - 1]);
            String tableName = normalize(parts[parts.length - 2]);
            String schemaName = parts.length >= 3 ? normalize(parts[parts.length - 3]) : null;
            TableMetadata table = findTable(result.getMetadata(),
                    new String[] {schemaName, tableName});
            if (table == null) {
                result.addWarning("COMMENT ON COLUMN target table not found: " + target);
                return;
            }
            ColumnMetadata col = table.findColumn(columnName);
            if (col == null) {
                result.addWarning("COMMENT ON COLUMN target column not found: " + target);
                return;
            }
            col.setComment(comment);
        } else {
            String[] qn = SqlTextUtil.splitQualified(target);
            if (kind.equals("TABLE")) {
                TableMetadata table = findTable(result.getMetadata(),
                        new String[] {qn[0] == null ? null : normalize(qn[0]), normalize(qn[1])});
                if (table != null) {
                    table.setComment(comment);
                    return;
                }
            }
            // TABLE指定でもビューに対するコメントの場合があるため探索する
            SchemaMetadata schema = qn[0] != null
                    ? result.getMetadata().findSchema(normalize(qn[0]))
                    : result.getMetadata().findSchema(options.getDefaultSchema());
            if (schema != null) {
                String name = normalize(qn[1]);
                for (ViewMetadata v : schema.getViews()) {
                    if (v.getName().equalsIgnoreCase(name)) {
                        v.setComment(comment);
                        return;
                    }
                }
                for (MaterializedViewMetadata v : schema.getMaterializedViews()) {
                    if (v.getName().equalsIgnoreCase(name)) {
                        v.setComment(comment);
                        return;
                    }
                }
            }
            result.addWarning("COMMENT target not found: " + target);
        }
    }

    // ------------------------------------------------------------------
    // その他のCREATE文 (汎用)
    // ------------------------------------------------------------------

    private void parseGeneric(String stmt, String head, ParseResult result) {
        String upper = head.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        String afterCreate = upper.startsWith("CREATE OR REPLACE ")
                ? upper.substring("CREATE OR REPLACE ".length())
                : upper.substring("CREATE ".length());
        String objectType = null;
        for (String t : MULTI_WORD_TYPES) {
            if (afterCreate.startsWith(t + " ")) {
                objectType = t;
                break;
            }
        }
        Matcher m = P_CREATE_GENERIC.matcher(head);
        if (objectType == null) {
            if (m.find()) {
                objectType = m.group(2).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
                // 複数語にマッチしすぎた場合は先頭1語に落とす
                if (objectType.contains(" ")) {
                    objectType = objectType.split(" ")[0];
                }
            } else {
                objectType = "UNKNOWN";
            }
        }
        String name = extractNameAfterKeyword(stmt, objectType.split(" ")[objectType.split(" ").length - 1]);
        addGeneric(result, name, objectType, stmt);
    }

    private void addGeneric(ParseResult result, String rawName, String objectType, String ddl) {
        String[] qn = rawName == null
                ? new String[] {null, "(unknown)"}
                : SqlTextUtil.splitQualified(rawName);
        GenericObjectMetadata obj = new GenericObjectMetadata(
                qn[1] == null ? "(unknown)" : normalize(qn[1]), objectType, ddl);
        schemaOf(result, qn).getOthers().add(obj);
    }

    /** 指定キーワードの直後に現れる識別子を取り出す。 */
    private String extractNameAfterKeyword(String stmt, String keyword) {
        Matcher m = Pattern.compile(
                "\\b" + keyword + "\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(" + QNAME + ")",
                Pattern.CASE_INSENSITIVE).matcher(stmt);
        return m.find() ? m.group(2) : null;
    }

    // ------------------------------------------------------------------
    // 共通ヘルパー
    // ------------------------------------------------------------------

    /** 文の先頭部分(コメント除去済み)。正規表現での分類・シグネチャ抽出に使う。 */
    private String headOf(String stmt) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = stmt.length();
        while (i < n) {
            char c = stmt.charAt(i);
            if (c == '-' && i + 1 < n && stmt.charAt(i + 1) == '-') {
                int end = stmt.indexOf('\n', i);
                i = (end < 0) ? n : end;
                continue;
            }
            if (c == '/' && i + 1 < n && stmt.charAt(i + 1) == '*') {
                int end = stmt.indexOf("*/", i + 2);
                i = (end < 0) ? n : end + 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString().trim();
    }

    private SchemaMetadata schemaOf(ParseResult result, String[] qualifiedName) {
        String schemaName = qualifiedName[0] != null
                ? normalize(qualifiedName[0])
                : options.getDefaultSchema();
        return result.getMetadata().findOrCreateSchema(schemaName);
    }

    private TableMetadata findTable(DatabaseMetadata db, String[] qualifiedName) {
        if (qualifiedName[0] != null) {
            SchemaMetadata schema = db.findSchema(normalize(qualifiedName[0]));
            return schema == null ? null : schema.findTable(normalize(qualifiedName[1]));
        }
        // スキーマ修飾なし: デフォルトスキーマ→全スキーマの順に探索
        SchemaMetadata def = db.findSchema(options.getDefaultSchema());
        if (def != null) {
            TableMetadata t = def.findTable(normalize(qualifiedName[1]));
            if (t != null) {
                return t;
            }
        }
        for (SchemaMetadata s : db.getSchemas()) {
            TableMetadata t = s.findTable(normalize(qualifiedName[1]));
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private String normalize(String ident) {
        return SqlTextUtil.normalizeIdentifier(ident, options.getDialect());
    }

    private String normalizeOrDefault(String schemaName) {
        return schemaName == null ? options.getDefaultSchema() : normalize(schemaName);
    }

    private List<String> normalizeList(String commaSeparated) {
        List<String> result = new ArrayList<String>();
        for (String s : SqlTextUtil.splitTopLevel(commaSeparated, ',')) {
            String t = s.trim();
            if (!t.isEmpty()) {
                // "col ASC" のような修飾を除去
                String[] tokens = t.split("\\s+");
                result.add(normalize(tokens[0]));
            }
        }
        return result;
    }

    private List<String> normalizeAll(List<String> idents) {
        List<String> result = new ArrayList<String>();
        if (idents != null) {
            for (String s : idents) {
                result.add(normalize(s));
            }
        }
        return result;
    }

    private Long findLong(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    private int indexAfterMatch(String stmt, Matcher headMatcher) {
        // headOfでコメントを除去しているため、元のstmtでの再検索が必要
        Matcher m = P_ROUTINE.matcher(stmt);
        return m.find() ? m.end() : headMatcher.end();
    }

    private static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static String summarize(String stmt) {
        String oneLine = stmt.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 80 ? oneLine.substring(0, 80) + "..." : oneLine;
    }
}
