package io.github.aqucc.ddltools.connect;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 辞書ビュー/カタログ問い合わせ用の識別子処理。
 */
public final class SqlIdentifiers {

    private static final Pattern SAFE_IDENT = Pattern.compile("[A-Za-z0-9_$#]+");

    private SqlIdentifiers() {
    }

    /**
     * IN句用のスキーマ名リテラルリストを組み立てる。
     * SQLインジェクション防止のため識別子文字のみ許可する。
     */
    public static String inList(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String s : names) {
            if (!SAFE_IDENT.matcher(s).matches()) {
                throw new IllegalArgumentException("invalid identifier: " + s);
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('\'').append(s).append('\'');
        }
        return sb.toString();
    }
}
