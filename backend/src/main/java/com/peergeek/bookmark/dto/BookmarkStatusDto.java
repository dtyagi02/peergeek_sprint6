package com.peergeek.bookmark.dto;

public record BookmarkStatusDto(
		boolean bookmarked,
		Long bookmarkId) {
}
