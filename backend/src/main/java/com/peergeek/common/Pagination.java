package com.peergeek.common;

import com.peergeek.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Pagination {
	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;
	public static final int DEFAULT_PAGE = 0;

	private Pagination() {
	}

	public static Pageable pageable(Integer page, Integer requestedSize, String sort) {
		int requestedPage = page == null ? DEFAULT_PAGE : page;
		if (sort == null || sort.isBlank()) {
			return pageable(requestedPage, requestedSize, null, null);
		}
		String[] parts = sort.split(",", -1);
		if (parts.length != 2 || parts[0].isBlank()) {
			throw new BadRequestException("sort must use the format field,dir");
		}
		return pageable(requestedPage, requestedSize, parts[0].trim(), parts[1].trim());
	}

	public static Pageable pageable(int page, Integer requestedSize, String sortField, String direction) {
		if (page < 0) {
			throw new BadRequestException("page must be zero or greater");
		}
		int size = requestedSize == null ? DEFAULT_SIZE : Math.min(Math.max(requestedSize, 1), MAX_SIZE);
		if (sortField == null || sortField.isBlank()) {
			return PageRequest.of(page, size);
		}
		Sort.Direction sortDirection;
		try {
			sortDirection = direction == null ? Sort.Direction.ASC : Sort.Direction.fromString(direction);
		} catch (IllegalArgumentException exception) {
			throw new BadRequestException("sort direction must be asc or desc");
		}
		return PageRequest.of(page, size, Sort.by(sortDirection, sortField));
	}
}
