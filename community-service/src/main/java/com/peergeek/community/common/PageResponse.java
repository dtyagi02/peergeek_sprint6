package com.peergeek.community.common;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {
	public PageResponse {
		content = content == null ? List.of() : List.copyOf(content);
	}

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(
				page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}
}

