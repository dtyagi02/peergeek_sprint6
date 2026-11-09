package com.peergeek.discovery;

import com.peergeek.common.PageResponse;

public interface CourseSearchRepository {
	PageResponse<CourseCardDto> search(CourseSearchCriteria criteria);
}
