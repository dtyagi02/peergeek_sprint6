package com.peergeek.directmessage.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record SendDirectMessageRequest(@NotBlank @Size(max=2000) String content) {}
