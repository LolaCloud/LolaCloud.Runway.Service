package com.miranda.luna.runway.domain.repository;

import com.miranda.luna.runway.domain.model.DnsRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DnsRecordRepositoryPort {
    DnsRecord save(DnsRecord record);
    Optional<DnsRecord> findById(UUID id);
    List<DnsRecord> findAll();
    List<DnsRecord> findByZoneId(String zoneId);
    List<DnsRecord> findByAccountId(UUID accountId);
    List<DnsRecord> findByAutoSyncTrue();
    Optional<DnsRecord> findByCfRecordId(String cfRecordId);
    Optional<DnsRecord> findByZoneIdAndTypeAndName(String zoneId, String type, String name);
    void deleteById(UUID id);
}
