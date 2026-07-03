package dev.crossauction;

import dev.crossauction.cache.BrowseCache;
import dev.crossauction.cache.RedisManager;
import dev.crossauction.command.AuctionAdminCommand;
import dev.crossauction.command.AuctionCommand;
import dev.crossauction.command.CollectCommand;
import dev.crossauction.config.ConfigManager;
import dev.crossauction.db.DatabaseManager;
import dev.crossauction.economy.EconomyProvider;
import dev.crossauction.economy.InternalEconomyProvider;
import dev.crossauction.economy.VaultEconomyProvider;
import dev.crossauction.gui.AuctionMenu;
import dev.crossauction.gui.GuiListener;
import dev.crossauction.listener.PlayerJoinListener;
import dev.crossauction.scheduler.SchedulerUtil;
import dev.crossauction.service.AuctionService;
import dev.crossauction.service.ExpirySweepService;
import dev.crossauction.util.ChatInputManager;
import dev.crossauction.util.Messages;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CrossAuction - a cross-server (network-wide) auction house built for
 * Paper/Folia 1.21.11, designed to keep working smoothly with up to ~1000
 * concurrent players spread across many backend servers.
 *
 * Architecture summary (see docs/ARCHITECTURE.md and docs/SCALING.md):
 * - MySQL (via HikariCP) is the single source of truth for listings,
 *   claims, history and (optionally) balances. All state changes run
 *   inside row-locked transactions so concurrent servers never race.
 * - Redis is used only for pub/sub cache invalidation/notifications and a
 *   lightweight distributed lock for the expiry sweep - never for
 *   correctness-critical state.
 * - A local Caffeine cache absorbs the GUI browse-read load so opening the
 *   auction house doesn't hit MySQL on every click across the whole network.
 * - All Bukkit API interaction is dispatched through SchedulerUtil, which
 *   uses Paper's region-aware schedulers so the plugin works unmodified on
 *   both Folia and regular Paper.
 */
public final class CrossAuctionPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private RedisManager redisManager;
    private BrowseCache browseCache;
    private EconomyProvider economyProvider;
    private AuctionService auctionService;
    private ExpirySweepService expirySweepService;
    private Messages messages;
    private ChatInputManager chatInputManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource_ifMissing("messages.yml");

        this.configManager = new ConfigManager(this);
        this.messages = new Messages(this);
        this.chatInputManager = new ChatInputManager();

        this.databaseManager = new DatabaseManager(configManager, getLogger());
        try {
            databaseManager.connect();
        } catch (Exception e) {
            getLogger().severe("Failed to connect to MySQL - disabling CrossAuction: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.redisManager = new RedisManager(configManager, getLogger());
        redisManager.connect();

        this.browseCache = new BrowseCache(configManager.browseCacheTtlMs());
        redisManager.onEvent(event -> browseCache.invalidateAll());

        this.economyProvider = configManager.economyProvider().equalsIgnoreCase("VAULT")
                ? createVaultProvider()
                : new InternalEconomyProvider(configManager);

        this.auctionService = new AuctionService(databaseManager, economyProvider, redisManager, browseCache, configManager);
        this.expirySweepService = new ExpirySweepService(databaseManager, economyProvider, redisManager, browseCache, configManager, getLogger());

        AuctionMenu menu = new AuctionMenu(this, auctionService, messages);

        getServer().getPluginManager().registerEvents(new GuiListener(this, auctionService, menu, messages, chatInputManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, auctionService, messages), this);
        getServer().getPluginManager().registerEvents(chatInputManager, this);

        var auctionCmd = getCommand("auction");
        if (auctionCmd != null) {
            auctionCmd.setExecutor(new AuctionCommand(this, auctionService, menu, messages, configManager));
        }
        var collectCmd = getCommand("collect");
        if (collectCmd != null) {
            collectCmd.setExecutor(new CollectCommand(this, auctionService, messages));
        }
        var adminCmd = getCommand("auctionadmin");
        if (adminCmd != null) {
            adminCmd.setExecutor(new AuctionAdminCommand(auctionService, expirySweepService, messages));
        }

        SchedulerUtil.runAsyncRepeating(this, expirySweepService::sweepOnce, 10, 10);

        getLogger().info("CrossAuction enabled on server-id '" + configManager.serverId() + "' (redis " +
                (redisManager.isEnabled() ? "enabled" : "disabled") + ").");
    }

    private EconomyProvider createVaultProvider() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("economy.provider is VAULT but Vault is not installed - falling back to INTERNAL economy.");
            return new InternalEconomyProvider(configManager);
        }
        try {
            return new VaultEconomyProvider();
        } catch (Exception e) {
            getLogger().warning("Failed to hook Vault economy, falling back to INTERNAL economy: " + e.getMessage());
            return new InternalEconomyProvider(configManager);
        }
    }

    private void saveResource_ifMissing(String name) {
        java.io.File file = new java.io.File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    @Override
    public void onDisable() {
        if (redisManager != null) redisManager.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
    }

    public AuctionService auctionService() {
        return auctionService;
    }

    public ExpirySweepService expirySweepService() {
        return expirySweepService;
    }
}
