package com.peergeek.readiness;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instructor/courses/{courseId}/readiness")
public class ReadinessController {
	private final ReadinessService readiness;

	public ReadinessController(ReadinessService readiness) {
		this.readiness = readiness;
	}

	@PostMapping("/runs")
	public ResponseEntity<ReadinessRunView> start(@PathVariable long courseId) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(readiness.start(courseId));
	}

	@GetMapping("/runs/{runId}")
	public ReadinessRunView get(@PathVariable long courseId, @PathVariable long runId) {
		return readiness.get(courseId, runId);
	}

	@GetMapping("/runs/latest")
	public ResponseEntity<ReadinessRunView> getLatest(@PathVariable long courseId) {
		ReadinessRunView latest = readiness.getLatest(courseId);
		return latest != null ? ResponseEntity.ok(latest) : ResponseEntity.noContent().build();
	}
}
