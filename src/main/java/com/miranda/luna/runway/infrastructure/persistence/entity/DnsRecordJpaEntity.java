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
@Table(name = "dns_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DnsRecordJpaEntity {

    @Id
    private UUID id;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int ttl;

    @Column(nullable = false)
    private boolean proxied;

    @Column(name = "auto_sync", nullable = false)
    private boolean autoSync;

    @Column(name = "cf_record_id")
    private String cfRecordId;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
