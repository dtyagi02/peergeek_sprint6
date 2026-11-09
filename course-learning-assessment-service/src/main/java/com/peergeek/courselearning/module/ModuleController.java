package com.peergeek.courselearning.module;

import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ModuleController {

    private final ModuleService moduleService;
    private final CurrentUserProvider currentUserProvider;

    public ModuleController(ModuleService moduleService, CurrentUserProvider currentUserProvider) {
        this.moduleService = moduleService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/v1/instructor/courses/{courseId}/modules")
    public ResponseEntity<ModuleDto> create(
            @PathVariable("courseId") Long courseId,
            @RequestBody ModuleRequests.Create request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        ModuleDto created = moduleService.addModule(courseId, ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/api/v1/instructor/modules/{moduleId}")
    public ResponseEntity<ModuleDto> patch(
            @PathVariable("moduleId") Long moduleId,
            @RequestBody ModuleRequests.Patch request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(moduleService.updateModule(moduleId, ownerId, request));
    }

    @DeleteMapping("/api/v1/instructor/modules/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("moduleId") Long moduleId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        moduleService.deleteModule(moduleId, ownerId);
    }

    @PutMapping("/api/v1/instructor/courses/{courseId}/modules/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(
            @PathVariable("courseId") Long courseId,
            @RequestBody ModuleRequests.Order request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        moduleService.reorderModules(courseId, ownerId, request.moduleIds());
    }
}

