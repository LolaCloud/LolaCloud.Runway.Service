package com.miranda.luna.runway.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class DnsRecord {

    private final UUID id;
    private final UUID accountId;
    private final String zoneId;
    private final String zoneName;
    private final String name;
    private final String type;
    private final String content;
    private final int ttl;
    private final boolean proxied;
    private final boolean autoSync;
    private final String cfRecordId;
    private final Instant lastSyncedAt;
    private final Instant createdAt;

    public DnsRecord(
            UUID id,
            UUID accountId,
            String zoneId,
            String zoneName,
            String name,
            String type,
            String content,
            int ttl,
            boolean proxied,
            boolean autoSync,
            String cfRecordId,
            Instant lastSyncedAt,
            Instant createdAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.accountId = accountId;
        this.zoneId = Objects.requireNonNull(zoneId, "Zone ID must not be null");
        this.zoneName = zoneName != null ? zoneName : "";
        this.name = Objects.requireNonNull(name, "Name must not be null");
        this.type = Objects.requireNonNull(type, "Type must not be null").toUpperCase();
        this.content = Objects.requireNonNull(content, "Content must not be null");
        this.ttl = ttl > 0 ? ttl : 1;
        this.proxied = proxied;
        this.autoSync = autoSync;
        this.cfRecordId = cfRecordId;
        this.lastSyncedAt = lastSyncedAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static DnsRecord create(
            UUID accountId,
            String zoneId,
            String zoneName,
            String name,
            String type,
            String content,
            int ttl,
            boolean proxied,
            boolean autoSync,
            String cfRecordId
    ) {
        Instant now = Instant.now();
        return new DnsRecord(
                UUID.randomUUID(),
                accountId,
                zoneId,
                zoneName,
                name,
                type,
                content,
                ttl,
                proxied,
                autoSync,
                cfRecordId,
                now,
                now
        );
    }

    public DnsRecord update(String type, String name, String content, int ttl, boolean proxied, boolean autoSync, String cfRecordId) {
        return new DnsRecord(
                this.id,
                this.accountId,
                this.zoneId,
                this.zoneName,
                name != null ? name : this.name,
                type != null ? type : this.type,
                content != null ? content : this.content,
                ttl > 0 ? ttl : this.ttl,
                proxied,
                autoSync,
                cfRecordId != null ? cfRecordId : this.cfRecordId,
                Instant.now(),
                this.createdAt
        );
    }

    public DnsRecord withAutoSync(boolean autoSync) {
        return new DnsRecord(
                this.id,
                this.accountId,
                this.zoneId,
                this.zoneName,
                this.name,
                this.type,
                this.content,
                this.ttl,
                this.proxied,
                autoSync,
                this.cfRecordId,
                Instant.now(),
                this.createdAt
        );
    }

    public DnsRecord withContent(String newContent) {
        return new DnsRecord(
                this.id,
                this.accountId,
                this.zoneId,
                this.zoneName,
                this.name,
                this.type,
                newContent,
                this.ttl,
                this.proxied,
                this.autoSync,
                this.cfRecordId,
                Instant.now(),
                this.createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public int getTtl() {
        return ttl;
    }

    public boolean isProxied() {
        return proxied;
    }

    public boolean isAutoSync() {
        return autoSync;
    }

    public String getCfRecordId() {
        return cfRecordId;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
