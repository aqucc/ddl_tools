package io.github.aqucc.ddltools.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.insert.Insert;

import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.parse.DdlParser;
import io.github.aqucc.ddltools.parse.DdlScriptSplitter;
import io.github.aqucc.ddltools.parse.Fixtures;
import io.github.aqucc.ddltools.parse.ParseOptions;

class WritersTest {

    private TestDataSet dataSet(Dialect dialect, String fixture) {
        DatabaseMetadata db = new DdlParser(new ParseOptions(dialect))
                .parse(Fixtures.read(fixture)).getMetadata();
        return new TestDataGenerator(db, new GenerationOptions().setRows(5)).generate();
    }

    @Test
    void insertStatementsAreParseableSql() throws Exception {
        TestDataSet ds = dataSet(Dialect.POSTGRESQL, "postgres_sample.sql");
        String script = new InsertWriter(Dialect.POSTGRESQL).writeAll(ds);
        List<String> statements = new DdlScriptSplitter(Dialect.POSTGRESQL).split(script);
        assertThat(statements).hasSize(10);
        for (String stmt : statements) {
            // 生成したINSERT文が構文的に正しいことをJSqlParserで自己検証する
            Object parsed = CCJSqlParserUtil.parse(stmt);
            assertThat(parsed).isInstanceOf(Insert.class);
        }
    }

    @Test
    void oracleInsertUsesToDate() {
        TestDataSet ds = dataSet(Dialect.ORACLE, "oracle_sample.sql");
        String script = new InsertWriter(Dialect.ORACLE).writeAll(ds);
        assertThat(script).contains("INSERT INTO HR.EMP");
        assertThat(script).contains("TO_DATE('");
    }

    @Test
    void javaSnippetHasThreeArgumentCalls() {
        TestDataSet ds = dataSet(Dialect.ORACLE, "oracle_sample.sql");
        String source = new JavaSnippetWriter().writeClass(ds, "com.example", "TestData");
        assertThat(source).contains("package com.example;");
        assertThat(source).contains("public class TestData");
        assertThat(source).contains("TestDataLoader.insertRow(conn, \"HR.DEPT\",");
        assertThat(source).contains("new String[] {\"DEPT_ID\", \"DEPT_NAME\", \"LOCATION\"}");
        assertThat(source).contains("new Object[] {");
    }

    @Test
    void csvHasHeaderAndRows() {
        TestDataSet ds = dataSet(Dialect.POSTGRESQL, "postgres_sample.sql");
        java.util.Map<String, String> files = CsvWriter.csv().writeAll(ds);
        assertThat(files).containsKeys("sales_customer.csv", "sales_orders.csv");
        String[] lines = files.get("sales_customer.csv").split("\n");
        assertThat(lines[0]).startsWith("customer_id,");
        assertThat(lines).hasSize(6);
    }

    @Test
    void tsvUsesTabDelimiter() {
        TestDataSet ds = dataSet(Dialect.POSTGRESQL, "postgres_sample.sql");
        java.util.Map<String, String> files = CsvWriter.tsv().writeAll(ds);
        assertThat(files.keySet().iterator().next()).endsWith(".tsv");
        assertThat(files.values().iterator().next()).contains("\t");
    }
}
