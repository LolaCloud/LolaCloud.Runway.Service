package com.miranda.luna.runway.infrastructure.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        String email,
        String maskedApiKey,
        String accountId,
        String status,
        int zonesCount,
        int tunnelsCount,
        Instant createdAt,
        Instant lastValidatedAt
) {}
