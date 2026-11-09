package com.peergeek.courselearning.module;

import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.exception.BadRequestException;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ModuleService {

    private final CourseModuleRepository moduleRepository;
    private final CourseRepository courseRepository;

    public ModuleService(CourseModuleRepository moduleRepository, CourseRepository courseRepository) {
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public ModuleDto addModule(Long courseId, Long ownerId, ModuleRequests.Create request) {
        CourseEntity course = findOwnedCourse(courseId, ownerId);

        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("Module title cannot be blank");
        }

        int position = moduleRepository.countByCourseId(courseId);
        CourseModuleEntity module = new CourseModuleEntity(courseId, request.title().trim(), request.description(), position);
        module = moduleRepository.save(module);

        course.setContentUpdatedAt(Instant.now());
        courseRepository.save(course);

        return ModuleDto.fromEntity(module);
    }

    @Transactional
    public ModuleDto updateModule(Long moduleId, Long ownerId, ModuleRequests.Patch request) {
        CourseModuleEntity module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module not found: " + moduleId));

        CourseEntity course = findOwnedCourse(module.getCourseId(), ownerId);

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BadRequestException("Module title cannot be blank");
            }
            module.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            module.setDescription(request.description());
        }

        module = moduleRepository.save(module);

        course.setContentUpdatedAt(Instant.now());
        courseRepository.save(course);

        return ModuleDto.fromEntity(module);
    }

    @Transactional
    public void deleteModule(Long moduleId, Long ownerId) {
        CourseModuleEntity module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module not found: " + moduleId));

        CourseEntity course = findOwnedCourse(module.getCourseId(), ownerId);
        moduleRepository.delete(module);

        course.setContentUpdatedAt(Instant.now());
        courseRepository.save(course);
    }

    @Transactional
    public void reorderModules(Long courseId, Long ownerId, List<Long> moduleIds) {
        CourseEntity course = findOwnedCourse(courseId, ownerId);

        if (moduleIds == null || moduleIds.isEmpty()) return;

        for (int i = 0; i < moduleIds.size(); i++) {
            Long modId = moduleIds.get(i);
            CourseModuleEntity mod = moduleRepository.findById(modId).orElse(null);
            if (mod != null && mod.getCourseId().equals(courseId)) {
                mod.setPosition(i);
                moduleRepository.save(mod);
            }
        }

        course.setContentUpdatedAt(Instant.now());
        courseRepository.save(course);
    }

    private CourseEntity findOwnedCourse(Long courseId, Long ownerId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("You are not the owner of this course");
        }
        return course;
    }
}

