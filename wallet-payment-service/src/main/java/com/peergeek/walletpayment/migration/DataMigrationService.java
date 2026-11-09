package com.peergeek.walletpayment.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataMigrationService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final String monolithDbName;
    private final boolean autoMigrate;

    public DataMigrationService(
            JdbcTemplate jdbcTemplate,
            @Value("${peergeek.migration.monolith-db:peergeek}") String monolithDbName,
            @Value("${peergeek.migration.auto-migrate-historical-data:true}") boolean autoMigrate) {
        this.jdbcTemplate = jdbcTemplate;
        this.monolithDbName = monolithDbName;
        this.autoMigrate = autoMigrate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoMigrate) {
            log.info("Historical wallet and payment data migration is disabled by configuration");
            return;
        }

        try {
            log.info("Checking for historical wallet records to migrate from {}.wallets", monolithDbName);
            String walletSql = String.format(
                    "INSERT IGNORE INTO wallets (id, user_id, balance, reserved_balance, currency_code, created_at, updated_at) " +
                    "SELECT id, user_id, balance, reserved_balance, currency_code, created_at, updated_at " +
                    "FROM `%s`.wallets",
                    monolithDbName);
            int walletsMigrated = jdbcTemplate.update(walletSql);
            log.info("Historical wallet migration completed: {} rows migrated", walletsMigrated);

            log.info("Checking for historical transactions to migrate from {}.wallet_transactions", monolithDbName);
            String txSql = String.format(
                    "INSERT IGNORE INTO wallet_transactions " +
                    "(id, wallet_id, user_id, type, amount, balance_after, reserved_after, reference_type, reference_id, description, created_at) " +
                    "SELECT id, wallet_id, user_id, type, amount, balance_after, reserved_after, reference_type, reference_id, description, created_at " +
                    "FROM `%s`.wallet_transactions",
                    monolithDbName);
            int txMigrated = jdbcTemplate.update(txSql);
            log.info("Historical transaction migration completed: {} rows migrated", txMigrated);

            log.info("Checking for historical payment orders to migrate from {}.payment_orders", monolithDbName);
            String orderSql = String.format(
                    "INSERT IGNORE INTO payment_orders " +
                    "(id, user_id, package_id, amount_paise, currency, pgc_amount, razorpay_order_id, razorpay_payment_id, razorpay_signature, status, wallet_transaction_id, failure_reason, captured_at, created_at, updated_at) " +
                    "SELECT id, user_id, package_id, amount_paise, currency, pgc_amount, razorpay_order_id, razorpay_payment_id, razorpay_signature, status, wallet_transaction_id, failure_reason, captured_at, created_at, updated_at " +
                    "FROM `%s`.payment_orders",
                    monolithDbName);
            int ordersMigrated = jdbcTemplate.update(orderSql);
            log.info("Historical payment order migration completed: {} rows migrated", ordersMigrated);
        } catch (Exception ex) {
            log.warn("Historical wallet data migration skipped or failed (expected if source tables do not exist or in standalone test mode): {}", ex.getMessage());
        }
    }
}

