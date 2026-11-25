Feature: Health Endpoint
  As a client or orchestrator
  I want to check the health status of Lola Runway
  So that I can verify the microservice is operational

  Scenario: Runway health check returns status UP in standard envelope
    When I send a GET request to "/health"
    Then the response status code should be 200
    And the response body should contain data status "UP"
    And the response body should contain a valid request_id
    And the response body should contain a valid timestamp

  Scenario: Runway health check preserves incoming X-Request-ID
    Given the request has header "X-Request-ID" set to "req-runway-health-123"
    When I send a GET request to "/health"
    Then the response status code should be 200
    And the response header "X-Request-ID" should be "req-runway-health-123"
    And the response body request_id should be "req-runway-health-123"
