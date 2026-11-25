package com.miranda.luna.runway.infrastructure.web.dto;

import java.util.List;

public record ExposePlatformResponse(
        String tunnelId,
        String tunnelName,
        String zoneName,
        List<ExposedServiceDto> services,
        String message
) {}
