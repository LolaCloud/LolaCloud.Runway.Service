package com.miranda.luna.runway.application.service;

import com.miranda.luna.runway.domain.exception.InvalidOperationException;
import com.miranda.luna.runway.domain.exception.ResourceNotFoundException;
import com.miranda.luna.runway.domain.model.CloudflareAccount;
import com.miranda.luna.runway.domain.model.DnsRecord;
import com.miranda.luna.runway.domain.model.DnsZone;
import com.miranda.luna.runway.domain.repository.CloudflareAccountRepositoryPort;
import com.miranda.luna.runway.domain.repository.DnsRecordRepositoryPort;
import com.miranda.luna.runway.domain.repository.EdgeTunnelRepositoryPort;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareClientPort;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareValidationResult;
import com.miranda.luna.runway.infrastructure.web.dto.AccountRequest;
import com.miranda.luna.runway.infrastructure.web.dto.AccountResponse;
import com.miranda.luna.runway.infrastructure.web.dto.ValidateAccountRequest;
import com.miranda.luna.runway.infrastructure.web.dto.ValidateAccountResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final CloudflareAccountRepositoryPort accountRepository;
    private final EdgeTunnelRepositoryPort tunnelRepository;
    private final DnsRecordRepositoryPort recordRepository;
    private final CloudflareClientPort cloudflareClient;
    private final RunwayDataSyncService runwayDataSyncService;

    public AccountService(
            CloudflareAccountRepositoryPort accountRepository,
            EdgeTunnelRepositoryPort tunnelRepository,
            DnsRecordRepositoryPort recordRepository,
            CloudflareClientPort cloudflareClient,
            RunwayDataSyncService runwayDataSyncService
    ) {
        this.accountRepository = accountRepository;
        this.tunnelRepository = tunnelRepository;
        this.recordRepository = recordRepository;
        this.cloudflareClient = cloudflareClient;
        this.runwayDataSyncService = runwayDataSyncService;
    }

    public ValidateAccountResponse validateCredentials(ValidateAccountRequest request) {
        CloudflareValidationResult result = cloudflareClient.validateCredentials(request.email(), request.apiKey());
        return new ValidateAccountResponse(
                result.valid(),
                result.accountId(),
                result.accountName(),
                result.zonesCount(),
                result.message()
        );
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new InvalidOperationException("Email is required");
        }
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw new InvalidOperationException("API key is required");
        }

        if (accountRepository.existsByEmail(request.email().trim())) {
            throw new InvalidOperationException("Account with email '" + request.email().trim() + "' already exists");
        }

        CloudflareValidationResult validation = cloudflareClient.validateCredentials(request.email(), request.apiKey());
        if (!validation.valid()) {
            throw new InvalidOperationException(validation.message());
        }

        String name = request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : "Cloudflare (" + request.email().trim() + ")";

        CloudflareAccount account = CloudflareAccount.create(
                name,
                request.email().trim(),
                request.apiKey().trim(),
                validation.accountId(),
                "CONNECTED"
        );

        CloudflareAccount saved = accountRepository.save(account);

        // Immediately synchronize zones, DNS records, and tunnels into PostgreSQL
        runwayDataSyncService.syncAccount(saved);

        // Compute actual zonesCount and tunnelsCount based on synchronized data
        int tunnelsCount = tunnelRepository.findByAccountId(saved.getId()).size();
        if (tunnelsCount == 0) {
            try {
                tunnelsCount = cloudflareClient.listTunnels(saved.getEmail(), saved.getGlobalApiKey(), saved.getAccountId()).size();
            } catch (Exception ignored) {}
        }
        long distinctZones = recordRepository.findByAccountId(saved.getId()).stream()
                .map(DnsRecord::getZoneId)
                .distinct()
                .count();
        int zonesCount = distinctZones > 0 ? (int) distinctZones : validation.zonesCount();

        return toResponse(saved, zonesCount, tunnelsCount);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {
        List<CloudflareAccount> accounts = accountRepository.findAll();
        return accounts.stream().map(acc -> {
            int tunnelsCount = 0;
            try {
                tunnelsCount = cloudflareClient.listTunnels(acc.getEmail(), acc.getGlobalApiKey(), acc.getAccountId()).size();
            } catch (Exception ignored) {
                tunnelsCount = tunnelRepository.findByAccountId(acc.getId()).size();
            }

            int zonesCount = 2;
            try {
                List<DnsZone> zones = cloudflareClient.listZones(acc.getEmail(), acc.getGlobalApiKey());
                zonesCount = zones.size();
            } catch (Exception ignored) {
            }

            return toResponse(acc, zonesCount, tunnelsCount);
        }).toList();
    }

    @Transactional
    public void deleteAccount(UUID id) {
        if (accountRepository.findById(id).isEmpty()) {
            throw new ResourceNotFoundException("Cloudflare account not found with id: " + id);
        }
        accountRepository.deleteById(id);
    }

    private AccountResponse toResponse(CloudflareAccount account, int zonesCount, int tunnelsCount) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getEmail(),
                account.getMaskedApiKey(),
                account.getAccountId(),
                account.getStatus(),
                zonesCount,
                tunnelsCount,
                account.getCreatedAt(),
                Instant.now()
        );
    }
}
