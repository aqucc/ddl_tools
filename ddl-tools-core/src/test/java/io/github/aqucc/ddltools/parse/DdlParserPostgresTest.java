package io.github.aqucc.ddltools.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.aqucc.ddltools.model.ColumnMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.model.RoutineMetadata;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.model.TriggerMetadata;

class DdlParserPostgresTest {

    private static ParseResult result;
    private static SchemaMetadata sales;

    @BeforeAll
    static void parseFixture() {
        DdlParser parser = new DdlParser(new ParseOptions(Dialect.POSTGRESQL));
        result = parser.parse(Fixtures.read("postgres_sample.sql"));
        sales = result.getMetadata().findSchema("sales");
    }

    @Test
    void schemaAndObjectCounts() {
        assertThat(sales).isNotNull();
        assertThat(sales.getTables()).hasSize(2);
        assertThat(sales.getViews()).hasSize(1);
        assertThat(sales.getMaterializedViews()).hasSize(1);
        assertThat(sales.getRoutines()).hasSize(3);
        assertThat(sales.getSequences()).hasSize(1);
        // DOMAINはothersへ
        assertThat(sales.getOthers()).extracting("objectType").contains("DOMAIN");
    }

    @Test
    void tableColumnsAndConstraints() {
        TableMetadata customer = sales.findTable("customer");
        assertThat(customer).isNotNull();
        assertThat(customer.getColumns()).hasSize(6);
        assertThat(customer.getPrimaryKey().getColumns()).containsExactly("customer_id");
        assertThat(customer.findColumn("name").getLength()).isEqualTo(50);
        assertThat(customer.findColumn("vip").isNullable()).isFalse();
        // インラインCHECK
        assertThat(customer.getCheckConstraints()).isNotEmpty();

        TableMetadata orders = sales.findTable("orders");
        assertThat(orders.findColumn("order_id").getIdentity()).isTrue();
        assertThat(orders.findColumn("amount").getPrecision()).isEqualTo(12);
        assertThat(orders.findColumn("amount").getScale()).isEqualTo(2);
        assertThat(orders.getPrimaryKey().getName()).isEqualTo("pk_orders");
        assertThat(orders.getForeignKeys().get(0).getReferencedTable()).isEqualTo("customer");
        assertThat(orders.getCheckConstraints()).hasSize(2);
        assertThat(orders.getComment()).isEqualTo("注文");
        assertThat(orders.findColumn("amount").getComment()).isEqualTo("注文金額");
    }

    @Test
    void viewAndMaterializedView() {
        assertThat(sales.getViews().get(0).getName()).isEqualTo("v_customer_orders");
        assertThat(sales.getViews().get(0).getDefinitionSql()).startsWith("SELECT");
        assertThat(sales.getMaterializedViews().get(0).getName()).isEqualTo("mv_customer_total");
    }

    @Test
    void functionWithDollarQuotedBody() {
        RoutineMetadata func = findRoutine("order_total");
        assertThat(func.getRoutineType()).isEqualTo("FUNCTION");
        assertThat(func.getReturnType()).isEqualTo("numeric");
        assertThat(func.getParameters()).hasSize(1);
        assertThat(func.getParameters().get(0).getName()).isEqualTo("p_customer_id");
        assertThat(func.getParameters().get(0).getTypeName()).isEqualTo("bigint");
        assertThat(func.getSourceText()).contains("COALESCE(sum(amount), 0)");
    }

    @Test
    void procedure() {
        RoutineMetadata proc = findRoutine("cancel_order");
        assertThat(proc.getRoutineType()).isEqualTo("PROCEDURE");
        assertThat(proc.getParameters()).hasSize(1);
        assertThat(proc.getParameters().get(0).getDirection()).isEqualTo("IN");
    }

    @Test
    void trigger() {
        // トリガーはスキーマ修飾なしで定義されているためデフォルトスキーマ(public)に入る
        SchemaMetadata pub = result.getMetadata().findSchema("public");
        assertThat(pub).isNotNull();
        TriggerMetadata trg = pub.getTriggers().get(0);
        assertThat(trg.getName()).isEqualTo("trg_orders_before");
        assertThat(trg.getTiming()).isEqualTo("BEFORE");
        assertThat(trg.getEvents()).containsExactly("INSERT", "UPDATE");
        assertThat(trg.getTargetTable()).isEqualTo("orders");
        assertThat(trg.getLevel()).isEqualTo("ROW");
    }

    private RoutineMetadata findRoutine(String name) {
        for (RoutineMetadata r : sales.getRoutines()) {
            if (r.getName().equals(name)) {
                return r;
            }
        }
        throw new AssertionError("routine not found: " + name);
    }
}
