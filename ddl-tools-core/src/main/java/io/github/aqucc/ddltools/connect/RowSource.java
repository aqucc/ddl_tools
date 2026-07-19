package io.github.aqucc.ddltools.connect;

import java.util.List;
import java.util.Map;

/**
 * 辞書ビュー/カタログへの問い合わせの薄い抽象。
 * テストではフィクスチャ実装に差し替えることで実DBなしで検証できる。
 */
public interface RowSource {

    /**
     * SELECTを実行し、行ごとの {カラム名(小文字) → 値} のリストを返す。
     */
    List<Map<String, Object>> query(String sql, Object... params);

    /**
     * 結果を返さない文(DBMS_METADATAの変換パラメータ設定など)を実行する。
     */
    void execute(String sql);
}
