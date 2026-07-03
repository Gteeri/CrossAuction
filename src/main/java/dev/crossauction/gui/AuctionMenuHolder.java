package dev.crossauction.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Marks an inventory as a CrossAuction GUI and tracks which listing each
 * slot represents, plus the current page/search context, so GuiListener can
 * resolve clicks without any static/global lookup maps (safe under Folia's
 * per-region threading).
 */
public final class AuctionMenuHolder implements InventoryHolder {

    public record ListingRef(long id, dev.crossauction.model.ListingType type, BigDecimal minNextBid) {}

    private Inventory inventory;
    private final Map<Integer, ListingRef> slotListings = new HashMap<>();
    private int page;
    private String search;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void putListing(int slot, ListingRef ref) {
        slotListings.put(slot, ref);
    }

    public ListingRef getListing(int slot) {
        return slotListings.get(slot);
    }

    public void clearListings() {
        slotListings.clear();
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }
}
