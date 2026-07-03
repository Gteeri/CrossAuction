package dev.crossauction.service;

import dev.crossauction.cache.BrowseCache;
import dev.crossauction.cache.RedisManager;
import dev.crossauction.config.ConfigManager;
import dev.crossauction.db.AuctionRepository;
import dev.crossauction.db.DatabaseManager;
import dev.crossauction.economy.EconomyProvider;
import dev.crossauction.model.AuctionClaim;
import dev.crossauction.model.AuctionListing;
import dev.crossauction.model.ListingStatus;
import dev.crossauction.model.ListingType;
import dev.crossauction.serialize.ItemSerialization;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * All auction-house business logic. Every public method that touches money
 * or listing state runs inside {@link DatabaseManager#transaction} on the
 * shared database so it is safe regardless of which of the (possibly many,
 * up to ~1000-player-supporting) backend servers calls it concurrently.
 * Returned futures complete on the DB executor; callers MUST hop back to
 * the main/region thread before touching Bukkit API objects.
 */
public final class AuctionService {

    private final DatabaseManager db;
    private final AuctionRepository repo;
    private final EconomyProvider economy;
    private final RedisManager redis;
    private final BrowseCache browseCache;
    private final ConfigManager cfg;

    public AuctionService(DatabaseManager db, EconomyProvider economy, RedisManager redis, BrowseCache browseCache, ConfigManager cfg) {
        this.db = db;
        this.economy = economy;
        this.redis = redis;
        this.browseCache = browseCache;
        this.cfg = cfg;
        this.repo = new AuctionRepository(cfg);
    }

    public CompletableFuture<Long> createListing(UUID seller, String sellerName, ItemStack item, ListingType type,
                                                  BigDecimal price, BigDecimal minBidIncrement, long durationSeconds) {
        return db.transaction(conn -> {
            int active = repo.countActiveBySeller(conn, seller);
            if (active >= cfg.maxListingsPerPlayer()) {
                throw new ServiceException("max-listings", "error.max-listings");
            }
            AuctionListing listing = new AuctionListing();
            listing.setSellerUuid(seller);
            listing.setSellerName(sellerName);
            listing.setItemData(ItemSerialization.serialize(item));
            listing.setItemAmount(item.getAmount());
            listing.setItemDisplay(ItemSerialization.displayName(item));
            listing.setType(type);
            listing.setPrice(price);
            listing.setMinBidIncrement(minBidIncrement);
            listing.setServerOrigin(cfg.serverId());
            listing.setCreatedAt(Instant.now());
            listing.setExpiresAt(Instant.now().plus(durationSeconds, ChronoUnit.SECONDS));
            long id = repo.insertListing(conn, listing);
            return id;
        }).whenComplete((id, err) -> {
            if (err == null) {
                browseCache.invalidateAll();
                redis.publish("CREATED", id, seller, null);
            }
        });
    }

    public CompletableFuture<List<AuctionListing>> browse(int page, String search) {
        List<AuctionListing> cached = browseCache.get(page, search);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return db.transaction(conn -> repo.findActivePage(conn, page, cfg.pageSize(), search))
                .thenApply(list -> {
                    browseCache.put(page, search, list);
                    return list;
                });
    }

    public CompletableFuture<Void> buyNow(UUID buyer, String buyerName, long listingId) {
        return db.transaction(conn -> {
            AuctionListing listing = repo.findByIdForUpdate(conn, listingId)
                    .orElseThrow(() -> new ServiceException("not-found", "error.listing-not-found"));
            if (listing.getStatus() != ListingStatus.ACTIVE) {
                throw new ServiceException("not-active", "error.listing-not-active");
            }
            if (listing.getType() != ListingType.BUY_NOW && listing.getType() != ListingType.AUCTION) {
                throw new ServiceException("not-buyable", "error.listing-not-buyable");
            }
            if (listing.getSellerUuid().equals(buyer)) {
                throw new ServiceException("own-listing", "error.cannot-buy-own");
            }
            BigDecimal cost = listing.effectivePrice();
            if (!economy.withdrawInTransaction(conn, buyer, cost.doubleValue())) {
                throw new ServiceException("insufficient-funds", "error.insufficient-funds");
            }
            economy.depositInTransaction(conn, listing.getSellerUuid(), cost.doubleValue());
            repo.markResolved(conn, listingId, ListingStatus.SOLD, buyer, buyerName);
            repo.insertHistory(conn, listing, buyer, cost, "BUY_NOW");
            repo.insertItemClaim(conn, listingId, buyer, listing.getItemData(), "purchase");
            return listing;
        }).thenAccept(listing -> {
            browseCache.invalidateAll();
            redis.publish("SOLD", listingId, listing.getSellerUuid(), buyer);
        });
    }

    public CompletableFuture<Void> placeBid(UUID bidder, String bidderName, long listingId, BigDecimal bidAmount) {
        return db.transaction(conn -> {
            AuctionListing listing = repo.findByIdForUpdate(conn, listingId)
                    .orElseThrow(() -> new ServiceException("not-found", "error.listing-not-found"));
            if (listing.getStatus() != ListingStatus.ACTIVE || listing.getType() != ListingType.AUCTION) {
                throw new ServiceException("not-active", "error.listing-not-active");
            }
            if (listing.getSellerUuid().equals(bidder)) {
                throw new ServiceException("own-listing", "error.cannot-bid-own");
            }
            BigDecimal minNext = listing.getCurrentBid().add(listing.getMinBidIncrement());
            if (bidAmount.compareTo(minNext) < 0) {
                throw new ServiceException("bid-too-low", "error.bid-too-low");
            }
            if (!economy.withdrawInTransaction(conn, bidder, bidAmount.doubleValue())) {
                throw new ServiceException("insufficient-funds", "error.insufficient-funds");
            }
            UUID previousBidder = listing.getCurrentBidderUuid();
            BigDecimal previousBid = listing.getCurrentBid();
            if (previousBidder != null) {
                economy.depositInTransaction(conn, previousBidder, previousBid.doubleValue());
                repo.insertMoneyClaim(conn, listingId, previousBidder, previousBid, "outbid-refund");
            }
            repo.updateBid(conn, listingId, bidAmount, bidder, bidderName);
            return listing;
        }).thenAccept(listing -> {
            browseCache.invalidateAll();
            redis.publish("BID", listingId, listing.getSellerUuid(), bidder);
        });
    }

    public CompletableFuture<Void> cancelListing(UUID requester, long listingId, boolean isAdmin) {
        return db.transaction(conn -> {
            AuctionListing listing = repo.findByIdForUpdate(conn, listingId)
                    .orElseThrow(() -> new ServiceException("not-found", "error.listing-not-found"));
            if (listing.getStatus() != ListingStatus.ACTIVE) {
                throw new ServiceException("not-active", "error.listing-not-active");
            }
            if (!isAdmin && !listing.getSellerUuid().equals(requester)) {
                throw new ServiceException("not-owner", "error.not-your-listing");
            }
            if (listing.getType() == ListingType.AUCTION && listing.getCurrentBidderUuid() != null) {
                economy.depositInTransaction(conn, listing.getCurrentBidderUuid(), listing.getCurrentBid().doubleValue());
                repo.insertMoneyClaim(conn, listingId, listing.getCurrentBidderUuid(), listing.getCurrentBid(), "cancel-refund");
            }
            repo.markResolved(conn, listingId, ListingStatus.CANCELLED, null, null);
            repo.insertItemClaim(conn, listingId, listing.getSellerUuid(), listing.getItemData(), "cancel-return");
            return listing;
        }).thenAccept(listing -> {
            browseCache.invalidateAll();
            redis.publish("CANCELLED", listingId, listing.getSellerUuid(), null);
        });
    }

    public CompletableFuture<Integer> countUnclaimed(UUID player) {
        return db.transaction(conn -> repo.countUnclaimed(conn, player));
    }

    /**
     * Delivers all pending claims (items and money) for a player. Item
     * claims are returned to the caller (already marked claimed in the DB)
     * so the caller can give them via Bukkit inventory APIs on the correct
     * region thread; money claims are fully resolved here since they don't
     * touch Bukkit API.
     */
    public CompletableFuture<List<ItemStack>> collect(UUID player) {
        return db.transaction(conn -> {
            List<AuctionClaim> claims = repo.findUnclaimed(conn, player);
            List<ItemStack> items = new java.util.ArrayList<>();
            for (AuctionClaim claim : claims) {
                if (claim.getType() == AuctionClaim.Type.MONEY) {
                    economy.depositInTransaction(conn, player, claim.getAmount());
                } else {
                    items.add(ItemSerialization.deserialize(claim.getItemData()));
                }
                repo.markClaimed(conn, claim.getId());
            }
            return items;
        });
    }

    public CompletableFuture<Optional<AuctionListing>> findById(long listingId) {
        return db.transaction(conn -> repo.findByIdForUpdate(conn, listingId));
    }
}
