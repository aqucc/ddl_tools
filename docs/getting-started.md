# はじめに

[← README](../README.md)

対応DB、必要環境、ビルド方法、JDBCドライバの用意について説明します。

## 対応DB

- Oracle 11g以降 (`ALL_*` 辞書ビュー、`DBMS_METADATA` パッケージを使用)
- PostgreSQL 14以降 (`information_schema` / `pg_catalog` および `pg_get_*` 系関数を使用)

## 必要環境

- Java 8以降 (本体はJava 8ソース/バイトコード互換でビルドされています)
- Maven 3.9以降

## ビルド方法

リポジトリルートで以下を実行します。

```sh
mvn package
```

`ddl-tools-core`(ライブラリ本体)と `ddl-tools-cli`(picocli製CLI)の2モジュール構成です。
CLIを単独の実行可能JAR(依存を同梱したshaded JAR)として使う場合は、
以下のパスに生成されます。

```
ddl-tools-cli/target/ddl-tools-cli-<version>.jar
```

このJARは `java -jar` でそのまま実行できます(JDBCドライバは別途 `-cp` で追加してください。
詳細は次節)。

```sh
java -jar ddl-tools-cli/target/ddl-tools-cli-0.1.0-SNAPSHOT.jar --help
```

## JDBCドライバの用意

`dump` / `extract` サブコマンドを使うには、対象DBのJDBCドライバが実行時クラスパス上に
必要です。ドライバはこのプロジェクトには**同梱していません**(商用ライセンス
(Oracle ojdbc)への配慮、および利用者が使いたいDBバージョンに合ったドライバを
自由に選べるようにするためです)。

利用者側で以下のようにドライバをダウンロードし、`-cp` でCLIのJARと一緒に指定してください。

```sh
# Oracle (ojdbc8など、利用中のOracleバージョンに合ったものを使用)
java -cp ddl-tools-cli/target/ddl-tools-cli-0.1.0-SNAPSHOT.jar:ojdbc8.jar \
    io.github.aqucc.ddltools.cli.DdlToolsCli dump --config oracle.properties --out ./ddl-out

# PostgreSQL
java -cp ddl-tools-cli/target/ddl-tools-cli-0.1.0-SNAPSHOT.jar:postgresql-42.7.3.jar \
    io.github.aqucc.ddltools.cli.DdlToolsCli extract --config pg.properties --out ./metadata.json
```

(Windows環境ではクラスパス区切りを `:` ではなく `;` にしてください。)

`parse` / `list` / `gen-data` はDB接続を行わないため、JDBCドライバは不要です。

ビルドが完了したら、次は [CLIの使い方](cli.md) を参照してください。
