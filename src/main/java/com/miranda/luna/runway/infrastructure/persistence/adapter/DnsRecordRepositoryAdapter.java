package com.miranda.luna.runway.infrastructure.persistence.adapter;

import com.miranda.luna.runway.domain.model.DnsRecord;
import com.miranda.luna.runway.domain.repository.DnsRecordRepositoryPort;
import com.miranda.luna.runway.infrastructure.persistence.entity.DnsRecordJpaEntity;
import com.miranda.luna.runway.infrastructure.persistence.repository.SpringDataDnsRecordRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DnsRecordRepositoryAdapter implements DnsRecordRepositoryPort {

    private final SpringDataDnsRecordRepository repository;

    public DnsRecordRepositoryAdapter(SpringDataDnsRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public DnsRecord save(DnsRecord record) {
        DnsRecordJpaEntity entity = toEntity(record);
        DnsRecordJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<DnsRecord> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<DnsRecord> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<DnsRecord> findByZoneId(String zoneId) {
        return repository.findByZoneId(zoneId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<DnsRecord> findByAccountId(UUID accountId) {
        return repository.findByAccountId(accountId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<DnsRecord> findByAutoSyncTrue() {
        return repository.findByAutoSyncTrue().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<DnsRecord> findByCfRecordId(String cfRecordId) {
        return repository.findByCfRecordId(cfRecordId).map(this::toDomain);
    }

    @Override
    public Optional<DnsRecord> findByZoneIdAndTypeAndName(String zoneId, String type, String name) {
        return repository.findByZoneIdAndTypeIgnoreCaseAndNameIgnoreCase(zoneId, type, name).map(this::toDomain);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteById(UUID id) {
        if (id != null) {
            repository.deleteRecordById(id);
        }
    }

    private DnsRecord toDomain(DnsRecordJpaEntity entity) {
        return new DnsRecord(
                entity.getId(),
                entity.getAccountId(),
                entity.getZoneId(),
                entity.getZoneName(),
                entity.getName(),
                entity.getType(),
                entity.getContent(),
                entity.getTtl(),
                entity.isProxied(),
                entity.isAutoSync(),
                entity.getCfRecordId(),
                entity.getLastSyncedAt(),
                entity.getCreatedAt()
        );
    }

    private DnsRecordJpaEntity toEntity(DnsRecord domain) {
        return new DnsRecordJpaEntity(
                domain.getId(),
                domain.getAccountId(),
                domain.getZoneId(),
                domain.getZoneName(),
                domain.getName(),
                domain.getType(),
                domain.getContent(),
                domain.getTtl(),
                domain.isProxied(),
                domain.isAutoSync(),
                domain.getCfRecordId(),
                domain.getLastSyncedAt(),
                domain.getCreatedAt()
        );
    }
}
