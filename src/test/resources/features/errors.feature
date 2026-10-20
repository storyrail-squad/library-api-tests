Feature: Errors are safe and predictable
  Every error is an RFC 7807 problem (application/problem+json) with the right status, and never reveals
  stack traces, exception or framework class names. Each problem assertion also checks for leaked internals.

  Rule: BE-AC6 – malformed ids are a 404, never a 500

    @BE-AC6
    Scenario Outline: GET with the malformed id <id> returns 404
      When I fetch book id "<id>"
      Then the response is a problem with status 404 and title "Book not found"

      Examples:
        | id                                   |
        | not-a-uuid                           |
        | 12345                                |
        | zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz |
        | 1' OR '1'='1                         |

    @BE-AC6
    Scenario: PUT with a malformed id returns 404
      When I replace book id "not-a-uuid" with a valid book
      Then the response is a problem with status 404 and title "Book not found"

    @BE-AC6
    Scenario: PATCH availability with a malformed id returns 404
      When I set the availability of book id "not-a-uuid" to false
      Then the response is a problem with status 404 and title "Book not found"

    @BE-AC6
    Scenario: DELETE with a malformed id returns 404
      When I delete book id "not-a-uuid"
      Then the response is a problem with status 404 and title "Book not found"

  Rule: BE-AC9 – unknown fields and malformed bodies are a clean 400

    @BE-AC9 @qa
    Scenario: POST with a field that is not in the contract is rejected
      When I create a valid book with:
        | subtitle | "not in the contract" |
      Then the response is a problem with status 400

    @BE-AC9
    Scenario: POST with a client-supplied id is rejected
      When I create a valid book with:
        | id | "00000000-0000-0000-0000-000000000001" |
      Then the response is a problem with status 400

    @BE-AC9
    Scenario: PUT with an unknown field is rejected and changes nothing
      Given a book "book" exists
      When I replace book "book" with a valid book with:
        | rating | 5 |
      Then the response is a problem with status 400
      And book "book" is unchanged

    @BE-AC9 @qa
    Scenario: Malformed JSON returns a clean 400
      When I create a book with the body:
        """
        {"title": "Broken", 
        """
      Then the response is a problem with status 400

    @BE-AC9
    Scenario: A wrong JSON type returns a clean 400
      When I create a valid book with:
        | publishedYear | "nineteen-ninety" |
      Then the response is a problem with status 400

    @BE-AC9
    Scenario: A JSON array body returns a clean 400
      When I create a book with the body:
        """
        [{ "title": "In an array", "author": "A", "isbn": "${isbn}", "publishedYear": 2001, "genre": "FICTION" }]
        """
      Then the response is a problem with status 400

  Rule: BE-AC9 – no internals in any error response

    @BE-AC9 @qa
    Scenario Outline: The <status> error for <request> is a clean problem
      When I send GET "<request>"
      Then the response is a problem with status <status>
      And the response does not leak internals

      Examples:
        | request                         | status |
        | /api/books?size=101             | 400    |
        | /api/books?page=NaN             | 400    |
        | /api/books?genre=java.lang.Object | 400  |

    @BE-AC9
    Scenario: A 409 duplicate is a clean problem
      Given a book "existing" exists
      When I create a valid book with the ISBN of book "existing"
      Then the response is a problem with status 409 and title "Duplicate ISBN"
      And the response does not leak internals

    @BE-AC9 @BE-AC4
    Scenario: Validation messages do not leak internals
      When I create a book with the body:
        """
        { "title": "", "author": "", "isbn": "1", "publishedYear": 1, "genre": "FICTION" }
        """
      Then the response is a validation error for "title, author, isbn, publishedYear"
      And the response does not leak internals
