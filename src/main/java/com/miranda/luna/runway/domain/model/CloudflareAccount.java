package com.miranda.luna.runway.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CloudflareAccount {

    private final UUID id;
    private final String name;
    private final String email;
    private final String globalApiKey;
    private final String accountId;
    private final String status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public CloudflareAccount(UUID id, String name, String email, String globalApiKey, String accountId, String status, Instant createdAt, Instant updatedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = Objects.requireNonNull(name, "Name must not be null");
        this.email = Objects.requireNonNull(email, "Email must not be null");
        this.globalApiKey = Objects.requireNonNull(globalApiKey, "Global API Key must not be null");
        this.accountId = accountId;
        this.status = status != null ? status : "CONNECTED";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static CloudflareAccount create(String name, String email, String globalApiKey, String accountId, String status) {
        Instant now = Instant.now();
        return new CloudflareAccount(UUID.randomUUID(), name, email, globalApiKey, accountId, status, now, now);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getAdminEmail() {
        return email;
    }

    public String getGlobalApiKey() {
        return globalApiKey;
    }

    public String getApiKey() {
        return globalApiKey;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCfAccountId() {
        return accountId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getMaskedApiKey() {
        if (globalApiKey == null || globalApiKey.length() <= 4) {
            return "••••";
        }
        return "••••••••••••••••" + globalApiKey.substring(globalApiKey.length() - 4);
    }
}
