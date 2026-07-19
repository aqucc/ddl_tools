package io.github.aqucc.ddltools.testdata;

import java.util.Locale;

import io.github.aqucc.ddltools.model.ColumnMetadata;

/**
 * 方言型名を値生成用の論理カテゴリへマッピングする。
 */
public enum TypeCategory {
    INTEGER, DECIMAL, STRING, DATE, TIMESTAMP, TIME, BOOLEAN, CLOB, BINARY, UUID, JSON, XML, OTHER;

    public static TypeCategory of(ColumnMetadata col) {
        String t = col.getTypeName() == null ? "" : col.getTypeName().toUpperCase(Locale.ROOT).trim();
        if (t.equals("NUMBER") || t.equals("NUMERIC") || t.equals("DECIMAL") || t.equals("DEC")) {
            return (col.getScale() == null || col.getScale() == 0) ? INTEGER : DECIMAL;
        }
        if (t.equals("INT") || t.equals("INTEGER") || t.equals("BIGINT") || t.equals("SMALLINT")
                || t.equals("TINYINT") || t.equals("INT2") || t.equals("INT4") || t.equals("INT8")
                || t.equals("SERIAL") || t.equals("BIGSERIAL") || t.equals("SMALLSERIAL")
                || t.equals("PLS_INTEGER") || t.equals("BINARY_INTEGER")) {
            return INTEGER;
        }
        if (t.equals("FLOAT") || t.equals("REAL") || t.equals("DOUBLE") || t.equals("DOUBLE PRECISION")
                || t.equals("BINARY_FLOAT") || t.equals("BINARY_DOUBLE") || t.equals("MONEY")) {
            return DECIMAL;
        }
        if (t.equals("CLOB") || t.equals("NCLOB") || t.equals("LONG")) {
            return CLOB;
        }
        if (t.startsWith("VARCHAR") || t.startsWith("NVARCHAR") || t.startsWith("CHAR")
                || t.startsWith("NCHAR") || t.startsWith("CHARACTER") || t.equals("BPCHAR")
                || t.equals("TEXT") || t.equals("NAME") || t.equals("CITEXT")) {
            return STRING;
        }
        if (t.equals("DATE")) {
            return DATE;
        }
        if (t.startsWith("TIMESTAMP") || t.equals("DATETIME") || t.equals("TIMESTAMPTZ")) {
            return TIMESTAMP;
        }
        if (t.startsWith("TIME")) {
            return TIME;
        }
        if (t.equals("BOOLEAN") || t.equals("BOOL")) {
            return BOOLEAN;
        }
        if (t.equals("BLOB") || t.equals("RAW") || t.equals("LONG RAW") || t.equals("BYTEA")) {
            return BINARY;
        }
        if (t.equals("UUID")) {
            return UUID;
        }
        if (t.equals("JSON") || t.equals("JSONB")) {
            return JSON;
        }
        if (t.equals("XMLTYPE") || t.equals("XML")) {
            return XML;
        }
        return OTHER;
    }
}
