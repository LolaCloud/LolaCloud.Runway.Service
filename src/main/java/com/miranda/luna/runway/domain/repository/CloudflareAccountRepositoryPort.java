package com.miranda.luna.runway.domain.repository;

import com.miranda.luna.runway.domain.model.CloudflareAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CloudflareAccountRepositoryPort {
    CloudflareAccount save(CloudflareAccount account);
    Optional<CloudflareAccount> findById(UUID id);
    List<CloudflareAccount> findAll();
    boolean existsByEmail(String email);
    void deleteById(UUID id);
}
