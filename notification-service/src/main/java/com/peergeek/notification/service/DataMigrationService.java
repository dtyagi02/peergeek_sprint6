package com.peergeek.notification.service;

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
            log.info("Historical notification data migration is disabled by configuration");
            return;
        }

        try {
            log.info("Checking for historical notification data to migrate from {}.notifications", monolithDbName);
            String sql = String.format(
                    "INSERT IGNORE INTO notifications " +
                    "(id, user_id, type, title, message, resource_type, resource_id, action_url, is_read, created_at, read_at, event_key) " +
                    "SELECT id, user_id, type, title, message, resource_type, resource_id, action_url, is_read, created_at, read_at, event_key " +
                    "FROM `%s`.notifications",
                    monolithDbName);

            int rowsMigrated = jdbcTemplate.update(sql);
            log.info("Historical notification data migration completed: {} new rows migrated", rowsMigrated);
        } catch (Exception ex) {
            log.warn("Historical notification data migration skipped or failed (expected if source table does not exist or in standalone mode): {}", ex.getMessage());
        }
    }
}

