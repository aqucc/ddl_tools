package io.github.aqucc.ddltools.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DB全体のメタ情報のルートオブジェクト。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DatabaseMetadata {

    public static final String CURRENT_FORMAT_VERSION = "1.0";

    private String formatVersion = CURRENT_FORMAT_VERSION;
    private Dialect dialect;
    private String extractedAt;
    private List<SchemaMetadata> schemas = new ArrayList<SchemaMetadata>();

    public DatabaseMetadata() {
    }

    public DatabaseMetadata(Dialect dialect) {
        this.dialect = dialect;
    }

    /** スキーマ名で検索し、無ければ作成して返す。 */
    public SchemaMetadata findOrCreateSchema(String name) {
        for (SchemaMetadata s : schemas) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        SchemaMetadata s = new SchemaMetadata(name);
        schemas.add(s);
        return s;
    }

    /** スキーマ名で検索(存在しなければnull)。 */
    public SchemaMetadata findSchema(String name) {
        for (SchemaMetadata s : schemas) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }

    public String getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(String formatVersion) {
        this.formatVersion = formatVersion;
    }

    public Dialect getDialect() {
        return dialect;
    }

    public void setDialect(Dialect dialect) {
        this.dialect = dialect;
    }

    public String getExtractedAt() {
        return extractedAt;
    }

    public void setExtractedAt(String extractedAt) {
        this.extractedAt = extractedAt;
    }

    public List<SchemaMetadata> getSchemas() {
        return schemas;
    }

    public void setSchemas(List<SchemaMetadata> schemas) {
        this.schemas = schemas;
    }
}
