package io.github.aqucc.ddltools.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import io.github.aqucc.ddltools.connect.JdbcRowSource;
import io.github.aqucc.ddltools.connect.RowSource;
import io.github.aqucc.ddltools.dump.DdlObject;
import io.github.aqucc.ddltools.dump.DumpResult;
import io.github.aqucc.ddltools.dump.OracleDdlDumper;
import io.github.aqucc.ddltools.extract.OracleMetadataExtractor;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.parse.DdlScriptSplitter;
import io.github.aqucc.ddltools.testdata.GenerationOptions;
import io.github.aqucc.ddltools.testdata.InsertWriter;
import io.github.aqucc.ddltools.testdata.TestDataGenerator;
import io.github.aqucc.ddltools.testdata.TestDataSet;

/**
 * Oracle実DBに対する結合テスト。
 *
 * <p>Testcontainersで{@code gvenzl/oracle-free:23-slim-faststart}を起動し、
 * システム接続でHRユーザー/スキーマを作成した上で、フィクスチャ
 * {@code fixtures/oracle_sample.sql}を流し込み、extract → gen-data(INSERT実行) →
 * ビュー参照 → DBMS_METADATA.GET_DDLによるdumpの一連の流れを実DBに対して検証する。
 * Dockerが利用できない環境ではコンテナ起動に失敗し実行できない。
 *
 * <p>単体実行: {@code mvn -pl ddl-tools-core verify -Pit -Dit.test=OracleIntegrationIT}
 *
 * <p>注意: Oracleイメージは数GBあり、初回pullに時間がかかる
 * (詳細は docs/integration-tests.md 参照)。
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OracleIntegrationIT {

    @Container
    private static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
                    .withStartupTimeout(Duration.ofMinutes(5));

    private static Connection connection;
    private static RowSource rowSource;

    /** {@link #extract()}で取得したメタ情報。後続のテストメソッドで再利用する。 */
    private static DatabaseMetadata metadata;

    @BeforeAll
    static void setUpSchema() throws Exception {
        // システム接続でHRユーザー/スキーマを作成する。
        // gvenzl/oracle-freeイメージはAPP_USERと同じパスワードをsystem/sysにも設定する。
        try (Connection sysConn = DriverManager.getConnection(
                ORACLE.getJdbcUrl(), "system", ORACLE.getPassword())) {
            try (Statement st = sysConn.createStatement()) {
                st.execute("CREATE USER hr IDENTIFIED BY hr");
                st.execute("GRANT CONNECT, RESOURCE, DBA TO hr");
                st.execute("GRANT UNLIMITED TABLESPACE TO hr");
            }
        }

        connection = DriverManager.getConnection(ORACLE.getJdbcUrl(), "hr", "hr");
        connection.setAutoCommit(true);
        rowSource = new JdbcRowSource(connection);

        String script = readFixture("oracle_sample.sql");
        for (String stmt : new DdlScriptSplitter(Dialect.ORACLE).split(script)) {
            rowSource.execute(stmt);
        }
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * extract: HRスキーマのメタ情報がテーブル/制約/ビュー/パッケージ等含めて正しく抽出できること。
     */
    @Test
    @Order(1)
    void extract() {
        DatabaseMetadata db = new OracleMetadataExtractor(rowSource).extract(Arrays.asList("HR"));
        metadata = db;
        SchemaMetadata hr = db.findSchema("HR");
        assertThat(hr).isNotNull();
        assertThat(hr.getTables()).hasSize(2);

        TableMetadata dept = hr.findTable("DEPT");
        assertThat(dept).isNotNull();
        assertThat(dept.getColumns()).hasSize(3);
        assertThat(dept.getPrimaryKey().getColumns()).containsExactly("DEPT_ID");
        assertThat(dept.getUniqueConstraints()).extracting("name").contains("UQ_DEPT_NAME");

        TableMetadata emp = hr.findTable("EMP");
        assertThat(emp).isNotNull();
        assertThat(emp.getColumns()).hasSize(9);
        assertThat(emp.getPrimaryKey().getName()).isEqualTo("PK_EMP");
        assertThat(emp.getForeignKeys()).hasSize(1);
        assertThat(emp.getForeignKeys().get(0).getReferencedTable()).isEqualTo("DEPT");
        assertThat(emp.getCheckConstraints()).hasSize(2);

        assertThat(hr.getViews()).extracting("name").contains("V_EMP_DEPT");
        assertThat(hr.getMaterializedViews()).extracting("name").contains("MV_DEPT_SALARY");
        assertThat(hr.getTriggers()).extracting("name").contains("TRG_EMP_UPD");
        assertThat(hr.getRoutines()).hasSize(2);
        assertThat(hr.getSequences()).extracting("name").contains("EMP_SEQ");
        assertThat(hr.getSynonyms()).extracting("name").contains("EMPLOYEES");
        assertThat(hr.getPackages()).extracting("name").contains("EMP_PKG");
    }

    /**
     * gen-data: FK整合(dept→emp)の順で生成したINSERT文が実DB上で全件実行できること。
     */
    @Test
    @Order(2)
    void genDataInsertsAreExecutable() {
        TestDataSet ds = new TestDataGenerator(metadata,
                new GenerationOptions().setRows(5).setSeed(42L)).generate();

        String script = new InsertWriter(Dialect.ORACLE).writeAll(ds);
        for (String stmt : new DdlScriptSplitter(Dialect.ORACLE).split(script)) {
            rowSource.execute(stmt);
        }

        assertThat(countRows("HR.DEPT")).isEqualTo(5L);
        assertThat(countRows("HR.EMP")).isEqualTo(5L);
    }

    /**
     * ビュー参照: v_emp_dept は WHERE status = 'ACTIVE' を持つが、これはビュー解析の
     * 固定値機能により全empのstatusが'ACTIVE'で生成されるため、ビュー件数はemp行数と一致する。
     */
    @Test
    @Order(3)
    void viewReturnsRows() {
        long empCount = countRows("HR.EMP");
        long viewCount = countRows("HR.V_EMP_DEPT");
        assertThat(viewCount).isGreaterThan(0L);
        assertThat(viewCount).isEqualTo(empCount);
    }

    /**
     * dump: DBMS_METADATA.GET_DDLが動作し、DEPT/EMPテーブルのDDLが取得できること。
     * Oracleではオブジェクト種別が多く完全なround-tripは複雑になるため、
     * ダンプ結果の非空とTABLE DDL内容の部分一致までを検証する。
     */
    @Test
    @Order(4)
    void dumpProducesTableDdl() {
        DumpResult dump = new OracleDdlDumper(rowSource).dump(Arrays.asList("HR"));
        assertThat(dump.getObjects()).isNotEmpty();

        DdlObject deptDdl = findObject(dump, "TABLE", "DEPT");
        assertThat(deptDdl.getDdl()).contains("CREATE TABLE").contains("DEPT");

        DdlObject empDdl = findObject(dump, "TABLE", "EMP");
        assertThat(empDdl.getDdl()).contains("CREATE TABLE").contains("EMP");
    }

    private DdlObject findObject(DumpResult result, String objectType, String name) {
        for (DdlObject o : result.getObjects()) {
            if (objectType.equals(o.getObjectType()) && name.equalsIgnoreCase(o.getObjectName())) {
                return o;
            }
        }
        throw new AssertionError(objectType + " " + name
                + " not found in dump result. warnings=" + result.getWarnings());
    }

    private long countRows(String qualifiedTable) {
        List<Map<String, Object>> rows =
                rowSource.query("SELECT count(*) AS cnt FROM " + qualifiedTable);
        return ((Number) rows.get(0).get("cnt")).longValue();
    }

    private static String readFixture(String name) throws IOException {
        try (InputStream in = OracleIntegrationIT.class.getClassLoader()
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
