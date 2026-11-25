package com.miranda.luna.runway.infrastructure.persistence.repository;

import com.miranda.luna.runway.infrastructure.persistence.entity.DnsRecordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataDnsRecordRepository extends JpaRepository<DnsRecordJpaEntity, UUID> {
    List<DnsRecordJpaEntity> findByZoneId(String zoneId);
    List<DnsRecordJpaEntity> findByAccountId(UUID accountId);
    List<DnsRecordJpaEntity> findByAutoSyncTrue();
    Optional<DnsRecordJpaEntity> findByCfRecordId(String cfRecordId);
    Optional<DnsRecordJpaEntity> findByZoneIdAndTypeIgnoreCaseAndNameIgnoreCase(String zoneId, String type, String name);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("delete from DnsRecordJpaEntity d where d.id = :id")
    void deleteRecordById(@org.springframework.data.repository.query.Param("id") UUID id);
}
