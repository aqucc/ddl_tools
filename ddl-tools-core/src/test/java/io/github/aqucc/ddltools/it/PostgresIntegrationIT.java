package io.github.aqucc.ddltools.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.aqucc.ddltools.connect.JdbcRowSource;
import io.github.aqucc.ddltools.connect.RowSource;
import io.github.aqucc.ddltools.dump.DdlObject;
import io.github.aqucc.ddltools.dump.DumpResult;
import io.github.aqucc.ddltools.dump.PostgresDdlDumper;
import io.github.aqucc.ddltools.extract.PostgresMetadataExtractor;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.parse.DdlScriptSplitter;
import io.github.aqucc.ddltools.testdata.GenerationOptions;
import io.github.aqucc.ddltools.testdata.InsertWriter;
import io.github.aqucc.ddltools.testdata.TableData;
import io.github.aqucc.ddltools.testdata.TestDataGenerator;
import io.github.aqucc.ddltools.testdata.TestDataSet;

/**
 * PostgreSQL実DBに対する結合テスト。
 *
 * <p>Testcontainersで{@code postgres:16-alpine}を起動し、フィクスチャ
 * {@code fixtures/postgres_sample.sql}(salesスキーマ)を流し込んだ上で、
 * extract → gen-data(INSERT実行) → ビュー参照 → dump/round-tripの一連の流れを
 * 実DBに対して検証する。Dockerが利用できない環境ではコンテナ起動に失敗し実行できない。
 *
 * <p>単体実行: {@code mvn -pl ddl-tools-core verify -Pit -Dit.test=PostgresIntegrationIT}
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgresIntegrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static Connection connection;
    private static RowSource rowSource;

    /** {@link #extract()}で取得したメタ情報。後続のテストメソッドで再利用する。 */
    private static DatabaseMetadata metadata;
    /** {@link #genDataInsertsAreExecutable()}で生成したテストデータ。後続のテストで再利用する。 */
    private static TestDataSet dataSet;

    @BeforeAll
    static void setUpSchema() throws Exception {
        connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setAutoCommit(true);
        rowSource = new JdbcRowSource(connection);

        String script = readFixture("postgres_sample.sql");
        for (String stmt : new DdlScriptSplitter(Dialect.POSTGRESQL).split(script)) {
            String trimmed = stmt.trim();
            // pg_trgm等のcontrib拡張はイメージに含まれない場合があるためスキップする
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("CREATE EXTENSION")) {
                continue;
            }
            rowSource.execute(trimmed);
        }
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * extract: salesスキーマのメタ情報がテーブル/制約/ビュー等含めて正しく抽出できること。
     */
    @Test
    @Order(1)
    void extract() {
        DatabaseMetadata db = new PostgresMetadataExtractor(rowSource).extract(Arrays.asList("sales"));
        metadata = db;
        SchemaMetadata sales = db.findSchema("sales");
        assertThat(sales).isNotNull();
        assertThat(sales.getTables()).hasSize(2);

        TableMetadata customer = sales.findTable("customer");
        assertThat(customer).isNotNull();
        assertThat(customer.getColumns()).hasSize(6);
        assertThat(customer.getPrimaryKey()).isNotNull();
        assertThat(customer.getPrimaryKey().getColumns()).containsExactly("customer_id");
        assertThat(customer.getCheckConstraints()).isNotEmpty();

        TableMetadata orders = sales.findTable("orders");
        assertThat(orders).isNotNull();
        assertThat(orders.getColumns()).hasSize(6);
        assertThat(orders.getPrimaryKey().getName()).isEqualTo("pk_orders");
        assertThat(orders.getForeignKeys()).hasSize(1);
        assertThat(orders.getForeignKeys().get(0).getReferencedTable()).isEqualTo("customer");
        assertThat(orders.getCheckConstraints()).hasSize(2);

        assertThat(sales.getViews()).extracting("name").contains("v_customer_orders");
        assertThat(sales.getMaterializedViews()).extracting("name").contains("mv_customer_total");
        assertThat(sales.getTriggers()).extracting("name").contains("trg_orders_before");
        assertThat(sales.getRoutines()).hasSize(3);
        assertThat(sales.getSequences()).isNotEmpty();
    }

    /**
     * gen-data: 生成したINSERT文が実DB上で全件実行でき、identity列(order_id)はDB採番されること。
     */
    @Test
    @Order(2)
    void genDataInsertsAreExecutable() {
        TestDataSet ds = new TestDataGenerator(metadata,
                new GenerationOptions().setRows(5).setSeed(42L)).generate();
        dataSet = ds;

        String script = new InsertWriter(Dialect.POSTGRESQL).writeAll(ds);
        for (String stmt : new DdlScriptSplitter(Dialect.POSTGRESQL).split(script)) {
            rowSource.execute(stmt);
        }

        assertThat(countRows("sales.customer")).isEqualTo(5L);
        assertThat(countRows("sales.orders")).isEqualTo(5L);
    }

    /**
     * ビュー参照: v_customer_orders は WHERE status &lt;&gt; 'NEW' で絞り込むため、
     * 生成されたordersのstatus値次第で0件になり得る。生成データ側の期待件数(status &lt;&gt; 'NEW'の行数)を
     * 集計してDBの実測値と突き合わせることで、乱数結果に依存しない検証にする。
     */
    @Test
    @Order(3)
    void viewReturnsRows() {
        TableData orders = dataSet.findTable("orders");
        assertThat(orders).isNotNull();
        int statusIdx = columnIndex(orders, "status");

        long expected = 0;
        for (List<Object> row : orders.getRows()) {
            if (!"NEW".equals(row.get(statusIdx))) {
                expected++;
            }
        }

        long actual = countRows("sales.v_customer_orders");
        assertThat(actual).isEqualTo(expected);
    }

    /**
     * dump/round-trip: ダンプしたDDLでスキーマを再構築でき、テーブル数・カラム数が元と一致すること。
     */
    @Test
    @Order(4)
    void dumpRoundTrip() {
        DumpResult dump = new PostgresDdlDumper(rowSource).dump(Arrays.asList("sales"));
        assertThat(dump.getObjects()).isNotEmpty();

        rowSource.execute("DROP SCHEMA sales CASCADE");
        for (DdlObject obj : dump.getObjects()) {
            for (String stmt : new DdlScriptSplitter(Dialect.POSTGRESQL).split(obj.getDdl())) {
                rowSource.execute(stmt);
            }
        }

        DatabaseMetadata redb = new PostgresMetadataExtractor(rowSource).extract(Arrays.asList("sales"));
        SchemaMetadata sales = redb.findSchema("sales");
        assertThat(sales).isNotNull();
        assertThat(sales.getTables()).hasSize(2);

        SchemaMetadata originalSales = metadata.findSchema("sales");
        assertThat(sales.findTable("customer").getColumns())
                .hasSameSizeAs(originalSales.findTable("customer").getColumns());
        assertThat(sales.findTable("orders").getColumns())
                .hasSameSizeAs(originalSales.findTable("orders").getColumns());
    }

    private long countRows(String qualifiedTable) {
        List<Map<String, Object>> rows =
                rowSource.query("SELECT count(*) AS cnt FROM " + qualifiedTable);
        return ((Number) rows.get(0).get("cnt")).longValue();
    }

    private static int columnIndex(TableData table, String columnName) {
        String[] names = table.getColumnNames();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        throw new AssertionError("column not found: " + columnName);
    }

    private static String readFixture(String name) throws IOException {
        try (InputStream in = PostgresIntegrationIT.class.getClassLoader()
                .getResourceAsStream("fixtures/" + name)) {
            if (in == null) {
                throw new IOException("fixture not found on classpath: fixtures/" + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
