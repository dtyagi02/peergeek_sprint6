package com.peergeek.courselearning.discovery;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/categories")
public class PublicCategoryController {

    private final PublicCourseQueryService queryService;

    public PublicCategoryController(PublicCourseQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<List<PublicCategoryDto>> list() {
        return ResponseEntity.ok(queryService.getPublicCategories());
    }
}
