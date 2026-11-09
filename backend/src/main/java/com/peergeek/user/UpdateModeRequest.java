package com.peergeek.user;

import jakarta.validation.constraints.NotNull;

public record UpdateModeRequest(@NotNull UserMode mode) {
}
