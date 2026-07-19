package io.github.aqucc.ddltools.parse;

import java.util.ArrayList;
import java.util.List;

import io.github.aqucc.ddltools.model.DatabaseMetadata;

/**
 * DDLパースの結果。メタ情報と警告のリストを保持する。
 */
public class ParseResult {

    private final DatabaseMetadata metadata;
    private final List<String> warnings = new ArrayList<String>();

    public ParseResult(DatabaseMetadata metadata) {
        this.metadata = metadata;
    }

    public DatabaseMetadata getMetadata() {
        return metadata;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }
}
