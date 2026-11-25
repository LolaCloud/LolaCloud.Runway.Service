package com.miranda.luna.runway.infrastructure.web.dto;

public record AutoIpFixResponse(
        boolean success,
        String newIp,
        int updatedRecords
) {}
