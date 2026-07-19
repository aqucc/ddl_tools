package io.github.aqucc.ddltools.dump;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.aqucc.ddltools.connect.RowSource;
import io.github.aqucc.ddltools.connect.SqlIdentifiers;

/**
 * DBMS_METADATA.GET_DDL を使ってOracleの全オブジェクトのDDLをダンプする。
 */
public class OracleDdlDumper implements DdlDumper {

    /** ALL_OBJECTS.OBJECT_TYPE → DBMS_METADATAの型名 */
    private static final Map<String, String> TYPE_MAP = new HashMap<String, String>();

    static {
        TYPE_MAP.put("TABLE", "TABLE");
        TYPE_MAP.put("VIEW", "VIEW");
        TYPE_MAP.put("MATERIALIZED VIEW", "MATERIALIZED_VIEW");
        TYPE_MAP.put("INDEX", "INDEX");
        TYPE_MAP.put("SEQUENCE", "SEQUENCE");
        TYPE_MAP.put("PROCEDURE", "PROCEDURE");
        TYPE_MAP.put("FUNCTION", "FUNCTION");
        TYPE_MAP.put("PACKAGE", "PACKAGE");
        TYPE_MAP.put("TRIGGER", "TRIGGER");
        TYPE_MAP.put("SYNONYM", "SYNONYM");
        TYPE_MAP.put("TYPE", "TYPE");
        TYPE_MAP.put("JAVA SOURCE", "JAVA_SOURCE");
        TYPE_MAP.put("LIBRARY", "LIBRARY");
        TYPE_MAP.put("MATERIALIZED VIEW LOG", "MATERIALIZED_VIEW_LOG");
    }

    /** GET_DDL対象外 (親オブジェクトのDDLに含まれる、または実体がないもの) */
    private static final String[] SKIPPED_TYPES = {
            "PACKAGE BODY", "TYPE BODY", "LOB", "TABLE PARTITION", "TABLE SUBPARTITION",
            "INDEX PARTITION", "INDEX SUBPARTITION", "LOB PARTITION",
    };

    private final RowSource rowSource;

    public OracleDdlDumper(RowSource rowSource) {
        this.rowSource = rowSource;
    }

    @Override
    public DumpResult dump(List<String> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            throw new IllegalArgumentException("schemas must not be empty for Oracle");
        }
        DumpResult result = new DumpResult();
        configureSession(result);

        String owners = SqlIdentifiers.inList(schemas);
        for (Map<String, Object> o : rowSource.query(
                "SELECT owner, object_name, object_type FROM all_objects"
                        + " WHERE owner IN (" + owners + ")"
                        + " AND generated = 'N'"
                        + " ORDER BY owner, object_type, object_name")) {
            String owner = string(o, "owner");
            String name = string(o, "object_name");
            String objectType = string(o, "object_type");
            if (isSkipped(objectType)) {
                continue;
            }
            String metadataType = TYPE_MAP.get(objectType);
            if (metadataType == null) {
                result.getWarnings().add("unsupported object type skipped: "
                        + objectType + " " + owner + "." + name);
                continue;
            }
            try {
                List<Map<String, Object>> rows = rowSource.query(
                        "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) AS ddl FROM DUAL",
                        metadataType, name, owner);
                if (!rows.isEmpty() && rows.get(0).get("ddl") != null) {
                    result.getObjects().add(new DdlObject(owner, objectType, name,
                            rows.get(0).get("ddl").toString().trim()));
                }
            } catch (RuntimeException e) {
                result.getWarnings().add("GET_DDL failed for " + objectType + " "
                        + owner + "." + name + ": " + e.getMessage());
            }
        }
        return result;
    }

    /** SQLTERMINATOR等のセッション変換パラメータを設定する。失敗しても続行する。 */
    private void configureSession(DumpResult result) {
        try {
            rowSource.execute(
                    "BEGIN"
                            + " DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,"
                            + " 'SQLTERMINATOR', TRUE);"
                            + " DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,"
                            + " 'PRETTY', TRUE);"
                            + " END;");
        } catch (RuntimeException e) {
            result.getWarnings().add("could not set DBMS_METADATA transform params: "
                    + e.getMessage());
        }
    }

    private static boolean isSkipped(String objectType) {
        for (String t : SKIPPED_TYPES) {
            if (t.equals(objectType)) {
                return true;
            }
        }
        return false;
    }

    private static String string(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }
}
