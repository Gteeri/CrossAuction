package dev.crossauction.db;

import dev.crossauction.model.AuctionClaim;
import dev.crossauction.model.AuctionListing;
import dev.crossauction.model.ListingStatus;
import dev.crossauction.model.ListingType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raw SQL access, matching schema.sql exactly. All money/state-changing
 * methods that take a {@link Connection} are meant to be called from inside
 * {@link DatabaseManager#transaction}, using {@code SELECT ... FOR UPDATE}
 * to serialize concurrent access to a single row regardless of which of the
 * many backend servers issued the request.
 */
public final class AuctionRepository {

    public long insertListing(Connection conn, AuctionListing l) throws SQLException {
        String sql = "INSERT INTO ca_listings (seller_uuid, seller_name, item_data, item_amount, item_display, " +
                "listing_type, price, current_bid, min_bid_increment, status, server_origin, created_at, expires_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, l.getSellerUuid().toString());
            ps.setString(2, l.getSellerName());
            ps.setString(3, l.getItemData());
            ps.setInt(4, l.getItemAmount());
            ps.setString(5, l.getItemDisplay());
            ps.setString(6, l.getType().name());
            ps.setBigDecimal(7, l.getPrice());
            ps.setBigDecimal(8, l.getType() == ListingType.AUCTION ? l.getPrice() : null);
            ps.setBigDecimal(9, l.getMinBidIncrement());
            ps.setString(10, ListingStatus.ACTIVE.name());
            ps.setString(11, l.getServerOrigin());
            ps.setTimestamp(12, Timestamp.from(l.getCreatedAt()));
            ps.setTimestamp(13, Timestamp.from(l.getExpiresAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Optional<AuctionListing> findByIdForUpdate(Connection conn, long id) throws SQLException {
        String sql = "SELECT * FROM ca_listings WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        }
    }

    public List<AuctionListing> findActivePage(Connection conn, int page, int pageSize, String search) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM ca_listings WHERE status = 'ACTIVE'");
        if (search != null && !search.isBlank()) {
            sql.append(" AND item_display LIKE ?");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (search != null && !search.isBlank()) {
                ps.setString(idx++, "%" + search + "%");
            }
            ps.setInt(idx++, pageSize);
            ps.setInt(idx, Math.max(0, page) * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                List<AuctionListing> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        }
    }

    public int countActiveBySeller(Connection conn, UUID seller) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ca_listings WHERE status = 'ACTIVE' AND seller_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seller.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<Long> findExpiredIds(Connection conn, int limit) throws SQLException {
        String sql = "SELECT id FROM ca_listings WHERE status = 'ACTIVE' AND expires_at <= NOW() LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getLong(1));
                return ids;
            }
        }
    }

    public void markResolved(Connection conn, long id, ListingStatus status, UUID bidderUuid, String bidderName) throws SQLException {
        String sql = "UPDATE ca_listings SET status=?, current_bidder_uuid=COALESCE(?, current_bidder_uuid), " +
                "current_bidder_name=COALESCE(?, current_bidder_name), resolved_at=NOW(), version=version+1 WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, bidderUuid == null ? null : bidderUuid.toString());
            ps.setString(3, bidderName);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    public void updateBid(Connection conn, long id, BigDecimal newBid, UUID bidderUuid, String bidderName) throws SQLException {
        String sql = "UPDATE ca_listings SET current_bid=?, current_bidder_uuid=?, current_bidder_name=?, version=version+1 WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newBid);
            ps.setString(2, bidderUuid.toString());
            ps.setString(3, bidderName);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    public void insertHistory(Connection conn, AuctionListing l, UUID buyer, BigDecimal finalPrice, String resolvedAs) throws SQLException {
        String sql = "INSERT INTO ca_history (listing_id, seller_uuid, buyer_uuid, item_display, final_price, listing_type, resolved_as) " +
                "VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, l.getId());
            ps.setString(2, l.getSellerUuid().toString());
            ps.setString(3, buyer == null ? null : buyer.toString());
            ps.setString(4, l.getItemDisplay());
            ps.setBigDecimal(5, finalPrice);
            ps.setString(6, l.getType().name());
            ps.setString(7, resolvedAs);
            ps.executeUpdate();
        }
    }

    public void insertItemClaim(Connection conn, long listingId, UUID player, String itemDataBase64, String reason) throws SQLException {
        String sql = "INSERT INTO ca_claims (listing_id, player_uuid, claim_type, item_data, reason) VALUES (?,?,'ITEM',?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, listingId);
            ps.setString(2, player.toString());
            ps.setString(3, itemDataBase64);
            ps.setString(4, reason);
            ps.executeUpdate();
        }
    }

    public void insertMoneyClaim(Connection conn, long listingId, UUID player, BigDecimal amount, String reason) throws SQLException {
        String sql = "INSERT INTO ca_claims (listing_id, player_uuid, claim_type, amount, reason) VALUES (?,?,'MONEY',?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, listingId);
            ps.setString(2, player.toString());
            ps.setBigDecimal(3, amount);
            ps.setString(4, reason);
            ps.executeUpdate();
        }
    }

    public List<AuctionClaim> findUnclaimed(Connection conn, UUID player) throws SQLException {
        String sql = "SELECT * FROM ca_claims WHERE player_uuid = ? AND claimed = 0 ORDER BY id ASC LIMIT 200";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<AuctionClaim> out = new ArrayList<>();
                while (rs.next()) {
                    AuctionClaim c = new AuctionClaim();
                    c.setId(rs.getLong("id"));
                    c.setListingId(rs.getLong("listing_id"));
                    c.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
                    c.setType(AuctionClaim.Type.valueOf(rs.getString("claim_type")));
                    c.setItemData(rs.getString("item_data"));
                    BigDecimal amt = rs.getBigDecimal("amount");
                    c.setAmount(amt == null ? 0 : amt.doubleValue());
                    c.setReason(rs.getString("reason"));
                    out.add(c);
                }
                return out;
            }
        }
    }

    public int countUnclaimed(Connection conn, UUID player) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ca_claims WHERE player_uuid = ? AND claimed = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void markClaimed(Connection conn, long claimId) throws SQLException {
        String sql = "UPDATE ca_claims SET claimed = 1, claimed_at = NOW() WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, claimId);
            ps.executeUpdate();
        }
    }

    private AuctionListing map(ResultSet rs) throws SQLException {
        AuctionListing l = new AuctionListing();
        l.setId(rs.getLong("id"));
        l.setSellerUuid(UUID.fromString(rs.getString("seller_uuid")));
        l.setSellerName(rs.getString("seller_name"));
        l.setItemData(rs.getString("item_data"));
        l.setItemAmount(rs.getInt("item_amount"));
        l.setItemDisplay(rs.getString("item_display"));
        l.setType(ListingType.valueOf(rs.getString("listing_type")));
        l.setPrice(rs.getBigDecimal("price"));
        l.setCurrentBid(rs.getBigDecimal("current_bid"));
        String bidder = rs.getString("current_bidder_uuid");
        l.setCurrentBidderUuid(bidder == null ? null : UUID.fromString(bidder));
        l.setCurrentBidderName(rs.getString("current_bidder_name"));
        l.setMinBidIncrement(rs.getBigDecimal("min_bid_increment"));
        l.setStatus(ListingStatus.valueOf(rs.getString("status")));
        l.setServerOrigin(rs.getString("server_origin"));
        l.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        l.setExpiresAt(rs.getTimestamp("expires_at").toInstant());
        l.setVersion(rs.getInt("version"));
        return l;
    }
}
