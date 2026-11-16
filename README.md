# API Test Framework (real backend)

Data-driven REST API tests for the **Booking API** (REST Assured + TestNG + Maven). The scenarios run against a
**real backend**: a server that is already running and answers the requests, for example the local `booking-api`
Spring Boot project on `http://localhost:8080`.

An earlier version of this framework could also run against a Prism mock server. That code (the `env` switch, two
expected status codes per scenario, the `prefer` header helpers) has been removed, so every scenario now has **one**
expected status code and one validation policy.

## Contents

1. [What you need](#1-what-you-need)
2. [Quick start](#2-quick-start)
3. [What the backend must provide](#3-what-the-backend-must-provide)
4. [Configuration](#4-configuration)
5. [Project layout](#5-project-layout)
6. [Tests and data providers by resource](#6-tests-and-data-providers-by-resource)
7. [Call flow: which method calls which](#7-call-flow-which-method-calls-which)
8. [How to add a scenario](#8-how-to-add-a-scenario)
9. [Choosing the validation policy](#9-choosing-the-validation-policy)
10. [Reports](#10-reports)
11. [Hiding secrets in the reports](#11-hiding-secrets-in-the-reports)
12. [Known limitations](#12-known-limitations)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. What you need

| Tool | Version | Why |
|---|---|---|
| JDK | 17 | the project compiles for Java 17 |
| Maven | 3.x | build and `mvn test` |
| A running backend | the Booking API | the server under test (see section 3) |

Main libraries (all managed by `pom.xml`): TestNG 7.12.0, REST Assured 5.5.6 (with JSON schema validator),
Jackson 2.18.0, Allure 2.24.0 (it brings FreeMarker, which draws the report attachments).

In Eclipse: **File > Import > Existing Maven Projects**, then select this folder.

## 2. Quick start

**Step 1. Start the backend** and leave it running (for the local project: `mvn spring-boot:run` in the `booking-api`
folder, or run `BookingApiApplication` from Eclipse). Check it: `http://localhost:8080/ping` must answer 200.

**Step 2. Check the settings** in `src/test/resources/config.properties`: `baseUri` and the accounts (section 4).

**Step 3. Run the tests** from the project root, in a second window:

```
mvn clean test
```

`mvn clean` also empties `allure-results`, so every run starts with a fresh report. To point one run at another
server without editing a file: `mvn clean test -DbaseUri=http://localhost:8081`.

**Step 4. Read the result.** The console shows each scenario (`Executing Scenario: ...`) and its outcome.
Open the Allure report as described in [Reports](#10-reports).

The suite first calls `GET /ping` (`BaseTest.setupBaseSpec()`); if it is not 200 every test stops with
`CRITICAL: Server is DOWN!`.

## 3. What the backend must provide

The scenarios assume the following. If your backend differs, the scenarios that depend on it fail, which is useful
information, not necessarily a framework bug.

| Need | Used by |
|---|---|
| `GET /ping` answers 200 | the health check in `BaseTest` |
| `POST /auth/login` returns a bearer token for a valid user | `AuthUtils`, every Bearer scenario |
| The accounts of `config.properties` exist: a full-permission user, and users that are denied create, update or delete | scenarios 1B and the happy paths |
| Errors are answered with the contract's Problem Details body (`type`, `title`, `status`, `detail`, `instance`) | the schema validation of the error scenarios |
| A request body above the backend's size limit is answered with 413 (before security and routing) | scenario 0.1: the payload must be bigger than that limit |
| A booking is a duplicate when firstname, lastname, checkin and checkout are all equal to an existing booking, and **any** existing booking counts | the stateful duplicate test (5.2) |
| Bookings stay until the backend restarts | the happy paths and the duplicate test create bookings |

Because the duplicate rule counts every existing booking, the happy-path scenarios use a **unique lastname** per row
(`uniqueValidBookingPayloadPOJO()` in `PostBookingDataProvider`) and the duplicate test uses a lastname with a timestamp.

## 4. Configuration

All settings live in `src/test/resources/config.properties`. A value can be overridden for one run with
`-Dkey=value`. Precedence: **`-D` option > `config.properties`**. There are **no built-in defaults** for these
keys: a missing value stops the run at once with a message that names the key.

| Key | Meaning |
|---|---|
| `baseUri` | Base URL of the server under test |
| `auth.validUser`, `auth.validPassword` | The full-permission account used by every scenario that is **not** about authentication or authorization |
| `auth.role.<roleName>.username` / `.password` | One account per role. The role name is a lookup key, for example `noCreateBookingPermissionRole` |

Notes:

- The comments inside `config.properties` explain the role naming rules. Read them before adding a role.
- Tokens are cached **per username**. Two role keys that use the same username share one token.
- The request timeout (3 s) is not a setting: it is a constant in `RequestSpecFactory`.
- Do not commit real credentials of a shared or production system.

## 5. Project layout

```
pom.xml                    build, dependencies, test run configuration (runs testng.xml)
testng.xml                 which test classes run by default
src/main/java/com/api/
  client/APIClient.java        sends the HTTP request (REST Assured)
  pojo/                        booking payload classes (BookingPayloadPOJO, BookingDatesPOJO, BookingFields)
  utils/
    ConfigUtils.java           reads config.properties and -D options; every value is required (fails fast)
    RequestSpecFactory.java    builds the REST Assured request specs: base URI and the 3 s timeouts live here;
                               one spec for the test requests, one for the login request
    AuthUtils.java             logs in and caches tokens (thread-safe; refreshed before a JWT expires)
    HeaderFactory.java         builders for request headers (valid and deliberately broken); HeaderUtils_MethodList.txt
                               is the "which method for which scenario" list
    PayloadFactory.java        valid payload and deliberately broken payloads
src/test/java/com/api/
  base/BaseTest.java           one-time setup: builds reqSpec_base (with the Allure filter and the masking
                               template), server health check, request helper
  dataProvider/booking/        the Booking scenarios (PostBookingDataProvider, PutBookingDataProvider)
  dataProvider/login/          the Login scenarios (LoginNegativeDataProvider)
  dto/TestScenarioRecord.java  one scenario: description, endpoint, method, headers, payload, expected;
                               its toString() hides the Authorization header in the reports
  model/ExpectedStatusCode.java        the expected status code plus the validation policy of a scenario
  validation/                  ResponseValidator (status code, headers, body, schema), the validation policy,
                               SensitiveInfoLeakValidator (the response must not leak internal details)
  tests/booking/               BookingNegativeTests, BookingPositiveTests, BookingWorkflowTests
  tests/login/                 LoginNegativeTests
  constants/SchemaPathConstants.java   paths of the JSON schemas
src/test/resources/
  config.properties            settings
  schemas/                     JSON schemas for responses, and the malformed-JSON payload file
  tpl/http-request-masked.ftl  template for the Allure "Request" attachment: hides the Authorization header,
                               cuts very long bodies, leaves out the Curl block
```

## 6. Tests and data providers by resource

Tests are organised **by resource** (Booking, Login). For each resource there are data-driven tests, whose
scenarios are rows in a data provider, and, separately, workflow tests.

| Resource and operation | Kind | Data provider: class, method (provider name), size | Test: class, method | In default suite |
|---|---|---|---|---|
| Booking: `POST /booking` | negative | `PostBookingDataProvider.getPostBookingNegativeTestData()` (`negativeBookingTestDataProvider-POST`), 23 scenarios | `BookingNegativeTests.testPostBookingNegativeScenarios` | yes |
| Booking: `POST /booking` | negative, stateful | none (a precondition is needed) | `BookingNegativeTests.testPostDuplicateBookingIsRejected` (scenario 5.2) | yes |
| Booking: `POST /booking` | positive | `PostBookingDataProvider.getPostBookingPositiveTestData()` (`positiveBookingTestDataProvider-POST`), 2 scenarios | `BookingPositiveTests.testPostBookingHappyPathScenario` | yes |
| Booking: `PUT /booking/{id}` | negative | `PutBookingDataProvider.getPutBookingNegativeTestData()` (`negativeBookingTestDataProvider-PUT`), 12 scenarios | `BookingNegativeTests.testPutBookingNegativeScenarios` | yes |
| Booking: create, read, update, delete (a chain of calls) | workflow | none | `BookingWorkflowTests` (see below) | no |
| Login: `POST /auth/login` | negative | `LoginNegativeDataProvider.getInvalidLoginScenarios()` (`invalidLoginCredentialsProvider`), 3 rows | `LoginNegativeTests.shouldRejectLogin_withInvalidCredentials` | yes |

Notes:

- A data provider is named after the operation it mostly covers, but a record decides its own endpoint and
  method. `PostBookingDataProvider` also holds a few scenarios that are not `POST /booking`, for example
  `GET /booking/7777777` (record not found) and `GET /users` (missing query parameter).
- `LoginNegativeTests` does not use `TestScenarioRecord`: its data provider returns plain values
  (description, username, password) and the test always expects 401.
- A data provider must not contain an empty row (`{}`): TestNG would call the test method with no argument and fail
  with `Data provider mismatch` (see [Troubleshooting](#13-troubleshooting)).
- To run a class that is not in the default suite, add a `<class>` entry in `testng.xml`.

### Why the duplicate scenario (5.2) is a test and not a row

A duplicate means "this booking already exists". A data row sends one request, so it cannot create the booking first.
`testPostDuplicateBookingIsRejected` does it in two steps inside one test: it creates a booking with a unique
lastname (expects 200), then sends the **same** payload again (expects 409). It does not depend on any other test
having run before it.

### Workflow tests are kept separate

A **data-driven scenario** sends one request and checks that one response: status code, headers, body, schema
(`ResponseValidator`). A **workflow test** is a sequence of calls where the output of one call is the input of
the next. For example, the `bookingid` returned by `POST /booking` is used in `GET`, `PUT`, `PATCH` and
`DELETE /booking/{bookingid}`, and the test checks that the server-side state stays consistent along the chain
(for example, `GET` returns 404 after a `DELETE`). No single response can answer that question, so workflow tests:

- live in their own class (`BookingWorkflowTests`), grouped **by resource** (a future Users resource would get
  `UserWorkflowTests`),
- do **not** use a data provider, `TestScenarioRecord`, `ExpectedStatusCode` or `ResponseValidator`,
- call small helper methods (`createBooking`, `replaceBooking`, `patchBooking`, `getBooking`, `deleteBooking`,
  `extractBookingId`) and assert with TestNG `Assert` and REST Assured `.then()`,
- are **not part of the default suite yet**: one test is `enabled = true`, the others are `enabled = false` until each
  has been checked against the backend.

To run them: add the class to `testng.xml` and run `mvn clean test`.

Note on numbering: the "Layer 5.x" labels in their descriptions are their own numbering. They are unrelated to the
scenario layers (0, 1A, 1B, 2A-2D, 3, 4, 5) listed in the **Layers** table in
[section 8, "How to add a scenario"](#8-how-to-add-a-scenario).

## 7. Call flow: which method calls which

### A data-driven scenario, step by step

```
testng.xml
  -> BookingNegativeTests / BookingPositiveTests
       (1) BaseTest.setupBaseSpec()                         @BeforeClass: reqSpec_base (RequestSpecFactory + Allure filter), GET /ping
       (2) data provider (e.g. PostBookingDataProvider.getPostBookingNegativeTestData())
             builds one TestScenarioRecord per scenario, calling:
               HeaderFactory.<builder>()  ->  AuthUtils.login()  ->  AuthUtils.callLoginEndpointRaw()
               PayloadFactory.getValidBookingPayloadPOJO() / PayloadFactory.<BROKEN>_PAYLOAD.get()
               ExpectedStatusCode.expect(status, policy)
       (3) test method (e.g. BookingNegativeTests.testPostBookingNegativeScenarios(record))
             -> BaseTest.sendRequestWithTimeoutHandling(...)  ->  APIClient.sendRequest(...)
             -> ResponseValidator.validateAll(response, record)
```

1. **`BaseTest.setupBaseSpec()`** (`@BeforeClass`) builds `reqSpec_base` from `RequestSpecFactory.baseRequestSpecBuilder()`
   (base URI from `ConfigUtils.getBaseUri()`, 3 s timeouts, "log the request and the response if a validation fails"),
   adds the Allure filter with the masking template ([section 11](#11-hiding-secrets-in-the-reports)), sends `GET /ping`
   and asserts 200 (`CRITICAL: Server is DOWN!` otherwise).
2. **The data provider** named in the test's `@Test(dataProvider = ...)` builds the rows. Each row is a
   `TestScenarioRecord`, created with:
   - a header method from `HeaderFactory`. A Bearer method calls the private `HeaderFactory.bearerAuthHeaders(user, password)`,
     which calls `AuthUtils.login(user, password)`. On the first call for a user this reaches
     `AuthUtils.callLoginEndpointRaw()` (`POST /auth/login`); later calls reuse the cached token,
   - a payload from `PayloadFactory` (for example `getValidBookingPayloadPOJO()` or `INVALID_DATA_TYPE_PAYLOAD.get()`),
   - an expected result from `ExpectedStatusCode.expect(status, policy)`, where the policy is
     built by `SchemaAndAllowHeaderValidationPolicy.validateSchemaOnly(...)`, `skipSchemaAndAllowHeaderValidation(...)` or
     `validateAllowHeaderOnly(...)`.

   TestNG may evaluate a data provider **before** `@BeforeClass` has run. That is why `AuthUtils` does not use
   `reqSpec_base`: it builds its own spec with `RequestSpecFactory.loginRequestSpecification()` (the same base URI and
   timeouts, but no Allure filter). A login failure inside a header builder therefore shows up as a data-provider
   error, not as a failed scenario.
3. **The test method** receives one record and calls `BaseTest.sendRequestWithTimeoutHandling(reqSpec_base, endpoint,
   method, headers, payload, description)`. That calls `APIClient.sendRequest(...)` (REST Assured). A timeout or any other
   exception while the request is sent fails the scenario with a message that names it.
4. **`ResponseValidator.validateAll(response, record)`** checks the response in this order:

| Step | Method | Assertion | What it does |
|---|---|---|---|
| a | `validateStatusCode` | hard | compares the status code with `ExpectedStatusCode.status` |
| b | `validateHeaders` | hard | `Content-Type` is JSON or problem+json; `SensitiveInfoLeakValidator.assertNoSensitiveHeadersPresent`; if the policy asks for it, the `Allow` header of a 405 is present |
| c | `validateBody` | soft | body checks, including `SensitiveInfoLeakValidator.assertNoSensitiveKeysInResponseBody` |
| d | `validateSchema` | soft | JSON schema check, if the policy asks for it |
| e | `softAssert.assertAll()` | | reports every soft failure together |

`BookingPositiveTests.testPostBookingHappyPathScenario` follows the same path, but calls `validateStatusCode`,
`validateHeaders`, `validateBody` and `validateSchema` one by one instead of `validateAll`.

### A workflow test

```
BookingWorkflowTests.<test>()                          (extends BaseTest, so step (1) above applies)
  -> createBooking(payload)   -> mutateBooking("POST", "/booking", payload)
        -> APIClient.sendRequest(reqSpec_base, endpoint, method, HeaderFactory.bearerAuthHeadersForFullPermissionUser(), payload)
  -> extractBookingId(response)                         reads "bookingid" from the response
  -> getBooking(id) / replaceBooking(id, payload) / patchBooking(id, payload) / deleteBooking(id)
        -> APIClient.sendRequest(...)                   same call, other method and endpoint /booking/{id}
  -> Assert.assertEquals(...) / .then().body(...)       compares the responses of different calls
```

### A login test

`LoginNegativeDataProvider.getInvalidLoginScenarios()` supplies (description, username, password).
`LoginNegativeTests.shouldRejectLogin_withInvalidCredentials` calls `AuthUtils.callLoginEndpointRaw(username, password)`
and asserts 401.

### Who calls the shared helpers

| Helper | Called by |
|---|---|
| `ConfigUtils.getBaseUri()` | `BaseTest` (the `BASE_URI` constant), `RequestSpecFactory` (both methods) |
| `RequestSpecFactory.baseRequestSpecBuilder()` | `BaseTest.setupBaseSpec()` only |
| `RequestSpecFactory.loginRequestSpecification()` | `AuthUtils.callLoginEndpointRaw()` only |
| `AuthUtils.login()` | `HeaderFactory.bearerAuthHeaders()` only |
| `AuthUtils.callLoginEndpointRaw()` | `AuthUtils.login()`, `LoginNegativeTests` |
| `APIClient.sendRequest()` | `BaseTest.sendRequestWithTimeoutHandling()`, the helper methods of `BookingWorkflowTests` |

## 8. How to add a scenario

### Add a negative scenario

1. Open the data provider: `PostBookingDataProvider` (POST /booking) or `PutBookingDataProvider` (PUT).
2. Copy the record that looks most like yours and change the six fields:

| Field | What to put |
|---|---|
| description | Number plus name, for example `"3.6. Empty first name-Parsing Layer"`. Use the layer number (table below) |
| endpoint, method | For example `"/booking"`, `"POST"` |
| headers | A method from `HeaderFactory` (see the table below) |
| payload | `getValidBookingPayloadPOJO()` or a broken one from `PayloadFactory` (add a new one there if needed) |
| expected | `expect(status, policy)` |

3. Choose the **policy** (what to check besides the status code), see [section 9](#9-choosing-the-validation-policy).
4. Run the suite. If the backend answers with another status code, decide which one is wrong: the scenario or the
   backend.

A scenario that needs a **precondition** (something must exist first, like a booking that is then duplicated) does
not fit a data row. Write a stateful test like `BookingNegativeTests.testPostDuplicateBookingIsRejected`.

### Layers (used in the scenario numbers)

| Layer | What it tests | Status code |
|---|---|---|
| 0 | Infrastructure: request size limit (gateway) | 413 |
| 1A | Authentication: missing or invalid credentials | 401 |
| 1B | Authorization: valid user without permission | 403 |
| 2A | Routing, path resolution: unknown path, missing path parameter | 404 |
| 2B | Routing, method resolution: the path exists, the method does not | 405 |
| 2C | Request media type (`Content-Type`) | 415 |
| 2D | Content negotiation (`Accept`) | 406 |
| 3 | Parsing: malformed body, missing body, wrong types, unknown fields, missing query parameter | 400 |
| 4 | Constraint validation: missing field, range, format, cross-field | 422 |
| 5 | Controller/service: record not found, duplicate | 404, 409 |

### Which header method for which scenario

Every method is in `HeaderFactory`. Names start with the authentication scheme, so IDE auto-complete groups them.
The same list is in `HeaderUtils_MethodList.txt`.

| Scenario | Method |
|---|---|
| Happy path | `bearerAuthHeadersForFullPermissionUser()` or `basicAuthHeadersForFullPermissionUser()` |
| No Authorization header (401) | `noAuthenticationHeaders()` |
| Forged Bearer token (401) | `bearerAuthHeadersWithInvalidToken()` |
| Wrong Basic credentials (401) | `basicAuthHeadersWithInvalidCredentials()` |
| Malformed Basic header (401) | `basicAuthHeadersWithoutBase64Encoding()` or `basicAuthHeadersWithoutColon()` |
| Role without permission (403) | `bearerAuthHeadersForRole(roleName)` or `basicAuthHeadersForRole(roleName)` |
| Wrong `Content-Type` (415) | `xmlContentTypeHeaders()` (the payload must be an XML **String**, for example `getValidBookingPayloadXML()`) |
| Wrong `Accept` (406) | `xmlAcceptHeaders()` |
| Everything else (422, 404, 409, 400 ...) | the happy-path headers: the backend produces these errors from the data of the request |

## 9. Choosing the validation policy

The expected result of a scenario is `expect(status, policy)`. The policy says what to check **besides** the status code.
The rule: ask **who defines the body of the response**.

| The response is... | Policy | Examples |
|---|---|---|
| defined by the API contract (the spec gives it a schema) | `validateSchemaOnly(SCHEMA, "reason")` | 200, 401, 403, 406, 409, 415, 422, 400 from validation |
| produced by the server or the gateway, not by the contract | `skipSchemaAndAllowHeaderValidation("reason")` | 413, 404 from routing or "record not found" |
| a 405 | `validateAllowHeaderOnly("reason")` | the spec cannot define a response for an operation that does not exist; RFC 9110 still requires the `Allow` header |

Write the `reason` as a sentence that explains the choice. It is printed in the console, shown in the Allure report
and added to the message of a failed assertion.

## 10. Reports

Results are written to `allure-results` (a folder in the project root). One test gives one `<uuid>-result.json` file,
plus one `.html` file for each attachment of that test (the "Request" and the response, for example
"HTTP/1.1 401 Unauthorized").

Open the report:

- With Maven (the Allure Maven plugin is configured in `pom.xml`): `mvn allure:serve`
- With the Allure command line tool bundled in the project:
  `.allure\allure-2.20.1\bin\allure.bat serve allure-results`

What the report page of one test shows, and where each part comes from:

| Part of the page | Comes from |
|---|---|
| **Parameters**, line `arg0: TestScenarioRecord[...]` | the test's argument (the record), turned into text by `TestScenarioRecord.toString()`. The text is stored **inside** the result JSON |
| **Test body > "Request"** | an HTML snapshot of the HTTP request that was sent (URL and method, body, headers), created by the Allure filter that `BaseTest` adds and drawn with `tpl/http-request-masked.ftl`. Stored in a **separate** `.html` file; the JSON only points to it |
| **Test body > "HTTP/1.1 ..."** | the response, drawn with Allure's default template |

Results are kept as they were written: if you change what the reports show (for example the masking), delete the old
`allure-results` first (`mvn clean` does it).

## 11. Hiding secrets in the reports

The `Authorization` header carries a secret: a Bearer token, or, for Basic, the base64 of `username:password`
(base64 is not encryption: anyone can decode it). Left alone, it would be printed into the reports. The framework hides
it in the places listed below. Only what the **report shows** changes: the request sent to the server still carries
the real header.

### Where the secret would appear, and what hides it

```
a test, for example BookingNegativeTests.testPostBookingNegativeScenarios(record)
 |
 |-- "Parameters" section of the Allure page (arg0), TestNG console, test-output
 |       text made by  TestScenarioRecord.toString()                        -> hides the header
 |
 |-- the request that is sent: APIClient.sendRequest(reqSpec_base, ...)    carries the REAL header
 |       '-- the Allure filter (added in BaseTest) creates the "Request" attachment
 |               drawn by  src/test/resources/tpl/http-request-masked.ftl   -> hides the header
 |
 '-- the login request (AuthUtils): RequestSpecFactory.loginRequestSpecification()
         has no Allure filter, so it never appears in the report
```

| Where the secret would appear | What hides it | What you see instead |
|---|---|---|
| Allure page, **Test body > "Request"**, line `authorization: ...` (Allure's default template prints it twice: in the Headers list and in the Curl block) | the template `src/test/resources/tpl/http-request-masked.ftl`, registered in `BaseTest.setupBaseSpec()` | `authorization: Bearer [masked]`; no Curl block |
| Allure page, **Parameters > `arg0`**, and the TestNG console and `test-output` | `TestScenarioRecord.toString()` | `headers={authorization=Bearer [masked], ...}` |
| The login request (its body carries the password) | not hidden but never recorded: `AuthUtils` uses `RequestSpecFactory.loginRequestSpecification()`, which has no Allure filter and no request logging | nothing |

The two fixes are independent. Neither covers the other's place, so both are needed:

| What you have | "Request" attachment | `arg0` and `test-output` |
|---|---|---|
| only the template | masked | **token visible** |
| only `toString()` | **token visible** | masked |
| both (what the framework does) | masked | masked |

### What an .ftl file is

FTL means FreeMarker Template Language. A template is a fixed layout with blanks that are filled in each time, like
a mail-merge letter. Allure brings FreeMarker and draws every attachment this way. Allure's default request template
sits inside its jar; ours replaces it. A template does not run on its own: for each request, the Allure filter hands
the request data (URL, method, headers, body, cookies) to FreeMarker, which fills the layout and returns HTML. That HTML
is saved as the "Request" attachment. The template never sees the `TestScenarioRecord`, which is why `toString()` is
needed as well.

Our template differs from Allure's default in three ways:

1. It masks the value of the `Authorization` header and keeps the scheme: `Bearer [masked]`, `Basic [masked]`.
2. It leaves out the Curl block, which repeats every header (the token too) and the whole body a second time.
3. It cuts a body longer than `maxBodyLength` (5000) characters and says how long the body really was. The
   oversized-payload scenario sends about 12 million characters; showing all of it makes the browser tab run out of
   memory (`STATUS_BREAKPOINT`).

It is registered in `BaseTest.setupBaseSpec()`:

```java
.addFilter(new AllureRestAssured().setRequestTemplate("http-request-masked.ftl"))
```

The name is relative to the `tpl` folder (Allure looks in the classpath folder `tpl`), so do **not** write
`"tpl/http-request-masked.ftl"`: that would be searched as `tpl/tpl/http-request-masked.ftl` and not found.

### Changing the masking

| What you want | Where |
|---|---|
| Mask another header (for example a cookie or an API key) | the condition `name?lower_case == "authorization"` in the `.ftl` **and** the check in `TestScenarioRecord.toString()`. Change both and keep the output format the same |
| Show more or fewer characters of a long body | `maxBodyLength` at the top of the `.ftl` |
| Mask the response as well | give the filter a response template with `setResponseTemplate("...")`, also in the `tpl` folder |

### Checking that it works

1. Run one test and open its page in the report.
2. **Test body > "Request"**: `authorization: Bearer [masked]`, and no "Curl" block.
3. **Parameters > `arg0`** (the line is cut with "...": click it): `headers={authorization=Bearer [masked], ...}`.
4. From the project root on Windows; it should print no file name (it looks for the start of a JWT token, so it only
   works for JWT tokens):

```
findstr /s /i /m "Bearer eyJ" allure-results\* test-output\*
```

### What is not covered

- Only the `Authorization` header is masked. Other secret headers (Cookie, API keys) need the change above.
- The response attachment uses Allure's default template. A real backend that returns secrets (a `Set-Cookie` header,
  a token in a body) needs a response template.
- A secret inside a request **body** is not masked. Today only the login request has one, and it is never recorded.
- `TestScenarioRecord.toString()` prints the payload in full (see [Known limitations](#12-known-limitations)).

### Housekeeping

- Old results keep their old content: after adding or changing the masking, delete `allure-results` (`mvn clean` does it)
  and `test-output` (by hand), then run again.
- Do not commit or share `allure-results`, `test-output` or `target`. The project has no `.gitignore`; add one with these
  three folders.

## 12. Known limitations

| Limitation | Effect |
|---|---|
| Tokens are reused for the whole run unless they are JWTs with an `exp` claim | If the backend issues non-JWT tokens that expire, late scenarios can get 401 |
| The timeout is 3 s (`RequestSpecFactory`), for the test requests and for login | A slow server can time out, and a timeout is a failure. The 11 MB payload of scenario 0.1 should be raised if the upload takes longer |
| The duplicate test needs a backend that counts **any** existing booking as a duplicate | On a backend that only protects seeded data, `testPostDuplicateBookingIsRejected` gets 200 on the second request |
| The scenarios change data (bookings are created) | Restart a local backend to get its seed data back; use unique data (as the happy paths do) on a shared one |
| Workflow tests are not in the default suite | They are not run unless you add them to `testng.xml` |
| The "Request" attachment shows only the first 5000 characters of a body | For the oversized-payload scenario (about 12 million characters) the report shows the start of the body only |
| `TestScenarioRecord.toString()` prints the payload in full | For the oversized-payload scenario the `arg0` parameter is about 12 million characters long: the result JSON is large |
| Only the `Authorization` header is masked | Other secret headers are shown as they are (see [section 11](#11-hiding-secrets-in-the-reports)) |

## 13. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `CRITICAL: Server is DOWN!` | The server is not running, `baseUri` is wrong, or it does not answer `GET /ping` with 200 |
| `'baseUri' is not configured` (or another key) | The key is missing in `config.properties` and no `-D` option was given. All keys of section 4 are required |
| `Login failed for user '...'` | Wrong username/password in `config.properties`, or the login endpoint is not reachable. It appears as a data-provider error |
| Many scenarios fail with 401 | The account in `auth.validUser` does not exist on this backend, or its password differs |
| A 403 scenario gets 200 | The role account of that key has the permission. Use an account that is denied that action |
| Scenario 0.1 gets 200 or 201 instead of 413 | The payload is not bigger than the backend's request-size limit, or the backend has no limit |
| Scenario 0.1 fails with a connection error | The server closed the connection while the client was still sending the large body. Let the server read and discard the rest of the body |
| A POST to an unknown path gets 405 instead of 404 | The backend answers unknown paths with its static-resource handler (Spring Boot: set `spring.web.resources.add-mappings=false`) |
| `testPostDuplicateBookingIsRejected` gets 200 on the second request | The backend does not count a booking created by a test as a duplicate (see section 3) |
| A happy-path scenario gets 409 | Its lastname is not unique: use `uniqueValidBookingPayloadPOJO()` |
| `setupBaseSpec` fails and every test is skipped, with `AttachmentRenderException: Could't render http attachment file` | Look at the `Caused by:` line. With `TemplateNotFoundException` the template name is wrong: it is relative to the `tpl` folder (`http-request-masked.ftl`, without `tpl/`) |
| The browser tab crashes (`STATUS_BREAKPOINT`) when you open the "Request" of one test | A very large body (the oversized-payload scenario). Make sure the template that cuts long bodies is registered ([section 11](#11-hiding-secrets-in-the-reports)) |
| The report still shows tokens | Those results were written before the masking existed. Delete `allure-results` and `test-output`, and run again |
| `Data provider mismatch ... Arguments: []` | A data provider contains an empty row (`{}`). Remove it or fill it with a scenario |

## Where to read more

- `HeaderFactory.java`: the comment at the top explains the method naming rules.
- `RequestSpecFactory.java`: the class comment explains the two request specs and why the login spec has no Allure filter.
- `BaseTest.java`: the comment above `.addFilter(...)` explains the Allure filter and the two places a token can appear.
- `TestScenarioRecord.java`: the comment above `toString()` explains the masking of the record.
- `tpl/http-request-masked.ftl`: the comment at the top lists what differs from Allure's default template.
- `config.properties`: the comments explain the settings and the role naming rules.
- `SchemaAndAllowHeaderValidationPolicy.java`: the comments explain the policy and why its fields are not static.
