package com.peergeek.course;

import java.net.URI;
import java.util.List;
import com.peergeek.file.LessonResourceQueryService;
import com.peergeek.file.ResourceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles course metadata, module and lesson content, and ready resource metadata
 * through the read-only query seams.
 *
 * <p>Contract: This service performs <strong>no authorization checks</strong>, does not
 * verify publication status, does not check enrollment, and does not issue resource
 * presigned download URLs. Callers (e.g. Phase 4 instructor preview, Phase 5/6 learner endpoints)
 * are responsible for enforcing access control and publication policies before returning this view.
 */
@Service
public class CourseLearnerViewAssembler {
	private final CourseStructureQueryService structure;
	private final LessonResourceQueryService lessonResources;

	public CourseLearnerViewAssembler(
			CourseStructureQueryService structure, LessonResourceQueryService lessonResources) {
		this.structure = structure;
		this.lessonResources = lessonResources;
	}

	/**
	 * Assembles a learner view for the given course without applying authorization or publication checks.
	 *
	 * @param courseId the ID of the course to assemble
	 * @return the assembled learner view
	 * @throws com.peergeek.exception.NotFoundException if the course does not exist or has been soft-deleted
	 */
	@Transactional(readOnly = true)
	public CourseLearnerView assemble(long courseId) {
		CourseMetadataSnapshot metadata = structure.getCourseMetadata(courseId)
				.filter(value -> !value.deleted())
				.orElseThrow(() -> new com.peergeek.exception.NotFoundException(
						"The requested resource was not found"));
		CourseStructureQueryService.CourseStructure course = structure.getStructure(courseId);
		List<CourseLearnerView.Module> modules = course.modules().stream()
				.map(module -> new CourseLearnerView.Module(module.id(), module.title(), module.description(),
						module.lessons().stream().map(lesson -> {
							CourseStructureQueryService.LessonContentSnapshot content =
									structure.getLessonContent(lesson.id());
							List<CourseLearnerView.Resource> resources =
									lessonResources.listResources(lesson.id()).stream()
									.filter(resource -> resource.status() == ResourceStatus.READY)
									.map(resource -> new CourseLearnerView.Resource(resource.id(),
											resource.type().name(), resource.title(), resource.mimeType(),
											resource.sizeBytes(), resource.externalUrl() == null
													? null : URI.create(resource.externalUrl())))
									.toList();
							return new CourseLearnerView.Lesson(lesson.id(), lesson.title(), lesson.summary(),
									content.contentFormat(), content.document(), resources);
						}).toList()))
				.toList();
		return new CourseLearnerView(courseId, metadata.title(), metadata.description(),
				metadata.categoryName(), metadata.difficulty(), metadata.topics(), modules);
	}
}
