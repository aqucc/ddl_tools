package io.github.aqucc.ddltools.testdata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * テストデータをテーブルごとのCSV/TSVとして書き出す。1行目はカラム名ヘッダ。
 */
public class CsvWriter {

    private final char delimiter;

    public CsvWriter(char delimiter) {
        this.delimiter = delimiter;
    }

    public static CsvWriter csv() {
        return new CsvWriter(',');
    }

    public static CsvWriter tsv() {
        return new CsvWriter('\t');
    }

    /** テーブルごとのファイル名 → 内容のマップを返す。 */
    public Map<String, String> writeAll(TestDataSet dataSet) {
        Map<String, String> files = new LinkedHashMap<String, String>();
        String ext = delimiter == '\t' ? ".tsv" : ".csv";
        for (TableData table : dataSet.getTables()) {
            String name = table.getQualifiedName().replace('.', '_') + ext;
            files.put(name, writeTable(table));
        }
        return files;
    }

    public String writeTable(TableData table) {
        StringBuilder sb = new StringBuilder();
        String[] columns = table.getColumnNames();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(escape(columns[i]));
        }
        sb.append('\n');
        for (List<Object> row : table.getRows()) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(delimiter);
                }
                sb.append(escape(toText(row.get(i))));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String toText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[]) {
            StringBuilder sb = new StringBuilder();
            for (byte b : (byte[]) value) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        }
        return value.toString();
    }

    private String escape(String s) {
        if (s.indexOf(delimiter) >= 0 || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
