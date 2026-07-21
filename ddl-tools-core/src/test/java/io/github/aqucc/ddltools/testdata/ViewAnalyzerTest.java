package io.github.aqucc.ddltools.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.parse.DdlParser;
import io.github.aqucc.ddltools.parse.ParseOptions;

/**
 * ビュー定義の再帰的解析(多段ビュー・派生表・CTE・UNION)を検証する。
 */
class ViewAnalyzerTest {

    private DatabaseMetadata parse(String ddl) {
        return new DdlParser(new ParseOptions(Dialect.ORACLE)).parse(ddl).getMetadata();
    }

    private ViewAnalyzer.Analysis analyzeView(DatabaseMetadata db, String viewName) {
        for (io.github.aqucc.ddltools.model.ViewMetadata v : db.findSchema("DEFAULT").getViews()) {
            if (v.getName().equals(viewName)) {
                return new ViewAnalyzer(db).analyze(v.getDefinitionSql());
            }
        }
        throw new AssertionError("view not found: " + viewName);
    }

    private boolean hasEqualPair(ViewAnalyzer.Analysis a, String key1, String key2) {
        for (String[] pair : a.getEqualPairs()) {
            if ((pair[0].equals(key1) && pair[1].equals(key2))
                    || (pair[0].equals(key2) && pair[1].equals(key1))) {
                return true;
            }
        }
        return false;
    }

    private static final String MULTI_LEVEL_DDL =
            "CREATE TABLE dept (dept_id NUMBER(4) NOT NULL, dept_name VARCHAR2(30),"
                    + " CONSTRAINT pk_dept PRIMARY KEY (dept_id));\n"
                    + "CREATE TABLE emp (emp_id NUMBER(6) NOT NULL, dept_id NUMBER(4),"
                    + " status VARCHAR2(10), CONSTRAINT pk_emp PRIMARY KEY (emp_id));\n"
                    + "CREATE VIEW v_emp_dept AS SELECT e.emp_id, e.status, d.dept_name, e.dept_id"
                    + " FROM emp e JOIN dept d ON e.dept_id = d.dept_id;\n"
                    + "CREATE VIEW v_active AS SELECT v.emp_id, v.dept_name FROM v_emp_dept v"
                    + " WHERE v.status = 'ACTIVE';\n";

    @Test
    void singleLevelViewJoinResolvesToBaseColumns() {
        DatabaseMetadata db = parse(MULTI_LEVEL_DDL);
        ViewAnalyzer.Analysis a = analyzeView(db, "V_EMP_DEPT");
        assertThat(hasEqualPair(a,
                ViewAnalyzer.columnKey("DEFAULT", "EMP", "DEPT_ID"),
                ViewAnalyzer.columnKey("DEFAULT", "DEPT", "DEPT_ID"))).isTrue();
    }

    @Test
    void nestedViewJoinPropagatesToBaseColumns() {
        DatabaseMetadata db = parse(MULTI_LEVEL_DDL);
        // v_active は v_emp_dept を参照している。末端テーブルまで辿り、
        // emp.dept_id = dept.dept_id の結合が伝播していること。
        ViewAnalyzer.Analysis a = analyzeView(db, "V_ACTIVE");
        assertThat(hasEqualPair(a,
                ViewAnalyzer.columnKey("DEFAULT", "EMP", "DEPT_ID"),
                ViewAnalyzer.columnKey("DEFAULT", "DEPT", "DEPT_ID"))).isTrue();
    }

    @Test
    void nestedViewWhereConditionPropagatesToBaseColumn() {
        DatabaseMetadata db = parse(MULTI_LEVEL_DDL);
        ViewAnalyzer.Analysis a = analyzeView(db, "V_ACTIVE");
        // v.status = 'ACTIVE' が emp.status へ伝播していること
        assertThat(a.getFixedValues())
                .containsEntry(ViewAnalyzer.columnKey("DEFAULT", "EMP", "STATUS"), "ACTIVE");
    }

    @Test
    void derivedTableSubqueryResolvesToBaseColumns() {
        DatabaseMetadata db = parse(
                "CREATE TABLE dept (dept_id NUMBER(4) NOT NULL, CONSTRAINT pk_dept PRIMARY KEY (dept_id));\n"
                        + "CREATE TABLE emp (emp_id NUMBER(6) NOT NULL, dept_id NUMBER(4),"
                        + " CONSTRAINT pk_emp PRIMARY KEY (emp_id));\n"
                        + "CREATE VIEW v AS SELECT x.emp_id FROM"
                        + " (SELECT emp_id, dept_id FROM emp) x JOIN dept d ON x.dept_id = d.dept_id;\n");
        ViewAnalyzer.Analysis a = analyzeView(db, "V");
        assertThat(hasEqualPair(a,
                ViewAnalyzer.columnKey("DEFAULT", "EMP", "DEPT_ID"),
                ViewAnalyzer.columnKey("DEFAULT", "DEPT", "DEPT_ID"))).isTrue();
    }

    @Test
    void cteResolvesToBaseColumns() {
        DatabaseMetadata db = parse(
                "CREATE TABLE dept (dept_id NUMBER(4) NOT NULL, CONSTRAINT pk_dept PRIMARY KEY (dept_id));\n"
                        + "CREATE TABLE emp (emp_id NUMBER(6) NOT NULL, dept_id NUMBER(4),"
                        + " CONSTRAINT pk_emp PRIMARY KEY (emp_id));\n"
                        + "CREATE VIEW v AS WITH c AS (SELECT dept_id FROM dept)"
                        + " SELECT e.emp_id FROM emp e JOIN c ON e.dept_id = c.dept_id;\n");
        ViewAnalyzer.Analysis a = analyzeView(db, "V");
        assertThat(hasEqualPair(a,
                ViewAnalyzer.columnKey("DEFAULT", "EMP", "DEPT_ID"),
                ViewAnalyzer.columnKey("DEFAULT", "DEPT", "DEPT_ID"))).isTrue();
    }

    @Test
    void unionViewIsAnalyzedWithoutError() {
        DatabaseMetadata db = parse(
                "CREATE TABLE dept (dept_id NUMBER(4) NOT NULL, CONSTRAINT pk_dept PRIMARY KEY (dept_id));\n"
                        + "CREATE TABLE emp (emp_id NUMBER(6) NOT NULL,"
                        + " CONSTRAINT pk_emp PRIMARY KEY (emp_id));\n"
                        + "CREATE VIEW v AS SELECT emp_id FROM emp UNION SELECT dept_id FROM dept;\n");
        // 以前は SetOperationList を解釈できず null(= could not analyze)になっていた
        ViewAnalyzer.Analysis a = analyzeView(db, "V");
        assertThat(a).isNotNull();
    }

    @Test
    void cyclicViewsDoNotCauseInfiniteLoop() {
        // 相互参照するビュー(実際にはDDLとして不正だが、解析器が停止することを確認する)
        DatabaseMetadata db = parse(
                "CREATE TABLE t (id NUMBER(4) NOT NULL, CONSTRAINT pk_t PRIMARY KEY (id));\n"
                        + "CREATE VIEW v1 AS SELECT id FROM v2;\n"
                        + "CREATE VIEW v2 AS SELECT id FROM v1;\n");
        ViewAnalyzer.Analysis a = analyzeView(db, "V1");
        assertThat(a).isNotNull();
    }
}
