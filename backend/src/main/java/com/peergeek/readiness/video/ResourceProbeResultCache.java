package com.peergeek.readiness.video;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ResourceProbeResultCache {
	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public ResourceProbeResultCache(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public Optional<ResourceProbeResult> findVideo(long resourceId, String checksumSha256) {
		return jdbcTemplate.query(
				"""
				select resource_id, checksum_sha256, ok, error_code, duration_ms, width, height,
					json_unquote(json_extract(info, '$.codec')) as codec, probed_at
				from resource_probe_results
				where resource_id = ? and checksum_sha256 = ? and kind = 'VIDEO'
				""",
				(resultSet, rowNumber) -> new ResourceProbeResult(
						resultSet.getLong("resource_id"),
						resultSet.getString("checksum_sha256"),
						resultSet.getBoolean("ok"),
						resultSet.getString("error_code"),
						nullableLong(resultSet, "duration_ms"),
						nullableInt(resultSet, "width"),
						nullableInt(resultSet, "height"),
						resultSet.getString("codec"),
						resultSet.getTimestamp("probed_at").toInstant()),
				resourceId,
				checksumSha256).stream().findFirst();
	}

	public void saveVideo(long resourceId, String checksumSha256, VideoProbeOutcome outcome) {
		String info = null;
		if (outcome.codec() != null) {
			try {
				info = objectMapper.writeValueAsString(Map.of("codec", outcome.codec()));
			} catch (JsonProcessingException exception) {
				throw new IllegalStateException("Could not serialize the video probe result", exception);
			}
		}
		jdbcTemplate.update(
				"""
				insert into resource_probe_results
					(resource_id, checksum_sha256, kind, ok, error_code, duration_ms, width, height, info, probed_at)
				values (?, ?, 'VIDEO', ?, ?, ?, ?, ?, cast(? as json), ?)
				on duplicate key update
					ok = values(ok), error_code = values(error_code), duration_ms = values(duration_ms),
					width = values(width), height = values(height), info = values(info), probed_at = values(probed_at)
				""",
				resourceId,
				checksumSha256,
				outcome.valid(),
				outcome.errorCode(),
				outcome.durationMilliseconds(),
				outcome.width(),
				outcome.height(),
				info,
				java.sql.Timestamp.from(Instant.now()));
	}

	private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private static Integer nullableInt(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
		int value = resultSet.getInt(column);
		return resultSet.wasNull() ? null : value;
	}
}
