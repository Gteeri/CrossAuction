package dev.crossauction.economy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Abstraction over "where does auction house money come from/go to".
 * Implementations: {@link VaultEconomyProvider} (delegates to whatever
 * economy plugin is installed via Vault) and {@link InternalEconomyProvider}
 * (CrossAuction manages its own network-wide balance table).
 */
public interface EconomyProvider {

    boolean has(UUID player, double amount);

    /**
     * Withdraws inside an existing DB transaction on {@code conn}. Must
     * return false (and change nothing) if the player can't afford it.
     */
    boolean withdrawInTransaction(Connection conn, UUID player, double amount) throws SQLException;

    void depositInTransaction(Connection conn, UUID player, double amount) throws SQLException;

    String format(double amount);
}
