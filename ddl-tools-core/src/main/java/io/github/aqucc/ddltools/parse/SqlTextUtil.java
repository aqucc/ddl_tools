package io.github.aqucc.ddltools.parse;

import java.util.ArrayList;
import java.util.List;

import io.github.aqucc.ddltools.model.Dialect;

/**
 * SQLテキスト処理の共通ユーティリティ。
 */
public final class SqlTextUtil {

    private SqlTextUtil() {
    }

    /**
     * 識別子の引用を外し、大文字小文字を方言の規定に正規化する。
     * 引用されていた識別子はそのままの表記を保つ。
     */
    public static String normalizeIdentifier(String ident, Dialect dialect) {
        if (ident == null) {
            return null;
        }
        String s = ident.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return dialect == Dialect.POSTGRESQL ? s.toLowerCase() : s.toUpperCase();
    }

    /**
     * schema.name 形式を分解する。返り値は {schema(無ければnull), name}。
     * 引用識別子内のドットを考慮する。
     */
    public static String[] splitQualified(String raw) {
        String s = raw.trim();
        List<String> parts = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                cur.append(c);
            } else if (c == '.' && !inQuote) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        if (parts.size() >= 2) {
            // a.b.c のような場合は末尾2要素を schema.name とみなす
            return new String[] {parts.get(parts.size() - 2), parts.get(parts.size() - 1)};
        }
        return new String[] {null, parts.get(0)};
    }

    /**
     * openIdx位置の '(' に対応する ')' の位置を返す。見つからなければ-1。
     * 文字列リテラル・引用識別子内の括弧は無視する。
     */
    public static int findMatchingParen(String s, int openIdx) {
        int depth = 0;
        int i = openIdx;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\'') {
                i = skipQuote(s, i, '\'');
                continue;
            }
            if (c == '"') {
                i = skipQuote(s, i, '"');
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    /**
     * トップレベル(括弧の外)のセパレータで分割する。文字列リテラル・引用識別子を考慮。
     */
    public static List<String> splitTopLevel(String s, char separator) {
        List<String> result = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"') {
                int end = skipQuote(s, i, c);
                cur.append(s, i, end);
                i = end;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == separator && depth == 0) {
                result.add(cur.toString());
                cur.setLength(0);
                i++;
                continue;
            }
            cur.append(c);
            i++;
        }
        if (cur.length() > 0) {
            result.add(cur.toString());
        }
        return result;
    }

    /**
     * トップレベル(括弧・引用の外)で単語 word が現れる最初の位置を返す。見つからなければ-1。
     */
    public static int indexOfTopLevelWord(String s, String word) {
        int n = s.length();
        int wlen = word.length();
        int depth = 0;
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipQuote(s, i, c);
                continue;
            }
            if (c == '(') {
                depth++;
                i++;
                continue;
            }
            if (c == ')') {
                depth--;
                i++;
                continue;
            }
            if (depth == 0 && regionMatchesIgnoreCase(s, i, word)) {
                boolean beforeOk = (i == 0) || !isIdentChar(s.charAt(i - 1));
                boolean afterOk = (i + wlen >= n) || !isIdentChar(s.charAt(i + wlen));
                if (beforeOk && afterOk) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private static boolean regionMatchesIgnoreCase(String s, int offset, String word) {
        return s.regionMatches(true, offset, word, 0, word.length());
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#';
    }

    private static int skipQuote(String s, int start, char quote) {
        int i = start + 1;
        int n = s.length();
        while (i < n) {
            if (s.charAt(i) == quote) {
                if (quote == '\'' && i + 1 < n && s.charAt(i + 1) == '\'') {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return n;
    }
}
