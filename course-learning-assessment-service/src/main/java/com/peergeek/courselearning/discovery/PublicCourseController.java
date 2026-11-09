package com.peergeek.courselearning.discovery;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.course.CourseStructureQueryService;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.file.ObjectStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/courses")
public class PublicCourseController {

    private final PublicCourseQueryService queryService;
    private final CourseRepository courseRepository;
    private final ObjectStorageService storageService;

    public PublicCourseController(
            PublicCourseQueryService queryService,
            CourseRepository courseRepository,
            ObjectStorageService storageService) {
        this.queryService = queryService;
        this.courseRepository = courseRepository;
        this.storageService = storageService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<CourseCardDto>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> difficulty,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(queryService.searchCourses(q, category, difficulty, page, size, sort));
    }

    @GetMapping("/popular")
    public ResponseEntity<List<CourseCardDto>> popular() {
        return ResponseEntity.ok(queryService.getPopularCourses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CoursePreviewDto> preview(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getCoursePreview(id));
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Void> thumbnail(@PathVariable Long id) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Course not found"));
        if (course.getThumbnailKey() == null) {
            return ResponseEntity.notFound().build();
        }
        String presigned = storageService.presignGet(course.getThumbnailKey(), Duration.ofMinutes(30));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(presigned)).build();
    }
}
