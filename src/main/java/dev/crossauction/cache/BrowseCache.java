package dev.crossauction.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.crossauction.model.AuctionListing;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Short-lived local read cache for the browse GUI only. Never used for
 * money-moving operations (buy/bid always re-read the row from MySQL inside
 * a transaction). This exists purely to keep MySQL read load flat when up
 * to ~1000 concurrent players are opening/paginating the auction GUI.
 */
public final class BrowseCache {

    private final Cache<String, List<AuctionListing>> cache;
    private final AtomicLong generation = new AtomicLong();

    public BrowseCache(long ttlMs) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttlMs, TimeUnit.MILLISECONDS)
                .maximumSize(2000)
                .build();
    }

    private String key(int page, String search) {
        return generation.get() + ":" + page + ":" + (search == null ? "" : search.toLowerCase());
    }

    public List<AuctionListing> get(int page, String search) {
        return cache.getIfPresent(key(page, search));
    }

    public void put(int page, String search, List<AuctionListing> listings) {
        cache.put(key(page, search), listings);
    }

    public void invalidateAll() {
        generation.incrementAndGet();
    }
}
