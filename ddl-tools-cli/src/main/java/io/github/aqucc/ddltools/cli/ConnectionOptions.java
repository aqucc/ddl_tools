package io.github.aqucc.ddltools.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.aqucc.ddltools.connect.ConnectionConfig;
import io.github.aqucc.ddltools.model.Dialect;

import picocli.CommandLine.Option;

/**
 * 全サブコマンド共通のDB接続オプション。
 *
 * <p>{@code --config} で指定したpropertiesファイル(url, user, password, schemas, dialect)を
 * ベースに、コマンドライン引数で個別に上書きする。優先順位はコマンドライン引数がpropertiesより高い。
 * パスワードは環境変数 {@value ConnectionConfig#ENV_PASSWORD} でさらに上書きできる
 * ({@link ConnectionConfig#effectivePassword()} が適用する)。
 */
public class ConnectionOptions {

    @Option(names = "--config", description = "接続設定propertiesファイル (url, user, password, schemas, dialect)")
    private Path config;

    @Option(names = "--url", description = "JDBC URL")
    private String url;

    @Option(names = "--user", description = "接続ユーザー")
    private String user;

    @Option(names = "--password", description = "接続パスワード (環境変数 " + ConnectionConfig.ENV_PASSWORD + " で上書き可能)")
    private String password;

    @Option(names = "--schemas", split = ",", description = "対象スキーマ名 (カンマ区切り)")
    private List<String> schemas;

    @Option(names = "--dialect", description = "DB方言 (oracle, postgres)")
    private String dialect;

    /** propertiesファイルとコマンドライン引数から{@link ConnectionConfig}を組み立てる。 */
    public ConnectionConfig toConnectionConfig() throws IOException {
        ConnectionConfig conf = (config != null) ? ConnectionConfig.fromFile(config) : new ConnectionConfig();
        if (url != null) {
            conf.setUrl(url);
        }
        if (user != null) {
            conf.setUser(user);
        }
        if (password != null) {
            conf.setPassword(password);
        }
        if (schemas != null && !schemas.isEmpty()) {
            conf.setSchemas(new ArrayList<String>(schemas));
        }
        if (dialect != null) {
            conf.setDialect(Dialect.fromString(dialect));
        }
        return conf;
    }
}
