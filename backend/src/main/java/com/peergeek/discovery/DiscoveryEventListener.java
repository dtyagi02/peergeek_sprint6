package com.peergeek.discovery;

import com.peergeek.course.CourseContentChangedEvent;
import com.peergeek.course.CoursePublishedEvent;
import com.peergeek.course.CourseUnpublishedEvent;
import com.peergeek.file.ResourceChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DiscoveryEventListener {
	private static final Logger log = LoggerFactory.getLogger(DiscoveryEventListener.class);

	private final IndexService indexService;
	private final DirtyCourseBuffer dirtyCourseBuffer;

	public DiscoveryEventListener(IndexService indexService, DirtyCourseBuffer dirtyCourseBuffer) {
		this.indexService = indexService;
		this.dirtyCourseBuffer = dirtyCourseBuffer;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onCoursePublished(CoursePublishedEvent event) {
		try {
			indexService.indexCourse(event.courseId());
		} catch (Exception exception) {
			log.error("Failed to index published course after commit courseId={}", event.courseId(), exception);
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onCourseUnpublished(CourseUnpublishedEvent event) {
		try {
			indexService.unindexCourse(event.courseId());
		} catch (Exception exception) {
			log.error("Failed to unindex unpublished course after commit courseId={}", event.courseId(), exception);
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onCourseContentChanged(CourseContentChangedEvent event) {
		try {
			if (event.getCourseId() != null) {
				dirtyCourseBuffer.markDirtyIfIndexed(event.getCourseId());
			}
		} catch (Exception exception) {
			log.warn("Failed to mark course dirty on content change courseId={}", event.getCourseId(), exception);
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onResourceChanged(ResourceChangedEvent event) {
		try {
			if (event.getCourseId() != null) {
				dirtyCourseBuffer.markDirtyIfIndexed(event.getCourseId());
			}
		} catch (Exception exception) {
			log.warn("Failed to mark course dirty on resource change courseId={}", event.getCourseId(), exception);
		}
	}
}
