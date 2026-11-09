package com.peergeek.auth;

public record RegisterResponse(String message, String email, boolean verificationRequired) {
}
