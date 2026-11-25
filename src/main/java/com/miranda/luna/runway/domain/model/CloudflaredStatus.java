package com.miranda.luna.runway.domain.model;

public record CloudflaredStatus(
        boolean installed,
        String version,
        boolean running,
        boolean configFileExists,
        int activeTunnelsCount
) {}
