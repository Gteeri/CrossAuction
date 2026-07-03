package dev.crossauction.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AuctionListing {

    private long id;
    private UUID sellerUuid;
    private String sellerName;
    private String itemData;
    private int itemAmount;
    private String itemDisplay;
    private ListingType type;
    private BigDecimal price;
    private BigDecimal currentBid;
    private UUID currentBidderUuid;
    private String currentBidderName;
    private BigDecimal minBidIncrement;
    private ListingStatus status;
    private String serverOrigin;
    private Instant createdAt;
    private Instant expiresAt;
    private int version;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public UUID getSellerUuid() { return sellerUuid; }
    public void setSellerUuid(UUID sellerUuid) { this.sellerUuid = sellerUuid; }

    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }

    public String getItemData() { return itemData; }
    public void setItemData(String itemData) { this.itemData = itemData; }

    public int getItemAmount() { return itemAmount; }
    public void setItemAmount(int itemAmount) { this.itemAmount = itemAmount; }

    public String getItemDisplay() { return itemDisplay; }
    public void setItemDisplay(String itemDisplay) { this.itemDisplay = itemDisplay; }

    public ListingType getType() { return type; }
    public void setType(ListingType type) { this.type = type; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getCurrentBid() { return currentBid; }
    public void setCurrentBid(BigDecimal currentBid) { this.currentBid = currentBid; }

    public UUID getCurrentBidderUuid() { return currentBidderUuid; }
    public void setCurrentBidderUuid(UUID currentBidderUuid) { this.currentBidderUuid = currentBidderUuid; }

    public String getCurrentBidderName() { return currentBidderName; }
    public void setCurrentBidderName(String currentBidderName) { this.currentBidderName = currentBidderName; }

    public BigDecimal getMinBidIncrement() { return minBidIncrement; }
    public void setMinBidIncrement(BigDecimal minBidIncrement) { this.minBidIncrement = minBidIncrement; }

    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = status; }

    public String getServerOrigin() { return serverOrigin; }
    public void setServerOrigin(String serverOrigin) { this.serverOrigin = serverOrigin; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    /** The price relevant right now: the current bid for auctions once one exists, otherwise the listing price. */
    public BigDecimal effectivePrice() {
        return currentBid != null ? currentBid : price;
    }
}
