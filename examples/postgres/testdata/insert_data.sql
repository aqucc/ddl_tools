-- sales.customer
INSERT INTO sales.customer (customer_id, name, email, age, vip, created_at) VALUES (1, 'alpha', 'email_1', NULL, FALSE, TIMESTAMP '2024-01-31 13:05:38');
INSERT INTO sales.customer (customer_id, name, email, age, vip, created_at) VALUES (2, 'kappa', 'email_2', NULL, FALSE, TIMESTAMP '2024-06-25 12:56:32');
INSERT INTO sales.customer (customer_id, name, email, age, vip, created_at) VALUES (3, 'eta', 'email_3', 27, TRUE, TIMESTAMP '2024-12-09 02:33:23');
INSERT INTO sales.customer (customer_id, name, email, age, vip, created_at) VALUES (4, 'beta', 'email_4', 105, FALSE, TIMESTAMP '2024-10-02 12:50:05');
INSERT INTO sales.customer (customer_id, name, email, age, vip, created_at) VALUES (5, 'theta', 'email_5', 111, TRUE, TIMESTAMP '2024-01-30 11:25:23');

-- sales.orders
INSERT INTO sales.orders (customer_id, order_date, amount, status, note) VALUES (1, DATE '2024-08-30', 939.75, 'NEW', 'theta');
INSERT INTO sales.orders (customer_id, order_date, amount, status, note) VALUES (2, DATE '2024-07-02', 271.93, 'SHIPPED', 'alpha');
INSERT INTO sales.orders (customer_id, order_date, amount, status, note) VALUES (3, DATE '2024-09-22', 257.13, 'PAID', 'theta');
INSERT INTO sales.orders (customer_id, order_date, amount, status, note) VALUES (4, DATE '2024-08-17', 275.52, 'PAID', 'iota');
INSERT INTO sales.orders (customer_id, order_date, amount, status, note) VALUES (5, DATE '2024-05-19', 837.75, 'NEW', 'delta');

