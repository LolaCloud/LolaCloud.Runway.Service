package com.miranda.luna.runway.infrastructure.persistence.adapter;

import com.miranda.luna.runway.domain.model.EdgeTunnel;
import com.miranda.luna.runway.domain.repository.EdgeTunnelRepositoryPort;
import com.miranda.luna.runway.infrastructure.persistence.entity.EdgeTunnelJpaEntity;
import com.miranda.luna.runway.infrastructure.persistence.repository.SpringDataEdgeTunnelRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class EdgeTunnelRepositoryAdapter implements EdgeTunnelRepositoryPort {

    private final SpringDataEdgeTunnelRepository repository;

    public EdgeTunnelRepositoryAdapter(SpringDataEdgeTunnelRepository repository) {
        this.repository = repository;
    }

    @Override
    public EdgeTunnel save(EdgeTunnel tunnel) {
        EdgeTunnelJpaEntity entity = toEntity(tunnel);
        EdgeTunnelJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<EdgeTunnel> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<EdgeTunnel> findByTunnelId(String tunnelId) {
        return repository.findByTunnelId(tunnelId).map(this::toDomain);
    }

    @Override
    public List<EdgeTunnel> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<EdgeTunnel> findByAccountId(UUID accountId) {
        return repository.findByAccountId(accountId).stream().map(this::toDomain).toList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteById(UUID id) {
        if (id != null) {
            repository.deleteTunnelById(id);
        }
    }

    private EdgeTunnel toDomain(EdgeTunnelJpaEntity entity) {
        return new EdgeTunnel(
                entity.getId(),
                entity.getAccountId(),
                entity.getTunnelId(),
                entity.getName(),
                entity.getStatus(),
                entity.getConfigJson(),
                entity.getCreatedAt()
        );
    }

    private EdgeTunnelJpaEntity toEntity(EdgeTunnel domain) {
        return new EdgeTunnelJpaEntity(
                domain.getId(),
                domain.getAccountId(),
                domain.getTunnelId(),
                domain.getName(),
                domain.getStatus(),
                domain.getConfigJson(),
                domain.getCreatedAt()
        );
    }
}
