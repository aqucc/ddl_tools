package io.github.aqucc.ddltools.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.aqucc.ddltools.json.MetadataJsonMapper;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.testdata.CsvWriter;
import io.github.aqucc.ddltools.testdata.GenerationOptions;
import io.github.aqucc.ddltools.testdata.InsertWriter;
import io.github.aqucc.ddltools.testdata.JavaSnippetWriter;
import io.github.aqucc.ddltools.testdata.TestDataGenerator;
import io.github.aqucc.ddltools.testdata.TestDataSet;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * メタ情報JSONから型適合テストデータを生成するサブコマンド。
 */
@Command(name = "gen-data", mixinStandardHelpOptions = true, description = "メタ情報JSONからテストデータを生成する")
public class GenDataCommand implements Callable<Integer> {

    @Option(names = "--in", required = true, description = "入力メタ情報JSONファイル")
    private Path in;

    @Option(names = "--rows", defaultValue = "10", description = "テーブルごとの生成行数 (デフォルト: ${DEFAULT-VALUE})")
    private int rows;

    @Option(names = "--seed", defaultValue = "42", description = "乱数シード (デフォルト: ${DEFAULT-VALUE})")
    private long seed;

    @Option(names = "--null-ratio", defaultValue = "0.2",
            description = "NULL許容カラムにNULLを混ぜる割合 0.0〜1.0 (デフォルト: ${DEFAULT-VALUE})")
    private double nullRatio;

    @Option(names = "--no-views", description = "ビュー定義の結合条件・固定条件の考慮を無効化する")
    private boolean noViews;

    @Option(names = "--format", defaultValue = "insert",
            description = "出力形式: insert, java, csv, tsv (デフォルト: ${DEFAULT-VALUE})")
    private String format;

    @Option(names = "--out", required = true, description = "出力先ディレクトリ")
    private Path out;

    @Option(names = "--package", defaultValue = "", description = "javaフォーマット時の生成クラスのパッケージ名")
    private String packageName;

    @Option(names = "--class-name", defaultValue = "TestData", description = "javaフォーマット時の生成クラス名 (デフォルト: ${DEFAULT-VALUE})")
    private String className;

    @Override
    public Integer call() {
        try {
            DatabaseMetadata metadata = new MetadataJsonMapper().readFromFile(in);
            GenerationOptions options = new GenerationOptions()
                    .setRows(rows)
                    .setSeed(seed)
                    .setNullRatio(nullRatio)
                    .setConsiderViews(!noViews);
            TestDataSet dataSet = new TestDataGenerator(metadata, options).generate();

            for (String warning : dataSet.getWarnings()) {
                System.err.println("WARN: " + warning);
            }

            Files.createDirectories(out);
            writeOutput(metadata, dataSet);
            return 0;
        } catch (Exception e) {
            System.err.println("ERROR: gen-data failed: " + e.getMessage());
            return 1;
        }
    }

    private void writeOutput(DatabaseMetadata metadata, TestDataSet dataSet) throws IOException {
        String fmt = format.trim().toLowerCase(Locale.ROOT);
        if ("insert".equals(fmt)) {
            Path file = out.resolve("insert_data.sql");
            String sql = new InsertWriter(metadata.getDialect()).writeAll(dataSet);
            Files.write(file, sql.getBytes(StandardCharsets.UTF_8));
            System.out.println("generated " + file);
        } else if ("java".equals(fmt)) {
            Path file = out.resolve(className + ".java");
            String code = new JavaSnippetWriter().writeClass(dataSet, packageName, className);
            Files.write(file, code.getBytes(StandardCharsets.UTF_8));
            System.out.println("generated " + file);
        } else if ("csv".equals(fmt) || "tsv".equals(fmt)) {
            CsvWriter writer = "csv".equals(fmt) ? CsvWriter.csv() : CsvWriter.tsv();
            Map<String, String> files = writer.writeAll(dataSet);
            for (Map.Entry<String, String> e : files.entrySet()) {
                Path file = out.resolve(e.getKey());
                Files.write(file, e.getValue().getBytes(StandardCharsets.UTF_8));
            }
            System.out.println("generated " + files.size() + " file(s) in " + out);
        } else {
            throw new IllegalArgumentException("unknown format: " + format
                    + " (expected: insert, java, csv, tsv)");
        }
    }
}
