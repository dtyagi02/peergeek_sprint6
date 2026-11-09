package com.peergeek.courselearning.security;

import com.peergeek.courselearning.common.UserRole;

public record CurrentUser(Long id, UserRole role) {}

