package com.miranda.luna.runway.infrastructure.web.dto;

public record CloudflaredStatusResponse(
        boolean installed,
        String version,
        boolean running,
        boolean configFileExists,
        int activeTunnelsCount
) {}
