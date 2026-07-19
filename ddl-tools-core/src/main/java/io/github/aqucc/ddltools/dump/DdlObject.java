package io.github.aqucc.ddltools.dump;

/**
 * ダンプされた1オブジェクト分のDDL。
 */
public class DdlObject {

    private final String schemaName;
    private final String objectType;
    private final String objectName;
    private final String ddl;

    public DdlObject(String schemaName, String objectType, String objectName, String ddl) {
        this.schemaName = schemaName;
        this.objectType = objectType;
        this.objectName = objectName;
        this.ddl = ddl;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getObjectType() {
        return objectType;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getDdl() {
        return ddl;
    }
}
