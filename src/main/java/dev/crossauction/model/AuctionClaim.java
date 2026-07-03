package dev.crossauction.model;

import java.util.UUID;

public final class AuctionClaim {

    public enum Type { ITEM, MONEY }

    private long id;
    private long listingId;
    private UUID playerUuid;
    private Type type;
    private String itemData;
    private double amount;
    private String reason;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getListingId() { return listingId; }
    public void setListingId(long listingId) { this.listingId = listingId; }

    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getItemData() { return itemData; }
    public void setItemData(String itemData) { this.itemData = itemData; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
