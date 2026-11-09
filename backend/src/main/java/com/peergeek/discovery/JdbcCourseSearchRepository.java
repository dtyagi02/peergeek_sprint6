package com.peergeek.discovery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.peergeek.common.PageResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCourseSearchRepository implements CourseSearchRepository {
	private final NamedParameterJdbcTemplate jdbc;

	public JdbcCourseSearchRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public PageResponse<CourseCardDto> search(CourseSearchCriteria criteria) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		List<String> conditions = new ArrayList<>();

		if (criteria.q() != null && !criteria.q().isBlank()) {
			String[] terms = criteria.q().trim().split("\\s+");
			for (int i = 0; i < terms.length; i++) {
				String paramName = "term_" + i;
				conditions.add("p.search_text LIKE :" + paramName);
				params.addValue(paramName, "%" + escapeLike(terms[i]) + "%");
			}
		}

		if (criteria.categorySlug() != null && !criteria.categorySlug().isBlank()) {
			conditions.add("p.category_slug = :categorySlug");
			params.addValue("categorySlug", criteria.categorySlug().trim());
		}

		if (criteria.difficulties() != null && !criteria.difficulties().isEmpty()) {
			conditions.add("p.difficulty IN (:difficulties)");
			params.addValue("difficulties", criteria.difficulties());
		}

		String whereClause = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);

		String countSql = "SELECT COUNT(*) FROM public_course_index p " + whereClause;
		Long total = jdbc.queryForObject(countSql, params, Long.class);
		long totalElements = total == null ? 0 : total;

		int totalPages = criteria.size() == 0
				? 1
				: (int) Math.ceil((double) totalElements / (double) criteria.size());

		if (totalElements == 0 || criteria.page() >= totalPages) {
			return new PageResponse<>(List.of(), criteria.page(), criteria.size(), totalElements, totalPages);
		}

		String orderByClause = buildOrderByClause(criteria.sort(), criteria.q(), params);

		long offset = (long) criteria.page() * criteria.size();
		params.addValue("limit", criteria.size());
		params.addValue("offset", offset);

		String selectSql = """
				SELECT p.course_id, p.title, p.short_description, p.category_id, p.category_name, p.category_slug,
				       p.difficulty, p.instructor_id, p.instructor_name, p.module_count, p.lesson_count,
				       p.thumbnail_resource_id, p.published_at,
				       s.enrollment_count, s.completion_count, s.rating_average, s.rating_count
				FROM public_course_index p
				LEFT JOIN course_public_stats s ON p.course_id = s.course_id
				""" + whereClause + " " + orderByClause + " LIMIT :limit OFFSET :offset";

		List<CourseCardDto> cards = jdbc.query(selectSql, params, (rs, rowNum) -> {
			long courseId = rs.getLong("course_id");
			String title = rs.getString("title");
			String shortDescription = rs.getString("short_description");
			Long thumbnailResourceId = rs.getObject("thumbnail_resource_id", Long.class);
			String thumbnailUrl = thumbnailResourceId == null
					? null
					: "/api/v1/public/courses/" + courseId + "/thumbnail?v=" + thumbnailResourceId;

			Long categoryId = rs.getObject("category_id", Long.class);
			CourseCardDto.CategoryCardDto category = categoryId == null
					? null
					: new CourseCardDto.CategoryCardDto(
							categoryId, rs.getString("category_name"), rs.getString("category_slug"));

			String difficulty = rs.getString("difficulty");
			Long instructorId = rs.getLong("instructor_id");
			CourseCardDto.InstructorCardDto instructor = new CourseCardDto.InstructorCardDto(
					instructorId, rs.getString("instructor_name"));

			int moduleCount = rs.getInt("module_count");
			int lessonCount = rs.getInt("lesson_count");
			Instant publishedAt = rs.getTimestamp("published_at").toInstant();

			Integer enrollmentCount = rs.getObject("enrollment_count", Integer.class);
			Integer completionCount = rs.getObject("completion_count", Integer.class);
			BigDecimal ratingAverage = rs.getBigDecimal("rating_average");
			Integer ratingCount = rs.getObject("rating_count", Integer.class);

			CourseStatsDto stats = CourseStatsDto.of(
					enrollmentCount, completionCount, ratingAverage, ratingCount, criteria.minCompletionSample());

			return new CourseCardDto(
					courseId,
					title,
					shortDescription,
					thumbnailUrl,
					category,
					difficulty,
					instructor,
					moduleCount,
					lessonCount,
					publishedAt,
					stats);
		});

		return new PageResponse<>(cards, criteria.page(), criteria.size(), totalElements, totalPages);
	}

	private String buildOrderByClause(String sort, String q, MapSqlParameterSource params) {
		String normalized = sort == null || sort.isBlank() ? "popularity,desc" : sort.trim();
		if ("relevance".equalsIgnoreCase(normalized)) {
			if (q != null && !q.isBlank()) {
				String rawTrimmed = q.trim();
				params.addValue("relPrefix", escapeLike(rawTrimmed) + "%");
				params.addValue("relSub", "%" + escapeLike(rawTrimmed) + "%");
				return """
						ORDER BY (
							CASE
								WHEN lower(p.title) LIKE lower(:relPrefix) ESCAPE '\\\\' THEN 100
								WHEN lower(p.title) LIKE lower(:relSub) ESCAPE '\\\\' THEN 70
								WHEN lower(p.topics_text) LIKE lower(:relSub) ESCAPE '\\\\' THEN 40
								ELSE 10
							END
						) DESC,
						COALESCE(s.enrollment_count, 0) DESC,
						COALESCE(s.rating_count, 0) DESC,
						p.published_at DESC,
						p.course_id DESC
						""";
			}
			return "ORDER BY COALESCE(s.enrollment_count, 0) DESC, COALESCE(s.rating_count, 0) DESC, p.published_at DESC, p.course_id DESC";
		}

		return switch (normalized) {
			case "publishedAt,desc" -> "ORDER BY p.published_at DESC, p.course_id DESC";
			case "rating,desc" -> """
					ORDER BY (CASE WHEN s.rating_count IS NOT NULL AND s.rating_count > 0 THEN 0 ELSE 1 END) ASC,
					         s.rating_average DESC,
					         s.rating_count DESC,
					         p.published_at DESC,
					         p.course_id DESC
					""";
			case "title,asc" -> "ORDER BY p.title ASC, p.course_id ASC";
			default -> "ORDER BY COALESCE(s.enrollment_count, 0) DESC, COALESCE(s.rating_count, 0) DESC, p.published_at DESC, p.course_id DESC";
		};
	}

	public static String escapeLike(String input) {
		if (input == null) return "";
		return input.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
	}
}
