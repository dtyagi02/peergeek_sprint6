package com.peergeek.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class DiscoverySearchAndSortIntegrationTest extends DiscoveryTestSupport {

	private long cat1Id;
	private long cat2Id;

	@BeforeEach
	void setUpCategoriesAndCourses() {
		cat1Id = jdbc.queryForObject("select id from categories where slug = 'programming' limit 1", Long.class);
		cat2Id = jdbc.queryForObject("select id from categories where slug = 'web-development' limit 1", Long.class);

		// Seed a rich set of indexed courses
		seedCourse(1L, "C Programming Fundamentals", "Learn low-level C programming", "C, systems", "programming", "BEGINNER", 100L, "Dennis Ritchie", 100, 80, new BigDecimal("4.80"), 50);
		seedCourse(2L, "Advanced AI and Machine Learning", "Deep learning models and neural networks", "AI, machine learning", "programming", "ADVANCED", 101L, "Alan Turing", 50, 40, new BigDecimal("4.90"), 30);
		seedCourse(3L, "Building Cloud APIs with Go", "Microservices in Go", "Go, microservices", "programming", "INTERMEDIATE", 102L, "Rob Pike", 30, 20, new BigDecimal("4.50"), 10);
		seedCourse(4L, "Introduction to Modern Café Architecture", "French cafe network design", "networking, café", "web-development", "BEGINNER", 103L, "Ada Lovelace", 0, 0, null, 0);
		seedCourse(5L, "SQL Injection & 100% Secure Design", "Handling 50% discount and % / _ wildcard queries safely", "security, sql", "programming", "INTERMEDIATE", 104L, "Grace Hopper", 10, 5, new BigDecimal("4.00"), 2);
	}

	private void seedCourse(long id, String title, String shortDesc, String topics, String catSlug, String difficulty,
							long instructorId, String instructorName, Integer enrollment, Integer completion,
							BigDecimal ratingAvg, Integer ratingCount) {
		PublicCourseIndexEntity index = new PublicCourseIndexEntity();
		index.setCourseId(id);
		index.setTitle(title);
		index.setShortDescription(shortDesc);
		index.setSearchText((title + " " + topics + " " + catSlug + " " + instructorName + " " + shortDesc).toLowerCase());
		index.setCategoryId(catSlug.equals("programming") ? cat1Id : cat2Id);
		index.setCategoryName(catSlug.equals("programming") ? "Programming" : "Web Development");
		index.setCategorySlug(catSlug);
		index.setDifficulty(difficulty);
		index.setTopicsText(topics);
		index.setInstructorId(instructorId);
		index.setInstructorName(instructorName);
		index.setModuleCount(2);
		index.setLessonCount(4);
		index.setPublishedAt(Instant.ofEpochSecond(1700000000L + id * 1000));
		index.setSourceFingerprint("fp-" + id);
		index.setIndexedAt(Instant.now());
		indexRepository.saveAndFlush(index);

		if (enrollment != null || ratingAvg != null) {
			CoursePublicStatsEntity stats = new CoursePublicStatsEntity(id, enrollment, completion, ratingAvg, ratingCount, Instant.now());
			statsRepository.saveAndFlush(stats);
		}
	}

	@Test
	void singleAndMultipleTermSearchWithAndLogic() throws Exception {
		// "Go" short term matches Go course exclusively
		mockMvc.perform(get("/api/v1/public/courses?q=Go"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(3));

		// "AI" short term matches AI and Machine Learning exclusively
		mockMvc.perform(get("/api/v1/public/courses?q=AI"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(2));

		// Multiple terms: "cloud microservices" matches Go course
		mockMvc.perform(get("/api/v1/public/courses?q=cloud microservices"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(3));

		// Non-matching AND combination returns 0
		mockMvc.perform(get("/api/v1/public/courses?q=cloud machine"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void caseAndAccentInsensitiveSearch() throws Exception {
		// "CAFE" and "café" both match the cafe course
		mockMvc.perform(get("/api/v1/public/courses?q=cafe"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(4));

		mockMvc.perform(get("/api/v1/public/courses?q=CAFÉ"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(4));
	}

	@Test
	void specialCharactersAndSqlMetacharactersAreSafelyEscaped() throws Exception {
		// Searching for "%" literally matches the course with "50% discount and %"
		mockMvc.perform(get("/api/v1/public/courses").param("q", "%"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(5));

		// Searching for "_" literally matches the course with "% / _ wildcard"
		mockMvc.perform(get("/api/v1/public/courses").param("q", "_"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(5));

		// SQL injection attempt string is safely treated as literal text
		mockMvc.perform(get("/api/v1/public/courses").param("q", "' OR '1'='1' --"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void searchValidationRules() throws Exception {
		// Query exceeding 100 characters returns 400
		String longQ = "a".repeat(101);
		mockMvc.perform(get("/api/v1/public/courses?q=" + longQ))
				.andExpect(status().isBadRequest());

		// Query with more than 8 terms returns 400
		String manyTerms = "t1 t2 t3 t4 t5 t6 t7 t8 t9";
		mockMvc.perform(get("/api/v1/public/courses?q=" + manyTerms))
				.andExpect(status().isBadRequest());

		// Single term exceeding 40 characters returns 400
		String longTerm = "a".repeat(41);
		mockMvc.perform(get("/api/v1/public/courses?q=" + longTerm))
				.andExpect(status().isBadRequest());
	}

	@Test
	void filteringByCategoryAndDifficulty() throws Exception {
		// Filter by category slug
		mockMvc.perform(get("/api/v1/public/courses?category=programming"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(4));

		// Filter by unknown category slug -> 400
		mockMvc.perform(get("/api/v1/public/courses?category=unknown-cat"))
				.andExpect(status().isBadRequest());

		// Filter by difficulty (repeatable)
		mockMvc.perform(get("/api/v1/public/courses?difficulty=BEGINNER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));

		mockMvc.perform(get("/api/v1/public/courses?difficulty=BEGINNER&difficulty=ADVANCED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3));

		// Filter by unknown difficulty -> 400
		mockMvc.perform(get("/api/v1/public/courses?difficulty=MASTER"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void sortingWhitelistAndPopularityOrder() throws Exception {
		// Default sort without q is popularity,desc
		MvcResult popResult = mockMvc.perform(get("/api/v1/public/courses"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode popItems = objectMapper.readTree(popResult.getResponse().getContentAsString()).get("content");
		// Course 1 has enrollment 100, Course 2 has 50, Course 3 has 30, Course 5 has 10, Course 4 has 0
		assertThat(popItems.get(0).get("id").asLong()).isEqualTo(1L);
		assertThat(popItems.get(1).get("id").asLong()).isEqualTo(2L);
		assertThat(popItems.get(2).get("id").asLong()).isEqualTo(3L);
		assertThat(popItems.get(3).get("id").asLong()).isEqualTo(5L);
		assertThat(popItems.get(4).get("id").asLong()).isEqualTo(4L);

		// Rating order: Course 2 (rating 4.90), Course 1 (4.80), Course 3 (4.50), Course 5 (4.00), Course 4 (null)
		MvcResult ratingResult = mockMvc.perform(get("/api/v1/public/courses?sort=rating,desc"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode ratingItems = objectMapper.readTree(ratingResult.getResponse().getContentAsString()).get("content");
		assertThat(ratingItems.get(0).get("id").asLong()).isEqualTo(2L);
		assertThat(ratingItems.get(1).get("id").asLong()).isEqualTo(1L);

		// Unknown sort returns 400
		mockMvc.perform(get("/api/v1/public/courses?sort=unknown,desc"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void paginationAndDeterministicTiebreaker() throws Exception {
		// Clean and seed 25 identical courses with same published_at and stats to test tiebreaker
		indexRepository.deleteAll();
		statsRepository.deleteAll();

		Instant fixedInstant = Instant.parse("2026-01-01T00:00:00Z");
		for (long i = 1; i <= 25; i++) {
			PublicCourseIndexEntity entity = new PublicCourseIndexEntity();
			entity.setCourseId(i);
			entity.setTitle("Tied Course " + i);
			entity.setShortDescription("Short");
			entity.setSearchText("tied course");
			entity.setCategoryId(cat1Id);
			entity.setCategoryName("Programming");
			entity.setCategorySlug("programming");
			entity.setDifficulty("BEGINNER");
			entity.setInstructorId(10L);
			entity.setInstructorName("Instructor");
			entity.setModuleCount(1);
			entity.setLessonCount(1);
			entity.setPublishedAt(fixedInstant);
			entity.setSourceFingerprint("fp-" + i);
			entity.setIndexedAt(Instant.now());
			indexRepository.save(entity);
		}
		indexRepository.flush();

		// Paginate page 0, size 10; page 1, size 10; page 2, size 10
		Set<Long> collectedIds = new HashSet<>();
		for (int page = 0; page < 3; page++) {
			MvcResult result = mockMvc.perform(get("/api/v1/public/courses?sort=publishedAt,desc&page=" + page + "&size=10"))
					.andExpect(status().isOk())
					.andReturn();

			JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
			for (JsonNode item : items) {
				long id = item.get("id").asLong();
				assertThat(collectedIds.add(id)).as("Duplicate ID found across pages: " + id).isTrue();
			}
		}

		assertThat(collectedIds).hasSize(25);

		// Offset limit: page * size > 1000 -> 400
		mockMvc.perform(get("/api/v1/public/courses?page=101&size=10"))
				.andExpect(status().isBadRequest());

		// Size cap: size > 50 -> 400
		mockMvc.perform(get("/api/v1/public/courses?size=51"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void completionRateThreshold() throws Exception {
		// Course with enrollment >= 10 has completionRate; course with < 10 has null completionRate
		MvcResult result = mockMvc.perform(get("/api/v1/public/courses?category=programming"))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
		for (JsonNode item : items) {
			long id = item.get("id").asLong();
			JsonNode stats = item.get("stats");
			if (id == 1L) { // enrollment 100, completion 80
				assertThat(stats.get("completionRate").asDouble()).isEqualTo(0.8);
			} else if (id == 5L) { // enrollment 10, completion 5
				assertThat(stats.get("completionRate").asDouble()).isEqualTo(0.5);
			}
		}
	}
}
