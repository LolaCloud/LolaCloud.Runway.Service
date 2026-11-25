package com.miranda.luna.runway.application.service;

import com.miranda.luna.runway.domain.exception.InvalidOperationException;
import com.miranda.luna.runway.domain.exception.ResourceNotFoundException;
import com.miranda.luna.runway.domain.model.CloudflareAccount;
import com.miranda.luna.runway.domain.model.DnsRecord;
import com.miranda.luna.runway.domain.model.DnsZone;
import com.miranda.luna.runway.domain.repository.CloudflareAccountRepositoryPort;
import com.miranda.luna.runway.domain.repository.DnsRecordRepositoryPort;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareClientPort;
import com.miranda.luna.runway.infrastructure.web.dto.DnsRecordRequest;
import com.miranda.luna.runway.infrastructure.web.dto.DnsRecordResponse;
import com.miranda.luna.runway.infrastructure.web.dto.DnsZoneResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class DnsService {

    private final DnsRecordRepositoryPort recordRepository;
    private final CloudflareAccountRepositoryPort accountRepository;
    private final CloudflareClientPort cloudflareClient;

    public DnsService(
            DnsRecordRepositoryPort recordRepository,
            CloudflareAccountRepositoryPort accountRepository,
            CloudflareClientPort cloudflareClient
    ) {
        this.recordRepository = recordRepository;
        this.accountRepository = accountRepository;
        this.cloudflareClient = cloudflareClient;
    }

    @Transactional(readOnly = true)
    public List<DnsZoneResponse> listZones() {
        List<CloudflareAccount> accounts = accountRepository.findAll();
        Map<UUID, CloudflareAccount> accountMap = new LinkedHashMap<>();
        for (CloudflareAccount acc : accounts) {
            accountMap.put(acc.getId(), acc);
        }

        List<DnsRecord> records = recordRepository.findAll();
        Map<String, List<DnsRecord>> recordsByZone = new LinkedHashMap<>();
        for (DnsRecord record : records) {
            if (record.getZoneId() != null && !record.getZoneId().isBlank()) {
                recordsByZone.computeIfAbsent(record.getZoneId(), k -> new ArrayList<>()).add(record);
            }
        }

        List<DnsZoneResponse> zoneResponses = new ArrayList<>();
        for (Map.Entry<String, List<DnsRecord>> entry : recordsByZone.entrySet()) {
            String zoneId = entry.getKey();
            List<DnsRecord> zoneRecords = entry.getValue();

            String zoneName = zoneRecords.stream()
                    .map(DnsRecord::getZoneName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse(zoneId);

            UUID accountId = zoneRecords.stream()
                    .map(DnsRecord::getAccountId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            String accountIdStr = accountId != null ? accountId.toString() : "";
            String accountName = "Default Account";
            if (accountId != null && accountMap.containsKey(accountId)) {
                accountName = accountMap.get(accountId).getName();
            } else if (!accounts.isEmpty()) {
                accountName = accounts.get(0).getName();
                accountIdStr = accounts.get(0).getId().toString();
            }

            zoneResponses.add(new DnsZoneResponse(
                    zoneId,
                    zoneName,
                    accountIdStr,
                    accountName,
                    "active",
                    zoneRecords.size()
            ));
        }

        return zoneResponses;
    }

    @Transactional(readOnly = true)
    public List<DnsRecordResponse> listRecords(String zoneId) {
        return listRecords(zoneId, null);
    }

    @Transactional(readOnly = true)
    public List<DnsRecordResponse> listRecords(String zoneId, Boolean autoSync) {
        List<DnsRecord> records;
        if (Boolean.TRUE.equals(autoSync)) {
            records = recordRepository.findByAutoSyncTrue();
            if (zoneId != null && !zoneId.isBlank()) {
                records = records.stream()
                        .filter(r -> (r.getZoneId() != null && r.getZoneId().equalsIgnoreCase(zoneId.trim()))
                                || (r.getZoneName() != null && r.getZoneName().equalsIgnoreCase(zoneId.trim())))
                        .toList();
            }
        } else if (zoneId != null && !zoneId.isBlank()) {
            records = recordRepository.findByZoneId(zoneId.trim());
            if (records.isEmpty()) {
                records = recordRepository.findAll().stream()
                        .filter(r -> r.getZoneName() != null && r.getZoneName().equalsIgnoreCase(zoneId.trim()))
                        .toList();
            }
            if (Boolean.FALSE.equals(autoSync)) {
                records = records.stream().filter(r -> !r.isAutoSync()).toList();
            }
        } else if (Boolean.FALSE.equals(autoSync)) {
            records = recordRepository.findAll().stream().filter(r -> !r.isAutoSync()).toList();
        } else {
            records = recordRepository.findAll();
        }
        return records.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DnsRecordResponse getRecordById(UUID id) {
        DnsRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DNS record not found with id: " + id));
        return toResponse(record);
    }

    @Transactional
    public DnsRecordResponse createRecord(DnsRecordRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new InvalidOperationException("Record name is required");
        }
        if (request.type() == null || request.type().isBlank()) {
            throw new InvalidOperationException("Record type is required");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new InvalidOperationException("Record content is required");
        }
        if (request.zoneId() == null || request.zoneId().isBlank()) {
            throw new InvalidOperationException("Zone ID is required");
        }

        CloudflareAccount account = resolveAccount(request.accountId());

        String cfRecordId = null;
        if (account != null) {
            cfRecordId = cloudflareClient.createDnsRecord(
                    account.getEmail(),
                    account.getGlobalApiKey(),
                    request.zoneId(),
                    request.type().toUpperCase(),
                    request.name(),
                    request.content(),
                    request.ttl() != null ? request.ttl() : 1,
                    Boolean.TRUE.equals(request.proxied())
            );
        }

        DnsRecord record = DnsRecord.create(
                account != null ? account.getId() : request.accountId(),
                request.zoneId(),
                request.zoneName() != null ? request.zoneName() : request.zoneId(),
                request.name(),
                request.type().toUpperCase(),
                request.content(),
                request.ttl() != null ? request.ttl() : 1,
                Boolean.TRUE.equals(request.proxied()),
                Boolean.TRUE.equals(request.autoSync()),
                cfRecordId
        );

        DnsRecord saved = recordRepository.save(record);
        return toResponse(saved);
    }

    @Transactional
    public DnsRecordResponse updateRecord(UUID id, DnsRecordRequest request) {
        DnsRecord existing = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DNS record not found with id: " + id));

        CloudflareAccount account = resolveAccount(existing.getAccountId());

        String type = request.type() != null ? request.type().toUpperCase() : existing.getType();
        String name = request.name() != null ? request.name() : existing.getName();
        String content = request.content() != null ? request.content() : existing.getContent();
        int ttl = request.ttl() != null ? request.ttl() : existing.getTtl();
        boolean proxied = request.proxied() != null ? request.proxied() : existing.isProxied();
        boolean autoSync = request.autoSync() != null ? request.autoSync() : existing.isAutoSync();

        if (account != null && existing.getCfRecordId() != null) {
            cloudflareClient.updateDnsRecord(
                    account.getEmail(),
                    account.getGlobalApiKey(),
                    existing.getZoneId(),
                    existing.getCfRecordId(),
                    type,
                    name,
                    content,
                    ttl,
                    proxied
            );
        }

        DnsRecord updated = existing.update(type, name, content, ttl, proxied, autoSync, existing.getCfRecordId());
        DnsRecord saved = recordRepository.save(updated);
        return toResponse(saved);
    }

    @Transactional
    public void deleteRecord(UUID id) {
        DnsRecord existing = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DNS record not found with id: " + id));

        CloudflareAccount account = resolveAccount(existing.getAccountId());
        if (account != null && existing.getCfRecordId() != null) {
            cloudflareClient.deleteDnsRecord(
                    account.getEmail(),
                    account.getGlobalApiKey(),
                    existing.getZoneId(),
                    existing.getCfRecordId()
            );
        }

        recordRepository.deleteById(id);
    }

    @Transactional
    public DnsRecordResponse toggleAutoSync(UUID id, boolean autoSync) {
        DnsRecord existing = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DNS record not found with id: " + id));

        DnsRecord updated = existing.withAutoSync(autoSync);
        DnsRecord saved = recordRepository.save(updated);
        return toResponse(saved);
    }

    private CloudflareAccount resolveAccount(UUID accountId) {
        if (accountId != null) {
            Optional<CloudflareAccount> opt = accountRepository.findById(accountId);
            if (opt.isPresent()) {
                return opt.get();
            }
        }
        List<CloudflareAccount> all = accountRepository.findAll();
        return all.isEmpty() ? null : all.get(0);
    }

    private DnsRecordResponse toResponse(DnsRecord record) {
        return new DnsRecordResponse(
                record.getId(),
                record.getAccountId(),
                record.getZoneId(),
                record.getZoneName(),
                record.getName(),
                record.getType(),
                record.getContent(),
                record.isProxied(),
                record.getTtl(),
                record.isAutoSync(),
                record.getCfRecordId(),
                record.getLastSyncedAt(),
                record.getCreatedAt()
        );
    }
}
