import java.sql.Connection;
import java.sql.SQLException;

import io.github.aqucc.ddltools.testdata.TestDataLoader;

/** ddl-tools が生成したテストデータ投入コード。 */
public class TestData {

    public static void load(Connection conn) throws SQLException {
        // sales.customer
        TestDataLoader.insertRow(conn, "sales.customer",
                new String[] {"customer_id", "name", "email", "age", "vip", "created_at"},
                new Object[] {1L, "alpha", "email_1", null, Boolean.FALSE, java.time.LocalDateTime.parse("2024-01-31T13:05:38")});
        TestDataLoader.insertRow(conn, "sales.customer",
                new String[] {"customer_id", "name", "email", "age", "vip", "created_at"},
                new Object[] {2L, "kappa", "email_2", null, Boolean.FALSE, java.time.LocalDateTime.parse("2024-06-25T12:56:32")});
        TestDataLoader.insertRow(conn, "sales.customer",
                new String[] {"customer_id", "name", "email", "age", "vip", "created_at"},
                new Object[] {3L, "eta", "email_3", 27L, Boolean.TRUE, java.time.LocalDateTime.parse("2024-12-09T02:33:23")});
        TestDataLoader.insertRow(conn, "sales.customer",
                new String[] {"customer_id", "name", "email", "age", "vip", "created_at"},
                new Object[] {4L, "beta", "email_4", 105L, Boolean.FALSE, java.time.LocalDateTime.parse("2024-10-02T12:50:05")});
        TestDataLoader.insertRow(conn, "sales.customer",
                new String[] {"customer_id", "name", "email", "age", "vip", "created_at"},
                new Object[] {5L, "theta", "email_5", 111L, Boolean.TRUE, java.time.LocalDateTime.parse("2024-01-30T11:25:23")});
        // sales.orders
        TestDataLoader.insertRow(conn, "sales.orders",
                new String[] {"customer_id", "order_date", "amount", "status", "note"},
                new Object[] {1L, java.time.LocalDate.parse("2024-08-30"), new java.math.BigDecimal("939.75"), "NEW", "theta"});
        TestDataLoader.insertRow(conn, "sales.orders",
                new String[] {"customer_id", "order_date", "amount", "status", "note"},
                new Object[] {2L, java.time.LocalDate.parse("2024-07-02"), new java.math.BigDecimal("271.93"), "SHIPPED", "alpha"});
        TestDataLoader.insertRow(conn, "sales.orders",
                new String[] {"customer_id", "order_date", "amount", "status", "note"},
                new Object[] {3L, java.time.LocalDate.parse("2024-09-22"), new java.math.BigDecimal("257.13"), "PAID", "theta"});
        TestDataLoader.insertRow(conn, "sales.orders",
                new String[] {"customer_id", "order_date", "amount", "status", "note"},
                new Object[] {4L, java.time.LocalDate.parse("2024-08-17"), new java.math.BigDecimal("275.52"), "PAID", "iota"});
        TestDataLoader.insertRow(conn, "sales.orders",
                new String[] {"customer_id", "order_date", "amount", "status", "note"},
                new Object[] {5L, java.time.LocalDate.parse("2024-05-19"), new java.math.BigDecimal("837.75"), "NEW", "delta"});
    }
}
