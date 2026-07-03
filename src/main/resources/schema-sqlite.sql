CREATE TABLE IF NOT EXISTS ca_listings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    seller_uuid TEXT NOT NULL,
    seller_name TEXT NOT NULL,
    item_data TEXT NOT NULL,
    item_amount INTEGER NOT NULL DEFAULT 1,
    item_display TEXT NOT NULL,
    listing_type TEXT NOT NULL CHECK (listing_type IN ('BUY_NOW', 'AUCTION')),
    price NUMERIC NOT NULL,
    current_bid NUMERIC NULL,
    current_bidder_uuid TEXT NULL,
    current_bidder_name TEXT NULL,
    min_bid_increment NUMERIC NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SOLD', 'EXPIRED', 'CANCELLED')),
    server_origin TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    resolved_at DATETIME NULL,
    version INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ca_listings_status ON ca_listings (status);
CREATE INDEX IF NOT EXISTS idx_ca_listings_expires_at ON ca_listings (expires_at);
CREATE INDEX IF NOT EXISTS idx_ca_listings_seller ON ca_listings (seller_uuid);
CREATE INDEX IF NOT EXISTS idx_ca_listings_status_created ON ca_listings (status, created_at);
CREATE INDEX IF NOT EXISTS idx_ca_listings_item_display ON ca_listings (item_display);

CREATE TABLE IF NOT EXISTS ca_claims (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    listing_id INTEGER NOT NULL,
    player_uuid TEXT NOT NULL,
    claim_type TEXT NOT NULL CHECK (claim_type IN ('ITEM', 'MONEY')),
    item_data TEXT NULL,
    amount NUMERIC NULL,
    reason TEXT NOT NULL,
    claimed INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at DATETIME NULL
);
CREATE INDEX IF NOT EXISTS idx_ca_claims_player_claimed ON ca_claims (player_uuid, claimed);

CREATE TABLE IF NOT EXISTS ca_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    listing_id INTEGER NOT NULL,
    seller_uuid TEXT NOT NULL,
    buyer_uuid TEXT NULL,
    item_display TEXT NOT NULL,
    final_price NUMERIC NOT NULL,
    listing_type TEXT NOT NULL CHECK (listing_type IN ('BUY_NOW', 'AUCTION')),
    resolved_as TEXT NOT NULL CHECK (resolved_as IN ('SOLD', 'EXPIRED', 'CANCELLED')),
    resolved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ca_history_seller ON ca_history (seller_uuid);
CREATE INDEX IF NOT EXISTS idx_ca_history_buyer ON ca_history (buyer_uuid);

CREATE TABLE IF NOT EXISTS ca_balances (
    player_uuid TEXT NOT NULL PRIMARY KEY,
    balance NUMERIC NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ca_locks (
    lock_name TEXT NOT NULL PRIMARY KEY,
    holder TEXT NOT NULL,
    expires_at DATETIME NOT NULL
);
