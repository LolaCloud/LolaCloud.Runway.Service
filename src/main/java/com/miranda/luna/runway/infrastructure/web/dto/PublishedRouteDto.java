package com.miranda.luna.runway.infrastructure.web.dto;

public record PublishedRouteDto(
        String hostname,
        String path,
        String service,
        String serviceUrl,
        int originConfigurations
) {
    public static PublishedRouteDto of(String hostname, String path, String service) {
        String effectivePath = (path != null && !path.isBlank()) ? path : "*";
        String effectiveService = service != null ? service : "";
        return new PublishedRouteDto(hostname, effectivePath, effectiveService, effectiveService, 0);
    }
}
