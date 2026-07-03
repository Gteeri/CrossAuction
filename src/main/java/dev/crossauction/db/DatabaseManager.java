package dev.crossauction.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.crossauction.config.ConfigManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Owns the single HikariCP pool shared by every code path in the plugin.
 *
 * Two backends are supported (see database.type in config.yml):
 * - MYSQL: every backend server in the network points this at the SAME
 *   MySQL instance, which is what makes listings/claims/balances consistent
 *   network-wide. Row-level {@code SELECT ... FOR UPDATE} locking serializes
 *   concurrent access to the same row from any server.
 * - SQLITE: a single local file, intended only for quick single-server
 *   testing (never cross-server). The Hikari pool is capped at 1 connection,
 *   which - combined with {@link #transaction(SqlFunction)} always running
 *   on a worker pool - gives the same full-mutual-exclusion guarantee that
 *   {@code FOR UPDATE} gives on MySQL, without needing that clause (SQLite
 *   doesn't support it).
 *
 * {@link #transaction(SqlFunction)} always runs off the calling thread (on
 * a small dedicated worker pool) and returns a {@link CompletableFuture},
 * so callers never block Bukkit's main/region threads - required for Folia
 * and for staying responsive at ~1000 concurrent players.
 */
public final class DatabaseManager {

    private final ConfigManager cfg;
    private final Logger logger;
    private final File dataFolder;
    private HikariDataSource dataSource;
    private ExecutorService executor;

    public DatabaseManager(ConfigManager cfg, Logger logger, File dataFolder) {
        this.cfg = cfg;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    public void connect() {
        HikariConfig hc = new HikariConfig();
        int poolSize;
        if (cfg.isSqlite()) {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, cfg.sqliteFile());
            String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath()
                    + "?journal_mode=WAL&busy_timeout=10000&synchronous=NORMAL";
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setJdbcUrl(jdbcUrl);
            poolSize = 1;
            hc.setMaximumPoolSize(poolSize);
            hc.setConnectionTimeout(cfg.dbConnectionTimeoutMs());
            hc.setPoolName("CrossAuction-" + cfg.serverId());
        } else {
            String jdbcUrl = "jdbc:mysql://" + cfg.dbHost() + ":" + cfg.dbPort() + "/" + cfg.dbName()
                    + "?useSSL=" + cfg.dbUseSsl() + "&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";
            hc.setJdbcUrl(jdbcUrl);
            hc.setUsername(cfg.dbUser());
            hc.setPassword(cfg.dbPassword());
            poolSize = cfg.dbPoolSize();
            hc.setMaximumPoolSize(poolSize);
            hc.setConnectionTimeout(cfg.dbConnectionTimeoutMs());
            hc.setPoolName("CrossAuction-" + cfg.serverId());
            hc.addDataSourceProperty("cachePrepStmts", "true");
            hc.addDataSourceProperty("prepStmtCacheSize", "250");
            hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }
        this.dataSource = new HikariDataSource(hc);
        this.executor = Executors.newFixedThreadPool(Math.max(2, poolSize), r -> {
            Thread t = new Thread(r, "CrossAuction-DB-Worker");
            t.setDaemon(true);
            return t;
        });
        applySchema();
    }

    private void applySchema() {
        String resourceName = cfg.isSqlite() ? "schema-sqlite.sql" : "schema.sql";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                logger.warning(resourceName + " not found on classpath, skipping auto-migration.");
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
     * Runs {@code body} inside a single JDBC transaction (autoCommit off)
     * on a background worker thread, committing on success and rolling
     * back on any exception. All money/state-mutating repository calls
     * MUST go through this so that concurrent requests serialize safely
     * (via row locks on MySQL, or via the single-connection pool on
     * SQLite), and so Bukkit's main/region threads are never blocked on
     * JDBC I/O.
     */
    public <T> CompletableFuture<T> transaction(SqlFunction<Connection, T> body) {
        return CompletableFuture.supplyAsync(() -> runTransaction(body), executor);
    }

    private <T> T runTransaction(SqlFunction<Connection, T> body) {
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
        if (executor != null) {
            executor.shutdown();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @FunctionalInterface
    public interface SqlFunction<A, R> {
        R apply(A a) throws SQLException;
    }
}
