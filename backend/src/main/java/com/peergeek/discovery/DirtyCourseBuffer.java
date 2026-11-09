package com.peergeek.discovery;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import org.springframework.stereotype.Component;

@Component
public class DirtyCourseBuffer {
	private final Set<Long> dirtyCourseIds = new ConcurrentSkipListSet<>();
	private final IndexService indexService;

	public DirtyCourseBuffer(IndexService indexService) {
		this.indexService = indexService;
	}

	public void markDirtyIfIndexed(long courseId) {
		if (indexService.isIndexed(courseId)) {
			dirtyCourseIds.add(courseId);
		}
	}

	public Set<Long> drain() {
		Set<Long> result = new ConcurrentSkipListSet<>(dirtyCourseIds);
		dirtyCourseIds.removeAll(result);
		return result;
	}
}
