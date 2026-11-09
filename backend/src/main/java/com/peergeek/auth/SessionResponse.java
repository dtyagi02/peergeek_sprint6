package com.peergeek.auth;

import com.peergeek.user.UserProfileDto;

public record SessionResponse(String accessToken, long expiresIn, UserProfileDto user) {
}
