package com.miranda.luna.runway.infrastructure.web.dto;

public record ExposedServiceDto(
        String name,
        String subdomain,
        String hostname,
        String targetUrl,
        String publicUrl,
        String status
) {}
