package io.github.aqucc.ddltools.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import io.github.aqucc.ddltools.json.MetadataJsonMapper;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.parse.DdlParser;
import io.github.aqucc.ddltools.parse.ParseOptions;
import io.github.aqucc.ddltools.parse.ParseResult;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * DDLファイル(またはディレクトリ)を解析しメタ情報JSONへ出力するサブコマンド。
 *
 * <p>{@code --in} にディレクトリを指定した場合、そのディレクトリ配下(サブディレクトリを含む)の
 * {@code *.sql}ファイルをパス名順に連結して解析する。
 */
@Command(name = "parse", mixinStandardHelpOptions = true, description = "DDLファイル(またはディレクトリ)を解析しメタ情報JSONへ出力する")
public class ParseCommand implements Callable<Integer> {

    @Option(names = "--in", required = true, description = "入力DDLファイルまたはディレクトリ")
    private Path in;

    @Option(names = "--dialect", required = true, description = "DB方言 (oracle, postgres)")
    private String dialect;

    @Option(names = "--default-schema", description = "スキーマ修飾のないオブジェクトを配置するスキーマ名")
    private String defaultSchema;

    @Option(names = "--out", required = true, description = "出力先メタ情報JSONファイル")
    private Path out;

    @Override
    public Integer call() {
        try {
            if (!Files.exists(in)) {
                System.err.println("ERROR: parse failed: input not found: " + in);
                return 1;
            }
            Dialect d = Dialect.fromString(dialect);
            String script = readScript(in);
            ParseOptions options = (defaultSchema != null)
                    ? new ParseOptions(d, defaultSchema)
                    : new ParseOptions(d);
            ParseResult result = new DdlParser(options).parse(script);

            for (String warning : result.getWarnings()) {
                System.err.println("WARN: " + warning);
            }

            new MetadataJsonMapper().writeToFile(result.getMetadata(), out);
            System.out.println("parsed metadata to " + out);
            return 0;
        } catch (Exception e) {
            System.err.println("ERROR: parse failed: " + e.getMessage());
            return 1;
        }
    }

    /**
     * 単一ファイルはそのまま、ディレクトリの場合は配下(サブディレクトリを含む)の
     * {@code *.sql}(拡張子は大文字小文字を区別しない)をパス名順に連結して読み込む。
     */
    private String readScript(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            final List<Path> files = new ArrayList<Path>();
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".sql")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (files.isEmpty()) {
                throw new IOException("no *.sql files found under directory: " + path);
            }
            Collections.sort(files);
            StringBuilder sb = new StringBuilder();
            for (Path p : files) {
                sb.append(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)).append('\n');
            }
            return sb.toString();
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
