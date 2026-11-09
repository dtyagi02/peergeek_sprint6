package com.peergeek.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DelegatingDataSource;

class DiscoveryQueryCountTest extends DiscoveryTestSupport {

	private static final AtomicInteger QUERY_COUNT = new AtomicInteger();

	@Autowired
	private PublicCourseQueryService queryService;

	@BeforeEach
	void resetCounter() {
		QUERY_COUNT.set(0);
	}

	@Test
	void listingCoursesRunsSmallConstantNumberOfQueries() throws Exception {
		long catId = getDefaultCategoryId();
		String catSlug = jdbc.queryForObject("select slug from categories where id = ?", String.class, catId);
		String catName = jdbc.queryForObject("select name from categories where id = ?", String.class, catId);

		// Seed 15 courses
		for (long i = 1; i <= 15; i++) {
			PublicCourseIndexEntity entity = new PublicCourseIndexEntity();
			entity.setCourseId(i);
			entity.setTitle("Query Count Course " + i);
			entity.setShortDescription("Short");
			entity.setSearchText("query count course");
			entity.setCategoryId(catId);
			entity.setCategoryName(catName);
			entity.setCategorySlug(catSlug);
			entity.setDifficulty("BEGINNER");
			entity.setInstructorId(10L);
			entity.setInstructorName("Instructor");
			entity.setModuleCount(1);
			entity.setLessonCount(1);
			entity.setPublishedAt(java.time.Instant.now());
			entity.setSourceFingerprint("fp-" + i);
			entity.setIndexedAt(java.time.Instant.now());
			indexRepository.save(entity);
		}
		indexRepository.flush();

		QUERY_COUNT.set(0);

		mockMvc.perform(get("/api/v1/public/courses?size=12"))
				.andExpect(status().isOk());

		// List endpoint should execute at most 3 queries: total count query + page select query (+ optional transaction/schema query)
		assertThat(QUERY_COUNT.get()).isLessThanOrEqualTo(3);
	}

	@Test
	void previewAssemblyRunsConstantNumberOfQueriesFor20ModulesAnd400Lessons() {
		UserEntity instructor = createInstructor("bulk-inst@example.test", "Bulk Instructor");
		long catId = getDefaultCategoryId();

		// Insert 1 published course directly
		long courseId = 98765L;
		jdbc.update("""
				insert into courses (id, title, description, category_id, difficulty, owner_id, status, version, published_at, content_updated_at, created_at, updated_at)
				values (?, 'Massive Course', 'Massive course with 20 modules and 400 lessons', ?, 'INTERMEDIATE', ?, 'PUBLISHED', 1, now(), now(), now(), now())
				""", courseId, catId, instructor.getId());

		// Insert 20 modules
		for (int m = 0; m < 20; m++) {
			long moduleId = courseId * 100 + m;
			jdbc.update("""
					insert into course_modules (id, course_id, title, description, position, created_at, updated_at)
					values (?, ?, ?, 'Module Desc', ?, now(), now())
					""", moduleId, courseId, "Module " + m, m);

			// Insert 20 lessons per module (total 400 lessons)
			for (int l = 0; l < 20; l++) {
				long lessonId = moduleId * 100 + l;
				jdbc.update("""
						insert into lessons (id, module_id, title, summary, position, created_at, updated_at)
						values (?, ?, ?, 'Lesson Summary', ?, now(), now())
						""", lessonId, moduleId, "Lesson " + l, l);
			}
		}

		cacheManager.evictAll();
		QUERY_COUNT.set(0);

		CoursePreviewDto preview = queryService.getCoursePreview(courseId);

		assertThat(preview).isNotNull();
		assertThat(preview.moduleCount()).isEqualTo(20);
		assertThat(preview.lessonCount()).isEqualTo(400);
		assertThat(preview.outline()).hasSize(20);
		assertThat(preview.outline().get(0).lessons()).hasSize(20);

		// Must be a small constant number of queries (<= 8 queries: isPublished, course, topics, modules, bulk lessons, user profile, stats)
		// NOT O(N) or O(N*M) which would be > 400 queries!
		assertThat(QUERY_COUNT.get()).isLessThanOrEqualTo(8);
	}

	@TestConfiguration
	static class QueryCountingConfiguration {
		@Bean
		static org.springframework.beans.factory.config.BeanPostProcessor dataSourceProxyPostProcessor() {
			return new org.springframework.beans.factory.config.BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					if (bean instanceof DataSource realDataSource && !(bean instanceof DelegatingDataSource)) {
						return new DelegatingDataSource(realDataSource) {
							@Override
							public Connection getConnection() throws java.sql.SQLException {
								Connection conn = super.getConnection();
								return (Connection) Proxy.newProxyInstance(
										Connection.class.getClassLoader(),
										new Class<?>[]{Connection.class},
										(proxy, method, args) -> {
											String name = method.getName();
											if ("prepareStatement".equals(name) || "createStatement".equals(name) || "prepareCall".equals(name)) {
												QUERY_COUNT.incrementAndGet();
											}
											return method.invoke(conn, args);
										}
								);
							}
						};
					}
					return bean;
				}
			};
		}
	}
}
