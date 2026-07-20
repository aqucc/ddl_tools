# examples/

ddl-tools の実際の生成物を眺められるサンプル集です。CLIの各コマンドを実行して
出てくるファイルをそのままコミットしているので、`docs/` の説明と合わせて
「実際に何が出力されるか」を確認するのに使ってください。

## 全体の流れ

```
元DDL (schema.sql)
      │
      │  ddl-tools parse --in schema.sql --dialect <oracle|postgres> --out metadata.json
      ▼
metadata.json (DB非依存のメタ情報JSON: テーブル/カラム/制約/インデックス/ビュー...)
      │
      ├─ ddl-tools list --in metadata.json --format table --out inventory.txt
      │        └─ オブジェクト一覧(テキスト表)
      │
      └─ ddl-tools gen-data --in metadata.json --rows 5 --seed 42 ...
               ├─ --format insert → testdata/insert_data.sql
               ├─ --format java   → testdata/TestData.java
               ├─ --format csv    → testdata/csv/<table>.csv
               └─ --format tsv    → testdata/tsv/<table>.tsv
```

## ディレクトリツリー

```
examples/
├── README.md                       このファイル
├── regenerate.sh                   全ファイルを再生成するスクリプト
├── oracle/
│   ├── schema.sql                  元DDL (fixtures/oracle_sample.sql のコピー)
│   ├── metadata.json               parse の出力 (メタ情報JSON)
│   ├── inventory.txt               list --format table の出力 (オブジェクト一覧)
│   └── testdata/
│       ├── insert_data.sql         gen-data --format insert の出力
│       ├── TestData.java           gen-data --format java の出力
│       ├── csv/HR_DEPT.csv         gen-data --format csv の出力 (テーブルごと)
│       ├── csv/HR_EMP.csv
│       ├── tsv/HR_DEPT.tsv         gen-data --format tsv の出力 (テーブルごと)
│       └── tsv/HR_EMP.tsv
└── postgres/
    ├── schema.sql                  元DDL (fixtures/postgres_sample.sql のコピー)
    ├── metadata.json               parse の出力 (メタ情報JSON)
    ├── inventory.txt               list --format table の出力 (オブジェクト一覧)
    └── testdata/
        ├── insert_data.sql         gen-data --format insert の出力
        ├── TestData.java           gen-data --format java の出力
        ├── csv/sales_customer.csv  gen-data --format csv の出力 (テーブルごと)
        ├── csv/sales_orders.csv
        ├── tsv/sales_customer.tsv  gen-data --format tsv の出力 (テーブルごと)
        └── tsv/sales_orders.tsv
```

## 再生成方法

```sh
./examples/regenerate.sh
```

リポジトリルートからでも `examples/` の中からでも実行できます。JAR
(`ddl-tools-cli/target/ddl-tools-cli-0.1.0-SNAPSHOT.jar`) がなければ
`mvn -q package -DskipTests` を実行してからビルドします。

`metadata.json` の `extractedAt` は本来parse実行時刻が入りますが、再実行のたびに
差分が出て比較しづらくなるため、このスクリプトでは固定文字列
`2026-01-01T00:00:00Z` に置換しています。そのため同じ入力・同じオプションであれば
何度実行しても出力は変わりません (2回連続実行して差分がないことを確認済み)。

## 見どころ

- **FK整合性**: `oracle/testdata/insert_data.sql` の `HR.EMP.DEPT_ID` の値
  (1〜5) は、先に生成される親テーブル `HR.DEPT` の `DEPT_ID` の値と完全に一致
  しています。postgres側も同様に `sales.orders.customer_id` が
  `sales.customer.customer_id` と一致します。子テーブルの行をそのままINSERT
  しても参照整合性エラーにならないのは、gen-dataが親テーブルを先にトポロジカル
  ソートし、FKカラムに親で実際に使った値を割り当てているためです
  ([テストデータ生成の仕様](../docs/test-data.md)参照)。
- **CHECK制約の遵守**: `HR.EMP` には
  `CHECK (status IN ('ACTIVE', 'RETIRED', 'LEAVE'))` という制約がありますが、
  `insert_data.sql` に現れる `STATUS` 列の値は常にこの3つのいずれかです。
  同様にpostgres側の `sales.orders.status` も `'NEW'/'PAID'/'SHIPPED'` の
  範囲に収まっています。単純な `IN (...)` 形式のCHECK制約をベストエフォートで
  解析し、生成値をその候補内に収めていることがわかります。
- **TestData.javaの3引数形式**: `testdata/TestData.java` の各行は
  `TestDataLoader.insertRow(conn, "テーブル名", new String[]{カラム名...}, new Object[]{値...})`
  という3引数(テーブル名・カラム名配列・値配列)形式で呼び出されています。
  型はカラムの型に応じて `Long` / `String` / `BigDecimal` /
  `java.time.LocalDate` / `java.time.LocalDateTime` などにマッピングされ、
  NULL許容カラムには `null` が混ざります (`--null-ratio` の既定値0.2)。
- **inventory.txtの網羅性**: `list --format table` の出力にはテーブル・
  ビュー・インデックスだけでなく、シーケンス・トリガー・プロシージャ/
  ファンクション・パッケージ・シノニム・型 (Oracle) やドメイン・拡張
  (PostgreSQL) まで、schema.sql に定義された全オブジェクトが方言ごとの
  分類で一覧化されています。
