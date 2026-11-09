package com.peergeek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.common.PageResponse;
import com.peergeek.common.Pagination;
import com.peergeek.common.SafeCache;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FoundationIntegrationTest extends AbstractIntegrationTest {
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private SafeCache safeCache;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserRepository users;
	@Autowired
	private JwtService jwt;
	@Autowired
	private HealthContributorRegistry healthContributorRegistry;

	@Test
	@Order(1)
	void flywayAppliesThePhaseZeroBaselineAndPhaseOneSchemaToRealMysql() {
		Integer baseline = jdbcTemplate.queryForObject(
				"select count(*) from flyway_schema_history where version = '0.001' and success = true", Integer.class);
		java.util.List<String> applicationTables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables where table_schema = database() and table_name <> 'flyway_schema_history' order by table_name",
				String.class);

		assertThat(baseline).isEqualTo(1);
		assertThat(applicationTables).containsExactly(
				"assignment_questions", "assignments", "audit_logs", "auth_otps", "bookmarks", "categories", "community_reports", "course_modules", "course_progress", "course_public_stats", "course_publish_locks",
				"course_topics", "courses", "enrollments", "lesson_contents", "lesson_expected_resources", "lesson_notes",
				"lesson_progress", "lessons", "link_check_results", "notifications", "password_reset_otps", "public_course_index", "readiness_issues",
				"readiness_runs", "refresh_tokens", "resource_probe_results", "resources", "reviews", "submission_answers", "submission_files",
				"submissions", "user_educations", "user_projects", "users");
	}

	@Test
	@Order(2)
	void notFoundExceptionMatchesTheStandardErrorContract() throws Exception {
		String body = mockMvc.perform(get("/api/v1/test/errors/not-found").header("X-Request-Id", "req-contract-1"))
				.andExpect(status().isNotFound())
				.andExpect(header().string("X-Request-Id", "req-contract-1"))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Test resource was not found"))
				.andExpect(jsonPath("$.path").value("/api/v1/test/errors/not-found"))
				.andExpect(jsonPath("$.fieldErrors").isArray())
				.andReturn().getResponse().getContentAsString();

		JsonNode json = objectMapper.readTree(body);
		assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
				"timestamp", "status", "error", "code", "message", "path", "fieldErrors");
		assertThat(json.get("timestamp").asText()).contains("T").endsWith("Z");
	}

	@Test
	@Order(3)
	void mapsEveryApiExceptionAndFrameworkErrorToTheContract() throws Exception {
		mockMvc.perform(get("/api/v1/test/errors/forbidden"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(get("/api/v1/test/errors/conflict"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
		mockMvc.perform(get("/api/v1/test/errors/bad-request"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		mockMvc.perform(post("/api/v1/test/errors/validation").contentType("application/json")
						.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
		mockMvc.perform(get("/api/v1/test/errors/constraint").param("limit", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post("/api/v1/test/errors/validation").contentType("application/json").content("{"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
		mockMvc.perform(get("/api/v1/test/errors/type-mismatch/not-a-number"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TYPE_MISMATCH"));
		mockMvc.perform(get("/api/v1/test/errors/unknown-route"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get("/api/v1/test/errors/method-not-allowed"))
				.andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		mockMvc.perform(get("/api/v1/test/errors/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value("An unexpected error occurred"))
				.andExpect(result -> assertThat(result.getResponse().getContentAsString())
						.doesNotContain("internal detail must not be returned"));
	}

	@Test
	@Order(4)
	void requestIdIsPresentOnNormalAndErrorResponses() throws Exception {
		mockMvc.perform(get("/actuator/info"))
				.andExpect(status().isOk()).andExpect(header().exists("X-Request-Id"));
		mockMvc.perform(get("/no-such-route"))
				.andExpect(status().isNotFound()).andExpect(header().exists("X-Request-Id"));
	}

	@Test
	@Order(5)
	void paginationUsesDefaultsAndCapsTheRequestedSize() {
		assertThat(Pagination.pageable(0, null, null, null).getPageSize()).isEqualTo(20);
		assertThat(Pagination.pageable(null, null, null).getPageNumber()).isZero();
		assertThat(Pagination.pageable(2, 500, "id", "desc").getPageSize()).isEqualTo(100);
		assertThat(Pagination.pageable(2, 50, "id,desc").getSort().getOrderFor("id").isDescending()).isTrue();
		assertThat(Pagination.pageable(2, 500, "id", "desc").getSort().getOrderFor("id").isDescending()).isTrue();

		PageResponse<String> result = PageResponse.from(new org.springframework.data.domain.PageImpl<>(
				java.util.List.of("one"), PageRequest.of(1, 20), 21));
		assertThat(result.page()).isEqualTo(1);
		assertThat(result.size()).isEqualTo(20);
		assertThat(result.totalElements()).isEqualTo(21);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	@Order(6)
	void redisFailureFallsBackAndDoesNotPreventApplicationOrReadiness() throws Exception {
		REDIS.stop();
		try {
			assertThat(safeCache.getOrLoad("sample", String.class, Duration.ofMinutes(1), () -> "database-value"))
					.isEqualTo("database-value");

			mockMvc.perform(get("/actuator/health"))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.components.db.status").value("UP"))
					.andExpect(jsonPath("$.components.redis.status").value("DOWN"))
					.andExpect(jsonPath("$.components.objectStorage.status").value("UP"));
			mockMvc.perform(get("/actuator/health/readiness"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("UP"));
		} finally {
			REDIS.start();
		}
	}

	@Test
	@Order(7)
	void safeCacheCanUseRedisWhenAvailable() {
		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
		try {
			StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
			redis.afterPropertiesSet();
			SafeCache cache = new SafeCache(redis, objectMapper);

			assertThat(cache.getOrLoad("cache-hit", String.class, Duration.ofMinutes(1), () -> "cached-value"))
					.isEqualTo("cached-value");
			assertThat(cache.getOrLoad("cache-hit", String.class, Duration.ofMinutes(1),
					() -> {
						throw new AssertionError("A cache hit must not load the source");
					}))
					.isEqualTo("cached-value");
		} finally {
			connectionFactory.destroy();
		}
	}

	@Test
	@Order(8)
	void corsAllowsOnlyConfiguredOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/test/errors/not-found")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

		mockMvc.perform(options("/api/v1/test/errors/not-found")
						.header("Origin", "https://untrusted.example")
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden());
	}

	@Test
	@Order(9)
	void openApiDocumentsTheErrorSchemaAndSwaggerUiResourceLoads() throws Exception {
		UserEntity apiDocsUser = users.saveAndFlush(
				new UserEntity("openapi@example.test", "test-password-hash", "OpenAPI test"));
		String token = jwt.issue(apiDocsUser);
		mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.timestamp.format")
						.value("date-time"))
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.fieldErrors.type")
						.value("array"));
		mockMvc.perform(get("/swagger-ui/index.html").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
						.string(org.hamcrest.Matchers.containsString("Swagger UI")));
	}

	@Test
	@Order(10)
	void readinessFailsWhenMysqlHealthIsDown() throws Exception {
		HealthContributor original = healthContributorRegistry.getContributor("db");
		healthContributorRegistry.unregisterContributor("db");
		healthContributorRegistry.registerContributor("db", (HealthIndicator) () -> Health.down().build());
		try {
			mockMvc.perform(get("/actuator/health/readiness"))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.status").value("DOWN"))
					.andExpect(jsonPath("$.components.db.status").value("DOWN"));
		} finally {
			healthContributorRegistry.unregisterContributor("db");
			healthContributorRegistry.registerContributor("db", original);
		}
	}
}
