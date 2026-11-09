-- Schema initialization for independent peergeek_wallet database (Phase 3 Microservice Extraction)

CREATE TABLE IF NOT EXISTS wallets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    reserved_balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    currency_code VARCHAR(10) NOT NULL DEFAULT 'PGC',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_wallets_user UNIQUE (user_id),
    CONSTRAINT chk_wallets_balance CHECK (balance >= 0),
    CONSTRAINT chk_wallets_reserved CHECK (reserved_balance >= 0),
    INDEX idx_wallets_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    reserved_after DECIMAL(19, 2) NOT NULL,
    reference_type VARCHAR(64) NULL,
    reference_id VARCHAR(64) NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_wallet_tx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE CASCADE,
    INDEX idx_wallet_tx_user (user_id, created_at),
    INDEX idx_wallet_tx_ref (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payment_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    package_id VARCHAR(64) NOT NULL,
    amount_paise BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    pgc_amount DECIMAL(19, 2) NOT NULL,
    razorpay_order_id VARCHAR(128) NOT NULL,
    razorpay_payment_id VARCHAR(128) NULL,
    razorpay_signature VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    wallet_transaction_id BIGINT NULL,
    failure_reason VARCHAR(500) NULL,
    captured_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_payment_orders_rzp_order_id UNIQUE (razorpay_order_id),
    CONSTRAINT uk_payment_orders_rzp_payment_id UNIQUE (razorpay_payment_id),
    INDEX idx_payment_orders_user_id (user_id),
    INDEX idx_payment_orders_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    error_message TEXT NULL,
    INDEX idx_outbox_published (published, retry_count, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

