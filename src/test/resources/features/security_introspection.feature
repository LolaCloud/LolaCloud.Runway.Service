Feature: LCI Security & Token Introspection
  As a Lola service
  I want to enforce inter-service token introspection via LCI
  So that only authorized operators with required permissions can access Runway APIs

  Scenario: Request without Authorization header is rejected with 401
    When I send a GET request to "/api/v1/runway/accounts"
    Then the response status code should be 401
    And the response body error code should be "UNAUTHORIZED"
    And the response body should contain a valid request_id

  Scenario: Request with invalid token is rejected with 401
    Given the request has header "Authorization" set to "Bearer invalid-token"
    When I send a GET request to "/api/v1/runway/accounts"
    Then the response status code should be 401
    And the response body error code should be "UNAUTHORIZED"

  Scenario: Request with token lacking permissions is rejected with 403
    Given the request has header "Authorization" set to "Bearer valid-no-perm-token"
    When I send a GET request to "/api/v1/runway/accounts"
    Then the response status code should be 403
    And the response body error code should be "FORBIDDEN"

  Scenario: Request with runway::* wildcard token is authorized
    Given the request has header "Authorization" set to "Bearer valid-admin-token"
    When I send a GET request to "/api/v1/runway/accounts"
    Then the response status code should be 200
    And the response body should contain a valid request_id

  Scenario: Request with specific runway::dns::manage token is authorized for DNS
    Given the request has header "Authorization" set to "Bearer valid-dns-token"
    When I send a GET request to "/api/v1/runway/records"
    Then the response status code should be 200
