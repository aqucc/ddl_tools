package io.github.aqucc.ddltools.dump;

import java.util.ArrayList;
import java.util.List;

/**
 * DDLダンプの結果。
 */
public class DumpResult {

    private final List<DdlObject> objects = new ArrayList<DdlObject>();
    private final List<String> warnings = new ArrayList<String>();

    public List<DdlObject> getObjects() {
        return objects;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
