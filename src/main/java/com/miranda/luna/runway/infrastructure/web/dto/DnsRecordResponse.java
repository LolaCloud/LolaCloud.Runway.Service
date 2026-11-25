package com.miranda.luna.runway.infrastructure.web.dto;

import java.time.Instant;
import java.util.UUID;

public record DnsRecordResponse(
        UUID id,
        UUID accountId,
        String zoneId,
        String zoneName,
        String name,
        String type,
        String content,
        boolean proxied,
        int ttl,
        boolean autoSync,
        String cfRecordId,
        Instant lastSyncedAt,
        Instant createdAt
) {}
