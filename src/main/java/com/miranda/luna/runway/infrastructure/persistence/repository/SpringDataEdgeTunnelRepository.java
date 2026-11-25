package com.miranda.luna.runway.infrastructure.persistence.repository;

import com.miranda.luna.runway.infrastructure.persistence.entity.EdgeTunnelJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataEdgeTunnelRepository extends JpaRepository<EdgeTunnelJpaEntity, UUID> {
    List<EdgeTunnelJpaEntity> findAllByTunnelId(String tunnelId);
    default Optional<EdgeTunnelJpaEntity> findByTunnelId(String tunnelId) {
        List<EdgeTunnelJpaEntity> list = findAllByTunnelId(tunnelId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
    List<EdgeTunnelJpaEntity> findByAccountId(UUID accountId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("delete from EdgeTunnelJpaEntity e where e.id = :id")
    void deleteTunnelById(@org.springframework.data.repository.query.Param("id") UUID id);
}
