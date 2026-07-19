package io.github.aqucc.ddltools.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.concurrent.Callable;

import io.github.aqucc.ddltools.connect.ConnectionConfig;
import io.github.aqucc.ddltools.connect.JdbcRowSource;
import io.github.aqucc.ddltools.connect.RowSource;
import io.github.aqucc.ddltools.dump.DdlDumper;
import io.github.aqucc.ddltools.dump.DdlFileWriter;
import io.github.aqucc.ddltools.dump.DumpResult;
import io.github.aqucc.ddltools.dump.OracleDdlDumper;
import io.github.aqucc.ddltools.dump.PostgresDdlDumper;
import io.github.aqucc.ddltools.model.Dialect;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * DB内の全オブジェクトのDDLをダンプするサブコマンド。
 *
 * <p>デフォルトでは {@code --out} で指定したディレクトリへオブジェクトごとにファイルを分割して出力する。
 * {@code --single-file} を指定した場合は全DDLを1ファイルへ連結して出力する。
 */
@Command(name = "dump", mixinStandardHelpOptions = true, description = "DBの全オブジェクトのDDLをダンプする")
public class DumpCommand implements Callable<Integer> {

    @Mixin
    private ConnectionOptions connectionOptions;

    @Option(names = "--out", description = "出力先ディレクトリ (オブジェクトごとにファイル分割、デフォルト: ${DEFAULT-VALUE})",
            defaultValue = "ddl-dump")
    private Path outDir;

    @Option(names = "--single-file", description = "指定した場合、全DDLを1ファイルへ連結して出力する (--outより優先)")
    private Path singleFile;

    @Override
    public Integer call() {
        try {
            ConnectionConfig config = connectionOptions.toConnectionConfig();
            Dialect dialect = config.effectiveDialect();

            try (Connection connection = config.open()) {
                RowSource rowSource = new JdbcRowSource(connection);
                DdlDumper dumper = (dialect == Dialect.ORACLE)
                        ? new OracleDdlDumper(rowSource)
                        : new PostgresDdlDumper(rowSource);
                DumpResult result = dumper.dump(config.getSchemas());

                for (String warning : result.getWarnings()) {
                    System.err.println("WARN: " + warning);
                }

                DdlFileWriter writer = new DdlFileWriter();
                if (singleFile != null) {
                    writer.writeSingle(result, singleFile);
                    System.out.println("dumped " + result.getObjects().size()
                            + " object(s) to " + singleFile);
                } else {
                    Path dir = (outDir != null) ? outDir : Paths.get("ddl-dump");
                    writer.writeSplit(result, dir);
                    System.out.println("dumped " + result.getObjects().size()
                            + " object(s) to " + dir);
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("ERROR: dump failed: " + e.getMessage());
            return 1;
        }
    }
}
