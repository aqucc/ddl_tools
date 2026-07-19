package io.github.aqucc.ddltools.parse;

import io.github.aqucc.ddltools.model.Dialect;

/**
 * DDLパースのオプション。
 */
public class ParseOptions {

    private final Dialect dialect;
    /** スキーマ修飾のないオブジェクトを配置するスキーマ名 */
    private final String defaultSchema;

    public ParseOptions(Dialect dialect) {
        this(dialect, defaultSchemaFor(dialect));
    }

    public ParseOptions(Dialect dialect, String defaultSchema) {
        this.dialect = dialect;
        this.defaultSchema = defaultSchema;
    }

    private static String defaultSchemaFor(Dialect dialect) {
        return dialect == Dialect.POSTGRESQL ? "public" : "DEFAULT";
    }

    public Dialect getDialect() {
        return dialect;
    }

    public String getDefaultSchema() {
        return defaultSchema;
    }
}
