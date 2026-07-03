package dev.crossauction.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.crossauction.config.ConfigManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Owns the single HikariCP pool shared by every code path in the plugin.
 * Every backend server in the network points this at the SAME MySQL
 * instance, which is what makes listings/claims/balances consistent
 * network-wide.
 */
public final class DatabaseManager {

    private final ConfigManager cfg;
    private final Logger logger;
    private HikariDataSource dataSource;

    public DatabaseManager(ConfigManager cfg, Logger logger) {
        this.cfg = cfg;
        this.logger = logger;
    }

    public void connect() {
        HikariConfig hc = new HikariConfig();
        String jdbcUrl = "jdbc:mysql://" + cfg.dbHost() + ":" + cfg.dbPort() + "/" + cfg.dbName()
                + "?useSSL=" + cfg.dbUseSsl() + "&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername(cfg.dbUser());
        hc.setPassword(cfg.dbPassword());
        hc.setMaximumPoolSize(cfg.dbPoolSize());
        hc.setConnectionTimeout(cfg.dbConnectionTimeoutMs());
        hc.setPoolName("CrossAuction-" + cfg.serverId());
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.dataSource = new HikariDataSource(hc);
        applySchema();
    }

    private void applySchema() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (in == null) {
                logger.warning("schema.sql not found on classpath, skipping auto-migration.");
                return;
            }
            String sql;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("--")) continue;
                    sb.append(line).append('\n');
                }
                sql = sb.toString();
            }
            try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        st.execute(trimmed);
                    }
                }
            }
        } catch (IOException | SQLException e) {
            logger.severe("Failed to apply CrossAuction schema: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Runs {@code body} inside a single JDBC transaction (autoCommit off),
     * committing on success and rolling back on any exception. All
     * money/state-mutating repository calls MUST go through this so that
     * concurrent requests from different backend servers serialize safely
     * via {@code SELECT ... FOR UPDATE} row locks in MySQL.
     */
    public <T> T transaction(SqlFunction<Connection, T> body) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = body.apply(conn);
                conn.commit();
                return result;
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @FunctionalInterface
    public interface SqlFunction<A, R> {
        R apply(A a) throws SQLException;
    }
}
