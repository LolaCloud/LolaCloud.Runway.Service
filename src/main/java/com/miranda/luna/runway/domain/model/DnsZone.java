package com.miranda.luna.runway.domain.model;

public record DnsZone(
        String id,
        String name,
        String accountId,
        String accountName,
        String status,
        int recordsCount
) {}
