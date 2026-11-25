package com.miranda.luna.runway.infrastructure.web.dto;

import com.miranda.luna.runway.domain.model.IngressRoute;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TunnelResponse(
        UUID id,
        String tunnelId,
        String name,
        UUID accountId,
        String accountName,
        String status,
        String tunnelType,
        String uptime,
        int connectionsCount,
        int routesCount,
        List<TunnelConnectorDto> connectors,
        List<PublishedRouteDto> routes,
        List<IngressRoute> ingressRoutes,
        String catchAllRule,
        Instant createdAt,
        Boolean isHostServer
) {
    public TunnelResponse(
            UUID id,
            String tunnelId,
            String name,
            UUID accountId,
            String accountName,
            String status,
            String tunnelType,
            String uptime,
            int connectionsCount,
            int routesCount,
            List<TunnelConnectorDto> connectors,
            List<PublishedRouteDto> routes,
            List<IngressRoute> ingressRoutes,
            String catchAllRule,
            Instant createdAt
    ) {
        this(
                id,
                tunnelId,
                name,
                accountId,
                accountName,
                status,
                tunnelType,
                uptime,
                connectionsCount,
                routesCount,
                connectors,
                routes,
                ingressRoutes,
                catchAllRule,
                createdAt,
                false
        );
    }

    public TunnelResponse(
            UUID id,
            String tunnelId,
            String name,
            UUID accountId,
            String accountName,
            String status,
            int connectionsCount,
            int routesCount,
            List<IngressRoute> ingressRoutes,
            Instant createdAt
    ) {
        this(
                id,
                tunnelId,
                name,
                accountId,
                accountName,
                status,
                "cloudflared",
                "--",
                connectionsCount,
                routesCount,
                List.of(),
                ingressRoutes != null ? ingressRoutes.stream()
                        .map(r -> PublishedRouteDto.of(r.hostname(), "*", r.serviceUrl()))
                        .toList() : List.of(),
                ingressRoutes != null ? ingressRoutes : List.of(),
                "http_status:404",
                createdAt,
                false
        );
    }
}
