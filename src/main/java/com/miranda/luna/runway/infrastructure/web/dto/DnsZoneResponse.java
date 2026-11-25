package com.miranda.luna.runway.infrastructure.web.dto;

public record DnsZoneResponse(
        String id,
        String name,
        String accountId,
        String accountName,
        String status,
        int recordsCount
) {}
