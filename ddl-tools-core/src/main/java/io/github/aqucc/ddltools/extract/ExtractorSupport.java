package io.github.aqucc.ddltools.extract;

import java.util.List;
import java.util.Map;

import io.github.aqucc.ddltools.connect.SqlIdentifiers;

/**
 * 抽出実装の共通処理。
 */
final class ExtractorSupport {

    private ExtractorSupport() {
    }

    /** IN句用のスキーマ名リテラルリスト。 */
    static String inList(List<String> schemas) {
        return SqlIdentifiers.inList(schemas);
    }

    static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }

    static Integer intOf(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Long longOf(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean bool(Map<String, Object> row, String key, String trueValue) {
        String v = str(row, key);
        return v != null && v.equalsIgnoreCase(trueValue);
    }
}
