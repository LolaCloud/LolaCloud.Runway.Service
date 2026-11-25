package com.miranda.luna.runway.infrastructure.web.dto;

import java.time.Instant;

public record EdgeIpResponse(
        String currentWanIp,
        Instant lastCheckedAt,
        boolean autoFixEnabled,
        String syncStatus
) {}
