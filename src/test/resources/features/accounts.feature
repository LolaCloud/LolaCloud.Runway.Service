Feature: Cloudflare Account Management
  As an operator
  I want to validate, register, and manage Cloudflare accounts
  So that Runway can orchestrate DNS records and tunnels

  Background:
    Given the request has header "Authorization" set to "Bearer valid-admin-token"

  Scenario: Validate valid Cloudflare credentials
    When I validate credentials with email "admin@example.com" and apiKey "0123456789abcdef0123456789"
    Then the response status code should be 200
    And the validation result should be valid

  Scenario: Validate invalid Cloudflare credentials
    When I validate credentials with email "invalid@example.com" and apiKey "invalid-key-short"
    Then the response status code should be 200
    And the validation result should be invalid

  Scenario: Connect new Cloudflare account and list it
    When I connect a Cloudflare account with name "Test CF", email "operator@lolacloud.internal", and apiKey "abcdef0123456789abcdef01"
    Then the response status code should be 201
    And the response body should contain account name "Test CF"
    And the response body should contain maskedApiKey
    When I send a GET request to "/api/v1/runway/accounts"
    Then the response status code should be 200
    And the accounts list should contain email "operator@lolacloud.internal"
