Feature: View, edit and remove a book
  As a librarian I open a book, correct its details or remove it from the catalog.

  Rule: BE-AC6 – read one book

    @BE-AC6 @qa
    Scenario: An existing book is returned
      Given a book "book" exists
      When I fetch book "book"
      Then the response status is 200
      And the response is a Book matching the contract
      And the response book equals book "book"

    @BE-AC6 @qa
    Scenario: An unknown id returns 404 "Book not found"
      When I fetch book id "${uuid}"
      Then the response is a problem with status 404 and title "Book not found"

  Rule: BE-AC7 – replace a book (PUT)

    @BE-AC7 @qa
    Scenario: PUT replaces every editable field and keeps id, createdAt and availability
      Given these books exist:
        | name | genre   | publishedYear |
        | book | FICTION | 1999          |
      When I replace book "book" with a valid book with:
        | title         | "Replaced title"  |
        | author        | "Replaced Author" |
        | publishedYear | 2020              |
        | genre         | "HISTORY"         |
      Then the response status is 200
      And the response is a Book matching the contract
      And the response book replaces book "book" with the fields I sent
      And the response book is remembered as "updated"
      And book "updated" is unchanged

    @BE-AC7
    Scenario: PUT does not change availability
      Given a book "book" exists
      And book "book" is on loan
      When I replace book "book" with a valid book with:
        | isbn | "${isbn:book}" |
      Then the response status is 200
      And the response book has:
        | available | false |

    @BE-AC7
    Scenario: Keeping the same ISBN is not a duplicate
      Given a book "book" exists
      When I replace book "book" with a valid book with:
        | isbn  | "${isbn:book}"         |
        | title | "Same ISBN, new title" |
      Then the response status is 200
      And the response book has:
        | isbn  | "${isbn:book}"         |
        | title | "Same ISBN, new title" |

    @BE-AC7 @BE-AC4
    Scenario Outline: PUT applies the same validation as create: <case>
      Given a book "book" exists
      When I replace book "book" with a valid book with:
        | <field> | <value> |
      Then the response is a validation error for "<field>"
      And book "book" is unchanged

      Examples:
        | case                     | field         | value                |
        | blank title              | title         | "  "                 |
        | title of 201 characters  | title         | "${repeat:x:201}"    |
        | author of 121 characters | author        | "${repeat:x:121}"    |
        | ISBN with a bad checksum | isbn          | "${badChecksumIsbn}" |
        | ISBN as a JSON number    | isbn          | 9780306406157        |
        | author as a JSON boolean | author        | false                |
        | publishedYear next year  | publishedYear | ${nextYear}          |
        | publishedYear 1449       | publishedYear | 1449                 |
        | unknown genre            | genre         | "POETRY"             |

    @BE-AC7 @BE-AC4
    Scenario: PUT is a full replace: missing fields are validation errors
      Given a book "book" exists
      When I replace book "book" with the body:
        """
        { "title": "Only a title" }
        """
      Then the response is a validation error for "author, isbn, publishedYear, genre"

    @BE-AC7 @BE-AC5 @qa
    Scenario: Taking another book's ISBN returns 409 "Duplicate ISBN"
      Given a book "first" exists
      And a book "second" exists
      When I replace book "second" with a valid book with:
        | isbn | "${isbn:first}" |
      Then the response is a problem with status 409 and title "Duplicate ISBN"
      And book "second" is unchanged

    @BE-AC7 @BE-AC6
    Scenario: PUT on an unknown id returns 404 "Book not found"
      When I replace book id "${uuid}" with a valid book
      Then the response is a problem with status 404 and title "Book not found"

  Rule: BE-AC8 – delete a book

    @BE-AC8 @qa
    Scenario: DELETE returns 204 with no body, then the book is gone
      Given a book "book" exists
      When I delete book "book"
      Then the response status is 204
      And the response body is empty
      And book "book" is not found

    @BE-AC8
    Scenario: A deleted book disappears from the list
      Given a book "book" exists
      When I delete book "book"
      And I list books with "q=${title:book}"
      Then the page shows:
        | totalItems | 0 |

    @BE-AC8
    Scenario: Deleting twice returns 404 the second time
      Given a book "book" exists
      When I delete book "book"
      And I delete book "book"
      Then the response is a problem with status 404 and title "Book not found"

    @BE-AC8
    Scenario: The ISBN of a deleted book can be reused
      Given a book "first" exists
      When I delete book "first"
      And I create a valid book with the ISBN of book "first"
      Then the response status is 201
      And the response book is not book "first"

    @BE-AC8 @BE-AC6
    Scenario: DELETE on an unknown id returns 404 "Book not found"
      When I delete book id "${uuid}"
      Then the response is a problem with status 404 and title "Book not found"
