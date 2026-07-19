package io.github.aqucc.ddltools.cli;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.Callable;

import io.github.aqucc.ddltools.connect.ConnectionConfig;
import io.github.aqucc.ddltools.connect.JdbcRowSource;
import io.github.aqucc.ddltools.connect.RowSource;
import io.github.aqucc.ddltools.extract.MetadataExtractor;
import io.github.aqucc.ddltools.extract.OracleMetadataExtractor;
import io.github.aqucc.ddltools.extract.PostgresMetadataExtractor;
import io.github.aqucc.ddltools.json.MetadataJsonMapper;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * DBの辞書ビュー/カタログからメタ情報を抽出しJSONファイルへ出力するサブコマンド。
 */
@Command(name = "extract", mixinStandardHelpOptions = true, description = "DBからメタ情報を抽出しJSONファイルへ出力する")
public class ExtractCommand implements Callable<Integer> {

    @Mixin
    private ConnectionOptions connectionOptions;

    @Option(names = "--out", required = true, description = "出力先メタ情報JSONファイル")
    private Path out;

    @Override
    public Integer call() {
        try {
            ConnectionConfig config = connectionOptions.toConnectionConfig();
            Dialect dialect = config.effectiveDialect();

            try (Connection connection = config.open()) {
                RowSource rowSource = new JdbcRowSource(connection);
                MetadataExtractor extractor = (dialect == Dialect.ORACLE)
                        ? new OracleMetadataExtractor(rowSource)
                        : new PostgresMetadataExtractor(rowSource);
                DatabaseMetadata metadata = extractor.extract(config.getSchemas());

                for (String warning : extractor.getWarnings()) {
                    System.err.println("WARN: " + warning);
                }

                new MetadataJsonMapper().writeToFile(metadata, out);
                System.out.println("extracted metadata to " + out);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("ERROR: extract failed: " + e.getMessage());
            return 1;
        }
    }
}
