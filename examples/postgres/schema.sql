-- PostgreSQL sample DDL for tests
CREATE SCHEMA sales;

CREATE SEQUENCE sales.order_seq START WITH 1000 INCREMENT BY 1 NO CYCLE;

CREATE TABLE sales.customer (
  customer_id bigint NOT NULL,
  name varchar(50) NOT NULL,
  email text,
  age integer CHECK (age >= 18),
  vip boolean DEFAULT false NOT NULL,
  created_at timestamp(0) DEFAULT now() NOT NULL,
  CONSTRAINT pk_customer PRIMARY KEY (customer_id)
);

CREATE TABLE sales.orders (
  order_id bigserial NOT NULL,
  customer_id bigint NOT NULL,
  order_date date NOT NULL,
  amount numeric(12,2) NOT NULL,
  status varchar(10) DEFAULT 'NEW' NOT NULL,
  note text
);

ALTER TABLE ONLY sales.orders ADD CONSTRAINT pk_orders PRIMARY KEY (order_id);
ALTER TABLE ONLY sales.orders ADD CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES sales.customer (customer_id);
ALTER TABLE ONLY sales.orders ADD CONSTRAINT ck_orders_status CHECK (status IN ('NEW', 'PAID', 'SHIPPED'));
ALTER TABLE ONLY sales.orders ADD CONSTRAINT ck_orders_amount CHECK (amount BETWEEN 0 AND 99999999);
CREATE UNIQUE INDEX ux_customer_email ON sales.customer (email);
CREATE INDEX ix_orders_customer ON sales.orders (customer_id);

COMMENT ON TABLE sales.orders IS '注文';
COMMENT ON COLUMN sales.orders.amount IS '注文金額';

CREATE VIEW sales.v_customer_orders AS
SELECT c.customer_id, c.name, o.order_id, o.amount
FROM sales.customer c
JOIN sales.orders o ON c.customer_id = o.customer_id
WHERE o.status <> 'NEW';

CREATE MATERIALIZED VIEW sales.mv_customer_total AS
SELECT c.customer_id, c.name, sum(o.amount) AS total_amount
FROM sales.customer c JOIN sales.orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.name;

CREATE OR REPLACE FUNCTION sales.order_total(p_customer_id bigint) RETURNS numeric AS $$
DECLARE
  v_total numeric;
BEGIN
  SELECT COALESCE(sum(amount), 0) INTO v_total FROM sales.orders WHERE customer_id = p_customer_id;
  RETURN v_total;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sales.trg_orders_stamp() RETURNS trigger AS $$
BEGIN
  NEW.order_date := COALESCE(NEW.order_date, CURRENT_DATE);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_before
BEFORE INSERT OR UPDATE ON sales.orders
FOR EACH ROW EXECUTE FUNCTION sales.trg_orders_stamp();

CREATE PROCEDURE sales.cancel_order(IN p_order_id bigint) LANGUAGE plpgsql AS $$
BEGIN
  UPDATE sales.orders SET status = 'NEW' WHERE order_id = p_order_id;
END;
$$;

CREATE DOMAIN sales.email_addr AS text CHECK (VALUE ~ '@');

CREATE EXTENSION IF NOT EXISTS pg_trgm;
