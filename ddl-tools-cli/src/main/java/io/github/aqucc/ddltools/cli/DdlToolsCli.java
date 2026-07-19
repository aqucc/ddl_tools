package io.github.aqucc.ddltools.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * ddl-tools コマンドラインツールのエントリポイント。
 *
 * <p>Oracle/PostgreSQLのDDLダンプ、メタ情報抽出/解析、一覧表示、テストデータ生成の
 * 各機能をサブコマンドとして提供する。
 */
@Command(
        name = "ddl-tools",
        mixinStandardHelpOptions = true,
        version = "ddl-tools 0.1.0-SNAPSHOT",
        description = "Oracle/PostgreSQLのDDLダンプ・メタ情報抽出・解析・一覧・テストデータ生成CLI",
        subcommands = {
                DumpCommand.class,
                ExtractCommand.class,
                ParseCommand.class,
                ListCommand.class,
                GenDataCommand.class,
        })
public class DdlToolsCli implements Runnable {

    @Override
    public void run() {
        // サブコマンド未指定時はヘルプを表示する
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DdlToolsCli()).execute(args);
        System.exit(exitCode);
    }
}
