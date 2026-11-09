package com.peergeek.courselearning.analytics;

import java.util.List;
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

    public RatingSummary getRatingSummary(long courseId) {
        return jdbc.query(
                "SELECT AVG(rating), COUNT(*) FROM reviews WHERE course_id = ? AND deleted_at IS NULL",
                rs -> {
                    if (rs.next()) {
                        Double avg = rs.getObject(1, Double.class);
                        long count = rs.getLong(2);
                        return new RatingSummary(avg != null ? Math.round(avg * 10.0) / 10.0 : null, count);
                    }
                    return new RatingSummary(null, 0L);
                },
                courseId);
    }

    public List<LessonDropOffDto> getLessonDropOff(long courseId) {
        String sql = """
                SELECT l.id AS lesson_id, l.title AS lesson_title, l.position AS order_index,
                       COUNT(lp.id) AS reached_count,
                       SUM(CASE WHEN lp.completed = 1 THEN 1 ELSE 0 END) AS completed_count
                FROM lessons l
                JOIN course_modules m ON l.module_id = m.id
                LEFT JOIN lesson_progress lp ON lp.lesson_id = l.id
                WHERE m.course_id = ? AND l.deleted_at IS NULL
                GROUP BY l.id, l.title, l.position
                ORDER BY m.position ASC, l.position ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> {
            long lessonId = rs.getLong("lesson_id");
            String title = rs.getString("lesson_title");
            int orderIndex = rs.getInt("order_index");
            long reached = rs.getLong("reached_count");
            long completed = rs.getLong("completed_count");
            double rate = reached > 0 ? Math.round((completed * 100.0 / reached) * 10.0) / 10.0 : 0.0;
            return new LessonDropOffDto(lessonId, title, orderIndex, reached, completed, rate);
        }, courseId);
    }

    public List<ScoreBucketDto> getScoreBuckets(long courseId) {
        String sql = """
                SELECT
                    SUM(CASE WHEN pct < 20 THEN 1 ELSE 0 END) AS b_0_19,
                    SUM(CASE WHEN pct >= 20 AND pct < 40 THEN 1 ELSE 0 END) AS b_20_39,
                    SUM(CASE WHEN pct >= 40 AND pct < 60 THEN 1 ELSE 0 END) AS b_40_59,
                    SUM(CASE WHEN pct >= 60 AND pct < 80 THEN 1 ELSE 0 END) AS b_60_79,
                    SUM(CASE WHEN pct >= 80 THEN 1 ELSE 0 END) AS b_80_100
                FROM (
                    SELECT (s.total_points / a.max_points) * 100.0 AS pct
                    FROM submissions s
                    JOIN assignments a ON s.assignment_id = a.id
                    WHERE a.course_id = ? AND s.status = 'GRADED' AND a.max_points > 0
                ) scored
                """;
        return jdbc.query(sql, rs -> {
            if (rs.next()) {
                return List.of(
                        new ScoreBucketDto("0-19%", rs.getLong("b_0_19")),
                        new ScoreBucketDto("20-39%", rs.getLong("b_20_39")),
                        new ScoreBucketDto("40-59%", rs.getLong("b_40_59")),
                        new ScoreBucketDto("60-79%", rs.getLong("b_60_79")),
                        new ScoreBucketDto("80-100%", rs.getLong("b_80_100")));
            }
            return List.of(
                    new ScoreBucketDto("0-19%", 0L),
                    new ScoreBucketDto("20-39%", 0L),
                    new ScoreBucketDto("40-59%", 0L),
                    new ScoreBucketDto("60-79%", 0L),
                    new ScoreBucketDto("80-100%", 0L));
        }, courseId);
    }

    public List<AssignmentPerformanceDto> getAssignmentPerformance(long courseId) {
        String sql = """
                SELECT a.id, a.title,
                       COUNT(s.id) AS total_submissions,
                       SUM(CASE WHEN s.status = 'GRADED' THEN 1 ELSE 0 END) AS graded_submissions,
                       AVG(CASE WHEN s.status = 'GRADED' AND a.max_points > 0 THEN (s.total_points / a.max_points) * 100.0 ELSE NULL END) AS avg_score
                FROM assignments a
                LEFT JOIN submissions s ON s.assignment_id = a.id
                WHERE a.course_id = ? AND a.deleted_at IS NULL
                GROUP BY a.id, a.title
                ORDER BY a.id ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> {
            long assignmentId = rs.getLong("id");
            String title = rs.getString("title");
            long total = rs.getLong("total_submissions");
            long graded = rs.getLong("graded_submissions");
            Double avg = rs.getObject("avg_score", Double.class);
            Double roundedAvg = avg != null ? Math.round(avg * 10.0) / 10.0 : null;
            return new AssignmentPerformanceDto(assignmentId, title, total, graded, roundedAvg);
        }, courseId);
    }

    public record RatingSummary(Double averageRating, long ratingCount) {}
}
