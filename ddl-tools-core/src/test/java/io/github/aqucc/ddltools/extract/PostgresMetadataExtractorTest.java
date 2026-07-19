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
import io.github.aqucc.ddltools.model.RoutineMetadata;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.SequenceMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.model.TriggerMetadata;

/**
 * {@link PostgresMetadataExtractor}のユニットテスト。
 * 実DBの代わりに{@link FakeRowSource}へsalesスキーマ相当のカタログ結果を登録して検証する。
 */
class PostgresMetadataExtractorTest {

    private static DatabaseMetadata db;
    private static SchemaMetadata sales;

    @BeforeAll
    static void extractFixture() {
        FakeRowSource rows = buildFixture();
        PostgresMetadataExtractor extractor = new PostgresMetadataExtractor(rows);
        db = extractor.extract(Arrays.asList("sales"));
        sales = db.findSchema("sales");
    }

    private static FakeRowSource buildFixture() {
        FakeRowSource rows = new FakeRowSource();

        // --- information_schema.tables / columns ---
        rows.on("FROM information_schema.tables")
                .row("table_schema", "sales", "table_name", "customer")
                .row("table_schema", "sales", "table_name", "orders");

        rows.on("FROM information_schema.columns")
                .row("table_schema", "sales", "table_name", "customer", "column_name", "id",
                        "udt_name", "int8", "is_nullable", "NO", "is_identity", "YES")
                .row("table_schema", "sales", "table_name", "customer", "column_name", "email",
                        "udt_name", "varchar", "character_maximum_length", 255,
                        "is_nullable", "NO")
                .row("table_schema", "sales", "table_name", "customer", "column_name", "balance",
                        "udt_name", "numeric", "numeric_precision", 10, "numeric_scale", 2,
                        "is_nullable", "YES")
                .row("table_schema", "sales", "table_name", "orders", "column_name", "id",
                        "udt_name", "int4", "is_nullable", "NO",
                        "column_default", "nextval('orders_id_seq'::regclass)")
                .row("table_schema", "sales", "table_name", "orders", "column_name", "customer_id",
                        "udt_name", "int8", "is_nullable", "YES")
                .row("table_schema", "sales", "table_name", "orders", "column_name", "amount",
                        "udt_name", "numeric", "numeric_precision", 12, "numeric_scale", 2,
                        "is_nullable", "NO");

        // --- key_column_usage / table_constraints ---
        rows.on("FROM information_schema.key_column_usage")
                .row("constraint_schema", "sales", "constraint_name", "customer_pkey",
                        "column_name", "id")
                .row("constraint_schema", "sales", "constraint_name", "orders_pkey",
                        "column_name", "id")
                .row("constraint_schema", "sales", "constraint_name", "orders_customer_id_fkey",
                        "column_name", "customer_id")
                .row("constraint_schema", "sales", "constraint_name", "customer_email_key",
                        "column_name", "email");

        rows.on("FROM information_schema.table_constraints")
                .row("constraint_schema", "sales", "constraint_name", "customer_pkey",
                        "table_schema", "sales", "table_name", "customer",
                        "constraint_type", "PRIMARY KEY")
                .row("constraint_schema", "sales", "constraint_name", "orders_pkey",
                        "table_schema", "sales", "table_name", "orders",
                        "constraint_type", "PRIMARY KEY")
                .row("constraint_schema", "sales", "constraint_name", "orders_customer_id_fkey",
                        "table_schema", "sales", "table_name", "orders",
                        "constraint_type", "FOREIGN KEY")
                .row("constraint_schema", "sales", "constraint_name", "customer_email_key",
                        "table_schema", "sales", "table_name", "customer",
                        "constraint_type", "UNIQUE");

        rows.on("referential_constraints")
                .row("table_schema", "sales", "table_name", "customer", "column_name", "id");

        // --- check_constraints (JOIN table_constraints) ---
        rows.on("FROM information_schema.check_constraints")
                .row("table_schema", "sales", "table_name", "orders",
                        "constraint_name", "orders_amount_check", "check_clause", "amount > 0")
                .row("table_schema", "sales", "table_name", "orders",
                        "constraint_name", "orders_customer_id_not_null",
                        "check_clause", "customer_id IS NOT NULL");

        // --- pg_index (pg_get_indexdef) ---
        rows.on("pg_get_indexdef")
                .row("schema_name", "sales", "table_name", "customer", "index_name", "ux",
                        "is_unique", Boolean.TRUE,
                        "indexdef", "CREATE UNIQUE INDEX ux ON sales.customer USING btree (email)");

        // --- pg_views / pg_matviews ---
        rows.on("FROM pg_views")
                .row("schemaname", "sales", "viewname", "v_customer_summary",
                        "definition", "SELECT id, email FROM sales.customer;");
        rows.on("FROM pg_matviews")
                .row("schemaname", "sales", "matviewname", "mv_daily_sales",
                        "definition", "SELECT sum(amount) FROM sales.orders;");

        // --- triggers (2行 -> 1トリガーに集約) ---
        rows.on("FROM information_schema.triggers")
                .row("trigger_schema", "sales", "trigger_name", "trg_orders_biu",
                        "action_timing", "BEFORE", "event_manipulation", "INSERT",
                        "event_object_table", "orders", "action_orientation", "ROW",
                        "action_statement", "EXECUTE FUNCTION set_updated_at()")
                .row("trigger_schema", "sales", "trigger_name", "trg_orders_biu",
                        "action_timing", "BEFORE", "event_manipulation", "UPDATE",
                        "event_object_table", "orders", "action_orientation", "ROW",
                        "action_statement", "EXECUTE FUNCTION set_updated_at()");

        // --- pg_proc (pg_get_functiondef) ---
        rows.on("pg_get_functiondef")
                .row("schema_name", "sales", "routine_name", "calc_total",
                        "routine_type", "FUNCTION", "return_type", "numeric",
                        "arguments", "p_id bigint, OUT total numeric",
                        "definition", "CREATE OR REPLACE FUNCTION sales.calc_total"
                                + "(p_id bigint, OUT total numeric) ...");

        // --- pg_sequences ---
        rows.on("FROM pg_sequences")
                .row("schemaname", "sales", "sequencename", "orders_id_seq",
                        "start_value", 1, "increment_by", 1, "min_value", 1,
                        "max_value", 9223372036854775807L, "cycle", Boolean.FALSE);

        // --- information_schema.domains ---
        rows.on("FROM information_schema.domains")
                .row("domain_schema", "sales", "domain_name", "email_address",
                        "data_type", "character varying");

        return rows;
    }

    @Test
    void schemaAndObjectCounts() {
        assertThat(sales).isNotNull();
        assertThat(db.getDialect()).isEqualTo(Dialect.POSTGRESQL);
        assertThat(sales.getTables()).hasSize(2);
        assertThat(sales.getViews()).hasSize(1);
        assertThat(sales.getMaterializedViews()).hasSize(1);
        assertThat(sales.getTriggers()).hasSize(1);
        assertThat(sales.getRoutines()).hasSize(1);
        assertThat(sales.getSequences()).hasSize(1);
        assertThat(sales.getOthers()).hasSize(1);
    }

    @Test
    void tableColumnsAndTypes() {
        TableMetadata customer = sales.findTable("customer");
        assertThat(customer).isNotNull();

        ColumnMetadata id = customer.findColumn("id");
        assertThat(id.isNullable()).isFalse();
        assertThat(id.getIdentity()).isTrue();

        ColumnMetadata email = customer.findColumn("email");
        assertThat(email.getLength()).isEqualTo(255);
        assertThat(email.isNullable()).isFalse();

        ColumnMetadata balance = customer.findColumn("balance");
        assertThat(balance.getPrecision()).isEqualTo(10);
        assertThat(balance.getScale()).isEqualTo(2);
        assertThat(balance.isNullable()).isTrue();

        TableMetadata orders = sales.findTable("orders");
        ColumnMetadata orderId = orders.findColumn("id");
        assertThat(orderId.getIdentity()).isTrue();
        assertThat(orderId.getDefaultValue()).isEqualTo("nextval('orders_id_seq'::regclass)");

        ColumnMetadata amount = orders.findColumn("amount");
        assertThat(amount.getPrecision()).isEqualTo(12);
        assertThat(amount.getScale()).isEqualTo(2);
    }

    @Test
    void constraintsAndChecks() {
        TableMetadata customer = sales.findTable("customer");
        assertThat(customer.getPrimaryKey().getName()).isEqualTo("customer_pkey");
        assertThat(customer.getPrimaryKey().getColumns()).containsExactly("id");
        assertThat(customer.getUniqueConstraints()).extracting("name")
                .contains("customer_email_key", "ux");

        TableMetadata orders = sales.findTable("orders");
        assertThat(orders.getPrimaryKey().getName()).isEqualTo("orders_pkey");
        assertThat(orders.getForeignKeys()).hasSize(1);
        ForeignKeyMetadata fk = orders.getForeignKeys().get(0);
        assertThat(fk.getName()).isEqualTo("orders_customer_id_fkey");
        assertThat(fk.getColumns()).containsExactly("customer_id");
        assertThat(fk.getReferencedSchema()).isEqualTo("sales");
        assertThat(fk.getReferencedTable()).isEqualTo("customer");
        assertThat(fk.getReferencedColumns()).containsExactly("id");

        // "IS NOT NULL"で終わるCHECKは除外される
        assertThat(orders.getCheckConstraints()).hasSize(1);
        assertThat(orders.getCheckConstraints().get(0).getName())
                .isEqualTo("orders_amount_check");
        assertThat(orders.getCheckConstraints().get(0).getExpression()).isEqualTo("amount > 0");
    }

    @Test
    void indexFromPgIndex() {
        TableMetadata customer = sales.findTable("customer");
        assertThat(customer.getIndexes()).hasSize(1);
        assertThat(customer.getIndexes().get(0).getName()).isEqualTo("ux");
        assertThat(customer.getIndexes().get(0).isUnique()).isTrue();
        assertThat(customer.getIndexes().get(0).getColumns()).containsExactly("email");
    }

    @Test
    void viewsAndMaterializedViews() {
        assertThat(sales.getViews().get(0).getName()).isEqualTo("v_customer_summary");
        assertThat(sales.getViews().get(0).getDefinitionSql())
                .isEqualTo("SELECT id, email FROM sales.customer");
        assertThat(sales.getMaterializedViews().get(0).getName()).isEqualTo("mv_daily_sales");
        assertThat(sales.getMaterializedViews().get(0).getDefinitionSql())
                .isEqualTo("SELECT sum(amount) FROM sales.orders");
    }

    @Test
    void triggerEventsAggregated() {
        TriggerMetadata trg = sales.getTriggers().get(0);
        assertThat(trg.getName()).isEqualTo("trg_orders_biu");
        assertThat(trg.getTiming()).isEqualTo("BEFORE");
        assertThat(trg.getLevel()).isEqualTo("ROW");
        assertThat(trg.getTargetTable()).isEqualTo("orders");
        assertThat(trg.getEvents()).containsExactly("INSERT", "UPDATE");
    }

    @Test
    void functionArguments() {
        RoutineMetadata func = sales.getRoutines().get(0);
        assertThat(func.getName()).isEqualTo("calc_total");
        assertThat(func.getRoutineType()).isEqualTo("FUNCTION");
        assertThat(func.getReturnType()).isEqualTo("numeric");
        assertThat(func.getParameters()).hasSize(2);
        assertThat(func.getParameters().get(0).getName()).isEqualTo("p_id");
        assertThat(func.getParameters().get(0).getDirection()).isEqualTo("IN");
        assertThat(func.getParameters().get(1).getName()).isEqualTo("total");
        assertThat(func.getParameters().get(1).getDirection()).isEqualTo("OUT");
        assertThat(func.getSourceText()).contains("CREATE OR REPLACE FUNCTION sales.calc_total");
    }

    @Test
    void sequenceAndGeneric() {
        SequenceMetadata seq = sales.getSequences().get(0);
        assertThat(seq.getName()).isEqualTo("orders_id_seq");
        assertThat(seq.getIncrementBy()).isEqualTo(1L);
        assertThat(seq.getCycle()).isFalse();

        GenericObjectMetadata domain = sales.getOthers().get(0);
        assertThat(domain.getName()).isEqualTo("email_address");
        assertThat(domain.getObjectType()).isEqualTo("DOMAIN");
        assertThat(domain.getDdlText()).isEqualTo("AS character varying");
    }
}
