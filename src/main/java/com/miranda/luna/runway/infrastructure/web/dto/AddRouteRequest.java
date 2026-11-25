package com.miranda.luna.runway.infrastructure.web.dto;

public record AddRouteRequest(
        String hostname,
        String path,
        String service,
        String serviceUrl
) {
    public String getEffectiveService() {
        if (serviceUrl != null && !serviceUrl.isBlank()) {
            return serviceUrl.trim();
        }
        if (service != null && !service.isBlank()) {
            return service.trim();
        }
        return "";
    }

    public String getEffectivePath() {
        return (path != null && !path.isBlank()) ? path.trim() : "*";
    }
}
