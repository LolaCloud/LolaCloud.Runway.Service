Feature: Tunnels & Edge Status Operations
  As an operator
  I want to manage Zero Trust tunnels and monitor Edge WAN IP status
  So that edge infrastructure operates reliably

  Background:
    Given the request has header "Authorization" set to "Bearer valid-admin-token"

  Scenario: Check cloudflared daemon status
    When I send a GET request to "/api/v1/runway/cloudflared/status"
    Then the response status code should be 200
    And the response body should contain cloudflared daemon info

  Scenario: Check Edge WAN IP status and trigger IP fix
    When I send a GET request to "/api/v1/runway/edge-ip"
    Then the response status code should be 200
    And the response body should contain current WAN IP

    When I send a POST request to "/api/v1/runway/edge-ip/fix"
    Then the response status code should be 200
    And the auto IP fix response should indicate success

  Scenario: Create and manage tunnel with routes
    When I create a tunnel with name "ops-gateway", hostname "ops.lolacloud.internal", and service "http://localhost:8080"
    Then the response status code should be 201
    And the response body should contain tunnel name "ops-gateway"

    When I add route with hostname "metrics.lolacloud.internal" and service "http://localhost:9090" to created tunnel
    Then the response status code should be 200
    And the tunnel routes count should be 2

    When I delete route "metrics.lolacloud.internal" from created tunnel
    Then the response status code should be 200
    And the tunnel routes count should be 1

    When I delete the created tunnel
    Then the response status code should be 200
