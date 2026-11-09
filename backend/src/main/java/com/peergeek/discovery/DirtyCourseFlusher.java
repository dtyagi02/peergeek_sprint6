package com.peergeek.discovery;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DirtyCourseFlusher {
	private static final Logger log = LoggerFactory.getLogger(DirtyCourseFlusher.class);

	private final DirtyCourseBuffer buffer;
	private final IndexService indexService;

	public DirtyCourseFlusher(DirtyCourseBuffer buffer, IndexService indexService) {
		this.buffer = buffer;
		this.indexService = indexService;
	}

	@Scheduled(fixedDelayString = "${discovery.dirty-flush:5s}")
	public void flush() {
		Set<Long> coursesToFlush = buffer.drain();
		if (coursesToFlush.isEmpty()) {
			return;
		}
		for (Long courseId : coursesToFlush) {
			try {
				indexService.rebuildCourseIfIndexed(courseId);
			} catch (Exception exception) {
				log.warn("Failed to flush dirty course courseId={}", courseId, exception);
			}
		}
	}
}
