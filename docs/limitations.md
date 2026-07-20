# 制限事項

[← README](../README.md)

現時点でのddl-toolsの既知の制限事項です。

- **PL/SQL本体**: プロシージャ/ファンクション/トリガー/パッケージ本体の構文解析は行わず、
  シグネチャ(名前・引数・戻り値型など)のみを抽出し、本体はソーステキストのまま保持する。
- **PostgreSQLの `CREATE TABLE` 組み立て**: `dump` コマンドは `pg_dump` 外部コマンドを
  使わず、カタログ(`pg_catalog`)と `pg_get_*` 系関数から直接DDLを組み立てている。
  カラム定義・制約(`pg_get_constraintdef`)・インデックス(`pg_get_indexdef`)は
  再現するが、パーティショニングやテーブルスペース、細かいストレージオプションなど
  `pg_dump` が出力する全ての付随情報までは再現しない。
- **実DBとの結合テスト**: 通常のユニットテストはフィクスチャ/フェイク実装によるものだが、
  Testcontainersを使った実DB結合テスト(`ddl-tools-core` の `it` プロファイル)も
  用意している。ただしDockerが必要なため既定のビルドでは実行されない。詳細は
  [docs/integration-tests.md](integration-tests.md) を参照。
