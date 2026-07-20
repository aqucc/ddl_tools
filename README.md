# ddl-tools

Oracle / PostgreSQL のDDLダンプ、メタ情報抽出・解析、オブジェクト一覧生成、
テストデータ生成をまとめたJavaライブラリ + CLIツールです。

## できること

1. **dump** — 稼働中のDBに接続し、全オブジェクト(テーブル、ビュー、インデックス、
   シーケンス、トリガー、プロシージャ/ファンクション、パッケージなど)のDDLをダンプする
   ([CLIの使い方](docs/cli.md#1-dump--dbの全オブジェクトをddlダンプ))
2. **extract** — DBの辞書ビュー/カタログ(Oracle: `ALL_*`、PostgreSQL:
   `information_schema` / `pg_catalog`)からメタ情報を抽出し、DB非依存のJSON形式で保存する
   ([CLIの使い方](docs/cli.md#2-extract--dbからメタ情報jsonを抽出))
3. **parse** — 既存のDDLスクリプトファイル(`.sql`)を解析し、DBに接続せずに
   同じメタ情報JSONを生成する
   ([CLIの使い方](docs/cli.md#3-parse--ddlファイルディレクトリを解析してメタ情報jsonを生成-db接続不要))
4. **list** — メタ情報JSONからスキーマ横断のオブジェクト一覧をtext/CSV/JSON形式で出力する
   ([CLIの使い方](docs/cli.md#4-list--メタ情報jsonからオブジェクト一覧を出力))
5. **gen-data** — メタ情報JSONから、型・制約(NOT NULL/PK/UNIQUE/CHECK/FK)に適合した
   テストデータを生成し、INSERT文・Javaコード・CSV/TSVとして書き出す
   ([CLIの使い方](docs/cli.md#5-gen-data--メタ情報jsonからテストデータを生成-db接続不要) /
   [テストデータ生成の仕様](docs/test-data.md))

各機能はDB接続なしでも動作するもの(parse/list/gen-data)と、DB接続が必要なもの
(dump/extract)に分かれています。dumpとextractで作られたメタ情報JSONは同一形式なので、
parseで作ったJSONに対してもlist/gen-dataがそのまま使えます。

## 対応DB・必要環境

Oracle 11g以降 / PostgreSQL 14以降に対応。Java 8以降・Maven 3.9以降が必要です。
詳細は [はじめに](docs/getting-started.md) を参照してください。

## クイックスタート

```sh
mvn package
alias ddl-tools='java -jar ddl-tools-cli/target/ddl-tools-cli-0.1.0-SNAPSHOT.jar'

ddl-tools parse --in ./schema.sql --dialect postgres --out ./metadata.json
ddl-tools gen-data --in ./metadata.json --rows 20 --seed 1 --out ./testdata
```

## ドキュメント

| ドキュメント | 内容 |
| --- | --- |
| [docs/getting-started.md](docs/getting-started.md) | 対応DB、必要環境、ビルド方法、JDBCドライバの用意 |
| [docs/cli.md](docs/cli.md) | 共通の接続オプション、5コマンドの全オプション・使用例、`DDLTOOLS_PASSWORD` |
| [docs/library.md](docs/library.md) | ライブラリとしての使用例(Javaコード) |
| [docs/test-data.md](docs/test-data.md) | テストデータ生成の仕様 |
| [docs/limitations.md](docs/limitations.md) | 制限事項 |
| [examples/](examples/README.md) | 元DDL→メタ情報JSON→テストデータの実生成サンプル |
