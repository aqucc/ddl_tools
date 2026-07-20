#!/usr/bin/env bash
# examples/regenerate.sh
#
# examples/ 配下の生成物 (metadata.json, inventory.txt, testdata/*) を
# fixtures の元DDLから再生成するスクリプト。
#
# 使い方:
#   ./examples/regenerate.sh
#
# リポジトリルート基準で動作するため、どのディレクトリから実行してもよい。

set -eu

cd "$(dirname "$0")/.."

JAR="ddl-tools-cli/target/ddl-tools-cli-0.1.0-SNAPSHOT.jar"
FIXTURES_DIR="ddl-tools-core/src/test/resources/fixtures"

if [ ! -f "$JAR" ]; then
  echo "JARが見つからないためビルドします: mvn -q package -DskipTests" >&2
  mvn -q package -DskipTests
fi

ddl_tools() {
  java -jar "$JAR" "$@"
}

# metadata.json の extractedAt は実行時刻が入り、再実行のたびに差分が出るため
# 再現性確保のために固定文字列へ置換する。
fix_extracted_at() {
  local file="$1"
  sed -i.bak -E 's/"extractedAt" *: *"[^"]*"/"extractedAt" : "2026-01-01T00:00:00Z"/' "$file"
  rm -f "${file}.bak"
}

for dialect in oracle postgres; do
  echo "=== ${dialect} ==="
  dir="examples/${dialect}"
  mkdir -p "$dir"

  cp "${FIXTURES_DIR}/${dialect}_sample.sql" "${dir}/schema.sql"

  ddl_tools parse \
    --in "${dir}/schema.sql" \
    --dialect "${dialect}" \
    --out "${dir}/metadata.json"
  fix_extracted_at "${dir}/metadata.json"

  ddl_tools list \
    --in "${dir}/metadata.json" \
    --format table \
    --out "${dir}/inventory.txt"

  ddl_tools gen-data \
    --in "${dir}/metadata.json" \
    --rows 5 --seed 42 \
    --format insert \
    --out "${dir}/testdata"

  ddl_tools gen-data \
    --in "${dir}/metadata.json" \
    --rows 5 --seed 42 \
    --format java --class-name TestData \
    --out "${dir}/testdata"

  ddl_tools gen-data \
    --in "${dir}/metadata.json" \
    --rows 5 --seed 42 \
    --format csv \
    --out "${dir}/testdata/csv"

  ddl_tools gen-data \
    --in "${dir}/metadata.json" \
    --rows 5 --seed 42 \
    --format tsv \
    --out "${dir}/testdata/tsv"
done

echo "完了: examples/ 配下の生成物を更新しました。"
