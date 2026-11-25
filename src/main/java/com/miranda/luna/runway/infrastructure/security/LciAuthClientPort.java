package com.miranda.luna.runway.infrastructure.security;

import com.miranda.luna.runway.domain.model.AuthenticatedOperator;

import java.util.Optional;

public interface LciAuthClientPort {
    Optional<AuthenticatedOperator> introspect(String token);
}
