package com.miranda.luna.runway.infrastructure.cloudflare;

import com.miranda.luna.runway.domain.model.IngressRoute;
import com.miranda.luna.runway.infrastructure.web.dto.PublishedRouteDto;
import com.miranda.luna.runway.infrastructure.web.dto.TunnelConnectorDto;

import java.time.Instant;
import java.util.List;

public record CloudflareTunnelDto(
        String tunnelId,
        String name,
        String status,
        String tunnelType,
        String uptime,
        int connectionsCount,
        List<TunnelConnectorDto> connectors,
        List<PublishedRouteDto> routes,
        String catchAllRule,
        Instant createdAt
) {
    public CloudflareTunnelDto(
            String tunnelId,
            String name,
            String status,
            int connectionsCount,
            Instant createdAt,
            List<IngressRoute> routes
    ) {
        this(
                tunnelId,
                name,
                status,
                "cloudflared",
                "--",
                connectionsCount,
                List.of(),
                routes != null ? routes.stream()
                        .map(r -> PublishedRouteDto.of(r.hostname(), "*", r.serviceUrl()))
                        .toList() : List.of(),
                "http_status:404",
                createdAt
        );
    }
}
