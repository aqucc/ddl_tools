package io.github.aqucc.ddltools.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.github.aqucc.ddltools.model.Dialect;

/**
 * DDLスクリプトを文単位に分割する。
 *
 * <p>考慮するもの:
 * <ul>
 *   <li>行コメント(--)、ブロックコメント(&#47;* *&#47;)</li>
 *   <li>文字列リテラル('...'、''エスケープ)、引用識別子("...")</li>
 *   <li>PostgreSQLのドル引用($$...$$、$tag$...$tag$)</li>
 *   <li>Oracleでは PL/SQL を含む文(CREATE PROCEDURE/FUNCTION/TRIGGER/PACKAGE/TYPE、
 *       DECLARE/BEGIN ブロック)は行頭の「/」のみで終端し、内部の「;」では分割しない</li>
 * </ul>
 */
public class DdlScriptSplitter {

    private static final Pattern ORACLE_PLSQL_START = Pattern.compile(
            "^(CREATE\\s+(OR\\s+REPLACE\\s+)?(NONEDITIONABLE\\s+|EDITIONABLE\\s+)?"
                    + "(PROCEDURE|FUNCTION|TRIGGER|PACKAGE|TYPE|LIBRARY|JAVA)\\b|DECLARE\\b|BEGIN\\b)",
            Pattern.CASE_INSENSITIVE);

    private final Dialect dialect;

    public DdlScriptSplitter(Dialect dialect) {
        this.dialect = dialect;
    }

    public List<String> split(String script) {
        List<String> statements = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        int n = script.length();
        boolean plsqlMode = false;
        boolean startChecked = false;

        while (i < n) {
            char c = script.charAt(i);

            // 行コメント
            if (c == '-' && i + 1 < n && script.charAt(i + 1) == '-') {
                int end = script.indexOf('\n', i);
                if (end < 0) {
                    end = n;
                }
                current.append(script, i, end);
                i = end;
                continue;
            }
            // ブロックコメント
            if (c == '/' && i + 1 < n && script.charAt(i + 1) == '*') {
                int end = script.indexOf("*/", i + 2);
                end = (end < 0) ? n : end + 2;
                current.append(script, i, end);
                i = end;
                continue;
            }
            // 文字列リテラル
            if (c == '\'') {
                int end = findQuoteEnd(script, i);
                current.append(script, i, end);
                i = end;
                continue;
            }
            // 引用識別子
            if (c == '"') {
                int end = script.indexOf('"', i + 1);
                end = (end < 0) ? n : end + 1;
                current.append(script, i, end);
                i = end;
                continue;
            }
            // PostgreSQLドル引用
            if (dialect == Dialect.POSTGRESQL && c == '$') {
                int tagEnd = dollarTagEnd(script, i);
                if (tagEnd > 0) {
                    String tag = script.substring(i, tagEnd);
                    int close = script.indexOf(tag, tagEnd);
                    int end = (close < 0) ? n : close + tag.length();
                    current.append(script, i, end);
                    i = end;
                    continue;
                }
            }

            // 文の先頭が確定した時点でPL/SQLモード判定(Oracleのみ)
            if (!startChecked && !Character.isWhitespace(c)) {
                startChecked = true;
                if (dialect == Dialect.ORACLE) {
                    int headEnd = Math.min(i + 120, n);
                    plsqlMode = ORACLE_PLSQL_START.matcher(script.substring(i, headEnd)).find();
                }
            }

            // Oracle: 行頭の「/」単独行は文の終端
            if (dialect == Dialect.ORACLE && c == '/' && isAloneOnLine(script, i)) {
                addStatement(statements, current);
                current.setLength(0);
                plsqlMode = false;
                startChecked = false;
                i++;
                continue;
            }

            if (c == ';' && !plsqlMode) {
                addStatement(statements, current);
                current.setLength(0);
                startChecked = false;
                i++;
                continue;
            }

            current.append(c);
            i++;
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder sb) {
        String s = stripComments(sb.toString()).trim();
        if (!s.isEmpty()) {
            statements.add(sb.toString().trim());
        }
    }

    /** 文がコメントのみで構成されるかの判定用にコメントを除去する。 */
    private static String stripComments(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '-' && i + 1 < n && s.charAt(i + 1) == '-') {
                int end = s.indexOf('\n', i);
                i = (end < 0) ? n : end;
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                i = (end < 0) ? n : end + 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** '...' の終端(終端引用符の次の位置)を返す。''エスケープを考慮。 */
    private static int findQuoteEnd(String s, int start) {
        int i = start + 1;
        int n = s.length();
        while (i < n) {
            if (s.charAt(i) == '\'') {
                if (i + 1 < n && s.charAt(i + 1) == '\'') {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return n;
    }

    /** $ または $tag$ の開始位置からタグ終端(閉じ$の次の位置)を返す。ドル引用でなければ-1。 */
    private static int dollarTagEnd(String s, int start) {
        int i = start + 1;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '$') {
                return i + 1;
            }
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return -1;
            }
            i++;
        }
        return -1;
    }

    /** 位置iの文字が行内で唯一の非空白文字であるか。 */
    private static boolean isAloneOnLine(String s, int i) {
        int j = i - 1;
        while (j >= 0 && s.charAt(j) != '\n') {
            if (!Character.isWhitespace(s.charAt(j))) {
                return false;
            }
            j--;
        }
        int k = i + 1;
        int n = s.length();
        while (k < n && s.charAt(k) != '\n') {
            if (!Character.isWhitespace(s.charAt(k))) {
                return false;
            }
            k++;
        }
        return true;
    }
}
