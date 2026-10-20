# Library Catalog – Books API acceptance tests (`library-api-tests`)

Black-box acceptance tests for the Books API in **Cucumber 7 + Java 21 + RestAssured**. One suite, two runs:

| Run | Against | When | Scenarios |
|---|---|---|---|
| **Isolated** | The backend jar, started on the CI runner (`http://localhost:8080`) | Every PR and push here, and every PR and push in [library-backend](https://github.com/storyrail-squad/library-backend) | All (`not @wip`) |
| **QA** | The deployed QA API | After every backend deploy to QA, and on every push to `main` here | `@qa` only |

The API under test is [library-backend](https://github.com/storyrail-squad/library-backend). Stories and bugs live in
[squad-backlog](https://github.com/storyrail-squad/squad-backlog), and [StoryRail](https://github.com/storyrail-squad/storyrail)
writes and runs these tests as part of its rail.

## Run locally

```bash
# isolated: start the backend first (in library-backend: mvn spring-boot:run)
mvn verify

# against QA (or any URL), only the QA-safe scenarios
mvn verify -Dapi.baseUrl=https://library-api-xxxx.onrender.com -Dcucumber.filter.tags="@qa"

# one acceptance criterion
mvn verify -Dcucumber.filter.tags="@BE-AC5"
```

Reports: `target/cucumber-report.html` (open in a browser), `target/cucumber-junit.xml`, `target/cucumber.json`.

## How it is built

| Path | What |
|---|---|
| `src/test/resources/features/*.feature` | The scenarios, in Gherkin, one feature per area of the API |
| `src/test/java/…/steps/` | Step definitions: `BookSteps` (set up and call the API), `ResponseSteps` (assertions), `Hooks` (cleanup) |
| `src/test/java/…/support/` | `Api` (RestAssured client), `Contract` (validates every body against the OpenAPI schemas), `TestData`, `Placeholders`, `ScenarioContext` |
| `contract/openapi.yaml` | Pinned copy of the backend's contract. CI fails while it differs from the backend's (`contract` job) |
| `.github/workflows/run-suite.yml` | Reusable workflow: isolated or QA run. library-backend calls it too |
| `.github/workflows/ci.yml` | Builds the backend (same-named branch if it exists, else `main`), checks the contract, runs the isolated suite; on `main` also the QA run if `QA_API_URL` is set |

**Writing scenarios.**
- Every scenario is tagged with the acceptance criterion it proves (`@BE-AC3`). Add `@qa` when it is safe and useful against QA, `@wip` to keep it out of every run.
- In tables, a value is a JSON literal (`"text"`, `1999`, `true`); `(omitted)` leaves the field out.
- Placeholders give fresh data: `${token}` (this scenario's unique token), `${isbn}`, `${badChecksumIsbn}`, `${currentYear}`, `${nextYear}`, `${uuid}`, `${repeat:T:200}`, `${id:name}` / `${isbn:name}` / `${title:name}` (a book created earlier in the scenario), `${sent:field}` (the last body sent).

**Safe against QA.**
- Each scenario creates its own books, with a unique token in the title, and the `@After` hook deletes them.
- The only assumption about existing data is that the catalog has at least one book.

**Errors.** Every problem response is checked for status, `application/problem+json`, the Problem schema, the `title`
where the contract names one, and no leaked internals (stack traces, exception or framework class names).

## Settings (repository variables)

| Variable | Used for | Default |
|---|---|---|
| `QA_API_URL` | The QA run on `main` (e.g. the Render URL) | not set → QA run skipped |
| `BACKEND_REPO` | Where the backend under test is built from | `storyrail-squad/library-backend` |

## Traceability

| AC | What is proven | Feature |
|---|---|---|
| BE-AC1 | Page envelope and BookPage schema; defaults page 0 / size 20; case-insensitive title sort; paging maths; size 100 ok; size 101/1000/0/-1, page -1, non-numeric → 400; offset overflow → 400 on `page`; seed has ≥ 1 book | `list-search`, `health` |
| BE-AC2 | `q` substring of title and author, case-insensitive; no match → empty page; `genre` exact enum; unknown genre → 400; `available` true/false; all three combined | `list-search` |
| BE-AC3 | 201 + `Location` + Book schema + `available: true`; every genre; boundaries (title 1/200, author 1/120, year 1450/current, check digit 0); rejects bad title, author, ISBN, year, genre and each missing field | `create` |
| BE-AC4 | 400 `Validation failed` with a message per field; several at once; `{}` lists all five; rejected book not stored | `create`, `read-update-delete` |
| BE-AC5 | Duplicate ISBN on POST and PUT → 409 `Duplicate ISBN`; original untouched; keeping your own ISBN on PUT is fine | `create`, `read-update-delete` |
| BE-AC6 | Unknown UUID → 404 `Book not found` on every verb; malformed ids → 404, not 500 | `read-update-delete`, `availability`, `errors` |
| BE-AC7 | PUT replaces editable fields, keeps id/createdAt/available, same validation, missing field → 400; PATCH changes only `available`, idempotent; bad PATCH bodies → 400 and nothing changes | `read-update-delete`, `availability` |
| BE-AC8 | DELETE → 204 empty body, then 404; gone from the list; second DELETE 404; ISBN reusable | `read-update-delete` |
| BE-AC9 | Unknown fields (incl. `id`, `available`) → 400; malformed JSON, wrong type, array body → 400; no internals in any 400/404/409 body | `errors`, `availability`, `create` |
| health | `GET /actuator/health` → 200 `{"status":"UP"}` | `health` |
