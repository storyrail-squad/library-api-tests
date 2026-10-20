Feature: Service health
  The API is alive and serves a seeded catalog.

  @BE-health @qa
  Scenario: The health endpoint reports UP
    When I send GET "/actuator/health"
    Then the response status is 200
    And the JSON field "status" is "UP"

  @BE-AC1 @qa
  Scenario: The catalog is seeded with at least one book
    When I list books
    Then the response status is 200
    And the page has at least 1 item
