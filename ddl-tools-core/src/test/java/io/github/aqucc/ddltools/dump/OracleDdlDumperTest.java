package io.github.aqucc.ddltools.dump;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.aqucc.ddltools.connect.FakeRowSource;

class OracleDdlDumperTest {

    @Test
    void dumpsSupportedObjectsAndSkipsOrWarnsOthers() {
        FakeRowSource rowSource = new FakeRowSource();
        rowSource.on("FROM all_objects")
                .row("owner", "HR", "object_name", "EMP", "object_type", "TABLE")
                .row("owner", "HR", "object_name", "EMP_LOB", "object_type", "LOB")
                .row("owner", "HR", "object_name", "RUN_JOB", "object_type", "JOB");
        rowSource.on("DBMS_METADATA.GET_DDL")
                .whenParams("EMP")
                .row("ddl", "CREATE TABLE HR.EMP (ID NUMBER);\n");

        OracleDdlDumper dumper = new OracleDdlDumper(rowSource);
        DumpResult result = dumper.dump(Arrays.asList("HR"));

        assertThat(result.getObjects()).hasSize(1);
        DdlObject table = result.getObjects().get(0);
        assertThat(table.getSchemaName()).isEqualTo("HR");
        assertThat(table.getObjectType()).isEqualTo("TABLE");
        assertThat(table.getObjectName()).isEqualTo("EMP");
        assertThat(table.getDdl()).isEqualTo("CREATE TABLE HR.EMP (ID NUMBER);");

        assertThat(result.getWarnings()).hasSize(1);
        assertThat(result.getWarnings().get(0)).contains("JOB").contains("RUN_JOB");

        boolean setTransformParamCalled = false;
        for (String statement : rowSource.getExecutedStatements()) {
            if (statement.contains("SET_TRANSFORM_PARAM")) {
                setTransformParamCalled = true;
            }
        }
        assertThat(setTransformParamCalled).isTrue();
    }
}
