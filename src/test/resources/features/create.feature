Feature: Add a book
  As a librarian I add books to the catalog. The API validates every field and rejects duplicate ISBNs.
  In the tables, a value is a JSON literal: "text", 1999, true. (omitted) leaves the field out.

  Rule: BE-AC3 – a valid book is created

    @BE-AC3 @qa
    Scenario: Creating a book returns 201, a Location header and the book, available by default
      When I create a valid book with:
        | genre | "NON_FICTION" |
      Then the response status is 201
      And the response is a Book matching the contract
      And the response book has the fields I sent and is available
      And the Location header points to the created book
      And fetching the response book returns the same book

    @BE-AC3 @BE-AC9
    Scenario: A client cannot set the availability flag on create
      When I create a valid book with:
        | available | false |
      Then the response is a problem with status 400

    @BE-AC3
    Scenario Outline: Every genre in the contract is accepted: <genre>
      When I create a valid book with:
        | genre | "<genre>" |
      Then the response status is 201
      And the response book has:
        | genre | "<genre>" |

      Examples:
        | genre       |
        | FICTION     |
        | NON_FICTION |
        | SCIENCE     |
        | HISTORY     |
        | TECHNOLOGY  |
        | CHILDREN    |

    @BE-AC3
    Scenario Outline: Boundary values are accepted: <case>
      When I create a valid book with:
        | <field> | <value> |
      Then the response status is 201
      And the response is a Book matching the contract
      And the response book has the fields I sent and is available

      Examples:
        | case                         | field         | value              |
        | title of 1 character         | title         | "X"                |
        | title of 200 characters      | title         | "${repeat:T:200}"  |
        | author of 1 character        | author        | "A"                |
        | author of 120 characters     | author        | "${repeat:A:120}"  |
        | publishedYear 1450           | publishedYear | 1450               |
        | publishedYear = current year | publishedYear | ${currentYear}     |
        | ISBN with check digit 0      | isbn          | "${isbnEndingWith0}" |

  Rule: BE-AC3, BE-AC4 – invalid input is rejected with 400 and a message per field

    @BE-AC3 @BE-AC4
    Scenario Outline: <case> is rejected with an error on <field>
      When I create a valid book with:
        | <field> | <value> |
      Then the response is a validation error for "<field>"

      Examples:
        | case                              | field         | value                  |
        | empty title                       | title         | ""                     |
        | blank title                       | title         | "   "                  |
        | title of 201 characters           | title         | "${repeat:T:201}"      |
        | empty author                      | author        | ""                     |
        | blank author                      | author        | " \t "                 |
        | author of 121 characters          | author        | "${repeat:A:121}"      |
        | ISBN with a bad checksum          | isbn          | "${badChecksumIsbn}"   |
        | ISBN of 12 digits                 | isbn          | "${isbn12}"            |
        | ISBN of 14 digits                 | isbn          | "${isbn14}"            |
        | ISBN with hyphens                 | isbn          | "978-0-306-40615-7"    |
        | ISBN with letters                 | isbn          | "978030640615X"        |
        | ISBN-10                           | isbn          | "0306406152"           |
        | empty ISBN                        | isbn          | ""                     |
        | ISBN as a JSON number             | isbn          | ${isbn}                |
        | title as a JSON number            | title         | 123                    |
        | title as a JSON float             | title         | 1.5                    |
        | author as a JSON boolean          | author        | true                   |
        | publishedYear 1449                | publishedYear | 1449                   |
        | publishedYear next year           | publishedYear | ${nextYear}            |
        | publishedYear as a numeric string | publishedYear | "1999"                 |
        | publishedYear with a fraction     | publishedYear | 1999.7                 |
        | genre not in the enum             | genre         | "POETRY"               |
        | genre in lower case               | genre         | "fiction"              |

    @BE-AC3 @BE-AC4
    Scenario Outline: A body without <field> is rejected
      When I create a valid book without "<field>"
      Then the response is a validation error for "<field>"

      Examples:
        | field         |
        | title         |
        | author        |
        | isbn          |
        | publishedYear |
        | genre         |

    @BE-AC4 @qa
    Scenario: Every invalid field is reported at once
      When I create a book with the body:
        """
        { "title": "", "author": "", "isbn": "123", "publishedYear": 1000, "genre": "FICTION" }
        """
      Then the response is a validation error for "title, author, isbn, publishedYear"
      And the validation errors do not include "genre"

    @BE-AC4
    Scenario: An empty JSON object reports all required fields
      When I create a book with the body:
        """
        {}
        """
      Then the response is a validation error for "title, author, isbn, publishedYear, genre"

    @BE-AC4
    Scenario: A rejected book is not stored
      When I create a valid book with:
        | publishedYear | ${nextYear} |
      Then the response is a validation error for "publishedYear"
      When I list books with "q=${sent:title}"
      Then the page shows:
        | totalItems | 0 |

  Rule: BE-AC5 – ISBNs are unique

    @BE-AC5 @qa
    Scenario: A duplicate ISBN returns 409 "Duplicate ISBN" and leaves the original untouched
      Given a book "existing" exists
      When I create a valid book with the ISBN of book "existing"
      Then the response is a problem with status 409 and title "Duplicate ISBN"
      And book "existing" is unchanged
