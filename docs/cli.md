# CLIの使い方

[← README](../README.md)

共通の接続オプションと、dump / extract / parse / list / gen-data の各コマンドの
オプション・使用例をまとめます。ビルド方法やJDBCドライバの用意については
[はじめに](getting-started.md) を参照してください。

## 共通の接続オプション (dump / extract)

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

## 1. dump — DBの全オブジェクトをDDLダンプ

```sh
# デフォルト: オブジェクトごとにファイルを分割して出力
ddl-tools dump --config oracle.properties --out ./ddl-out

# 1ファイルに連結して出力
ddl-tools dump --config oracle.properties --single-file ./ddl-out/all.sql
```

分割出力は `<out>/<スキーマ>/<種別>/<オブジェクト名>.sql` に配置されます。
ダンプ中の警告(取得失敗したオブジェクトなど)は標準エラー出力に、
成功件数は標準出力に表示されます。

## 2. extract — DBからメタ情報JSONを抽出

```sh
ddl-tools extract --config oracle.properties --out ./metadata.json
```

## 3. parse — DDLファイル/ディレクトリを解析してメタ情報JSONを生成 (DB接続不要)

```sh
# 単一ファイル
ddl-tools parse --in ./schema.sql --dialect postgres --out ./metadata.json

# ディレクトリ (直下の *.sql を名前順に連結して解析)
ddl-tools parse --in ./sql --dialect oracle --default-schema HR --out ./metadata.json
```

## 4. list — メタ情報JSONからオブジェクト一覧を出力

```sh
ddl-tools list --in ./metadata.json --format table
ddl-tools list --in ./metadata.json --format csv --out ./inventory.csv
ddl-tools list --in ./metadata.json --format json
```

## 5. gen-data — メタ情報JSONからテストデータを生成 (DB接続不要)

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
標準エラー出力に表示されます。生成されるデータの詳しい仕様は
[テストデータ生成の仕様](test-data.md) を参照してください。
