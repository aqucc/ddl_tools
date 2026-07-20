# 実DB結合テスト (Testcontainers)

[← README](../README.md)

`ddl-tools-core` には、Testcontainersを使ってOracle/PostgreSQLの実DBコンテナを起動し、
extract / gen-data / dump が実際のDB上で正しく動作することを検証する結合テストが含まれます。
本ドキュメントではその実行方法と検証内容、CIでの組み込み方を説明します。

通常のユニットテスト(フィクスチャ/フェイク実装によるもの)はDB接続なしに常時実行されますが、
結合テストはDockerが必要なため、明示的にプロファイル `it` を指定したときのみ実行されます。
`mvn test` / `mvn package` などの通常のビルドでは一切実行されません。

## 必要環境

- Docker (またはDocker互換のコンテナランタイム。Testcontainersが利用できること)
- PostgreSQLコンテナ (`postgres:16-alpine`): 数十MB程度で、通常は数秒〜数十秒でpullできます
- Oracleコンテナ (`gvenzl/oracle-free:23-slim-faststart`): **イメージサイズが数GBあり、
  初回pullには数分〜十数分かかることがあります**。CI等で毎回pullする構成の場合は
  実行時間・帯域に注意してください(イメージキャッシュの利用を推奨します)。

## 実行方法

結合テストクラスは `ddl-tools-core/src/test/java/io/github/aqucc/ddltools/it/` にあります
(命名規則 `*IT.java` により、通常のsurefireでの `mvn test` では実行対象から除外されます)。

```sh
# 両方の結合テストを実行 (integration-test → verify)
mvn -pl ddl-tools-core verify -Pit

# PostgreSQLの結合テストだけを実行
mvn -pl ddl-tools-core verify -Pit -Dit.test=PostgresIntegrationIT

# Oracleの結合テストだけを実行
mvn -pl ddl-tools-core verify -Pit -Dit.test=OracleIntegrationIT
```

`-Pit` を付けない限り、`maven-failsafe-plugin` はビルドに一切登場しません。

## テストクラスと検証内容

### `PostgresIntegrationIT`

`postgres:16-alpine` コンテナを起動し、`fixtures/postgres_sample.sql`
(salesスキーマ)を流し込んだ上で、以下を順に検証します
(`@TestMethodOrder` で1→4の順に実行されます)。

1. **extract** — `PostgresMetadataExtractor` で `sales` スキーマを抽出し、
   テーブル2件・各カラム数・PK/FK/CHECK制約・ビュー/マテリアライズドビュー/
   トリガー/ルーチン/シーケンスの存在を確認する。
2. **gen-data実行** — 抽出したメタ情報から `TestDataGenerator`(5行、seed=42)+
   `InsertWriter`(PostgreSQL方言)でINSERT文を生成し、`DdlScriptSplitter` で
   分割して実DBに全件投入できること、`customer`/`orders` が各5行になることを確認する。
   `orders.order_id` はidentity(bigserial)列のため生成対象外とし、DB側の採番に任せる。
3. **ビュー参照** — 投入後に `sales.v_customer_orders` (WHERE `status <> 'NEW'`)を
   問い合わせる。このビューの行数は生成された `orders.status` の値次第で変わりうるため、
   生成データ側で `status <> 'NEW'` の行数を集計し、DB側の実測件数と突き合わせることで
   乱数結果に依存しない検証にしている。
4. **dump/round-trip** — `PostgresDdlDumper` でダンプしたDDLを使って
   `DROP SCHEMA sales CASCADE` 後にスキーマを再構築し、再extractしたテーブル数・
   カラム数が元と一致することを確認する。

### `OracleIntegrationIT`

`gvenzl/oracle-free:23-slim-faststart` コンテナを起動し、システム接続(`system`ユーザー)で
`hr` ユーザー/スキーマを作成した上で、`fixtures/oracle_sample.sql` を
`DdlScriptSplitter(ORACLE)` で分割して実行します(CREATE TYPE/PACKAGE を含む)。

1. **extract** — `OracleMetadataExtractor` で `HR` スキーマを抽出し、
   テーブル2件・カラム数・PK/FK/CHECK・ビュー/マテリアライズドビュー/トリガー/
   ルーチン/シーケンス/シノニム/パッケージの存在を確認する。
2. **gen-data実行** — FK整合(`dept` → `emp`)の順で生成したINSERT文が実DB上で
   全件実行でき、各5行になることを確認する。
3. **ビュー参照** — `hr.v_emp_dept` (WHERE `status = 'ACTIVE'`)を問い合わせる。
   この条件は等値のリテラル条件のため、ビュー解析の固定値機能により生成データの
   `emp.status` が全行 `'ACTIVE'` になる。そのため、ビューの行数は `emp` の行数と
   一致するはずであることを利用して検証する。
4. **dump** — `OracleDdlDumper`(`DBMS_METADATA.GET_DDL`)がエラーなく動作し、
   `DEPT`/`EMP` テーブルのDDLが取得できることを確認する。Oracleは対応オブジェクト種別が
   多く完全なround-tripの再構築は複雑になるため、ダンプ結果が空でないことと
   TABLE DDLの内容が部分一致することまでを検証範囲としている。

## このリポジトリでの制約

- Dockerが使えない環境(本リポジトリのCI環境やサンドボックスなど)では、コンテナ起動時に
  `IllegalStateException: Could not find a valid Docker environment` で失敗します。
  これは想定内の失敗であり、`-Pit` を付けなければこの問題は発生しません。
- 結合テストはコンパイル時には常に検証されます(`mvn test-compile -Pit`)。実行時にのみ
  Dockerが必要になります。

## CIでの利用例 (GitHub Actions)

以下はGitHub Actionsでの組み込み例です(このリポジトリに実際のワークフローファイルとして
追加してはいません。参考として例示します)。GitHub Actionsの標準ランナーには
Dockerが同梱されているため、追加のセットアップなしで動作します。

```yaml
name: integration-tests

on:
  workflow_dispatch:
  schedule:
    - cron: '0 18 * * *'  # 毎日 UTC 18:00 (JST 3:00)

jobs:
  it:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - name: Run Testcontainers integration tests
        run: mvn -B -pl ddl-tools-core verify -Pit
```

Oracleイメージのpullが重いため、通常のPR毎のCIには含めず、上記のように
`workflow_dispatch` や定期実行(スケジュール)として分離するか、
自己ホストランナーでイメージをキャッシュしておく運用を推奨します。
