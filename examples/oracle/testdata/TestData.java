import java.sql.Connection;
import java.sql.SQLException;

import io.github.aqucc.ddltools.testdata.TestDataLoader;

/** ddl-tools が生成したテストデータ投入コード。 */
public class TestData {

    public static void load(Connection conn) throws SQLException {
        // HR.DEPT
        TestDataLoader.insertRow(conn, "HR.DEPT",
                new String[] {"DEPT_ID", "DEPT_NAME", "LOCATION"},
                new Object[] {1L, "dept_name_1", "iota"});
        TestDataLoader.insertRow(conn, "HR.DEPT",
                new String[] {"DEPT_ID", "DEPT_NAME", "LOCATION"},
                new Object[] {2L, "dept_name_2", null});
        TestDataLoader.insertRow(conn, "HR.DEPT",
                new String[] {"DEPT_ID", "DEPT_NAME", "LOCATION"},
                new Object[] {3L, "dept_name_3", "iota"});
        TestDataLoader.insertRow(conn, "HR.DEPT",
                new String[] {"DEPT_ID", "DEPT_NAME", "LOCATION"},
                new Object[] {4L, "dept_name_4", "gamma"});
        TestDataLoader.insertRow(conn, "HR.DEPT",
                new String[] {"DEPT_ID", "DEPT_NAME", "LOCATION"},
                new Object[] {5L, "dept_name_5", "gamma"});
        // HR.EMP
        TestDataLoader.insertRow(conn, "HR.EMP",
                new String[] {"EMP_ID", "EMP_NAME", "EMAIL", "SALARY", "STATUS", "HIRE_DATE", "UPDATED_AT", "DEPT_ID", "MEMO"},
                new Object[] {1L, "eta", "email_1", new java.math.BigDecimal("780.32"), "ACTIVE", java.time.LocalDate.parse("2024-07-30"), java.time.LocalDateTime.parse("2024-07-28T08:43:26"), 1L, "text-1"});
        TestDataLoader.insertRow(conn, "HR.EMP",
                new String[] {"EMP_ID", "EMP_NAME", "EMAIL", "SALARY", "STATUS", "HIRE_DATE", "UPDATED_AT", "DEPT_ID", "MEMO"},
                new Object[] {2L, "beta", "email_2", new java.math.BigDecimal("460.30"), "ACTIVE", java.time.LocalDate.parse("2024-10-05"), null, 2L, "text-2"});
        TestDataLoader.insertRow(conn, "HR.EMP",
                new String[] {"EMP_ID", "EMP_NAME", "EMAIL", "SALARY", "STATUS", "HIRE_DATE", "UPDATED_AT", "DEPT_ID", "MEMO"},
                new Object[] {3L, "alpha", "email_3", new java.math.BigDecimal("910.85"), "ACTIVE", java.time.LocalDate.parse("2024-02-27"), java.time.LocalDateTime.parse("2024-10-30T01:24:59"), 3L, "text-3"});
        TestDataLoader.insertRow(conn, "HR.EMP",
                new String[] {"EMP_ID", "EMP_NAME", "EMAIL", "SALARY", "STATUS", "HIRE_DATE", "UPDATED_AT", "DEPT_ID", "MEMO"},
                new Object[] {4L, "theta", "email_4", new java.math.BigDecimal("939.75"), "ACTIVE", java.time.LocalDate.parse("2024-02-14"), java.time.LocalDateTime.parse("2024-09-29T19:13:20"), 4L, "text-4"});
        TestDataLoader.insertRow(conn, "HR.EMP",
                new String[] {"EMP_ID", "EMP_NAME", "EMAIL", "SALARY", "STATUS", "HIRE_DATE", "UPDATED_AT", "DEPT_ID", "MEMO"},
                new Object[] {5L, "alpha", "email_5", new java.math.BigDecimal("644.10"), "ACTIVE", java.time.LocalDate.parse("2024-05-18"), null, 5L, "text-5"});
    }
}
