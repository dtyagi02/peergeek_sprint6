package com.peergeek.readiness;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReadinessRunStore {
	private static final TypeReference<Map<String, Object>> DETAILS_TYPE = new TypeReference<>() { };
	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;

	public ReadinessRunStore(JdbcTemplate jdbc, ObjectMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	@Transactional
	public long create(long courseId, long userId, String fingerprint) {
		jdbc.update("INSERT INTO readiness_runs (course_id, requested_by, status, fingerprint, "
						+ "blocking_count, warning_count, started_at, created_at) "
						+ "VALUES (?, ?, 'RUNNING', ?, 0, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
				courseId, userId, fingerprint);
		return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	@Transactional
	public void complete(long runId, List<ReadinessIssue> issues) {
		String status = jdbc.queryForObject("SELECT status FROM readiness_runs WHERE id = ? FOR UPDATE",
				String.class, runId);
		if (!"RUNNING".equals(status)) return;
		jdbc.update("DELETE FROM readiness_issues WHERE run_id = ?", runId);
		for (ReadinessIssue issue : issues) {
			try {
				jdbc.update("INSERT INTO readiness_issues (run_id, rule_code, severity, scope, module_id, "
								+ "lesson_id, resource_id, module_title, lesson_title, resource_title, message, "
								+ "fix_hint, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
						runId, issue.ruleCode(), issue.severity().name(), issue.scope().name(),
						issue.moduleId(), issue.lessonId(), issue.resourceId(), issue.moduleTitle(),
						issue.lessonTitle(), issue.resourceTitle(), issue.message(), issue.fixHint(),
						mapper.writeValueAsString(issue.details()));
			} catch (Exception exception) {
				throw new IllegalStateException("Unable to persist readiness issues.", exception);
			}
		}
		int blockers = (int) issues.stream()
				.filter(issue -> issue.severity() == ReadinessIssue.Severity.BLOCKING_ERROR).count();
		int warnings = issues.size() - blockers;
		jdbc.update("UPDATE readiness_runs SET status = 'COMPLETED', blocking_count = ?, "
						+ "warning_count = ?, finished_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
				blockers, warnings, runId);
	}

	@Transactional
	public void fail(long runId, String reason) {
		jdbc.update("UPDATE readiness_runs SET status = 'FAILED', failure_reason = ?, "
				+ "finished_at = CURRENT_TIMESTAMP(6) WHERE id = ?", reason, runId);
	}

	@Transactional(readOnly = true)
	public Optional<ReadinessRunView> find(long courseId, long runId) {
		List<ReadinessRunView> rows = jdbc.query("SELECT id, course_id, status, fingerprint, blocking_count, "
						+ "warning_count, failure_reason, started_at, finished_at FROM readiness_runs "
						+ "WHERE course_id = ? AND id = ?",
				(rs, index) -> new ReadinessRunView(rs.getLong("id"), rs.getLong("course_id"),
						rs.getString("status"), rs.getString("fingerprint"), rs.getInt("blocking_count"),
						rs.getInt("warning_count"), rs.getString("failure_reason"),
						rs.getTimestamp("started_at").toInstant(),
						rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
						List.of()), courseId, runId);
		if (rows.isEmpty()) return Optional.empty();
		ReadinessRunView run = rows.getFirst();
		List<ReadinessIssue> issues = jdbc.query("SELECT rule_code, severity, scope, module_id, lesson_id, "
						+ "resource_id, module_title, lesson_title, resource_title, message, fix_hint, details "
						+ "FROM readiness_issues WHERE run_id = ? ORDER BY id",
				(rs, index) -> new ReadinessIssue(rs.getString("rule_code"),
						ReadinessIssue.Severity.valueOf(rs.getString("severity")),
						ReadinessIssue.Scope.valueOf(rs.getString("scope")), nullableLong(rs, "module_id"),
						nullableLong(rs, "lesson_id"), nullableLong(rs, "resource_id"),
						rs.getString("module_title"), rs.getString("lesson_title"),
						rs.getString("resource_title"), rs.getString("message"), rs.getString("fix_hint"),
						readDetails(rs.getString("details"))), runId);
		return Optional.of(new ReadinessRunView(run.id(), run.courseId(), run.status(), run.fingerprint(),
				run.blockingCount(), run.warningCount(), run.failureReason(), run.startedAt(),
				run.finishedAt(), issues));
	}

	@Transactional(readOnly = true)
	public Optional<Instant> latestRunCreatedAt(long courseId) {
		return jdbc.query("SELECT created_at FROM readiness_runs WHERE course_id = ? "
						+ "ORDER BY created_at DESC LIMIT 1",
				rs -> rs.next() ? Optional.of(rs.getTimestamp(1).toInstant()) : Optional.empty(), courseId);
	}

	@Transactional(readOnly = true)
	public long countRunningByUser(long userId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM readiness_runs WHERE requested_by = ? AND status = 'RUNNING'",
				Long.class, userId);
	}

	@Transactional(readOnly = true)
	public Optional<Long> latestCompletedRunId(long courseId) {
		return jdbc.query("SELECT id FROM readiness_runs WHERE course_id = ? AND status = 'COMPLETED' "
						+ "ORDER BY finished_at DESC, id DESC LIMIT 1",
				rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), courseId);
	}

	@Transactional
	public void enforceRetention(long courseId, int keep) {
		jdbc.update("DELETE FROM readiness_runs WHERE course_id = ? AND id NOT IN "
				+ "(SELECT id FROM (SELECT id FROM readiness_runs WHERE course_id = ? "
				+ "ORDER BY created_at DESC LIMIT ?) retained)", courseId, courseId, keep);
	}

	@Transactional
	public int failTimedOut(Instant cutoff) {
		return jdbc.update("UPDATE readiness_runs SET status = 'FAILED', failure_reason = 'Readiness check timed out', "
				+ "finished_at = CURRENT_TIMESTAMP(6) WHERE status = 'RUNNING' AND started_at < ?",
				Timestamp.from(cutoff));
	}

	private Map<String, Object> readDetails(String raw) {
		if (raw == null) return Map.of();
		try {
			return mapper.readValue(raw, DETAILS_TYPE);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to read readiness issue details.", exception);
		}
	}

	private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}
}
