package io.github.aqucc.ddltools.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.parse.DdlParser;
import io.github.aqucc.ddltools.parse.Fixtures;
import io.github.aqucc.ddltools.parse.ParseOptions;

class InventoryReporterTest {

    private final InventoryReporter reporter = new InventoryReporter();

    private DatabaseMetadata oracle() {
        return new DdlParser(new ParseOptions(Dialect.ORACLE))
                .parse(Fixtures.read("oracle_sample.sql")).getMetadata();
    }

    @Test
    void collectsAllObjectTypes() {
        List<InventoryReporter.Row> rows = reporter.collectRows(oracle());
        assertThat(rows).extracting("objectType")
                .contains("TABLE", "INDEX", "VIEW", "MATERIALIZED VIEW", "TRIGGER",
                        "PROCEDURE", "FUNCTION", "SEQUENCE", "SYNONYM", "PACKAGE", "TYPE");
    }

    @Test
    void textFormatHasHeaderAndSummary() {
        String text = reporter.report(oracle(), InventoryReporter.Format.TEXT);
        assertThat(text).contains("SCHEMA").contains("TYPE").contains("NAME");
        assertThat(text).contains("Total:");
        assertThat(text).contains("EMP");
    }

    @Test
    void csvFormatIsWellFormed() {
        String csv = reporter.report(oracle(), InventoryReporter.Format.CSV);
        String[] lines = csv.split("\n");
        assertThat(lines[0]).isEqualTo("schema,object_type,name,detail");
        assertThat(lines.length).isGreaterThan(10);
    }

    @Test
    void jsonFormatContainsSummary() {
        String json = reporter.report(oracle(), InventoryReporter.Format.JSON);
        assertThat(json).contains("\"objects\"").contains("\"summary\"").contains("\"total\"");
    }
}
