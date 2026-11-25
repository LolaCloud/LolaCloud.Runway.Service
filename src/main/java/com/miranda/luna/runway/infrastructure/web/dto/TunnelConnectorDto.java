package com.miranda.luna.runway.infrastructure.web.dto;

public record TunnelConnectorDto(
        String connectorId,
        String hostname,
        String dataCenter,
        String originIp,
        String version,
        String platform,
        String status,
        Boolean isHostServer
) {
    public TunnelConnectorDto(
            String connectorId,
            String hostname,
            String dataCenter,
            String originIp,
            String version,
            String platform,
            String status
    ) {
        this(connectorId, hostname, dataCenter, originIp, version, platform, status, false);
    }
}
