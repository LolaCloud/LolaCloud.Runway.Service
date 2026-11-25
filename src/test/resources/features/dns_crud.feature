Feature: DNS CRUD Operations
  As an operator
  I want full CRUD capabilities over DNS records and zone listings
  So that Edge traffic can be dynamically routed

  Background:
    Given the request has header "Authorization" set to "Bearer valid-admin-token"

  Scenario: List DNS zones
    When I send a GET request to "/api/v1/runway/zones"
    Then the response status code should be 200
    And the response body should contain a valid request_id

  Scenario: Full DNS record lifecycle
    When I create a DNS record with zoneId "zone-100", name "api.lolacloud.internal", type "A", content "192.168.1.100", ttl 300, and autoSync true
    Then the response status code should be 201
    And the created record should have name "api.lolacloud.internal"
    And the created record should have content "192.168.1.100"

    When I get the DNS record by created ID
    Then the response status code should be 200
    And the response body should contain record content "192.168.1.100"

    When I update the DNS record with new content "192.168.1.200" and proxied true
    Then the response status code should be 200
    And the response body should contain record content "192.168.1.200"

    When I toggle auto-sync to false on the DNS record
    Then the response status code should be 200
    And the response body auto-sync should be false

    When I delete the created DNS record
    Then the response status code should be 200

    When I get the DNS record by created ID
    Then the response status code should be 404
