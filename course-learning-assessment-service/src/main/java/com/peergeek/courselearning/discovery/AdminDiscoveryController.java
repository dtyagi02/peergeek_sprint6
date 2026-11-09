package com.peergeek.courselearning.discovery;

import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.course.CourseStructureQueryService;
import com.peergeek.courselearning.course.CourseTopicRepository;
import com.peergeek.courselearning.common.CourseStatus;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;
import com.peergeek.courselearning.profile.LearningUserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/discovery")
public class AdminDiscoveryController {

    private static final Logger log = LoggerFactory.getLogger(AdminDiscoveryController.class);

    private final CourseRepository courseRepository;
    private final PublicCourseIndexRepository indexRepository;
    private final CourseTopicRepository topicRepository;
    private final CourseStructureQueryService courseStructure;
    private final LearningUserProfileRepository userProfileRepository;

    public AdminDiscoveryController(
            CourseRepository courseRepository,
            PublicCourseIndexRepository indexRepository,
            CourseTopicRepository topicRepository,
            CourseStructureQueryService courseStructure,
            LearningUserProfileRepository userProfileRepository) {
        this.courseRepository = courseRepository;
        this.indexRepository = indexRepository;
        this.topicRepository = topicRepository;
        this.courseStructure = courseStructure;
        this.userProfileRepository = userProfileRepository;
    }

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> reindex() {
        List<CourseEntity> publishedCourses = courseRepository.findByStatusAndDeletedAtIsNull(
                CourseStatus.PUBLISHED, org.springframework.data.domain.Pageable.unpaged()).getContent();
        log.info("Reindexing {} published courses into public_course_index", publishedCourses.size());

        for (CourseEntity course : publishedCourses) {
            LearningUserProfileEntity instructor = userProfileRepository.findById(course.getOwnerId()).orElse(null);
            var structure = courseStructure.getStructure(course.getId());
            var topics = topicRepository.findByCourseId(course.getId());
            String topicsText = topics.isEmpty() ? null : String.join(", ", topics.stream().map(t -> t.getName()).toList());
            int moduleCount = structure.modules().size();
            int lessonCount = structure.modules().stream().mapToInt(m -> m.lessons().size()).sum();
            String searchText = (course.getTitle() + " " + (course.getDescription() != null ? course.getDescription() : "") + " " + (topicsText != null ? topicsText : "")).toLowerCase();

            PublicCourseIndexEntity entity = new PublicCourseIndexEntity(
                    course.getId(),
                    course.getTitle(),
                    course.getDescription() != null && course.getDescription().length() > 300
                            ? course.getDescription().substring(0, 300) : course.getDescription(),
                    searchText,
                    course.getCategoryId(),
                    null,
                    null,
                    course.getDifficulty() != null ? course.getDifficulty().name() : null,
                    topicsText,
                    course.getOwnerId(),
                    instructor != null ? instructor.getDisplayName() : "Instructor",
                    moduleCount,
                    lessonCount,
                    course.getThumbnailResourceId(),
                    course.getPublishedAt() != null ? course.getPublishedAt() : Instant.now(),
                    "fingerprint-" + course.getId(),
                    Instant.now()
            );
            indexRepository.save(entity);
        }

        return ResponseEntity.accepted().build();
    }
}
