CREATE TABLE payment_orders (
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
    CONSTRAINT fk_payment_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_payment_orders_user_id ON payment_orders(user_id);
CREATE INDEX idx_payment_orders_status ON payment_orders(status);

