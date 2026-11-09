package com.peergeek.discovery;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/categories")
public class PublicCategoryController {
	private static final String CACHE_CONTROL_VALUE = "public, max-age=60";
	private static final String VARY_VALUE = "Accept-Encoding";

	private final PublicCourseQueryService queryService;

	public PublicCategoryController(PublicCourseQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	public ResponseEntity<List<PublicCategoryDto>> list() {
		List<PublicCategoryDto> response = queryService.getPublicCategories();
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
				.header(HttpHeaders.VARY, VARY_VALUE)
				.body(response);
	}
}
