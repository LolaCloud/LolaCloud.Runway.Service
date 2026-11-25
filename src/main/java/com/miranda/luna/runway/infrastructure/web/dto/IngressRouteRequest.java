package com.miranda.luna.runway.infrastructure.web.dto;

public record IngressRouteRequest(
        String hostname,
        String serviceUrl
) {}
