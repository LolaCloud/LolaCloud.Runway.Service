package com.miranda.luna.runway.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record AuthenticatedOperator(
        UUID id,
        String username,
        List<String> permissions
) {
    public AuthenticatedOperator {
        permissions = permissions != null ? Collections.unmodifiableList(permissions) : List.of();
    }

    public boolean can(String requiredPermission) {
        if (requiredPermission == null || requiredPermission.isBlank()) {
            return false;
        }

        if ("root".equalsIgnoreCase(this.username)) {
            return true;
        }

        return permissions.stream().anyMatch(slug -> {
            if ("*::*::*".equals(slug) || "*".equals(slug) || slug.equals(requiredPermission)) {
                return true;
            }

            if (slug.contains("*")) {
                String prefix = slug.split("\\*")[0];
                return requiredPermission.startsWith(prefix);
            }

            return false;
        });
    }
}
