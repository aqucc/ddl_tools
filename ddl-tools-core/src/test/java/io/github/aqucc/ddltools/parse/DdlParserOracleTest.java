package io.github.aqucc.ddltools.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.aqucc.ddltools.model.ColumnMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.model.GenericObjectMetadata;
import io.github.aqucc.ddltools.model.PackageMetadata;
import io.github.aqucc.ddltools.model.RoutineMetadata;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.SequenceMetadata;
import io.github.aqucc.ddltools.model.SynonymMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.model.TriggerMetadata;

class DdlParserOracleTest {

    private static ParseResult result;
    private static SchemaMetadata hr;

    @BeforeAll
    static void parseFixture() {
        DdlParser parser = new DdlParser(new ParseOptions(Dialect.ORACLE));
        result = parser.parse(Fixtures.read("oracle_sample.sql"));
        hr = result.getMetadata().findSchema("HR");
    }

    @Test
    void schemaAndObjectCounts() {
        assertThat(hr).isNotNull();
        assertThat(hr.getTables()).hasSize(2);
        assertThat(hr.getViews()).hasSize(1);
        assertThat(hr.getMaterializedViews()).hasSize(1);
        assertThat(hr.getTriggers()).hasSize(1);
        assertThat(hr.getRoutines()).hasSize(2);
        assertThat(hr.getSequences()).hasSize(1);
        assertThat(hr.getSynonyms()).hasSize(1);
        assertThat(hr.getPackages()).hasSize(1);
        assertThat(hr.getOthers()).hasSize(1);
    }

    @Test
    void tableColumnsAndTypes() {
        TableMetadata emp = hr.findTable("EMP");
        assertThat(emp).isNotNull();
        assertThat(emp.getColumns()).hasSize(9);

        ColumnMetadata salary = emp.findColumn("SALARY");
        assertThat(salary.getTypeName()).isEqualTo("NUMBER");
        assertThat(salary.getPrecision()).isEqualTo(10);
        assertThat(salary.getScale()).isEqualTo(2);
        assertThat(salary.isNullable()).isFalse();
        assertThat(salary.getDefaultValue()).isEqualTo("0");

        ColumnMetadata name = emp.findColumn("EMP_NAME");
        assertThat(name.getTypeName()).isEqualTo("VARCHAR2");
        assertThat(name.getLength()).isEqualTo(50);

        ColumnMetadata deptName = hr.findTable("DEPT").findColumn("DEPT_NAME");
        assertThat(deptName.getLength()).isEqualTo(30);

        ColumnMetadata status = emp.findColumn("STATUS");
        assertThat(status.getDefaultValue()).isEqualTo("'ACTIVE'");
    }

    @Test
    void constraintsFromCreateAndAlter() {
        TableMetadata dept = hr.findTable("DEPT");
        assertThat(dept.getPrimaryKey().getName()).isEqualTo("PK_DEPT");
        assertThat(dept.getPrimaryKey().getColumns()).containsExactly("DEPT_ID");
        assertThat(dept.getUniqueConstraints()).extracting("name").contains("UQ_DEPT_NAME");

        TableMetadata emp = hr.findTable("EMP");
        assertThat(emp.getPrimaryKey().getName()).isEqualTo("PK_EMP");
        assertThat(emp.getForeignKeys()).hasSize(1);
        assertThat(emp.getForeignKeys().get(0).getReferencedTable()).isEqualTo("DEPT");
        assertThat(emp.getForeignKeys().get(0).getColumns()).containsExactly("DEPT_ID");
        assertThat(emp.getCheckConstraints()).hasSize(2);
        assertThat(emp.getCheckConstraints().get(0).getExpression()).contains("'ACTIVE'");
        assertThat(emp.getIndexes()).extracting("name").contains("IX_EMP_EMAIL", "IX_EMP_DEPT");
    }

    @Test
    void comments() {
        TableMetadata emp = hr.findTable("EMP");
        assertThat(emp.getComment()).isEqualTo("従業員");
        assertThat(emp.findColumn("EMP_NAME").getComment()).isEqualTo("氏名");
    }

    @Test
    void viewAndMaterializedView() {
        assertThat(hr.getViews().get(0).getName()).isEqualTo("V_EMP_DEPT");
        assertThat(hr.getViews().get(0).getDefinitionSql()).startsWith("SELECT");
        assertThat(hr.getMaterializedViews().get(0).getName()).isEqualTo("MV_DEPT_SALARY");
        assertThat(hr.getMaterializedViews().get(0).getDefinitionSql()).contains("GROUP BY");
    }

    @Test
    void trigger() {
        TriggerMetadata trg = hr.getTriggers().get(0);
        assertThat(trg.getName()).isEqualTo("TRG_EMP_UPD");
        assertThat(trg.getTiming()).isEqualTo("BEFORE");
        assertThat(trg.getEvents()).containsExactly("INSERT", "UPDATE");
        assertThat(trg.getTargetTable()).isEqualTo("EMP");
        assertThat(trg.getLevel()).isEqualTo("ROW");
        assertThat(trg.getBody()).contains(":NEW.updated_at");
    }

    @Test
    void routines() {
        RoutineMetadata proc = findRoutine("RAISE_SALARY");
        assertThat(proc.getRoutineType()).isEqualTo("PROCEDURE");
        assertThat(proc.getParameters()).hasSize(2);
        assertThat(proc.getParameters().get(0).getName()).isEqualTo("P_EMP_ID");
        assertThat(proc.getParameters().get(0).getDirection()).isEqualTo("IN");
        assertThat(proc.getParameters().get(1).getDirection()).isEqualTo("INOUT");

        RoutineMetadata func = findRoutine("GET_DEPT_NAME");
        assertThat(func.getRoutineType()).isEqualTo("FUNCTION");
        assertThat(func.getReturnType()).isEqualTo("VARCHAR2");
        assertThat(func.getSourceText()).contains("SELECT dept_name");
    }

    private RoutineMetadata findRoutine(String name) {
        for (RoutineMetadata r : hr.getRoutines()) {
            if (r.getName().equals(name)) {
                return r;
            }
        }
        throw new AssertionError("routine not found: " + name);
    }

    @Test
    void packageSpecAndBody() {
        PackageMetadata pkg = hr.getPackages().get(0);
        assertThat(pkg.getName()).isEqualTo("EMP_PKG");
        assertThat(pkg.getSpecSource()).contains("PROCEDURE hire");
        assertThat(pkg.getBodySource()).contains("INSERT INTO hr.emp");
    }

    @Test
    void sequenceAndSynonymAndGeneric() {
        SequenceMetadata seq = hr.getSequences().get(0);
        assertThat(seq.getName()).isEqualTo("EMP_SEQ");
        assertThat(seq.getStartValue()).isEqualTo(1L);
        assertThat(seq.getIncrementBy()).isEqualTo(1L);
        assertThat(seq.getCycle()).isFalse();

        SynonymMetadata syn = hr.getSynonyms().get(0);
        assertThat(syn.getName()).isEqualTo("EMPLOYEES");
        assertThat(syn.getTargetName()).isEqualTo("EMP");

        GenericObjectMetadata type = hr.getOthers().get(0);
        assertThat(type.getObjectType()).isEqualTo("TYPE");
        assertThat(type.getName()).isEqualTo("ADDR_TYPE");
        assertThat(type.getDdlText()).contains("AS OBJECT");
    }
}
