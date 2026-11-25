package com.miranda.luna.runway.infrastructure.web.dto;

import java.util.UUID;

public record DnsRecordRequest(
        UUID accountId,
        String zoneId,
        String zoneName,
        String name,
        String type,
        String content,
        Integer ttl,
        Boolean proxied,
        Boolean autoSync
) {}
