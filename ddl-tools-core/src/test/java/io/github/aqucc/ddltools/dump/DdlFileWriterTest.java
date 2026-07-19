package io.github.aqucc.ddltools.dump;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DdlFileWriterTest {

    @TempDir
    Path tempDir;

    private DumpResult sampleResult() {
        DumpResult result = new DumpResult();
        result.getObjects().add(new DdlObject("sales", "SCHEMA", "sales",
                "CREATE SCHEMA IF NOT EXISTS sales;"));
        result.getObjects().add(new DdlObject("sales", "TABLE", "customer",
                "CREATE TABLE sales.customer (\n  id integer\n);"));
        result.getObjects().add(new DdlObject("sales", "MATERIALIZED VIEW", "summary",
                "CREATE MATERIALIZED VIEW sales.summary AS\nSELECT 1;"));
        return result;
    }

    @Test
    void writeSplitCreatesOneFilePerObjectUnderSchemaAndType() throws IOException {
        DdlFileWriter writer = new DdlFileWriter();
        List<Path> written = writer.writeSplit(sampleResult(), tempDir);

        assertThat(written).hasSize(3);

        Path schemaFile = tempDir.resolve("sales").resolve("schema").resolve("sales.sql");
        Path tableFile = tempDir.resolve("sales").resolve("table").resolve("customer.sql");
        Path mviewFile = tempDir.resolve("sales").resolve("materialized_view").resolve("summary.sql");

        assertThat(written).contains(schemaFile, tableFile, mviewFile);
        assertThat(schemaFile).exists();
        assertThat(Files.readAllLines(schemaFile, StandardCharsets.UTF_8))
                .containsExactly("CREATE SCHEMA IF NOT EXISTS sales;");
        assertThat(new String(Files.readAllBytes(tableFile), StandardCharsets.UTF_8))
                .isEqualTo("CREATE TABLE sales.customer (\n  id integer\n);");
        assertThat(mviewFile).exists();
    }

    @Test
    void writeSingleConcatenatesAllObjectsWithHeaderComments() throws IOException {
        DdlFileWriter writer = new DdlFileWriter();
        Path outFile = tempDir.resolve("all.sql");
        writer.writeSingle(sampleResult(), outFile);

        String content = new String(Files.readAllBytes(outFile), StandardCharsets.UTF_8);
        assertThat(content).contains("-- sales.sales (SCHEMA)\nCREATE SCHEMA IF NOT EXISTS sales;");
        assertThat(content).contains("-- sales.customer (TABLE)\nCREATE TABLE sales.customer");
        assertThat(content).contains("-- sales.summary (MATERIALIZED VIEW)\nCREATE MATERIALIZED VIEW");
        assertThat(content).contains("\n\n");
    }

    @Test
    void sanitizesUnsafeCharactersInNames() throws IOException {
        DumpResult result = new DumpResult();
        result.getObjects().add(new DdlObject("my schema", "FOREIGN TABLE", "weird/name?",
                "CREATE FOREIGN TABLE dummy;"));

        DdlFileWriter writer = new DdlFileWriter();
        List<Path> written = writer.writeSplit(result, tempDir);

        assertThat(written).hasSize(1);
        Path file = written.get(0);
        assertThat(file.getFileName().toString()).isEqualTo("weird_name_.sql");
        assertThat(file.getParent().getFileName().toString()).isEqualTo("foreign_table");
        assertThat(file.getParent().getParent().getFileName().toString()).isEqualTo("my_schema");
        assertThat(file).exists();
    }
}
