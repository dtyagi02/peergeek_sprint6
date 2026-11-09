package com.peergeek.module;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModuleController {
	private final ModuleService modules;

	public ModuleController(ModuleService modules) {
		this.modules = modules;
	}

	@PostMapping("/api/v1/instructor/courses/{courseId}/modules")
	@ResponseStatus(HttpStatus.CREATED)
	public ModuleDto create(
			@PathVariable long courseId, @RequestBody ModuleCreateRequest request) {
		return modules.create(courseId, request.title(), request.description());
	}

	@PatchMapping("/api/v1/instructor/modules/{moduleId}")
	public ModuleDto patch(@PathVariable long moduleId, @RequestBody JsonNode patch) {
		return modules.patch(moduleId, patch);
	}

	@DeleteMapping("/api/v1/instructor/modules/{moduleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long moduleId) {
		modules.delete(moduleId);
	}

	@PutMapping("/api/v1/instructor/courses/{courseId}/modules/order")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reorder(@PathVariable long courseId, @RequestBody ModuleOrderRequest request) {
		modules.reorder(courseId, request.moduleIds());
	}
}
