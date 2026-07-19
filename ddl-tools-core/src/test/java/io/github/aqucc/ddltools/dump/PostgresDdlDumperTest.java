package io.github.aqucc.ddltools.dump;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.aqucc.ddltools.connect.FakeRowSource;

class PostgresDdlDumperTest {

    private FakeRowSource rowSource() {
        FakeRowSource fake = new FakeRowSource();

        fake.on("FROM pg_sequences")
                .row("schemaname", "sales", "sequencename", "customer_id_seq",
                        "start_value", 1L, "increment_by", 1L, "min_value", 1L,
                        "max_value", 9223372036854775807L, "cycle", Boolean.FALSE);

        fake.on("c.relkind IN ('r', 'p')")
                .row("schema_name", "sales", "table_name", "customer");

        fake.on("FROM pg_attribute a")
                .whenParams("sales", "customer")
                .row("column_name", "id", "data_type", "integer",
                        "not_null", Boolean.TRUE, "default_value", null)
                .row("column_name", "name", "data_type", "character varying(100)",
                        "not_null", Boolean.FALSE, "default_value", "'unknown'::character varying");

        fake.on("pg_get_constraintdef(oid)")
                .whenParams("sales", "customer")
                .row("conname", "customer_pkey", "condef", "PRIMARY KEY (id)");

        fake.on("FROM pg_views")
                .row("schemaname", "sales", "viewname", "active_customer",
                        "definition", "SELECT * FROM sales.customer WHERE name IS NOT NULL");

        fake.on("FROM pg_proc p")
                .row("schema_name", "sales", "routine_name", "calc_total",
                        "routine_type", "FUNCTION",
                        "definition", "CREATE OR REPLACE FUNCTION sales.calc_total()\n"
                                + "RETURNS integer\nLANGUAGE plpgsql\nAS $$ BEGIN RETURN 1; END; $$");

        return fake;
    }

    private DdlObject find(DumpResult result, String objectType, String name) {
        for (DdlObject o : result.getObjects()) {
            if (o.getObjectType().equals(objectType) && o.getObjectName().equals(name)) {
                return o;
            }
        }
        throw new AssertionError(objectType + " " + name + " should be present");
    }

    @Test
    void dumpsSalesSchemaObjects() {
        PostgresDdlDumper dumper = new PostgresDdlDumper(rowSource());
        DumpResult result = dumper.dump(Arrays.asList("sales"));

        assertThat(result.getWarnings()).isEmpty();

        DdlObject schema = find(result, "SCHEMA", "sales");
        assertThat(schema.getDdl()).isEqualTo("CREATE SCHEMA IF NOT EXISTS sales;");

        DdlObject sequence = find(result, "SEQUENCE", "customer_id_seq");
        assertThat(sequence.getDdl()).isEqualTo(
                "CREATE SEQUENCE sales.customer_id_seq START WITH 1 INCREMENT BY 1"
                        + " MINVALUE 1 MAXVALUE 9223372036854775807 NO CYCLE;");

        DdlObject table = find(result, "TABLE", "customer");
        assertThat(table.getDdl()).contains("CREATE TABLE sales.customer (");
        assertThat(table.getDdl()).contains("id integer").contains("NOT NULL");
        assertThat(table.getDdl()).contains("DEFAULT 'unknown'::character varying");
        assertThat(table.getDdl()).contains(
                "ALTER TABLE sales.customer ADD CONSTRAINT customer_pkey PRIMARY KEY (id);");

        DdlObject view = find(result, "VIEW", "active_customer");
        assertThat(view.getDdl()).startsWith("CREATE OR REPLACE VIEW sales.active_customer AS\n");
        assertThat(view.getDdl()).endsWith(";");

        DdlObject function = find(result, "FUNCTION", "calc_total");
        assertThat(function.getDdl()).contains("CREATE OR REPLACE FUNCTION sales.calc_total()");
        assertThat(function.getDdl()).endsWith(";");
    }

    @Test
    void defaultsToPublicSchemaWhenNoneGiven() {
        PostgresDdlDumper dumper = new PostgresDdlDumper(new FakeRowSource());
        DumpResult result = dumper.dump(null);

        List<DdlObject> schemas = new ArrayList<DdlObject>();
        for (DdlObject o : result.getObjects()) {
            if ("SCHEMA".equals(o.getObjectType())) {
                schemas.add(o);
            }
        }
        assertThat(schemas).hasSize(1);
        assertThat(schemas.get(0).getObjectName()).isEqualTo("public");
        assertThat(result.getWarnings()).isEmpty();
    }
}
