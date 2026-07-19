package io.github.aqucc.ddltools.extract;

import java.util.List;

import io.github.aqucc.ddltools.model.DatabaseMetadata;

/**
 * DBの辞書ビュー/カタログからメタ情報を抽出する。
 */
public interface MetadataExtractor {

    /**
     * 指定スキーマのメタ情報を抽出する。
     *
     * @param schemas 対象スキーマ名のリスト (空の場合は実装のデフォルト)
     */
    DatabaseMetadata extract(List<String> schemas);

    /** 抽出中に発生した警告。 */
    List<String> getWarnings();
}
