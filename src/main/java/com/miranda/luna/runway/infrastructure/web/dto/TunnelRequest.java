package com.miranda.luna.runway.infrastructure.web.dto;

import java.util.UUID;

public record TunnelRequest(
        String name,
        UUID accountId,
        String hostname,
        String targetService
) {}
