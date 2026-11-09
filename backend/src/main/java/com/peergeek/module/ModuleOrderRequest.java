package com.peergeek.module;

import java.util.List;

public record ModuleOrderRequest(List<Long> moduleIds) {
}
