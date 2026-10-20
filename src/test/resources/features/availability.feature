Feature: Loan and return a book
  As a librarian I mark a book as on loan and back on the shelf. Nothing else about the book changes.

  @BE-AC7 @qa
  Scenario: Availability toggles false, then true, and nothing else changes
    Given a book "book" exists
    When I set the availability of book "book" to false
    Then the response status is 200
    And the response is a Book matching the contract
    And the response book equals book "book" with available false
    And the response book is remembered as "loaned"
    And book "loaned" is unchanged
    When I set the availability of book "book" to true
    Then the response book equals book "book"

  @BE-AC7
  Scenario: Setting the same value again is idempotent
    Given a book "book" exists
    When I set the availability of book "book" to true
    Then the response status is 200
    And the response book equals book "book"

  @BE-AC7 @BE-AC9
  Scenario Outline: A bad availability body (<case>) is rejected and nothing changes
    Given a book "book" exists
    When I send the availability body '<body>' for book "book"
    Then the response is a problem with status 400
    And book "book" is unchanged

    Examples:
      | case                     | body                                           |
      | an empty object          | {}                                             |
      | available: null          | {"available": null}                            |
      | a non-boolean string     | {"available": "maybe"}                         |
      | a number                 | {"available": 2}                               |
      | an extra editable field  | {"available": false, "title": "Sneaky rename"} |

  @BE-AC7 @BE-AC6
  Scenario: An unknown id returns 404 "Book not found"
    When I set the availability of book id "${uuid}" to false
    Then the response is a problem with status 404 and title "Book not found"
