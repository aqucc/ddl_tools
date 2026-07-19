package io.github.aqucc.ddltools.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.aqucc.ddltools.model.Dialect;

class DdlScriptSplitterTest {

    @Test
    void splitsSimpleStatements() {
        List<String> stmts = new DdlScriptSplitter(Dialect.POSTGRESQL)
                .split("CREATE TABLE a (x int);\nCREATE TABLE b (y int);");
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).startsWith("CREATE TABLE a");
    }

    @Test
    void ignoresSemicolonsInLiteralsAndComments() {
        String script = "CREATE TABLE a (x varchar(10) DEFAULT 'a;b');\n"
                + "-- comment; with semicolon\n"
                + "/* block; comment */\n"
                + "CREATE TABLE b (y int);";
        List<String> stmts = new DdlScriptSplitter(Dialect.POSTGRESQL).split(script);
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).contains("'a;b'");
    }

    @Test
    void keepsDollarQuotedBodyTogether() {
        String script = "CREATE FUNCTION f() RETURNS int AS $$\n"
                + "BEGIN\n  RETURN 1;\nEND;\n$$ LANGUAGE plpgsql;\n"
                + "CREATE TABLE t (x int);";
        List<String> stmts = new DdlScriptSplitter(Dialect.POSTGRESQL).split(script);
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).contains("RETURN 1;");
    }

    @Test
    void oraclePlsqlBlockEndsAtSlash() {
        String script = "CREATE OR REPLACE PROCEDURE p AS\nBEGIN\n  NULL;\nEND;\n/\n"
                + "CREATE TABLE t (x NUMBER);";
        List<String> stmts = new DdlScriptSplitter(Dialect.ORACLE).split(script);
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).endsWith("END;");
        assertThat(stmts.get(1)).startsWith("CREATE TABLE t");
    }

    @Test
    void oracleFixtureSplitsAllStatements() {
        List<String> stmts = new DdlScriptSplitter(Dialect.ORACLE)
                .split(Fixtures.read("oracle_sample.sql"));
        // sequence, dept, emp, 4 alter, 2 index, 2 comment, view, mview,
        // trigger, procedure, function, package, package body, synonym, type
        assertThat(stmts).hasSize(20);
    }

    @Test
    void postgresFixtureSplitsAllStatements() {
        List<String> stmts = new DdlScriptSplitter(Dialect.POSTGRESQL)
                .split(Fixtures.read("postgres_sample.sql"));
        // schema, sequence, 2 table, 4 alter, 2 index, 2 comment, view, mview,
        // 2 function, trigger, procedure, domain, extension
        assertThat(stmts).hasSize(20);
    }
}
