package io.github.aqucc.ddltools.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.aqucc.ddltools.connect.FakeRowSource;
import io.github.aqucc.ddltools.model.ColumnMetadata;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.model.ForeignKeyMetadata;
import io.github.aqucc.ddltools.model.GenericObjectMetadata;
import io.github.aqucc.ddltools.model.PackageMetadata;
import io.github.aqucc.ddltools.model.RoutineMetadata;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.SequenceMetadata;
import io.github.aqucc.ddltools.model.SynonymMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.model.TriggerMetadata;

/**
 * {@link OracleMetadataExtractor}のユニットテスト。
 * 実DBの代わりに{@link FakeRowSource}へHRスキーマ相当の辞書ビュー結果を登録して検証する。
 */
class OracleMetadataExtractorTest {

    private static DatabaseMetadata db;
    private static SchemaMetadata hr;

    @BeforeAll
    static void extractFixture() {
        FakeRowSource rows = buildFixture();
        OracleMetadataExtractor extractor = new OracleMetadataExtractor(rows);
        db = extractor.extract(Arrays.asList("HR"));
        hr = db.findSchema("HR");
    }

    private static FakeRowSource buildFixture() {
        FakeRowSource rows = new FakeRowSource();

        // --- all_tables / all_tab_columns ---
        rows.on("FROM all_tables WHERE")
                .row("owner", "HR", "table_name", "EMP")
                .row("owner", "HR", "table_name", "DEPT");

        rows.on("FROM all_tab_columns WHERE")
                .row("owner", "HR", "table_name", "EMP", "column_name", "EMP_ID",
                        "data_type", "NUMBER", "data_precision", 6, "data_scale", 0,
                        "nullable", "N")
                .row("owner", "HR", "table_name", "EMP", "column_name", "DEPT_ID",
                        "data_type", "NUMBER", "data_precision", 4, "data_scale", 0,
                        "nullable", "Y")
                .row("owner", "HR", "table_name", "EMP", "column_name", "STATUS",
                        "data_type", "VARCHAR2", "char_length", 10, "nullable", "Y",
                        "data_default", "'ACTIVE'")
                .row("owner", "HR", "table_name", "DEPT", "column_name", "DEPT_ID",
                        "data_type", "NUMBER", "data_precision", 4, "data_scale", 0,
                        "nullable", "N")
                .row("owner", "HR", "table_name", "DEPT", "column_name", "DEPT_NAME",
                        "data_type", "VARCHAR2", "char_length", 30, "nullable", "N");

        // --- all_cons_columns / all_constraints ---
        rows.on("FROM all_cons_columns WHERE")
                .row("owner", "HR", "constraint_name", "PK_DEPT", "table_name", "DEPT",
                        "column_name", "DEPT_ID")
                .row("owner", "HR", "constraint_name", "PK_EMP", "table_name", "EMP",
                        "column_name", "EMP_ID")
                .row("owner", "HR", "constraint_name", "FK_EMP_DEPT", "table_name", "EMP",
                        "column_name", "DEPT_ID");

        rows.on("FROM all_constraints WHERE")
                .row("owner", "HR", "constraint_name", "PK_DEPT", "table_name", "DEPT",
                        "constraint_type", "P")
                .row("owner", "HR", "constraint_name", "PK_EMP", "table_name", "EMP",
                        "constraint_type", "P")
                .row("owner", "HR", "constraint_name", "FK_EMP_DEPT", "table_name", "EMP",
                        "constraint_type", "R", "r_owner", "HR", "r_constraint_name", "PK_DEPT")
                .row("owner", "HR", "constraint_name", "CK_EMP_STATUS", "table_name", "EMP",
                        "constraint_type", "C", "search_condition", "STATUS IN ('ACTIVE','INACTIVE')")
                .row("owner", "HR", "constraint_name", "SYS_C00001", "table_name", "EMP",
                        "constraint_type", "C", "search_condition", "\"EMP_ID\" IS NOT NULL");

        // --- all_ind_columns / all_indexes ---
        rows.on("FROM all_ind_columns WHERE")
                .row("index_owner", "HR", "index_name", "IX_EMP_DEPT", "column_name", "DEPT_ID");
        rows.on("FROM all_indexes WHERE")
                .row("owner", "HR", "index_name", "IX_EMP_DEPT", "table_owner", "HR",
                        "table_name", "EMP", "uniqueness", "NONUNIQUE");

        // --- comments ---
        rows.on("FROM all_tab_comments")
                .row("owner", "HR", "table_name", "EMP", "comments", "従業員");
        rows.on("FROM all_col_comments")
                .row("owner", "HR", "table_name", "EMP", "column_name", "STATUS",
                        "comments", "ステータス");

        // --- views / materialized views ---
        rows.on("FROM all_views WHERE")
                .row("owner", "HR", "view_name", "V_EMP", "text", "SELECT * FROM emp");
        rows.on("FROM all_mviews WHERE")
                .row("owner", "HR", "mview_name", "MV_DEPT_COUNT",
                        "query", "SELECT dept_id, COUNT(*) FROM emp GROUP BY dept_id");

        // --- triggers ---
        rows.on("FROM all_triggers WHERE")
                .row("owner", "HR", "trigger_name", "TRG_EMP_BIU",
                        "trigger_type", "BEFORE EACH ROW",
                        "triggering_event", "INSERT OR UPDATE",
                        "table_name", "EMP", "trigger_body", ":NEW.updated_at := SYSDATE;");

        // --- routines (all_objects PROCEDURE/FUNCTION) ---
        rows.on("object_type IN ('PROCEDURE', 'FUNCTION')")
                .row("owner", "HR", "object_name", "GET_DEPT_NAME", "object_type", "FUNCTION");
        rows.on("FROM all_arguments WHERE")
                .row("position", 0, "data_type", "VARCHAR2")
                .row("argument_name", "P_DEPT_ID", "position", 1, "in_out", "IN",
                        "data_type", "NUMBER");
        rows.on("FROM all_source WHERE")
                .whenParams("GET_DEPT_NAME", "FUNCTION")
                .row("text", "FUNCTION get_dept_name(p_dept_id NUMBER) RETURN VARCHAR2 IS\n")
                .row("text", "BEGIN RETURN 'X'; END;\n");

        // --- sequences / synonyms ---
        rows.on("FROM all_sequences WHERE")
                .row("sequence_owner", "HR", "sequence_name", "EMP_SEQ", "min_value", 1,
                        "max_value", 999999999999999999L, "increment_by", 1, "cycle_flag", "N");
        rows.on("FROM all_synonyms WHERE")
                .row("owner", "HR", "synonym_name", "EMPLOYEES", "table_owner", "HR",
                        "table_name", "EMP");

        // --- packages ---
        rows.on("DISTINCT owner, name")
                .row("owner", "HR", "name", "EMP_PKG");
        rows.on("FROM all_source WHERE")
                .whenParams("EMP_PKG", "PACKAGE BODY")
                .row("text", "PACKAGE BODY emp_pkg IS\n")
                .row("text", "  PROCEDURE hire(p_name VARCHAR2) IS BEGIN INSERT INTO hr.emp VALUES (p_name); END;\nEND emp_pkg;\n");
        rows.on("FROM all_source WHERE")
                .whenParams("EMP_PKG", "PACKAGE")
                .row("text", "PACKAGE emp_pkg IS\n")
                .row("text", "  PROCEDURE hire(p_name VARCHAR2);\nEND emp_pkg;\n");

        // --- others (all_objects NOT IN) ---
        rows.on("object_type NOT IN")
                .row("owner", "HR", "object_name", "ADDR_TYPE", "object_type", "TYPE");

        return rows;
    }

    @Test
    void schemaAndObjectCounts() {
        assertThat(hr).isNotNull();
        assertThat(db.getDialect()).isEqualTo(Dialect.ORACLE);
        assertThat(hr.getTables()).hasSize(2);
        assertThat(hr.getViews()).hasSize(1);
        assertThat(hr.getMaterializedViews()).hasSize(1);
        assertThat(hr.getTriggers()).hasSize(1);
        assertThat(hr.getRoutines()).hasSize(1);
        assertThat(hr.getSequences()).hasSize(1);
        assertThat(hr.getSynonyms()).hasSize(1);
        assertThat(hr.getPackages()).hasSize(1);
        assertThat(hr.getOthers()).hasSize(1);
    }

    @Test
    void tableColumnsAndTypes() {
        TableMetadata emp = hr.findTable("EMP");
        assertThat(emp.getColumns()).hasSize(3);

        ColumnMetadata empId = emp.findColumn("EMP_ID");
        assertThat(empId.getTypeName()).isEqualTo("NUMBER");
        assertThat(empId.getPrecision()).isEqualTo(6);
        assertThat(empId.getScale()).isEqualTo(0);
        assertThat(empId.isNullable()).isFalse();

        ColumnMetadata deptId = emp.findColumn("DEPT_ID");
        assertThat(deptId.isNullable()).isTrue();

        ColumnMetadata status = emp.findColumn("STATUS");
        assertThat(status.getTypeName()).isEqualTo("VARCHAR2");
        assertThat(status.getLength()).isEqualTo(10);
        assertThat(status.getDefaultValue()).isEqualTo("'ACTIVE'");

        TableMetadata dept = hr.findTable("DEPT");
        assertThat(dept.getColumns()).hasSize(2);
        assertThat(dept.findColumn("DEPT_NAME").getLength()).isEqualTo(30);
    }

    @Test
    void constraintsAndIndexes() {
        TableMetadata dept = hr.findTable("DEPT");
        assertThat(dept.getPrimaryKey().getName()).isEqualTo("PK_DEPT");
        assertThat(dept.getPrimaryKey().getColumns()).containsExactly("DEPT_ID");

        TableMetadata emp = hr.findTable("EMP");
        assertThat(emp.getPrimaryKey().getName()).isEqualTo("PK_EMP");
        assertThat(emp.getPrimaryKey().getColumns()).containsExactly("EMP_ID");

        assertThat(emp.getForeignKeys()).hasSize(1);
        ForeignKeyMetadata fk = emp.getForeignKeys().get(0);
        assertThat(fk.getName()).isEqualTo("FK_EMP_DEPT");
        assertThat(fk.getColumns()).containsExactly("DEPT_ID");
        assertThat(fk.getReferencedSchema()).isEqualTo("HR");
        assertThat(fk.getReferencedTable()).isEqualTo("DEPT");
        assertThat(fk.getReferencedColumns()).containsExactly("DEPT_ID");

        // NOT NULL由来のCHECKは除外され、通常のCHECKのみ残る
        assertThat(emp.getCheckConstraints()).hasSize(1);
        assertThat(emp.getCheckConstraints().get(0).getName()).isEqualTo("CK_EMP_STATUS");
        assertThat(emp.getCheckConstraints().get(0).getExpression())
                .isEqualTo("STATUS IN ('ACTIVE','INACTIVE')");

        assertThat(emp.getIndexes()).hasSize(1);
        assertThat(emp.getIndexes().get(0).getName()).isEqualTo("IX_EMP_DEPT");
        assertThat(emp.getIndexes().get(0).isUnique()).isFalse();
        assertThat(emp.getIndexes().get(0).getColumns()).containsExactly("DEPT_ID");
    }

    @Test
    void comments() {
        TableMetadata emp = hr.findTable("EMP");
        assertThat(emp.getComment()).isEqualTo("従業員");
        assertThat(emp.findColumn("STATUS").getComment()).isEqualTo("ステータス");
    }

    @Test
    void viewAndMaterializedView() {
        assertThat(hr.getViews().get(0).getName()).isEqualTo("V_EMP");
        assertThat(hr.getViews().get(0).getDefinitionSql()).isEqualTo("SELECT * FROM emp");
        assertThat(hr.getMaterializedViews().get(0).getName()).isEqualTo("MV_DEPT_COUNT");
        assertThat(hr.getMaterializedViews().get(0).getDefinitionSql()).contains("GROUP BY");
    }

    @Test
    void trigger() {
        TriggerMetadata trg = hr.getTriggers().get(0);
        assertThat(trg.getName()).isEqualTo("TRG_EMP_BIU");
        assertThat(trg.getTiming()).isEqualTo("BEFORE");
        assertThat(trg.getLevel()).isEqualTo("ROW");
        assertThat(trg.getEvents()).containsExactly("INSERT", "UPDATE");
        assertThat(trg.getTargetTable()).isEqualTo("EMP");
        assertThat(trg.getBody()).contains(":NEW.updated_at");
    }

    @Test
    void functionWithArgumentsAndSource() {
        RoutineMetadata func = hr.getRoutines().get(0);
        assertThat(func.getName()).isEqualTo("GET_DEPT_NAME");
        assertThat(func.getRoutineType()).isEqualTo("FUNCTION");
        assertThat(func.getReturnType()).isEqualTo("VARCHAR2");
        assertThat(func.getParameters()).hasSize(1);
        assertThat(func.getParameters().get(0).getName()).isEqualTo("P_DEPT_ID");
        assertThat(func.getParameters().get(0).getDirection()).isEqualTo("IN");
        assertThat(func.getSourceText())
                .isEqualTo("FUNCTION get_dept_name(p_dept_id NUMBER) RETURN VARCHAR2 IS\n"
                        + "BEGIN RETURN 'X'; END;\n");
    }

    @Test
    void sequenceSynonymPackageAndGeneric() {
        SequenceMetadata seq = hr.getSequences().get(0);
        assertThat(seq.getName()).isEqualTo("EMP_SEQ");
        assertThat(seq.getIncrementBy()).isEqualTo(1L);
        assertThat(seq.getCycle()).isFalse();

        SynonymMetadata syn = hr.getSynonyms().get(0);
        assertThat(syn.getName()).isEqualTo("EMPLOYEES");
        assertThat(syn.getTargetSchema()).isEqualTo("HR");
        assertThat(syn.getTargetName()).isEqualTo("EMP");

        PackageMetadata pkg = hr.getPackages().get(0);
        assertThat(pkg.getName()).isEqualTo("EMP_PKG");
        assertThat(pkg.getSpecSource()).contains("PROCEDURE hire(p_name VARCHAR2);");
        assertThat(pkg.getBodySource()).contains("INSERT INTO hr.emp");

        GenericObjectMetadata type = hr.getOthers().get(0);
        assertThat(type.getName()).isEqualTo("ADDR_TYPE");
        assertThat(type.getObjectType()).isEqualTo("TYPE");
    }
}
