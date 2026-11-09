package com.peergeek.outbox.events;

public record UserRegisteredEvent(
        Long userId,
        String email,
        String displayName
) {}

