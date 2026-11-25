package com.miranda.luna.runway.infrastructure.persistence.adapter;

import com.miranda.luna.runway.domain.model.CloudflareAccount;
import com.miranda.luna.runway.domain.repository.CloudflareAccountRepositoryPort;
import com.miranda.luna.runway.infrastructure.persistence.entity.CloudflareAccountJpaEntity;
import com.miranda.luna.runway.infrastructure.persistence.repository.SpringDataCloudflareAccountRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CloudflareAccountRepositoryAdapter implements CloudflareAccountRepositoryPort {

    private final SpringDataCloudflareAccountRepository repository;

    public CloudflareAccountRepositoryAdapter(SpringDataCloudflareAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public CloudflareAccount save(CloudflareAccount account) {
        CloudflareAccountJpaEntity entity = toEntity(account);
        CloudflareAccountJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<CloudflareAccount> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<CloudflareAccount> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    private CloudflareAccount toDomain(CloudflareAccountJpaEntity entity) {
        return new CloudflareAccount(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getGlobalApiKey(),
                entity.getAccountId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private CloudflareAccountJpaEntity toEntity(CloudflareAccount domain) {
        return new CloudflareAccountJpaEntity(
                domain.getId(),
                domain.getName(),
                domain.getEmail(),
                domain.getGlobalApiKey(),
                domain.getAccountId(),
                domain.getStatus(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
