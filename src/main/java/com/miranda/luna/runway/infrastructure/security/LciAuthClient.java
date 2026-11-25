package com.miranda.luna.runway.infrastructure.security;

import com.miranda.luna.runway.domain.model.AuthenticatedOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LciAuthClient implements LciAuthClientPort {

    private static final Logger logger = LoggerFactory.getLogger(LciAuthClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String introspectUrl;

    public LciAuthClient(
            ObjectMapper objectMapper,
            @Value("${lola.lci.auth.introspect-url:http://localhost:8080/internal/v1/auth/introspect}") String introspectUrl
    ) {
        this.objectMapper = objectMapper;
        this.introspectUrl = introspectUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public Optional<AuthenticatedOperator> introspect(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            String requestPayload = objectMapper.writeValueAsString(Map.of("token", token));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(introspectUrl))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("LCI introspect returned non-2xx status: {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode dataNode = root.has("data") ? root.get("data") : root;

            boolean valid = dataNode.path("valid").asBoolean(false);
            if (!valid) {
                String error = dataNode.path("error").asText("Unknown error");
                logger.debug("LCI token introspection declared token invalid: {}", error);
                return Optional.empty();
            }

            JsonNode operatorNode = dataNode.get("operator");
            if (operatorNode == null || operatorNode.isNull()) {
                return Optional.empty();
            }

            String idStr = operatorNode.path("id").asText(null);
            UUID id = idStr != null ? UUID.fromString(idStr) : UUID.randomUUID();
            String username = operatorNode.path("username").asText();

            List<String> permissions = new ArrayList<>();
            JsonNode permsNode = operatorNode.get("permissions");
            if (permsNode != null && permsNode.isArray()) {
                for (JsonNode p : permsNode) {
                    permissions.add(p.asText());
                }
            }

            return Optional.of(new AuthenticatedOperator(id, username, permissions));
        } catch (Exception e) {
            logger.error("Error communicating with LCI token introspection endpoint: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
