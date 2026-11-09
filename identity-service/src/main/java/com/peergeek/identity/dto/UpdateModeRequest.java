package com.peergeek.identity.dto;

import com.peergeek.identity.entity.UserMode;
import jakarta.validation.constraints.NotNull;

public record UpdateModeRequest(@NotNull UserMode mode) {
}

