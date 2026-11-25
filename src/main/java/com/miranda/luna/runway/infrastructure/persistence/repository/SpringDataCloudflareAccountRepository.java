package com.miranda.luna.runway.infrastructure.persistence.repository;

import com.miranda.luna.runway.infrastructure.persistence.entity.CloudflareAccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataCloudflareAccountRepository extends JpaRepository<CloudflareAccountJpaEntity, UUID> {
    Optional<CloudflareAccountJpaEntity> findByEmail(String email);
    boolean existsByEmail(String email);
}
