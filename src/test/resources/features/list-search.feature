Feature: Browse, search and filter the catalog
  As a librarian I list books page by page, sorted by title, and narrow the list by text, genre and availability.
  Every scenario creates its own books with a unique token in the title, so results never depend on other data.

  Rule: BE-AC1 – paging and sorting

    @BE-AC1 @qa
    Scenario: The list is a page envelope with default page 0 and size 20
      When I list books
      Then the response status is 200
      And the response is a BookPage matching the contract
      And the page has page 0 and size 20 and consistent totals

    @BE-AC1 @qa
    Scenario: Books are sorted by title, ignoring case
      Given these books exist:
        | title            |
        | banana ${token}  |
        | Apple ${token}   |
        | cherry ${token}  |
        | Avocado ${token} |
      When I list books with "q=${token}"
      Then the response is a BookPage matching the contract
      And the listed titles are:
        | Apple ${token}   |
        | Avocado ${token} |
        | banana ${token}  |
        | cherry ${token}  |

    @BE-AC1
    Scenario: The unfiltered first page is ordered by title, ignoring case
      When I list books with "size=100"
      Then the response status is 200
      And the listed titles are sorted ignoring case

    @BE-AC1 @qa
    Scenario: Pages through results with page and size
      Given these books exist:
        | title      |
        | a ${token} |
        | b ${token} |
        | c ${token} |
      When I list books with "q=${token}&size=2&page=0"
      Then the page shows:
        | page       | 0 |
        | size       | 2 |
        | totalItems | 3 |
        | totalPages | 2 |
      And the listed titles are:
        | a ${token} |
        | b ${token} |
      When I list books with "q=${token}&size=2&page=1"
      Then the page shows:
        | page | 1 |
      And the listed titles are:
        | c ${token} |
      When I list books with "q=${token}&size=2&page=5"
      Then the page has no items
      And the page shows:
        | totalItems | 3 |

    @BE-AC1
    Scenario: The maximum page size of 100 is accepted
      When I list books with "size=100"
      Then the response is a BookPage matching the contract
      And the page shows:
        | size | 100 |

    @BE-AC1
    Scenario Outline: Out-of-range paging "<query>" is rejected with 400
      When I list books with "<query>"
      Then the response is a problem with status 400

      Examples:
        | query     |
        | size=101  |
        | size=1000 |
        | size=0    |
        | size=-1   |
        | page=-1   |
        | size=abc  |
        | page=x    |

    @BE-AC1
    Scenario: A page whose row offset overflows a 32-bit int is a 400 on page, not a 500
      When I list books with "page=21474837&size=100"
      Then the response is a problem with status 400
      And the problem has an error for "page"

    @BE-AC1
    Scenario: The largest page whose offset fits is an empty page
      When I list books with "page=21474836&size=100"
      Then the response is a BookPage matching the contract
      And the page has no items

  Rule: BE-AC2 – search and filters

    @BE-AC2 @qa
    Scenario: q matches a case-insensitive substring of the title
      Given these books exist:
        | name | title                          |
        | hit  | The ${tokenUpper} Chronicles   |
      When I list books with "q=${tokenPart}"
      Then the response is a BookPage matching the contract
      And the list contains book "hit"

    @BE-AC2 @qa
    Scenario: q matches a case-insensitive substring of the author
      Given these books exist:
        | name | author            |
        | hit  | Ada ${token}son   |
        | miss | Somebody Else     |
      When I list books with "q=${tokenUpper}"
      Then the listed books are exactly "hit"

    @BE-AC2
    Scenario: q with no match returns an empty page
      When I list books with "q=${token}"
      Then the response is a BookPage matching the contract
      And the page has no items
      And the page shows:
        | totalItems | 0 |

    @BE-AC2 @qa
    Scenario: genre filters by the exact enum value
      Given these books exist:
        | name | title          | genre   |
        | sci  | Sci ${token}   | SCIENCE |
        | hist | Hist ${token}  | HISTORY |
      When I list books with "q=${token}&genre=SCIENCE"
      Then the listed books are exactly "sci"
      When I list books with "genre=SCIENCE&size=100"
      Then every listed book has genre SCIENCE

    @BE-AC2
    Scenario: A genre that is not in the enum is rejected
      When I list books with "genre=POETRY"
      Then the response is a problem with status 400

    @BE-AC2 @qa
    Scenario: available filters by availability
      Given these books exist:
        | name    | title           |
        | onShelf | On ${token}     |
        | out     | Out ${token}    |
      And book "out" is on loan
      When I list books with "q=${token}&available=true"
      Then the listed books are exactly "onShelf"
      When I list books with "q=${token}&available=false"
      Then the listed books are exactly "out"
      And every listed book has available false

    @BE-AC2 @qa
    Scenario: q, genre and available combine
      Given these books exist:
        | name      | title                  | author | genre      |
        | match     | Match ${token}         |        | TECHNOLOGY |
        | wrongKind | Wrong genre ${token}   |        | CHILDREN   |
        | loaned    | Loaned ${token}        |        | TECHNOLOGY |
        | unrelated | Unrelated title        | Nobody | TECHNOLOGY |
      And book "loaned" is on loan
      When I list books with "q=${token}&genre=TECHNOLOGY&available=true"
      Then the listed books are exactly "match"
      And the page shows:
        | totalItems | 1 |
