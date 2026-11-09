package com.peergeek.courselearning.discovery;

import com.peergeek.courselearning.common.PageResponse;

public interface CourseSearchRepository {
    PageResponse<CourseCardDto> search(CourseSearchCriteria criteria);
}
