package io.github.aqucc.ddltools.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.aqucc.ddltools.model.ColumnMetadata;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.parse.DdlParser;
import io.github.aqucc.ddltools.parse.Fixtures;
import io.github.aqucc.ddltools.parse.ParseOptions;

class TestDataGeneratorTest {

    private DatabaseMetadata oracle() {
        return new DdlParser(new ParseOptions(Dialect.ORACLE))
                .parse(Fixtures.read("oracle_sample.sql")).getMetadata();
    }

    private DatabaseMetadata postgres() {
        return new DdlParser(new ParseOptions(Dialect.POSTGRESQL))
                .parse(Fixtures.read("postgres_sample.sql")).getMetadata();
    }

    private TestDataSet generate(DatabaseMetadata db, GenerationOptions opts) {
        return new TestDataGenerator(db, opts).generate();
    }

    @Test
    void generatesRequestedRowCountForAllTables() {
        TestDataSet ds = generate(oracle(), new GenerationOptions().setRows(7));
        assertThat(ds.getTables()).hasSize(2);
        for (TableData t : ds.getTables()) {
            assertThat(t.getRows()).hasSize(7);
        }
    }

    @Test
    void parentTableComesBeforeChild() {
        TestDataSet ds = generate(oracle(), new GenerationOptions());
        assertThat(ds.getTables().get(0).getTableName()).isEqualTo("DEPT");
        assertThat(ds.getTables().get(1).getTableName()).isEqualTo("EMP");
    }

    @Test
    void primaryKeyValuesAreUnique() {
        TestDataSet ds = generate(oracle(), new GenerationOptions().setRows(50));
        TableData emp = ds.findTable("EMP");
        int pkIndex = indexOf(emp, "EMP_ID");
        Set<Object> seen = new HashSet<Object>();
        for (List<Object> row : emp.getRows()) {
            assertThat(seen.add(row.get(pkIndex))).as("duplicate PK value").isTrue();
        }
    }

    @Test
    void notNullColumnsAlwaysHaveValues() {
        TestDataSet ds = generate(oracle(), new GenerationOptions().setRows(30));
        for (TableData table : ds.getTables()) {
            for (int c = 0; c < table.getColumns().size(); c++) {
                ColumnMetadata col = table.getColumns().get(c);
                if (!col.isNullable()) {
                    for (List<Object> row : table.getRows()) {
                        assertThat(row.get(c))
                                .as(table.getTableName() + "." + col.getName() + " must not be null")
                                .isNotNull();
                    }
                }
            }
        }
    }

    @Test
    void checkInListIsRespected() {
        TestDataSet ds = generate(oracle(), new GenerationOptions().setRows(30));
        TableData emp = ds.findTable("EMP");
        int statusIndex = indexOf(emp, "STATUS");
        for (List<Object> row : emp.getRows()) {
            assertThat(row.get(statusIndex)).isIn("ACTIVE", "RETIRED", "LEAVE");
        }
    }

    @Test
    void checkRangeIsRespected() {
        TestDataSet ds = generate(postgres(), new GenerationOptions().setRows(30));
        TableData customer = ds.findTable("customer");
        int ageIndex = indexOf(customer, "age");
        for (List<Object> row : customer.getRows()) {
            Object age = row.get(ageIndex);
            if (age != null) {
                assertThat(((Number) age).longValue()).isGreaterThanOrEqualTo(18L);
            }
        }
    }

    @Test
    void sameSeedProducesSameData() {
        TestDataSet a = generate(oracle(), new GenerationOptions().setSeed(123).setRows(10));
        TestDataSet b = generate(oracle(), new GenerationOptions().setSeed(123).setRows(10));
        InsertWriter writer = new InsertWriter(Dialect.ORACLE);
        assertThat(writer.writeAll(a)).isEqualTo(writer.writeAll(b));

        TestDataSet c = generate(oracle(), new GenerationOptions().setSeed(999).setRows(10));
        assertThat(writer.writeAll(c)).isNotEqualTo(writer.writeAll(a));
    }

    @Test
    void foreignKeyValuesMatchParent() {
        TestDataSet ds = generate(oracle(), new GenerationOptions().setRows(10));
        TableData dept = ds.findTable("DEPT");
        TableData emp = ds.findTable("EMP");
        Set<Object> deptIds = new HashSet<Object>();
        int deptPk = indexOf(dept, "DEPT_ID");
        for (List<Object> row : dept.getRows()) {
            deptIds.add(row.get(deptPk));
        }
        int empFk = indexOf(emp, "DEPT_ID");
        for (List<Object> row : emp.getRows()) {
            assertThat(deptIds).contains(row.get(empFk));
        }
    }

    @Test
    void viewJoinColumnsShareValuesRowByRow() {
        TestDataSet ds = generate(postgres(), new GenerationOptions().setRows(10));
        TableData customer = ds.findTable("customer");
        TableData orders = ds.findTable("orders");
        int custPk = indexOf(customer, "customer_id");
        int orderFk = indexOf(orders, "customer_id");
        for (int i = 0; i < 10; i++) {
            assertThat(orders.getRows().get(i).get(orderFk))
                    .isEqualTo(customer.getRows().get(i).get(custPk));
        }
    }

    @Test
    void viewWhereConditionFixesColumnValue() {
        // v_emp_dept は WHERE e.status = 'ACTIVE' を持つため、statusは常にACTIVEになる
        TestDataSet ds = generate(oracle(), new GenerationOptions().setRows(10));
        TableData emp = ds.findTable("EMP");
        int statusIndex = indexOf(emp, "STATUS");
        for (List<Object> row : emp.getRows()) {
            assertThat(row.get(statusIndex)).isEqualTo("ACTIVE");
        }
    }

    @Test
    void identityColumnsAreExcluded() {
        TestDataSet ds = generate(postgres(), new GenerationOptions());
        TableData orders = ds.findTable("orders");
        for (String name : orders.getColumnNames()) {
            assertThat(name).isNotEqualTo("order_id");
        }
    }

    @Test
    void stringValuesRespectLength() {
        TestDataSet ds = generate(oracle(), new GenerationOptions().setRows(30));
        for (TableData table : ds.getTables()) {
            for (int c = 0; c < table.getColumns().size(); c++) {
                ColumnMetadata col = table.getColumns().get(c);
                if (col.getLength() != null) {
                    for (List<Object> row : table.getRows()) {
                        Object v = row.get(c);
                        if (v instanceof String) {
                            assertThat(((String) v).length())
                                    .as(table.getTableName() + "." + col.getName())
                                    .isLessThanOrEqualTo(col.getLength());
                        }
                    }
                }
            }
        }
    }

    private static int indexOf(TableData table, String columnName) {
        String[] names = table.getColumnNames();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        throw new AssertionError("column not found: " + columnName);
    }
}
