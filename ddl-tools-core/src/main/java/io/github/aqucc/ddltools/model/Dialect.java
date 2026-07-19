package io.github.aqucc.ddltools.model;

/**
 * 対象RDBMSの方言。
 */
public enum Dialect {
    ORACLE,
    POSTGRESQL;

    public static Dialect fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("dialect is null");
        }
        String v = value.trim().toUpperCase();
        if ("ORACLE".equals(v)) {
            return ORACLE;
        }
        if ("POSTGRESQL".equals(v) || "POSTGRES".equals(v) || "PG".equals(v)) {
            return POSTGRESQL;
        }
        throw new IllegalArgumentException("unknown dialect: " + value);
    }
}
