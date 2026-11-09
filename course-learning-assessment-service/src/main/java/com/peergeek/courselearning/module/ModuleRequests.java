package com.peergeek.courselearning.module;

import java.util.List;

public class ModuleRequests {

    public record Create(String title, String description) {}
    public record Patch(String title, String description) {}
    public record Order(List<Long> moduleIds) {}
}

