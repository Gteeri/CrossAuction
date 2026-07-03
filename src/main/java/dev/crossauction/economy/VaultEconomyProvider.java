package dev.crossauction.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.Connection;
import java.util.UUID;

/**
 * Delegates to whichever Vault-compatible economy plugin is installed.
 *
 * IMPORTANT network-wide caveat: Vault economy calls are NOT part of our
 * MySQL transaction, so this only gives the same cross-server consistency
 * guarantee as CrossAuction's own tables if the underlying economy plugin's
 * storage is ALSO shared across the whole network (e.g. also backed by a
 * central MySQL server). If it stores balances per-server (e.g. flat-file),
 * use economy.provider: INTERNAL instead.
 */
public final class VaultEconomyProvider implements EconomyProvider {

    private final Economy economy;

    public VaultEconomyProvider() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            throw new IllegalStateException("Vault is present but no Economy provider is registered.");
        }
        this.economy = rsp.getProvider();
    }

    @Override
    public boolean has(UUID player, double amount) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(player);
        return economy.has(op, amount);
    }

    @Override
    public boolean withdrawInTransaction(Connection conn, UUID player, double amount) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(player);
        if (!economy.has(op, amount)) return false;
        return economy.withdrawPlayer(op, amount).transactionSuccess();
    }

    @Override
    public void depositInTransaction(Connection conn, UUID player, double amount) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(player);
        economy.depositPlayer(op, amount);
    }

    @Override
    public String format(double amount) {
        try {
            return economy.format(amount);
        } catch (Exception e) {
            return String.format("%.2f", amount);
        }
    }
}
