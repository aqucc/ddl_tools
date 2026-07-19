package io.github.aqucc.ddltools.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * DB接続なしで完結するCLIのE2Eフローを検証する:
 * サンプルDDLの {@code parse} → メタ情報JSON → {@code list} / {@code gen-data}。
 *
 * <p>coreモジュールのテストフィクスチャはcliモジュールから参照できないため、
 * DDLは文字列リテラルとして本クラス内に保持する。
 */
class DdlToolsCliTest {

    /** テーブル2つ(PK/FK付き)+ビュー1つの小さなOracleサンプルDDL。 */
    private static final String ORACLE_SAMPLE_DDL =
            "CREATE TABLE dept (\n"
                    + "  dept_id NUMBER(4) NOT NULL,\n"
                    + "  dept_name VARCHAR2(50) NOT NULL,\n"
                    + "  CONSTRAINT pk_dept PRIMARY KEY (dept_id)\n"
                    + ");\n"
                    + "\n"
                    + "CREATE TABLE emp (\n"
                    + "  emp_id NUMBER(6) NOT NULL,\n"
                    + "  emp_name VARCHAR2(50) NOT NULL,\n"
                    + "  dept_id NUMBER(4),\n"
                    + "  CONSTRAINT pk_emp PRIMARY KEY (emp_id),\n"
                    + "  CONSTRAINT fk_emp_dept FOREIGN KEY (dept_id) REFERENCES dept (dept_id)\n"
                    + ");\n"
                    + "\n"
                    + "CREATE VIEW emp_view AS\n"
                    + "SELECT e.emp_id, e.emp_name, d.dept_name\n"
                    + "FROM emp e JOIN dept d ON e.dept_id = d.dept_id;\n";

    @TempDir
    Path tempDir;

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void redirectStreams() {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String stdout() {
        return new String(capturedOut.toByteArray(), StandardCharsets.UTF_8);
    }

    private Path writeSampleDdl() throws Exception {
        Path ddlFile = tempDir.resolve("oracle_sample.sql");
        Files.write(ddlFile, ORACLE_SAMPLE_DDL.getBytes(StandardCharsets.UTF_8));
        return ddlFile;
    }

    private int execute(String... args) {
        return new CommandLine(new DdlToolsCli()).execute(args);
    }

    @Test
    void parseGeneratesMetadataJsonFromDdlFile() throws Exception {
        Path ddlFile = writeSampleDdl();
        Path jsonFile = tempDir.resolve("metadata.json");

        int exitCode = execute("parse",
                "--in", ddlFile.toString(),
                "--dialect", "oracle",
                "--out", jsonFile.toString());

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(jsonFile)).isTrue();
        String json = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
        assertThat(json).contains("\"DEPT\"").contains("\"EMP\"").contains("\"EMP_VIEW\"");
        assertThat(json).contains("\"dialect\" : \"ORACLE\"");
    }

    @Test
    void listWithCsvFormatContainsTableNames() throws Exception {
        Path ddlFile = writeSampleDdl();
        Path jsonFile = tempDir.resolve("metadata.json");
        assertThat(execute("parse",
                "--in", ddlFile.toString(),
                "--dialect", "oracle",
                "--out", jsonFile.toString())).isEqualTo(0);

        capturedOut.reset();
        int exitCode = execute("list",
                "--in", jsonFile.toString(),
                "--format", "csv");

        assertThat(exitCode).isEqualTo(0);
        String csv = stdout();
        assertThat(csv).contains("schema,object_type,name,detail");
        assertThat(csv).contains("DEPT").contains("EMP").contains("TABLE");
    }

    @Test
    void genDataWithInsertFormatProducesInsertStatements() throws Exception {
        Path ddlFile = writeSampleDdl();
        Path jsonFile = tempDir.resolve("metadata.json");
        assertThat(execute("parse",
                "--in", ddlFile.toString(),
                "--dialect", "oracle",
                "--out", jsonFile.toString())).isEqualTo(0);

        Path outDir = tempDir.resolve("gen-out");
        int exitCode = execute("gen-data",
                "--in", jsonFile.toString(),
                "--format", "insert",
                "--rows", "3",
                "--out", outDir.toString());

        assertThat(exitCode).isEqualTo(0);
        Path sqlFile = outDir.resolve("insert_data.sql");
        assertThat(Files.exists(sqlFile)).isTrue();
        String sql = new String(Files.readAllBytes(sqlFile), StandardCharsets.UTF_8);
        assertThat(sql).contains("INSERT INTO");
        assertThat(sql).contains("DEPT");
        assertThat(sql).contains("EMP");
    }
}
