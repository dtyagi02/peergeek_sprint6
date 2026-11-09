package com.peergeek.community.migration;

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
            log.info("Historical community data migration is disabled by configuration");
            return;
        }

        try {
            log.info("Checking for historical users to populate community_user_profiles from {}.users", monolithDbName);
            String userSql = String.format(
                    "INSERT IGNORE INTO community_user_profiles (user_id, full_name, username, email, role, mode, avatar_url, enabled, updated_at) " +
                    "SELECT id, display_name, username, email, role, last_mode, avatar_url, enabled, updated_at " +
                    "FROM `%s`.users",
                    monolithDbName);
            int usersMigrated = jdbcTemplate.update(userSql);
            log.info("Historical user profiles migration completed: {} rows inserted", usersMigrated);

            log.info("Checking for historical domain communities to migrate from {}.domain_communities", monolithDbName);
            String domainSql = String.format(
                    "INSERT IGNORE INTO domain_communities (id, category_id, name, slug, description, created_at, updated_at) " +
                    "SELECT dc.id, dc.category_id, COALESCE(c.name, CONCAT('Domain ', dc.id)), COALESCE(c.slug, CONCAT('domain-', dc.id)), dc.description, dc.created_at, dc.updated_at " +
                    "FROM `%s`.domain_communities dc " +
                    "LEFT JOIN `%s`.categories c ON dc.category_id = c.id",
                    monolithDbName, monolithDbName);
            int domainsMigrated = jdbcTemplate.update(domainSql);
            log.info("Historical domain communities migration completed: {} rows migrated", domainsMigrated);

            log.info("Checking for historical domain memberships to migrate from {}.domain_community_memberships", monolithDbName);
            String memSql = String.format(
                    "INSERT IGNORE INTO domain_community_memberships (id, domain_id, user_id, joined_at) " +
                    "SELECT id, domain_id, user_id, joined_at " +
                    "FROM `%s`.domain_community_memberships",
                    monolithDbName);
            int memMigrated = jdbcTemplate.update(memSql);
            log.info("Historical domain memberships migration completed: {} rows migrated", memMigrated);

            log.info("Checking for historical domain chat messages to migrate from {}.domain_chat_messages", monolithDbName);
            String dcmSql = String.format(
                    "INSERT IGNORE INTO domain_chat_messages (id, domain_id, sender_id, sender_name, sender_username, sender_avatar_url, message_type, content, created_at, updated_at) " +
                    "SELECT dcm.id, dcm.domain_id, dcm.sender_id, u.display_name, u.username, u.avatar_url, dcm.message_type, dcm.content, dcm.created_at, dcm.updated_at " +
                    "FROM `%s`.domain_chat_messages dcm " +
                    "LEFT JOIN `%s`.users u ON dcm.sender_id = u.id",
                    monolithDbName, monolithDbName);
            int dcmMigrated = jdbcTemplate.update(dcmSql);
            log.info("Historical domain chat messages migration completed: {} rows migrated", dcmMigrated);

            log.info("Checking for historical global community messages to migrate from {}.global_community_messages", monolithDbName);
            String gcmSql = String.format(
                    "INSERT IGNORE INTO global_community_messages (id, user_id, user_name, user_username, user_avatar_url, user_role, user_mode, content, deleted_at, deleted_by, deleted_by_name, delete_reason, created_at, updated_at) " +
                    "SELECT gcm.id, gcm.user_id, u.display_name, u.username, u.avatar_url, u.role, u.last_mode, gcm.content, gcm.deleted_at, gcm.deleted_by, del_u.display_name, gcm.delete_reason, gcm.created_at, gcm.updated_at " +
                    "FROM `%s`.global_community_messages gcm " +
                    "LEFT JOIN `%s`.users u ON gcm.user_id = u.id " +
                    "LEFT JOIN `%s`.users del_u ON gcm.deleted_by = del_u.id",
                    monolithDbName, monolithDbName, monolithDbName);
            int gcmMigrated = jdbcTemplate.update(gcmSql);
            log.info("Historical global community messages migration completed: {} rows migrated", gcmMigrated);

            log.info("Checking for historical global community blocks to migrate from {}.global_community_blocks", monolithDbName);
            String blockSql = String.format(
                    "INSERT IGNORE INTO global_community_blocks (id, user_id, user_name, user_email, user_username, user_role, blocked_by, blocked_by_name, reason, created_at, unblocked_at, unblocked_by, unblock_reason) " +
                    "SELECT b.id, b.user_id, u.display_name, u.email, u.username, u.role, b.blocked_by, admin_u.display_name, COALESCE(b.reason, 'Violated community guidelines'), b.created_at, b.unblocked_at, b.unblocked_by, b.unblock_reason " +
                    "FROM `%s`.global_community_blocks b " +
                    "LEFT JOIN `%s`.users u ON b.user_id = u.id " +
                    "LEFT JOIN `%s`.users admin_u ON b.blocked_by = admin_u.id",
                    monolithDbName, monolithDbName, monolithDbName);
            int blocksMigrated = jdbcTemplate.update(blockSql);
            log.info("Historical global community blocks migration completed: {} rows migrated", blocksMigrated);

            log.info("Checking for historical community posts to migrate from {}.community_posts", monolithDbName);
            String postSql = String.format(
                    "INSERT IGNORE INTO community_posts (id, author_id, author_name, author_username, author_avatar_url, content, created_at, updated_at) " +
                    "SELECT p.id, p.author_id, u.display_name, u.username, u.avatar_url, p.content, p.created_at, p.updated_at " +
                    "FROM `%s`.community_posts p " +
                    "LEFT JOIN `%s`.users u ON p.author_id = u.id",
                    monolithDbName, monolithDbName);
            int postsMigrated = jdbcTemplate.update(postSql);
            log.info("Historical community posts migration completed: {} rows migrated", postsMigrated);

            log.info("Checking for historical community comments to migrate from {}.community_comments", monolithDbName);
            String commentSql = String.format(
                    "INSERT IGNORE INTO community_comments (id, post_id, author_id, author_name, author_username, author_avatar_url, parent_comment_id, content, created_at, updated_at) " +
                    "SELECT c.id, c.post_id, c.author_id, u.display_name, u.username, u.avatar_url, c.parent_comment_id, c.content, c.created_at, c.updated_at " +
                    "FROM `%s`.community_comments c " +
                    "LEFT JOIN `%s`.users u ON c.author_id = u.id",
                    monolithDbName, monolithDbName);
            int commentsMigrated = jdbcTemplate.update(commentSql);
            log.info("Historical community comments migration completed: {} rows migrated", commentsMigrated);

            log.info("Checking for historical community mentions to migrate from {}.community_mentions", monolithDbName);
            String mentionSql = String.format(
                    "INSERT IGNORE INTO community_mentions (id, post_id, comment_id, mentioned_user_id, created_at, updated_at) " +
                    "SELECT id, post_id, comment_id, mentioned_user_id, created_at, updated_at " +
                    "FROM `%s`.community_mentions",
                    monolithDbName);
            int mentionsMigrated = jdbcTemplate.update(mentionSql);
            log.info("Historical community mentions migration completed: {} rows migrated", mentionsMigrated);

        } catch (Exception ex) {
            log.warn("Historical community data migration skipped or failed (expected if source tables do not exist or in standalone test mode): {}", ex.getMessage());
        }
    }
}

