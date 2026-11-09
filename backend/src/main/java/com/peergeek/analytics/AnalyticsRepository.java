package com.peergeek.analytics;

import com.peergeek.analytics.dto.AssignmentPerformanceDto;
import com.peergeek.analytics.dto.ScoreBucketDto;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {

	private final JdbcTemplate jdbc;

	public AnalyticsRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public long countTotalEnrollments(long courseId) {
		Long count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM enrollments WHERE course_id = ?",
				Long.class,
				courseId);
		return count != null ? count : 0L;
	}

	public long countActiveLearners(long courseId) {
		Long count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM enrollments WHERE course_id = ? AND status = 'ACTIVE'",
				Long.class,
				courseId);
		return count != null ? count : 0L;
	}

	public long countCompletedLearners(long courseId) {
		Long count = jdbc.queryForObject(
				"SELECT COUNT(cp.enrollment_id) FROM course_progress cp "
						+ "JOIN enrollments e ON cp.enrollment_id = e.id "
						+ "WHERE e.course_id = ? AND e.status = 'ACTIVE' AND cp.completed_at IS NOT NULL",
				Long.class,
				courseId);
		return count != null ? count : 0L;
	}

	public Double getAverageAssessmentScore(long courseId) {
		return jdbc.queryForObject(
				"SELECT AVG((s.total_points / a.max_points) * 100.0) "
						+ "FROM submissions s "
						+ "JOIN assignments a ON s.assignment_id = a.id "
						+ "WHERE a.course_id = ? AND s.status = 'GRADED' AND a.max_points > 0 AND s.total_points IS NOT NULL",
				Double.class,
				courseId);
	}

	public record RatingSummary(Double averageRating, long ratingCount) {}

	public RatingSummary getRatingSummary(long courseId) {
		return jdbc.query(
				"SELECT AVG(rating) as avg_rating, COUNT(rating) as rating_count "
						+ "FROM reviews WHERE course_id = ? AND deleted_at IS NULL",
				rs -> {
					if (rs.next()) {
						long count = rs.getLong("rating_count");
						Double avg = count > 0 ? rs.getDouble("avg_rating") : null;
						return new RatingSummary(avg, count);
					}
					return new RatingSummary(null, 0L);
				},
				courseId);
	}

	public long countPendingSubmissions(long courseId) {
		Long count = jdbc.queryForObject(
				"SELECT COUNT(s.id) FROM submissions s "
						+ "JOIN assignments a ON s.assignment_id = a.id "
						+ "WHERE a.course_id = ? AND s.status = 'SUBMITTED'",
				Long.class,
				courseId);
		return count != null ? count : 0L;
	}

	public Map<Long, Long> getLessonCompletionCounts(long courseId) {
		Map<Long, Long> counts = new HashMap<>();
		jdbc.query(
				"SELECT lp.lesson_id, COUNT(DISTINCT lp.enrollment_id) as completed_count "
						+ "FROM lesson_progress lp "
						+ "JOIN enrollments e ON lp.enrollment_id = e.id "
						+ "WHERE e.course_id = ? AND e.status = 'ACTIVE' "
						+ "GROUP BY lp.lesson_id",
				rs -> {
					counts.put(rs.getLong("lesson_id"), rs.getLong("completed_count"));
				},
				courseId);
		return counts;
	}

	public List<AssignmentPerformanceDto> getAssignmentStats(long courseId, long activeLearners) {
		return jdbc.query(
				"SELECT a.id, a.title, a.status, a.due_at, a.max_points, "
						+ "COUNT(CASE WHEN s.status IN ('SUBMITTED', 'GRADED') THEN 1 END) as submitted_count, "
						+ "COUNT(CASE WHEN s.status = 'GRADED' THEN 1 END) as graded_count, "
						+ "AVG(CASE WHEN s.status = 'GRADED' AND a.max_points > 0 THEN (s.total_points / a.max_points) * 100.0 END) as avg_score "
						+ "FROM assignments a "
						+ "LEFT JOIN submissions s ON a.id = s.assignment_id "
						+ "WHERE a.course_id = ? "
						+ "GROUP BY a.id, a.title, a.status, a.due_at, a.max_points "
						+ "ORDER BY a.created_at ASC",
				(rs, rowNum) -> {
					long id = rs.getLong("id");
					String title = rs.getString("title");
					String status = rs.getString("status");
					Timestamp dueAtTs = rs.getTimestamp("due_at");
					Instant dueAt = dueAtTs != null ? dueAtTs.toInstant() : null;
					BigDecimal maxPoints = rs.getBigDecimal("max_points");
					long submittedCount = rs.getLong("submitted_count");
					long gradedCount = rs.getLong("graded_count");
					double subRate = activeLearners > 0
							? Math.round((submittedCount * 100.0 / activeLearners) * 10.0) / 10.0
							: 0.0;
					Double avgScore = rs.getObject("avg_score") != null
							? Math.round(rs.getDouble("avg_score") * 10.0) / 10.0
							: null;

					return new AssignmentPerformanceDto(
							id, title, status, dueAt, maxPoints, submittedCount, gradedCount, subRate, avgScore);
				},
				courseId);
	}

	public List<ScoreBucketDto> getScoreDistribution(long courseId) {
		String sql = "SELECT "
				+ "SUM(CASE WHEN pct >= 0 AND pct <= 20 THEN 1 ELSE 0 END) as b1, "
				+ "SUM(CASE WHEN pct > 20 AND pct <= 40 THEN 1 ELSE 0 END) as b2, "
				+ "SUM(CASE WHEN pct > 40 AND pct <= 60 THEN 1 ELSE 0 END) as b3, "
				+ "SUM(CASE WHEN pct > 60 AND pct <= 80 THEN 1 ELSE 0 END) as b4, "
				+ "SUM(CASE WHEN pct > 80 THEN 1 ELSE 0 END) as b5 "
				+ "FROM ( "
				+ "  SELECT ((s.total_points / a.max_points) * 100.0) as pct "
				+ "  FROM submissions s "
				+ "  JOIN assignments a ON s.assignment_id = a.id "
				+ "  WHERE a.course_id = ? AND s.status = 'GRADED' AND a.max_points > 0 AND s.total_points IS NOT NULL "
				+ ") scores";

		return jdbc.query(
				sql,
				rs -> {
					if (rs.next()) {
						long b1 = rs.getLong("b1");
						long b2 = rs.getLong("b2");
						long b3 = rs.getLong("b3");
						long b4 = rs.getLong("b4");
						long b5 = rs.getLong("b5");
						long total = b1 + b2 + b3 + b4 + b5;

						return List.of(
								new ScoreBucketDto("0-20%", b1, total > 0 ? Math.round((b1 * 100.0 / total) * 10.0) / 10.0 : 0.0),
								new ScoreBucketDto("21-40%", b2, total > 0 ? Math.round((b2 * 100.0 / total) * 10.0) / 10.0 : 0.0),
								new ScoreBucketDto("41-60%", b3, total > 0 ? Math.round((b3 * 100.0 / total) * 10.0) / 10.0 : 0.0),
								new ScoreBucketDto("61-80%", b4, total > 0 ? Math.round((b4 * 100.0 / total) * 10.0) / 10.0 : 0.0),
								new ScoreBucketDto("81-100%", b5, total > 0 ? Math.round((b5 * 100.0 / total) * 10.0) / 10.0 : 0.0)
						);
					}
					return List.of(
							new ScoreBucketDto("0-20%", 0, 0.0),
							new ScoreBucketDto("21-40%", 0, 0.0),
							new ScoreBucketDto("41-60%", 0, 0.0),
							new ScoreBucketDto("61-80%", 0, 0.0),
							new ScoreBucketDto("81-100%", 0, 0.0)
					);
				},
				courseId);
	}
}
