package io.github.aqucc.ddltools.dump;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * {@link DumpResult}をファイルへ書き出す。
 */
public class DdlFileWriter {

    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^A-Za-z0-9_$#.\\-]");

    /**
     * 1オブジェクト=1ファイルとして書き出す。
     * 出力先は {@code <outDir>/<スキーマ>/<種別小文字(空白は_)>/<名前>.sql}。
     * 書いたファイルのパスの一覧を返す。
     */
    public List<Path> writeSplit(DumpResult result, Path outDir) throws IOException {
        List<Path> written = new ArrayList<Path>();
        for (DdlObject object : result.getObjects()) {
            String typeDir = sanitize(object.getObjectType().toLowerCase(Locale.ROOT).replace(' ', '_'));
            String schemaDir = sanitize(object.getSchemaName());
            String fileName = sanitize(object.getObjectName()) + ".sql";
            Path file = outDir.resolve(schemaDir).resolve(typeDir).resolve(fileName);
            Files.createDirectories(file.getParent());
            Files.write(file, object.getDdl().getBytes(StandardCharsets.UTF_8));
            written.add(file);
        }
        return written;
    }

    /**
     * 全DDLを1ファイルへ連結して書き出す。
     * オブジェクト間は空行で区切り、各オブジェクトの先頭に
     * {@code -- <schema>.<name> (<type>)} のコメント行を付ける。
     */
    public void writeSingle(DumpResult result, Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        List<DdlObject> objects = result.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            DdlObject object = objects.get(i);
            sb.append("-- ").append(object.getSchemaName()).append('.').append(object.getObjectName())
                    .append(" (").append(object.getObjectType()).append(")\n");
            sb.append(object.getDdl());
            if (i < objects.size() - 1) {
                sb.append("\n\n");
            }
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** ファイル名・ディレクトリ名として安全な文字だけを残す。 */
    private static String sanitize(String name) {
        return UNSAFE_CHARS.matcher(name).replaceAll("_");
    }
}
