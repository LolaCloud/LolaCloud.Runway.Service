package com.miranda.luna.runway.domain.repository;

import com.miranda.luna.runway.domain.model.EdgeTunnel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EdgeTunnelRepositoryPort {
    EdgeTunnel save(EdgeTunnel tunnel);
    Optional<EdgeTunnel> findById(UUID id);
    Optional<EdgeTunnel> findByTunnelId(String tunnelId);
    List<EdgeTunnel> findAll();
    List<EdgeTunnel> findByAccountId(UUID accountId);
    void deleteById(UUID id);
}
