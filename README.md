# ddl-tools

Oracle / PostgreSQL のDDLダンプ、メタ情報抽出・解析、オブジェクト一覧生成、
テストデータ生成をまとめたJavaライブラリ + CLIツールです。

## できること

1. **dump** — 稼働中のDBに接続し、全オブジェクト(テーブル、ビュー、インデックス、
   シーケンス、トリガー、プロシージャ/ファンクション、パッケージなど)のDDLをダンプする
2. **extract** — DBの辞書ビュー/カタログ(Oracle: `ALL_*`、PostgreSQL:
   `information_schema` / `pg_catalog`)からメタ情報を抽出し、DB非依存のJSON形式で保存する
3. **parse** — 既存のDDLスクリプトファイル(`.sql`)を解析し、DBに接続せずに
   同じメタ情報JSONを生成する
4. **list** — メタ情報JSONからスキーマ横断のオブジェクト一覧をtext/CSV/JSON形式で出力する
5. **gen-data** — メタ情報JSONから、型・制約(NOT NULL/PK/UNIQUE/CHECK/FK)に適合した
   テストデータを生成し、INSERT文・Javaコード・CSV/TSVとして書き出す

各機能はDB接続なしでも動作するもの(parse/list/gen-data)と、DB接続が必要なもの
(dump/extract)に分かれています。dumpとextractで作られたメタ情報JSONは同一形式なので、
parseで作ったJSONに対してもlist/gen-dataがそのまま使えます。

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

## CLIの使い方

### 共通の接続オプション (dump / extract)

| オプション | 説明 |
| --- | --- |
| `--config <file>` | 接続設定propertiesファイル (`url`, `user`, `password`, `schemas`, `dialect`) |
| `--url <jdbc-url>` | JDBC URL (propertiesより優先) |
| `--user <user>` | 接続ユーザー (propertiesより優先) |
| `--password <password>` | 接続パスワード (propertiesより優先) |
| `--schemas <s1,s2,...>` | 対象スキーマ名 (カンマ区切り、propertiesより優先) |
| `--dialect <oracle\|postgres>` | DB方言 (省略時はurlから自動判定、propertiesより優先) |

優先順位は「コマンドライン引数 > propertiesファイル」です。パスワードはさらに
環境変数 `DDLTOOLS_PASSWORD` で上書きできます(コマンドライン引数・propertiesの
どちらより優先)。CIなどでパスワードをコマンドライン引数やファイルに残したくない場合に
使ってください。

接続設定propertiesの例 (`oracle.properties`):

```properties
url=jdbc:oracle:thin:@localhost:1521/ORCLPDB1
user=hr
password=changeit
schemas=HR,SALES
dialect=oracle
```

```sh
export DDLTOOLS_PASSWORD=changeit
java -cp ddl-tools-cli/target/ddl-tools-cli-0.1.0-SNAPSHOT.jar:ojdbc8.jar \
    io.github.aqucc.ddltools.cli.DdlToolsCli dump --config oracle.properties --out ./ddl-out
```

### 1. dump — DBの全オブジェクトをDDLダンプ

```sh
# デフォルト: オブジェクトごとにファイルを分割して出力
ddl-tools dump --config oracle.properties --out ./ddl-out

# 1ファイルに連結して出力
ddl-tools dump --config oracle.properties --single-file ./ddl-out/all.sql
```

分割出力は `<out>/<スキーマ>/<種別>/<オブジェクト名>.sql` に配置されます。
ダンプ中の警告(取得失敗したオブジェクトなど)は標準エラー出力に、
成功件数は標準出力に表示されます。

### 2. extract — DBからメタ情報JSONを抽出

```sh
ddl-tools extract --config oracle.properties --out ./metadata.json
```

### 3. parse — DDLファイル/ディレクトリを解析してメタ情報JSONを生成 (DB接続不要)

```sh
# 単一ファイル
ddl-tools parse --in ./schema.sql --dialect postgres --out ./metadata.json

# ディレクトリ (直下の *.sql を名前順に連結して解析)
ddl-tools parse --in ./sql --dialect oracle --default-schema HR --out ./metadata.json
```

### 4. list — メタ情報JSONからオブジェクト一覧を出力

```sh
ddl-tools list --in ./metadata.json --format table
ddl-tools list --in ./metadata.json --format csv --out ./inventory.csv
ddl-tools list --in ./metadata.json --format json
```

### 5. gen-data — メタ情報JSONからテストデータを生成 (DB接続不要)

```sh
# INSERT文 (デフォルト): <out>/insert_data.sql
ddl-tools gen-data --in ./metadata.json --rows 20 --seed 1 --out ./testdata

# Javaコード: <out>/<class-name>.java
ddl-tools gen-data --in ./metadata.json --format java \
    --package com.example.testdata --class-name SampleData --out ./testdata

# CSV/TSV: テーブルごとのファイル群
ddl-tools gen-data --in ./metadata.json --format csv --out ./testdata
```

主なオプション: `--rows` (テーブルごとの生成行数、既定10)、`--seed` (乱数シード、既定42)、
`--null-ratio` (NULL許容カラムにNULLを混ぜる割合、既定0.2)、`--no-views`
(ビュー定義の結合条件の考慮を無効化)。生成時の警告(一意値の枯渇、ビュー解析失敗など)は
標準エラー出力に表示されます。

## ライブラリとしての使用例

CLIを経由せず、`ddl-tools-core` をJavaコードから直接呼び出すこともできます。
以下は「DDLファイルを解析 → テストデータを生成 → INSERT文として書き出す」例です。

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.parse.DdlParser;
import io.github.aqucc.ddltools.parse.ParseOptions;
import io.github.aqucc.ddltools.parse.ParseResult;
import io.github.aqucc.ddltools.testdata.GenerationOptions;
import io.github.aqucc.ddltools.testdata.InsertWriter;
import io.github.aqucc.ddltools.testdata.TestDataGenerator;
import io.github.aqucc.ddltools.testdata.TestDataSet;

public class GenerateSampleData {
    public static void main(String[] args) throws Exception {
        String ddl = new String(Files.readAllBytes(Paths.get("schema.sql")), "UTF-8");

        // DDLを解析してDB非依存のメタ情報を得る
        ParseResult parseResult = new DdlParser(new ParseOptions(Dialect.POSTGRESQL)).parse(ddl);
        DatabaseMetadata metadata = parseResult.getMetadata();

        // メタ情報からPK/FK/CHECK/NOT NULLに適合したテストデータを生成する
        GenerationOptions options = new GenerationOptions().setRows(20).setSeed(1L);
        TestDataSet dataSet = new TestDataGenerator(metadata, options).generate();

        // INSERT文として書き出す
        String sql = new InsertWriter(Dialect.POSTGRESQL).writeAll(dataSet);
        Files.write(Paths.get("insert_data.sql"), sql.getBytes("UTF-8"));
    }
}
```

## テストデータ生成の仕様

`gen-data` (および `TestDataGenerator`) は以下の方針でデータを生成します。

- **PK/UNIQUE制約**: 対象カラムには連番ベースの値を割り当て、生成行数の範囲内では
  重複を避ける。値域(CHECK制約や桁数)を使い切って重複が避けられない場合は
  警告として報告する。
- **NOT NULL**: 必ず値を入れる。NULL許容カラムには `--null-ratio` で指定した割合で
  NULLを混ぜる(一意制約対象・後述のグループ共有カラムは除外)。
- **CHECK制約**: `IN (...)` やスカラー比較・`BETWEEN` など単純な形式の値域をベストエフォートで
  解析し、生成値がその範囲/候補に収まるようにする。
- **FK整合性**: 親テーブルを先にトポロジカルソートで並べ、子テーブルのFKカラムには
  親テーブルで実際に生成した値と同じ値を使う。そのため生成順にそのままINSERTすれば
  参照整合性エラーは発生しない(循環参照がある場合は元の宣言順にフォールバックし、
  警告を出す)。
- **ビュー結合の考慮**: `--no-views` を指定しない限り、ビュー/マテリアライズドビューの
  定義SQLを解析し、等値結合条件のカラムに同じ値を割り当てることで、ビューが
  1件以上の行を返すデータセットになるようにする(ベストエフォート。解析できない
  定義は警告を出してスキップする)。
- **シードによる再現性**: `--seed` で指定した値で `java.util.Random` を初期化するため、
  同じメタ情報・同じオプションであれば毎回同じデータが生成される。

## 制限事項

- **PL/SQL本体**: プロシージャ/ファンクション/トリガー/パッケージ本体の構文解析は行わず、
  シグネチャ(名前・引数・戻り値型など)のみを抽出し、本体はソーステキストのまま保持する。
- **PostgreSQLの `CREATE TABLE` 組み立て**: `dump` コマンドは `pg_dump` 外部コマンドを
  使わず、カタログ(`pg_catalog`)と `pg_get_*` 系関数から直接DDLを組み立てている。
  カラム定義・制約(`pg_get_constraintdef`)・インデックス(`pg_get_indexdef`)は
  再現するが、パーティショニングやテーブルスペース、細かいストレージオプションなど
  `pg_dump` が出力する全ての付随情報までは再現しない。
- **実DBとの結合テスト**: 本リポジトリのテストはフィクスチャ/フェイク実装によるもので、
  実際のOracle/PostgreSQLに接続した結合テストは含まれていない。利用者側の環境で
  実DBに対して動作確認することを推奨する。
