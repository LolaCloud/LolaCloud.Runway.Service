package com.miranda.luna.runway.domain.model;

import java.time.Instant;

public record EdgeIpStatus(
        String currentWanIp,
        Instant lastCheckedAt,
        boolean autoFixEnabled,
        String syncStatus
) {}
