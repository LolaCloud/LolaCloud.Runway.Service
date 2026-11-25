package com.miranda.luna.runway.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "edge_tunnels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EdgeTunnelJpaEntity {

    @Id
    private UUID id;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "tunnel_id", nullable = false)
    private String tunnelId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
