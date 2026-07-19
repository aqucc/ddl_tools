package io.github.aqucc.ddltools.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

import io.github.aqucc.ddltools.json.MetadataJsonMapper;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.report.InventoryReporter;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * メタ情報JSONからオブジェクト一覧を出力するサブコマンド。
 */
@Command(name = "list", mixinStandardHelpOptions = true, description = "メタ情報JSONからオブジェクト一覧を出力する")
public class ListCommand implements Callable<Integer> {

    @Option(names = "--in", required = true, description = "入力メタ情報JSONファイル")
    private Path in;

    @Option(names = "--format", defaultValue = "table", description = "出力形式: table, csv, json (デフォルト: ${DEFAULT-VALUE})")
    private String format;

    @Option(names = "--out", description = "出力先ファイル (省略時は標準出力)")
    private Path out;

    @Override
    public Integer call() {
        try {
            DatabaseMetadata metadata = new MetadataJsonMapper().readFromFile(in);
            InventoryReporter.Format fmt = resolveFormat(format);
            String report = new InventoryReporter().report(metadata, fmt);

            if (out != null) {
                Files.write(out, report.getBytes(StandardCharsets.UTF_8));
            } else {
                System.out.print(report);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("ERROR: list failed: " + e.getMessage());
            return 1;
        }
    }

    private InventoryReporter.Format resolveFormat(String value) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        if ("TABLE".equals(v) || "TEXT".equals(v)) {
            return InventoryReporter.Format.TEXT;
        }
        if ("CSV".equals(v)) {
            return InventoryReporter.Format.CSV;
        }
        if ("JSON".equals(v)) {
            return InventoryReporter.Format.JSON;
        }
        throw new IllegalArgumentException("unknown format: " + value
                + " (expected: table, csv, json)");
    }
}
