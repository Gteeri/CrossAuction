package dev.crossauction.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Locale;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    public String serverId() { return cfg.getString("server-id", "server-1"); }

    public String dbType() { return cfg.getString("database.type", "SQLITE").toUpperCase(Locale.ROOT); }
    public boolean isSqlite() { return dbType().equals("SQLITE"); }
    public String sqliteFile() { return cfg.getString("database.sqlite-file", "crossauction.db"); }

    public String dbHost() { return cfg.getString("database.host", "127.0.0.1"); }
    public int dbPort() { return cfg.getInt("database.port", 3306); }
    public String dbName() { return cfg.getString("database.database", "crossauction"); }
    public String dbUser() { return cfg.getString("database.username", "crossauction"); }
    public String dbPassword() { return cfg.getString("database.password", ""); }
    public boolean dbUseSsl() { return cfg.getBoolean("database.useSSL", false); }
    public int dbPoolSize() { return cfg.getInt("database.pool-size", 10); }
    public long dbConnectionTimeoutMs() { return cfg.getLong("database.connection-timeout-ms", 8000); }

    public boolean redisEnabled() { return cfg.getBoolean("redis.enabled", true); }
    public String redisHost() { return cfg.getString("redis.host", "127.0.0.1"); }
    public int redisPort() { return cfg.getInt("redis.port", 6379); }
    public String redisPassword() { return cfg.getString("redis.password", ""); }
    public int redisDatabase() { return cfg.getInt("redis.database", 0); }
    public String redisChannel() { return cfg.getString("redis.channel", "crossauction:events"); }
    public String redisLockKey() { return cfg.getString("redis.lock-key", "crossauction:lock:expiry-sweep"); }

    public String economyProvider() { return cfg.getString("economy.provider", "INTERNAL"); }
    public String currencySymbol() { return cfg.getString("economy.currency-symbol", "$"); }
    public double saleTaxPercent() { return cfg.getDouble("economy.sale-tax-percent", 5.0); }

    public int maxListingsPerPlayer() { return cfg.getInt("auction.max-active-listings-per-player", 10); }
    public long defaultDurationMinutes() { return cfg.getLong("auction.default-duration-minutes", 2880); }
    public long defaultDurationSeconds() { return defaultDurationMinutes() * 60; }
    public long minDurationMinutes() { return cfg.getLong("auction.min-duration-minutes", 5); }
    public long maxDurationMinutes() { return cfg.getLong("auction.max-duration-minutes", 10080); }
    public double minPrice() { return cfg.getDouble("auction.min-price", 1.0); }
    public double maxPrice() { return cfg.getDouble("auction.max-price", 1_000_000_000.0); }
    public boolean allowBidding() { return cfg.getBoolean("auction.allow-bidding", true); }
    public double minBidIncrementPercent() { return cfg.getDouble("auction.min-bid-increment-percent", 5.0); }
    public BigDecimal defaultMinBidIncrement() { return BigDecimal.valueOf(cfg.getDouble("auction.default-min-bid-increment", 1.0)); }
    public long actionCooldownMs() { return cfg.getLong("auction.action-cooldown-ms", 500); }
    public long expirySweepIntervalSeconds() { return cfg.getLong("auction.expiry-sweep-interval-seconds", 30); }
    public long browseCacheTtlMs() { return cfg.getLong("auction.browse-cache-ttl-ms", 3000); }
    public int pageSize() { return cfg.getInt("auction.listings-per-page", 45); }

    public String guiTitle() { return cfg.getString("gui.title", "Cross-Server Auction House"); }
    public int guiRows() { return cfg.getInt("gui.rows", 6); }

    public boolean debug() { return cfg.getBoolean("logging.debug", false); }
}
