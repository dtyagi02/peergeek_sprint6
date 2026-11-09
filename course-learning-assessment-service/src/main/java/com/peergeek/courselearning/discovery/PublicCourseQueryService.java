package com.peergeek.courselearning.discovery;

import java.util.*;
import java.util.stream.Collectors;
import com.peergeek.courselearning.common.CourseStatus;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.course.CategoryEntity;
import com.peergeek.courselearning.course.CategoryRepository;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.course.CourseStructureQueryService;
import com.peergeek.courselearning.course.CourseStructureQueryService.CourseStructure;
import com.peergeek.courselearning.course.CourseStructureQueryService.CourseMetadataSnapshot;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;
import com.peergeek.courselearning.profile.LearningUserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCourseQueryService {

    private final CourseSearchRepository searchRepository;
    private final PublicCourseIndexRepository indexRepository;
    private final CoursePublicStatsRepository statsRepository;
    private final CourseStructureQueryService courseStructure;
    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final com.peergeek.courselearning.course.CourseTopicRepository topicRepository;
    private final LearningUserProfileRepository userProfileRepository;
    private final DiscoverySettings discoverySettings;

    public PublicCourseQueryService(
            CourseSearchRepository searchRepository,
            PublicCourseIndexRepository indexRepository,
            CoursePublicStatsRepository statsRepository,
            CourseStructureQueryService courseStructure,
            CourseRepository courseRepository,
            CategoryRepository categoryRepository,
            com.peergeek.courselearning.course.CourseTopicRepository topicRepository,
            LearningUserProfileRepository userProfileRepository,
            DiscoverySettings discoverySettings) {
        this.searchRepository = searchRepository;
        this.indexRepository = indexRepository;
        this.statsRepository = statsRepository;
        this.courseStructure = courseStructure;
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.topicRepository = topicRepository;
        this.userProfileRepository = userProfileRepository;
        this.discoverySettings = discoverySettings;
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseCardDto> searchCourses(
            String q, String categorySlug, List<String> difficulties, Integer page, Integer size, String sort) {
        int validatedPage = page == null || page < 0 ? 0 : page;
        int validatedSize = size == null || size < 1 ? 12 : Math.min(size, 50);

        CourseSearchCriteria criteria = new CourseSearchCriteria(
                q,
                categorySlug,
                difficulties,
                sort,
                validatedPage,
                validatedSize,
                discoverySettings.getStats().getMinCompletionSample());

        return searchRepository.search(criteria);
    }

    @Transactional(readOnly = true)
    public List<CourseCardDto> getPopularCourses() {
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
    }

    @Transactional(readOnly = true)
    public List<PublicCategoryDto> getPublicCategories() {
        List<CategoryEntity> categories = categoryRepository.findAll();
        List<Object[]> counts = indexRepository.countPublishedCoursesByCategoryId();
        Map<Long, Long> countMap = counts.stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        return categories.stream().map(c -> new PublicCategoryDto(
                c.getId(),
                c.getName(),
                c.getSlug(),
                countMap.getOrDefault(c.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public CoursePreviewDto getCoursePreview(long courseId) {
        CourseMetadataSnapshot metadata = courseStructure.getCourseMetadata(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found"));

        if (metadata.deleted() || metadata.status() != CourseStatus.PUBLISHED) {
            throw new NotFoundException("Course not found");
        }

        CourseStructure structure = courseStructure.getStructure(courseId);
        LearningUserProfileEntity instructor = userProfileRepository.findById(metadata.ownerId()).orElse(null);
        CoursePublicStatsEntity statsEntity = statsRepository.findById(courseId).orElse(null);
        CourseEntity course = courseRepository.findById(courseId).orElse(null);
        CategoryEntity category = metadata.categoryId() != null
                ? categoryRepository.findById(metadata.categoryId()).orElse(null) : null;
        List<String> topics = topicRepository.findByCourseId(courseId).stream()
                .map(com.peergeek.courselearning.course.CourseTopicEntity::getName).toList();

        CourseStatsDto statsDto = statsEntity == null ? null : CourseStatsDto.of(
                statsEntity.getEnrollmentCount(),
                statsEntity.getCompletionCount(),
                statsEntity.getRatingAverage(),
                statsEntity.getRatingCount(),
                discoverySettings.getStats().getMinCompletionSample());

        CourseCardDto.CategoryCardDto catCard = category == null ? null
                : new CourseCardDto.CategoryCardDto(category.getId(), category.getName(), category.getSlug());

        CoursePreviewDto.InstructorPreviewDto instDto = new CoursePreviewDto.InstructorPreviewDto(
                metadata.ownerId(),
                instructor != null ? instructor.getDisplayName() : "Instructor",
                null,
                null);

        List<CoursePreviewDto.ModulePreviewDto> outline = structure.modules().stream().map(m -> {
            List<CoursePreviewDto.LessonPreviewDto> lessonList = m.lessons().stream()
                    .map(l -> new CoursePreviewDto.LessonPreviewDto(l.title())).toList();
            return new CoursePreviewDto.ModulePreviewDto(m.title(), lessonList);
        }).toList();

        int lessonCount = structure.modules().stream().mapToInt(m -> m.lessons().size()).sum();
        Long thumbnailId = course != null ? course.getThumbnailResourceId() : null;
        String thumbnailUrl = thumbnailId == null ? null
                : "/api/v1/public/courses/" + courseId + "/thumbnail?v=" + thumbnailId;

        return new CoursePreviewDto(
                courseId,
                metadata.title(),
                metadata.description() != null && metadata.description().length() > 300
                        ? metadata.description().substring(0, 300) : metadata.description(),
                metadata.description(),
                thumbnailUrl,
                catCard,
                metadata.difficulty() != null ? metadata.difficulty().name() : null,
                topics,
                instDto,
                structure.modules().size(),
                lessonCount,
                metadata.publishedAt(),
                statsDto,
                outline);
    }
}
