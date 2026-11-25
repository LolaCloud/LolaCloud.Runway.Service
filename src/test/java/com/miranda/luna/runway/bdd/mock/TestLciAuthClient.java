package com.miranda.luna.runway.bdd.mock;

import com.miranda.luna.runway.domain.model.AuthenticatedOperator;
import com.miranda.luna.runway.infrastructure.security.LciAuthClient;
import com.miranda.luna.runway.infrastructure.security.LciAuthClientPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Primary
public class TestLciAuthClient implements LciAuthClientPort {

    private final LciAuthClient realClient;

    public TestLciAuthClient(LciAuthClient realClient) {
        this.realClient = realClient;
    }

    @Override
    public Optional<AuthenticatedOperator> introspect(String token) {
        if ("valid-admin-token".equals(token) || "test-admin-token".equals(token)) {
            return Optional.of(new AuthenticatedOperator(
                    UUID.fromString("90f815ae-d8fb-481d-ac71-cc764b23abcd"),
                    "admin",
                    List.of("runway::*")
            ));
        }

        if ("valid-dns-token".equals(token)) {
            return Optional.of(new AuthenticatedOperator(
                    UUID.randomUUID(),
                    "dns-user",
                    List.of("runway::dns::manage")
            ));
        }

        if ("valid-accounts-token".equals(token)) {
            return Optional.of(new AuthenticatedOperator(
                    UUID.randomUUID(),
                    "accounts-user",
                    List.of("runway::accounts::manage")
            ));
        }

        if ("valid-tunnel-read-token".equals(token)) {
            return Optional.of(new AuthenticatedOperator(
                    UUID.randomUUID(),
                    "tunnel-reader",
                    List.of("runway::tunnel::read")
            ));
        }

        if ("valid-root-token".equals(token)) {
            return Optional.of(new AuthenticatedOperator(
                    UUID.randomUUID(),
                    "root",
                    List.of("*::*::*")
            ));
        }

        if ("valid-no-perm-token".equals(token)) {
            return Optional.of(new AuthenticatedOperator(
                    UUID.randomUUID(),
                    "unprivileged",
                    List.of("other::service")
            ));
        }

        if ("invalid-token".equals(token) || "expired-token".equals(token)) {
            return Optional.empty();
        }

        // Otherwise attempt real LCI call
        return realClient.introspect(token);
    }
}
