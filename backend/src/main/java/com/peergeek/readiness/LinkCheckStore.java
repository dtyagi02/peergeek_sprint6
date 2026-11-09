package com.peergeek.readiness;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LinkCheckStore {
	private final JdbcTemplate jdbc;

	public LinkCheckStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<CachedResult> findFresh(long resourceId, String urlHash, Instant cutoff) {
		return jdbc.query("SELECT outcome, http_status FROM link_check_results WHERE resource_id = ? "
						+ "AND url_sha256 = ? AND checked_at >= ?",
				rs -> {
					if (!rs.next()) return Optional.empty();
					int status = rs.getInt(2);
					Integer httpStatus = rs.wasNull() ? null : status;
					return Optional.of(new CachedResult(rs.getString(1), httpStatus));
				}, resourceId, urlHash, java.sql.Timestamp.from(cutoff));
	}

	public void save(long resourceId, String urlHash, String outcome, Integer status) {
		jdbc.update("INSERT INTO link_check_results (resource_id, url_sha256, outcome, http_status, checked_at) "
						+ "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE "
						+ "url_sha256 = VALUES(url_sha256), outcome = VALUES(outcome), "
						+ "http_status = VALUES(http_status), checked_at = VALUES(checked_at)",
				resourceId, urlHash, outcome, status);
	}

	public record CachedResult(String outcome, Integer httpStatus) {
	}
}
