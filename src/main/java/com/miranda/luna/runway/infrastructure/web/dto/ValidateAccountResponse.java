package com.miranda.luna.runway.infrastructure.web.dto;

public record ValidateAccountResponse(
        boolean valid,
        String accountId,
        String accountName,
        int zonesCount,
        String message
) {}
