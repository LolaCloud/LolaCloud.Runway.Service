package com.miranda.luna.runway.infrastructure.cloudflare;

public record CloudflareValidationResult(
        boolean valid,
        String accountId,
        String accountName,
        int zonesCount,
        String message
) {
    public static CloudflareValidationResult success(String accountId, String accountName, int zonesCount, String message) {
        return new CloudflareValidationResult(true, accountId, accountName, zonesCount, message);
    }

    public static CloudflareValidationResult invalid(String message) {
        return new CloudflareValidationResult(false, null, null, 0, message);
    }
}
