package com.miranda.luna.runway.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class EdgeTunnel {

    private final UUID id;
    private final UUID accountId;
    private final String tunnelId;
    private final String name;
    private final String status;
    private final String configJson;
    private final Instant createdAt;

    public EdgeTunnel(UUID id, UUID accountId, String tunnelId, String name, String status, String configJson, Instant createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.accountId = accountId;
        this.tunnelId = Objects.requireNonNull(tunnelId, "Tunnel ID must not be null");
        this.name = Objects.requireNonNull(name, "Name must not be null");
        this.status = status != null ? status : "HEALTHY";
        this.configJson = configJson != null ? configJson : "{\"routes\":[]}";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static EdgeTunnel create(UUID accountId, String tunnelId, String name, String status, String configJson) {
        return new EdgeTunnel(UUID.randomUUID(), accountId, tunnelId, name, status, configJson, Instant.now());
    }

    public EdgeTunnel withConfigJson(String newConfigJson) {
        return new EdgeTunnel(this.id, this.accountId, this.tunnelId, this.name, this.status, newConfigJson, this.createdAt);
    }

    public EdgeTunnel withStatus(String newStatus) {
        return new EdgeTunnel(this.id, this.accountId, this.tunnelId, this.name, newStatus, this.configJson, this.createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getTunnelId() {
        return tunnelId;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getConfigJson() {
        return configJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
