package dev.crossauction.economy;

import dev.crossauction.config.ConfigManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.UUID;

/**
 * Self-contained network-wide balance ledger stored in ca_balances, in the
 * SAME MySQL database as listings. Recommended when the server network
 * doesn't already have a synced (network-wide) economy plugin, since it
 * guarantees every backend server sees the same balance at all times.
 */
public final class InternalEconomyProvider implements EconomyProvider {

    private final ConfigManager cfg;

    public InternalEconomyProvider(ConfigManager cfg) {
        this.cfg = cfg;
    }

    @Override
    public boolean has(UUID player, double amount) {
        // Best-effort outside a transaction (e.g. for GUI display); the
        // authoritative check happens inside withdrawInTransaction.
        return true;
    }

    private BigDecimal getOrCreateForUpdate(Connection conn, UUID player) throws SQLException {
        String select = "SELECT balance FROM ca_balances WHERE player_uuid = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        }
        String insert = "INSERT INTO ca_balances (player_uuid, balance) VALUES (?, 0)";
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        }
        return BigDecimal.ZERO;
    }

    @Override
    public boolean withdrawInTransaction(Connection conn, UUID player, double amount) throws SQLException {
        BigDecimal current = getOrCreateForUpdate(conn, player);
        BigDecimal cost = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        if (current.compareTo(cost) < 0) {
            return false;
        }
        String sql = "UPDATE ca_balances SET balance = balance - ?, version = version + 1 WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, cost);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        }
        return true;
    }

    @Override
    public void depositInTransaction(Connection conn, UUID player, double amount) throws SQLException {
        getOrCreateForUpdate(conn, player);
        BigDecimal gain = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        String sql = "UPDATE ca_balances SET balance = balance + ?, version = version + 1 WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, gain);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        }
    }

    @Override
    public String format(double amount) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return cfg.currencySymbol() + df.format(amount);
    }
}
