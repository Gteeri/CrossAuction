package dev.crossauction.service;

import dev.crossauction.cache.BrowseCache;
import dev.crossauction.cache.RedisManager;
import dev.crossauction.config.ConfigManager;
import dev.crossauction.db.AuctionRepository;
import dev.crossauction.db.DatabaseManager;
import dev.crossauction.economy.EconomyProvider;
import dev.crossauction.model.AuctionListing;
import dev.crossauction.model.ListingStatus;
import dev.crossauction.model.ListingType;

import java.util.List;
import java.util.logging.Logger;

/**
 * Periodically resolves expired listings network-wide. Only one server in
 * the whole network actually performs the sweep at a time (protected by a
 * short-lived Redis lock), which keeps this O(1) extra load regardless of
 * how many backend servers exist. If Redis is disabled, every node runs its
 * own sweep; this stays correct because every mutation is still guarded by
 * a `FOR UPDATE` row lock (MySQL) or the single-connection pool (SQLite)
 * plus an ACTIVE status check.
 */
public final class ExpirySweepService {

    private static final String LOCK_KEY = "crossauction:lock:expiry-sweep";
    private static final long LOCK_TTL_MS = 25_000L;

    private final DatabaseManager db;
    private final AuctionRepository repo;
    private final EconomyProvider economy;
    private final RedisManager redis;
    private final BrowseCache browseCache;
    private final ConfigManager cfg;
    private final Logger logger;

    public ExpirySweepService(DatabaseManager db, EconomyProvider economy, RedisManager redis,
                               BrowseCache browseCache, ConfigManager cfg, Logger logger) {
        this.db = db;
        this.economy = economy;
        this.redis = redis;
        this.browseCache = browseCache;
        this.cfg = cfg;
        this.logger = logger;
        this.repo = new AuctionRepository(cfg);
    }

    public void sweepOnce() {
        if (!redis.tryAcquireLock(LOCK_KEY, LOCK_TTL_MS)) {
            return;
        }
        try {
            List<Long> expiredIds = db.transaction(conn -> repo.findExpiredIds(conn, 100)).join();
            for (Long id : expiredIds) {
                resolveExpired(id);
            }
        } catch (Exception e) {
            logger.warning("CrossAuction expiry sweep failed: " + e.getMessage());
        }
    }

    private void resolveExpired(long listingId) {
        try {
            AuctionListing resolved = db.transaction(conn -> {
                AuctionListing listing = repo.findByIdForUpdate(conn, listingId).orElse(null);
                if (listing == null || listing.getStatus() != ListingStatus.ACTIVE) {
                    return null;
                }
                if (listing.getType() == ListingType.AUCTION && listing.getCurrentBidderUuid() != null) {
                    repo.markResolved(conn, listingId, ListingStatus.SOLD, listing.getCurrentBidderUuid(), listing.getCurrentBidderName());
                    repo.insertHistory(conn, listing, listing.getCurrentBidderUuid(), listing.getCurrentBid(), "AUCTION_WON");
                    repo.insertItemClaim(conn, listingId, listing.getCurrentBidderUuid(), listing.getItemData(), "auction-won");
                    economy.depositInTransaction(conn, listing.getSellerUuid(), listing.getCurrentBid().doubleValue());
                } else {
                    repo.markResolved(conn, listingId, ListingStatus.EXPIRED, null, null);
                    repo.insertHistory(conn, listing, null, java.math.BigDecimal.ZERO, "EXPIRED");
                    repo.insertItemClaim(conn, listingId, listing.getSellerUuid(), listing.getItemData(), "expired-return");
                }
                return listing;
            }).join();
            if (resolved != null) {
                browseCache.invalidateAll();
                redis.publish("EXPIRED", listingId, resolved.getSellerUuid(), resolved.getCurrentBidderUuid());
            }
        } catch (Exception e) {
            logger.warning("Failed to resolve expired listing " + listingId + ": " + e.getMessage());
        }
    }

    /** Exposed for /auctionadmin forcesweep. */
    public void forceSweepNow() {
        sweepOnce();
    }
}
