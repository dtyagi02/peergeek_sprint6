package com.peergeek.file;

public record ResourceAccessContext(Long userId, ResourceSummary resource, ResourceAction action) {
}
