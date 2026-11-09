package com.peergeek.discovery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.peergeek.common.PageResponse;
import com.peergeek.config.DiscoverySettings;
import com.peergeek.course.CategoryEntity;
import com.peergeek.course.CategoryRepository;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.course.PublishedCourseSnapshot;
import com.peergeek.exception.NotFoundException;
import com.peergeek.user.PublicProfileDto;
import com.peergeek.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCourseQueryService {
	private static final Set<String> ALLOWED_DIFFICULTIES = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
	private static final Set<String> ALLOWED_SORTS = Set.of(
			"popularity,desc",
			"publishedAt,desc",
			"rating,desc",
			"title,asc",
			"relevance");

	private final CourseSearchRepository searchRepository;
	private final PublicCourseIndexRepository indexRepository;
	private final CoursePublicStatsRepository statsRepository;
	private final CourseStructureQueryService courseStructure;
	private final CategoryRepository categoryRepository;
	private final UserService userService;
	private final IndexBuilder indexBuilder;
	private final DiscoverySettings discoverySettings;
	private final DiscoveryCacheService cacheService;

	public PublicCourseQueryService(
			CourseSearchRepository searchRepository,
			PublicCourseIndexRepository indexRepository,
			CoursePublicStatsRepository statsRepository,
			CourseStructureQueryService courseStructure,
			CategoryRepository categoryRepository,
			UserService userService,
			IndexBuilder indexBuilder,
			DiscoverySettings discoverySettings,
			DiscoveryCacheService cacheService) {
		this.searchRepository = searchRepository;
		this.indexRepository = indexRepository;
		this.statsRepository = statsRepository;
		this.courseStructure = courseStructure;
		this.categoryRepository = categoryRepository;
		this.userService = userService;
		this.indexBuilder = indexBuilder;
		this.discoverySettings = discoverySettings;
		this.cacheService = cacheService;
	}

	@Transactional(readOnly = true)
	public PageResponse<CourseCardDto> searchCourses(
			String q, String categorySlug, List<String> difficulties, Integer page, Integer size, String sort) {
		String validatedQ = validateQuery(q);
		String validatedCategorySlug = validateCategorySlug(categorySlug);
		List<String> validatedDifficulties = validateDifficulties(difficulties);
		int validatedPage = page == null ? 0 : page;
		if (validatedPage < 0) {
			throw new DiscoveryValidationException("Page index must not be negative");
		}
		int validatedSize = size == null ? 12 : size;
		if (validatedSize < 1 || validatedSize > 50) {
			throw new DiscoveryValidationException("Page size must be between 1 and 50");
		}
		if ((long) validatedPage * validatedSize > discoverySettings.getMaxOffset()) {
			throw new DiscoveryValidationException("Requested page offset exceeds maximum allowed limit");
		}
		String validatedSort = validateSort(sort, validatedQ);

		CourseSearchCriteria criteria = new CourseSearchCriteria(
				validatedQ,
				validatedCategorySlug,
				validatedDifficulties,
				validatedSort,
				validatedPage,
				validatedSize,
				discoverySettings.getStats().getMinCompletionSample());

		return searchRepository.search(criteria);
	}

	@Transactional(readOnly = true)
	public List<CourseCardDto> getPopularCourses() {
		return cacheService.getOrLoadPopular(() -> {
			int popularSize = discoverySettings.getPopular().getSize();
			CourseSearchCriteria criteria = new CourseSearchCriteria(
					null,
					null,
					List.of(),
					"popularity,desc",
					0,
					popularSize,
					discoverySettings.getStats().getMinCompletionSample());
			return searchRepository.search(criteria).content();
		});
	}

	@Transactional(readOnly = true)
	public CoursePreviewDto getCoursePreview(long courseId) {
		if (!courseStructure.isPublished(courseId)) {
			throw new NotFoundException("The requested course was not found");
		}
		return cacheService.getOrLoadPreview(courseId, () -> assemblePreview(courseId));
	}

	private CoursePreviewDto assemblePreview(long courseId) {
		PublishedCourseSnapshot snapshot = courseStructure.getPublishedCourse(courseId)
				.orElseThrow(() -> new NotFoundException("The requested course was not found"));

		PublicProfileDto instructor = userService.getPublicProfiles(Set.of(snapshot.ownerId()))
				.get(snapshot.ownerId());

		CoursePublicStatsEntity statsEntity = statsRepository.findById(courseId).orElse(null);
		CourseStatsDto stats = statsEntity == null
				? CourseStatsDto.of(null, null, null, null, discoverySettings.getStats().getMinCompletionSample())
				: CourseStatsDto.of(
						statsEntity.getEnrollmentCount(),
						statsEntity.getCompletionCount(),
						statsEntity.getRatingAverage(),
						statsEntity.getRatingCount(),
						discoverySettings.getStats().getMinCompletionSample());

		String thumbnailUrl = snapshot.thumbnailResourceId() == null
				? null
				: "/api/v1/public/courses/" + courseId + "/thumbnail?v=" + snapshot.thumbnailResourceId();

		CourseCardDto.CategoryCardDto category = snapshot.categoryId() == null
				? null
				: new CourseCardDto.CategoryCardDto(
						snapshot.categoryId(), snapshot.categoryName(), snapshot.categorySlug());

		CoursePreviewDto.InstructorPreviewDto instructorDto = instructor == null
				? null
				: new CoursePreviewDto.InstructorPreviewDto(
						instructor.id(), instructor.displayName(), instructor.headline(), instructor.bio());

		List<CoursePreviewDto.ModulePreviewDto> outline = cacheService.getOrLoadStructure(courseId, () ->
				snapshot.outline().stream()
						.map(m -> new CoursePreviewDto.ModulePreviewDto(
								m.title(),
								m.lessons().stream()
										.map(l -> new CoursePreviewDto.LessonPreviewDto(l.title()))
										.toList()))
						.toList());

		String shortDescription = indexBuilder.extractShortDescription(snapshot.description());

		return new CoursePreviewDto(
				snapshot.courseId(),
				snapshot.title(),
				shortDescription,
				snapshot.description(),
				thumbnailUrl,
				category,
				snapshot.difficulty() == null ? null : snapshot.difficulty().name(),
				snapshot.topics(),
				instructorDto,
				snapshot.moduleCount(),
				snapshot.lessonCount(),
				snapshot.publishedAt(),
				stats,
				outline);
	}

	@Transactional(readOnly = true)
	public List<PublicCategoryDto> getPublicCategories() {
		return cacheService.getOrLoadCategories(() -> {
			List<CategoryEntity> activeCategories = categoryRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc();
			Map<Long, Long> countsByCatId = indexRepository.countPublishedCoursesByCategoryId().stream()
					.collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

			return activeCategories.stream()
					.map(cat -> new PublicCategoryDto(
							cat.getId(),
							cat.getName(),
							cat.getSlug(),
							countsByCatId.getOrDefault(cat.getId(), 0L)))
					.toList();
		});
	}

	private String validateQuery(String q) {
		if (q == null || q.isBlank()) {
			return null;
		}
		String trimmed = q.trim();
		if (trimmed.length() > 100) {
			throw new DiscoveryValidationException("Search query must not exceed 100 characters");
		}
		String[] terms = trimmed.split("\\s+");
		if (terms.length > 8) {
			throw new DiscoveryValidationException("Search query must not contain more than 8 terms");
		}
		for (String term : terms) {
			if (term.length() > 40) {
				throw new DiscoveryValidationException("Search terms must not exceed 40 characters each");
			}
		}
		return trimmed;
	}

	private String validateCategorySlug(String categorySlug) {
		if (categorySlug == null || categorySlug.isBlank()) {
			return null;
		}
		String slug = categorySlug.trim();
		boolean exists = categoryRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
				.anyMatch(c -> c.getSlug().equalsIgnoreCase(slug));
		if (!exists) {
			throw new DiscoveryValidationException("Unknown category slug: " + slug);
		}
		return slug;
	}

	private List<String> validateDifficulties(List<String> difficulties) {
		if (difficulties == null || difficulties.isEmpty()) {
			return List.of();
		}
		List<String> validated = new ArrayList<>();
		for (String item : difficulties) {
			if (item == null || item.isBlank()) continue;
			// Handle comma-separated difficulties if sent as single string
			String[] split = item.split(",");
			for (String d : split) {
				String upper = d.trim().toUpperCase(Locale.ROOT);
				if (!ALLOWED_DIFFICULTIES.contains(upper)) {
					throw new DiscoveryValidationException("Unknown difficulty value: " + d.trim());
				}
				if (!validated.contains(upper)) {
					validated.add(upper);
				}
			}
		}
		return validated;
	}

	private String validateSort(String sort, String q) {
		if (sort == null || sort.isBlank()) {
			return q != null && !q.isBlank() ? "relevance" : "popularity,desc";
		}
		String trimmed = sort.trim();
		if (!ALLOWED_SORTS.contains(trimmed)) {
			throw new DiscoveryValidationException("Unknown sort parameter: " + trimmed);
		}
		return trimmed;
	}
}
