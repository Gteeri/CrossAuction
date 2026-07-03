CREATE TABLE IF NOT EXISTS ca_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller_uuid VARCHAR(36) NOT NULL,
    seller_name VARCHAR(16) NOT NULL,
    item_data MEDIUMTEXT NOT NULL,
    item_amount INT NOT NULL,
    item_display VARCHAR(255) NOT NULL,
    listing_type ENUM('BUY_NOW','AUCTION') NOT NULL,
    price DECIMAL(18,2) NOT NULL,
    current_bid DECIMAL(18,2) NULL,
    current_bidder_uuid VARCHAR(36) NULL,
    current_bidder_name VARCHAR(16) NULL,
    min_bid_increment DECIMAL(18,2) NULL,
    status ENUM('ACTIVE','SOLD','EXPIRED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    server_origin VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    resolved_at DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    INDEX idx_ca_listings_status (status),
    INDEX idx_ca_listings_expires_at (expires_at),
    INDEX idx_ca_listings_seller_uuid (seller_uuid),
    INDEX idx_ca_listings_status_created_at (status, created_at),
    INDEX idx_ca_listings_item_display (item_display)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ca_claims (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    claim_type ENUM('ITEM','MONEY') NOT NULL,
    item_data MEDIUMTEXT NULL,
    amount DECIMAL(18,2) NULL,
    reason VARCHAR(64) NOT NULL,
    claimed TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at DATETIME NULL,
    INDEX idx_ca_claims_player_uuid (player_uuid),
    INDEX idx_ca_claims_claimed (claimed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ca_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT NOT NULL,
    seller_uuid VARCHAR(36) NOT NULL,
    buyer_uuid VARCHAR(36) NULL,
    item_display VARCHAR(255) NOT NULL,
    final_price DECIMAL(18,2) NOT NULL,
    listing_type ENUM('BUY_NOW','AUCTION') NOT NULL,
    resolved_as ENUM('SOLD','EXPIRED','CANCELLED') NOT NULL,
    resolved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ca_history_seller_uuid (seller_uuid),
    INDEX idx_ca_history_buyer_uuid (buyer_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ca_balances (
    player_uuid VARCHAR(36) PRIMARY KEY,
    balance DECIMAL(18,2) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ca_locks (
    lock_name VARCHAR(64) PRIMARY KEY,
    holder VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
