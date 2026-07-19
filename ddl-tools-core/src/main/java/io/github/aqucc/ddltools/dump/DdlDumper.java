package io.github.aqucc.ddltools.dump;

import java.util.List;

/**
 * DBから全オブジェクトのDDLをダンプする。
 */
public interface DdlDumper {

    DumpResult dump(List<String> schemas);
}
