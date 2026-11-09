package com.peergeek.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.peergeek.AbstractIntegrationTest;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"peergeek.redis.host=127.0.0.1",
		"peergeek.redis.port=65432" // Closed port simulating Redis down
})
class DiscoveryRedisDownIntegrationTest extends AbstractIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private UserRepository userRepository;
	@Autowired private PublicCourseIndexRepository indexRepository;

	@BeforeEach
	void clearData() {
		jdbc.update("delete from course_public_stats");
		jdbc.update("delete from public_course_index");
		jdbc.update("delete from readiness_issues");
		jdbc.update("delete from readiness_runs");
		jdbc.update("delete from audit_logs");
		jdbc.update("delete from resources");
		jdbc.update("delete from lesson_contents");
		jdbc.update("delete from lessons");
		jdbc.update("delete from course_modules");
		jdbc.update("delete from course_topics");
		jdbc.update("delete from courses");
		jdbc.update("delete from refresh_tokens");
		jdbc.update("delete from password_reset_otps");
		jdbc.update("delete from users");

		UserEntity user = new UserEntity("redis-down@example.test", "hash", "Offline Instructor");
		userRepository.saveAndFlush(user);

		long catId = jdbc.queryForObject("select id from categories where active = true limit 1", Long.class);
		String catName = jdbc.queryForObject("select name from categories where id = ?", String.class, catId);
		String catSlug = jdbc.queryForObject("select slug from categories where id = ?", String.class, catId);

		PublicCourseIndexEntity index = new PublicCourseIndexEntity();
		index.setCourseId(42L);
		index.setTitle("Redis Resilient Architecture");
		index.setShortDescription("Survives Redis outages without failures");
		index.setSearchText("redis resilient architecture offline instructor");
		index.setCategoryId(catId);
		index.setCategoryName(catName);
		index.setCategorySlug(catSlug);
		index.setDifficulty("INTERMEDIATE");
		index.setInstructorId(user.getId());
		index.setInstructorName("Offline Instructor");
		index.setModuleCount(2);
		index.setLessonCount(5);
		index.setPublishedAt(java.time.Instant.now());
		index.setSourceFingerprint("resilient-fp");
		index.setIndexedAt(java.time.Instant.now());
		indexRepository.saveAndFlush(index);
	}

	@Test
	void allPublicEndpointsFunctionNormallyWhenRedisIsDown() throws Exception {
		// App context started successfully without Redis!
		// 1. List / search courses
		mockMvc.perform(get("/api/v1/public/courses"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].title").value("Redis Resilient Architecture"));

		// 2. Popular courses
		mockMvc.perform(get("/api/v1/public/courses/popular"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").value("Redis Resilient Architecture"));

		// 3. Categories with published counts
		mockMvc.perform(get("/api/v1/public/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].courseCount").isNumber());

		// 4. Non-published course returns 404 (not 500)
		mockMvc.perform(get("/api/v1/public/courses/999999"))
				.andExpect(status().isNotFound());
	}
}
