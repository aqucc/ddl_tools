package io.github.aqucc.ddltools.connect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.github.aqucc.ddltools.model.Dialect;

/**
 * DB接続設定。propertiesファイルとプログラムの両方から構築できる。
 *
 * <p>propertiesのキー: url, user, password, schemas (カンマ区切り), dialect (省略時はurlから推定)。
 * パスワードは環境変数 DDLTOOLS_PASSWORD でも指定できる(propertiesより優先)。
 */
public class ConnectionConfig {

    public static final String ENV_PASSWORD = "DDLTOOLS_PASSWORD";

    private String url;
    private String user;
    private String password;
    private List<String> schemas = new ArrayList<String>();
    private Dialect dialect;

    public static ConnectionConfig fromFile(Path file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        return fromProperties(props);
    }

    public static ConnectionConfig fromProperties(Properties props) {
        ConnectionConfig config = new ConnectionConfig();
        config.url = props.getProperty("url");
        config.user = props.getProperty("user");
        config.password = props.getProperty("password");
        String schemas = props.getProperty("schemas");
        if (schemas != null) {
            for (String s : schemas.split(",")) {
                if (!s.trim().isEmpty()) {
                    config.schemas.add(s.trim());
                }
            }
        }
        String dialect = props.getProperty("dialect");
        if (dialect != null) {
            config.dialect = Dialect.fromString(dialect);
        }
        return config;
    }

    /** 環境変数によるパスワード上書きを適用した実効パスワード。 */
    public String effectivePassword() {
        String env = System.getenv(ENV_PASSWORD);
        return (env != null && !env.isEmpty()) ? env : password;
    }

    /** dialect未指定の場合、JDBC URLから推定する。 */
    public Dialect effectiveDialect() {
        if (dialect != null) {
            return dialect;
        }
        if (url != null) {
            if (url.startsWith("jdbc:oracle:")) {
                return Dialect.ORACLE;
            }
            if (url.startsWith("jdbc:postgresql:")) {
                return Dialect.POSTGRESQL;
            }
        }
        throw new IllegalStateException("dialect could not be determined from url: " + url);
    }

    public Connection open() throws SQLException {
        if (url == null) {
            throw new IllegalStateException("connection url is not set");
        }
        return DriverManager.getConnection(url, user, effectivePassword());
    }

    public String getUrl() {
        return url;
    }

    public ConnectionConfig setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getUser() {
        return user;
    }

    public ConnectionConfig setUser(String user) {
        this.user = user;
        return this;
    }

    public ConnectionConfig setPassword(String password) {
        this.password = password;
        return this;
    }

    public List<String> getSchemas() {
        return schemas;
    }

    public ConnectionConfig setSchemas(List<String> schemas) {
        this.schemas = schemas;
        return this;
    }

    public ConnectionConfig setDialect(Dialect dialect) {
        this.dialect = dialect;
        return this;
    }
}
