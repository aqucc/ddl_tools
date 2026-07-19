-- Oracle sample DDL for tests
CREATE SEQUENCE hr.emp_seq START WITH 1 INCREMENT BY 1 NOCYCLE;

CREATE TABLE hr.dept (
  dept_id NUMBER(4) NOT NULL,
  dept_name VARCHAR2(30 CHAR) NOT NULL,
  location VARCHAR2(20),
  CONSTRAINT pk_dept PRIMARY KEY (dept_id),
  CONSTRAINT uq_dept_name UNIQUE (dept_name)
);

CREATE TABLE hr.emp (
  emp_id NUMBER(6) NOT NULL,
  emp_name VARCHAR2(50) NOT NULL,
  email VARCHAR2(100),
  salary NUMBER(10,2) DEFAULT 0 NOT NULL,
  status VARCHAR2(10) DEFAULT 'ACTIVE' NOT NULL,
  hire_date DATE NOT NULL,
  updated_at TIMESTAMP(6),
  dept_id NUMBER(4) NOT NULL,
  memo CLOB
);

ALTER TABLE hr.emp ADD CONSTRAINT pk_emp PRIMARY KEY (emp_id);
ALTER TABLE hr.emp ADD CONSTRAINT fk_emp_dept FOREIGN KEY (dept_id) REFERENCES hr.dept (dept_id);
ALTER TABLE hr.emp ADD CONSTRAINT ck_emp_status CHECK (status IN ('ACTIVE', 'RETIRED', 'LEAVE'));
ALTER TABLE hr.emp ADD CONSTRAINT ck_emp_salary CHECK (salary >= 0);
CREATE UNIQUE INDEX hr.ix_emp_email ON hr.emp (email);
CREATE INDEX hr.ix_emp_dept ON hr.emp (dept_id);

COMMENT ON TABLE hr.emp IS '従業員';
COMMENT ON COLUMN hr.emp.emp_name IS '氏名';

CREATE OR REPLACE VIEW hr.v_emp_dept AS
SELECT e.emp_id, e.emp_name, e.salary, d.dept_name
FROM hr.emp e
JOIN hr.dept d ON e.dept_id = d.dept_id
WHERE e.status = 'ACTIVE';

CREATE MATERIALIZED VIEW hr.mv_dept_salary
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT d.dept_id, d.dept_name, SUM(e.salary) AS total_salary
FROM hr.emp e JOIN hr.dept d ON e.dept_id = d.dept_id
GROUP BY d.dept_id, d.dept_name;

CREATE OR REPLACE TRIGGER hr.trg_emp_upd
BEFORE INSERT OR UPDATE ON hr.emp
FOR EACH ROW
BEGIN
  :NEW.updated_at := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE PROCEDURE hr.raise_salary (
  p_emp_id IN NUMBER,
  p_amount IN OUT NUMBER
) AS
BEGIN
  UPDATE hr.emp SET salary = salary + p_amount WHERE emp_id = p_emp_id;
  SELECT salary INTO p_amount FROM hr.emp WHERE emp_id = p_emp_id;
END raise_salary;
/

CREATE OR REPLACE FUNCTION hr.get_dept_name (p_dept_id IN NUMBER) RETURN VARCHAR2 IS
  v_name VARCHAR2(30);
BEGIN
  SELECT dept_name INTO v_name FROM hr.dept WHERE dept_id = p_dept_id;
  RETURN v_name;
END;
/

CREATE OR REPLACE PACKAGE hr.emp_pkg AS
  PROCEDURE hire (p_name IN VARCHAR2);
  FUNCTION head_count RETURN NUMBER;
END emp_pkg;
/

CREATE OR REPLACE PACKAGE BODY hr.emp_pkg AS
  PROCEDURE hire (p_name IN VARCHAR2) IS
  BEGIN
    INSERT INTO hr.emp (emp_id, emp_name, hire_date, dept_id)
    VALUES (hr.emp_seq.NEXTVAL, p_name, SYSDATE, 10);
  END hire;
  FUNCTION head_count RETURN NUMBER IS
    v_count NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_count FROM hr.emp;
    RETURN v_count;
  END head_count;
END emp_pkg;
/

CREATE SYNONYM hr.employees FOR hr.emp;

CREATE TYPE hr.addr_type AS OBJECT (street VARCHAR2(50), city VARCHAR2(30));
/
