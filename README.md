# API Test Framework (real backend)

English | [বাংলা](README_bn.md)

Data-driven REST API tests (REST Assured + TestNG + Maven) for a backend called **`shop-api`**: a small e-commerce API
with the resources **users, products, cart and orders** (plus login).

> **Start `shop-api` before you run the tests.** The tests send real requests to a server that is already running
> (by default `http://localhost:8081`). They do not start it. If it is not running, every test stops at once with
> `CRITICAL: Server is DOWN!`.

Every scenario has **one** expected status code and one validation policy.

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
| A running backend | the e-commerce API (the local `shop-api`) | the server under test (see section 3) |

Main libraries (all managed by `pom.xml`): TestNG 7.12.0, REST Assured 5.5.6 (with JSON schema validator),
Jackson 2.18.0, Allure 2.24.0 (it brings FreeMarker, which draws the report attachments).

In Eclipse: **File > Import > Existing Maven Projects**, then select this folder.

## 2. Quick start

**Step 1. Start `shop-api`** and leave it running (it is a Spring Boot project: for example `mvn spring-boot:run` in its
folder, or run its main class from Eclipse). Check it: `http://localhost:8081/ping` must answer 200.
Do this **before** step 3.

**Step 2. Check the settings** in `src/test/resources/config.properties`: `baseUri` and the accounts (section 4).

**Step 3. Run the tests** from the project root, in a second window:

```
mvn clean test
```

`mvn clean` also empties `allure-results`, so every run starts with a fresh report. To point one run at another
server without editing a file: `mvn clean test -DbaseUri=http://localhost:8081`.

`pom.xml` lists two suite files (`suiteXmlFiles` of the surefire plugin): `regression-test.xml` and
`smoke-test.-happyPath.xml`. So `mvn clean test` runs **both**, and `ProductsPositiveTests` runs twice, because it is in
both. To run one suite file only, without editing `pom.xml`:

```
mvn clean test -Dsurefire.suiteXmlFiles=regression-test.xml
mvn clean test -Dsurefire.suiteXmlFiles=smoke-test.-happyPath.xml
```

| Suite file | What it runs |
|---|---|
| `regression-test.xml` | all test classes: login, request schema, users, products, cart and the flow tests |
| `smoke-test.-happyPath.xml` | the happy-path tests only (`ProductsPositiveTests`) |

**Step 4. Read the result.** The console shows each scenario (`Executing Scenario: ...`) and its outcome.
Open the Allure report as described in [Reports](#10-reports).

The suite first calls `GET /ping` (`BaseTest.setupBaseSpec()`); if it is not 200 every test stops with
`CRITICAL: Server is DOWN!`.

### Parallel execution (optional, off by default)

The suite files run the tests one after another. To run them in parallel, add `parallel="tests" thread-count="3"` to the
`<suite>` tag of `regression-test.xml`: each `<test>` tag then runs in its own thread, and the classes inside one `<test>`
tag still run one after another. Keep this in mind:

- Classes that use the **same account's cart** (`WorkflowPutCartItemTests`, `CreateOrderFlowTest`) must stay in the **same**
  `<test>` tag. Every account has one cart, and these tests empty it first. Two of them in parallel would empty each
  other's cart.
- `parallel="classes"` or `"methods"` needs separate accounts for these classes first (and `"methods"` cannot be used
  for the cart tests at all: the tests of one class share one cart).
- Every test builds its own data (a unique `sku`, a unique username), so tests do not depend on each other's data.
- Add parallel execution only when the run time becomes a problem. It adds the risk of tests that fail only sometimes.

## 3. What the backend must provide

The tests are written for `shop-api`. They assume the following. If you point them at another backend, the scenarios
that depend on it fail, which is useful information, not necessarily a framework bug.

| Need | Used by |
|---|---|
| `GET /ping` answers 200 | the health check in `BaseTest` |
| `POST /auth/login` returns a bearer token for a valid user | `AuthUtils`, every Bearer scenario |
| The accounts of `config.properties` exist: one account for each role (admin, seller, customer) | the happy paths and the permission scenarios |
| Errors are answered with the contract's Problem Details body (`type`, `title`, `status`, `detail`, `instance`) | the schema validation of the error scenarios |
| A request body above the backend's size limit is answered with 413 (before security and routing) | scenario 0.1: the payload must be bigger than that limit |
| A product `sku` and a username must be unique; a duplicate is answered with 409 | the duplicate and uniqueness tests |
| `POST /orders` needs the header `Idempotency-Key`; an empty cart or too little stock is answered with 409 | `CreateOrderFlowTest` |
| Data stays until the backend restarts (the `shop-api` keeps it in memory) | the happy paths and workflow tests create data and do not delete it |

## 4. Configuration

All settings live in `src/test/resources/config.properties`. A value can be overridden for one run with
`-Dkey=value`. Precedence: **`-D` option > `config.properties`**. There are **no built-in defaults** for these
keys: a missing value stops the run at once with a message that names the key.

| Key | Meaning |
|---|---|
| `baseUri` | Base URL of the server under test |
| `auth.validUser`, `auth.validPassword` | The full-permission account used by every scenario that is **not** about authentication or authorization (here the same account as the role `admin`) |
| `auth.account.<role>.username` / `.password` | One account per role of the backend (here `admin`, `seller`, `customer`). They are used by the permission matrix |

Notes:

- The comments inside `config.properties` explain the role keys, the permission matrix and an alternative key style
  (`auth.role.<resource>No<Action>Role`) for a backend whose permissions are not known. Read them before adding a role.
- Tokens are cached **per username**. Two keys that use the same username share one token.
- The request timeout (3 s) is not a setting: it is a constant in `RequestSpecFactory`.
- The passwords in `config.properties` are for a local throw-away server. Do not commit real credentials of a shared or
  production system. For a shared server, pass them with `-D` from the command line.

## 5. Project layout

```
pom.xml                    build, dependencies, test run configuration (which suite file runs)
regression-test.xml        the suite with all test classes
smoke-test.-happyPath.xml  the smoke suite
src/main/java/com/api/
  client/                      APIClient (sends the HTTP request with REST Assured), HttpMethods (GET, POST, ... constants)
  pojo/<resource>/             payload classes and field-name constants: users, products, order
  utils/
    ConfigUtils.java           reads config.properties and -D options; every value is required (fails fast)
    RequestSpecFactory.java    builds the REST Assured request specs: base URI and the 3 s timeouts live here;
                               one spec for the test requests, one for the login request
    AuthUtils.java             logs in and caches tokens (thread-safe; refreshed before a JWT expires)
    HeaderFactory.java         builders for request headers (valid and deliberately broken); HeaderUtils_MethodList.txt
                               is the "which method for which scenario" list
    payload/                   PayloadUtils (helpers that build broken payloads), ProductsPayloadFactory,
                               UsersPayloadFactory: the valid payload and the deliberately broken payloads
src/test/java/com/api/
  base/BaseTest.java           one-time setup: builds reqSpec_base (with the Allure filter and the masking
                               template), server health check, request helper
  dataProvider/<resource>/     the scenarios, one data provider class for each operation:
                               products (Post, Put, Patch), users (Post; `GetUsersDataProvider` is still empty),
                               login (LoginNegativeDataProvider)
  dto/                         TestScenarioRecord (one scenario: description, endpoint, method, headers, payload,
                               expected; its toString() hides the Authorization header in the reports) and
                               ExpectedStatusCode (the expected status code plus the validation policy)
  validation/                  ResponseValidator (status code, headers, body, schema), the validation policy,
                               SensitiveInfoLeakValidator (the response must not leak internal details)
  tests/<resource>/            the test classes of each resource: products, users, cart, login;
                               tests/flows holds a flow that uses more than one resource;
                               tests/requestSchema checks the request schemas against valid payloads
  schemaPathConstants/         paths of the JSON schemas (ManualSchemaPathConstants, GeneratedSchemaPathConstants)
  tools/OpenApiSchemaGenerator.java   makes JSON schema files from the components/schemas of openapi.yaml
src/test/resources/
  config.properties            settings
  openapi.yaml                 the API contract
  manualSchemas/               response schemas written by hand
  malformedPayload/            the malformed-JSON payload file
  tpl/http-request-masked.ftl  template for the Allure "Request" attachment: hides the Authorization header,
                               cuts very long bodies, leaves out the Curl block
```

## 6. Tests and data providers by resource

Tests are organised **by resource**. Each resource has its own package under `tests/` (for example
`com.api.tests.products`). The naming rules:

| What | Rule | Example |
|---|---|---|
| Data provider class | one for each **operation**. It holds the negative and the positive scenarios of that operation as two separate methods | `PutProductDataProvider` |
| Positive and negative test class | one pair for each **resource** | `ProductsPositiveTests`, `ProductsNegativeTests` |
| Workflow test class | one for each **operation**, in the package of its resource | `WorkflowPutProductTests` |
| Payload factory | one for each resource | `ProductsPayloadFactory` |

| Resource and operation | Kind | Data provider (provider name) | Test class and method |
|---|---|---|---|
| Products: `POST /products` | negative | `PostProductsDataProvider` (`PostProducts-NegativeTestDataProvider`) | `ProductsNegativeTests.testPostProductsNegativeScenarios` |
| Products: `POST /products` | positive | `PostProductsDataProvider` (`PostProducts-PositiveTestDataProvider`) | `ProductsPositiveTests.testPostProductssHappyPathScenarios` (group `smoke`) |
| Products: `PUT /products/{id}` | negative | `PutProductDataProvider` (`PutProduct-NegativeTestDataProvider`) | `ProductsNegativeTests.testPutProductsNegativeScenarios` |
| Products: `PUT /products/{id}` | positive | `PutProductDataProvider` (`PutProduct-PositiveTestDataProvider`) | `ProductsPositiveTests.testPutProductssHappyPathScenarios` |
| Products: `PATCH /products/{id}` | negative | `PatchProductDataProvider` (`PatchProduct-NegativeTestDataProvider`) | `ProductsNegativeTests.testPatchProductsNegativeScenarios` |
| Products: `PATCH /products/{id}` | positive | `PatchProductDataProvider` (`PatchProduct-PositiveTestDataProvider`) | `ProductsPositiveTests.testPatchProductssHappyPathScenarios` |
| Users: `POST /users` | negative | `PostUsersDataProvider` (`PostUsers-NegativeTestDataProvider`) | `UsersNegativeTests.testPostUsersNegativeScenarios` |
| Users: `POST /users` | positive | `PostUsersDataProvider` (`PostUsers-PositiveTestDataProvider`) | `UsersPositiveTests.testPostUsersHappyPathScenarios` |
| Login: `POST /auth/login` | negative | `LoginNegativeDataProvider` (`invalidLoginCredentialsProvider`) | `LoginNegativeTests.shouldRejectLogin_withInvalidCredentials` |
| Request schema of products | contract check | the test's own provider (`validPayloads`) | `ProductRequestSchemaTest` |

Notes:

- A data provider is named after the operation it mostly covers, but a record decides its own endpoint and
  method. A provider can therefore hold a few scenarios that are not that operation.
- The negative test for `GET /users` in `UsersNegativeTests` is commented out until its data provider is written.
- `LoginNegativeTests` does not use `TestScenarioRecord`: its data provider returns plain values
  (description, username, password) and the test always expects 401.
- A data provider must not contain an empty row (`{}`): TestNG would call the test method with no argument and fail
  with `Data provider mismatch` (see [Troubleshooting](#13-troubleshooting)).
- To run a class that is not in a suite yet, add a `<class>` entry to the suite file.

### Where each rule is tested

The same schema rule is tested **once**. The full set of negative scenarios (every constraint, with several wrong
values) is in the data provider of one operation of a resource, usually the `POST`. The other operations of that
resource (`PUT`, `PATCH`) get one scenario for each error code of the contract, and every negative scenario breaks
exactly **one** field or header rule. The scenarios of a data-changing operation (`PUT`, `PATCH`) send a payload that
really changes data.

### Why a duplicate scenario is a test and not a row

A duplicate means "this resource already exists". A data row sends one request, so it cannot create the resource first.
The duplicate scenarios (same `sku` twice, same username twice) are workflow tests that do it in two steps: create the
resource (expect success), then send the same data again (expect 409). They do not depend on any other test having run
before them.

### Workflow tests are kept separate

A **data-driven scenario** sends one request and checks that one response: status code, headers, body, schema
(`ResponseValidator`). A **workflow test** sends several calls in a row, where the output of one call is the input of
the next. For example, the `id` returned by `POST /products` is used in `GET /products/{id}`, and the test checks that
the data that was sent comes back unchanged. No single response can answer that question, so workflow tests:

- live in their own classes, one class for each operation (`WorkflowPostProductTests`, `WorkflowPutProductTests`,
  `WorkflowPatchProductTests`), in the package of the resource,
- do **not** use a data provider, `TestScenarioRecord`, `ExpectedStatusCode` or `ResponseValidator`,
- call small helper methods (for example `createProduct`, `getProduct`, `extractProductId`) and assert with TestNG
  `Assert` and REST Assured `.then()`,
- send every call with `sendRequestWithTimeoutHandling(...)`, whose last argument names the step, so a timeout says
  which call failed.

| Class | What it checks |
|---|---|
| `WorkflowPostProductTests` | a created product can be read back unchanged; a duplicate `sku` is refused (409) |
| `WorkflowPutProductTests` | an updated product can be read back; the same `PUT` twice gives the same result; a `sku` of another product is refused |
| `WorkflowPatchProductTests` | only the sent field changes; the same `PATCH` twice gives the same result; a `sku` of another product is refused |
| `UsersWorkflowTests` | a registered user can be read back; a second registration of the same user is refused; a username that differs only in letter case is refused |
| `WorkflowPutCartItemTests` | after `PUT /cart/items/{productId}`, `GET /cart` shows the same item; the same `PUT` twice does not double the quantity; a second `PUT` replaces the quantity |
| `CreateOrderFlowTest` (package `flows`) | a flow over three resources: create a product, put it in the cart, place an order; the order shows the product from the cart, and the stock goes down |

`ResourceWorkflowTestsTemplate` is a placeholder class with no tests. Its comment explains how workflow tests are
organised. To add one: copy an existing workflow class, change the endpoint, payload and helpers, and add the class
to the suite file.

The workflow tests do not delete what they create: the `shop-api` keeps its data in memory, so a restart clears it.

Note on numbering: the "Layer 5.x" labels in their descriptions are their own numbering. They are unrelated to the
scenario layers (0, 1A, 1B, 2A-2D, 3, 4, 5) listed in the **Layers** table in
[section 8, "How to add a scenario"](#8-how-to-add-a-scenario).

## 7. Call flow: which method calls which

### A data-driven scenario, step by step

```
suite file (for example regression-test.xml)
  -> ProductsNegativeTests / ProductsPositiveTests
       (1) BaseTest.setupBaseSpec()                         @BeforeClass: reqSpec_base (RequestSpecFactory + Allure filter), GET /ping
       (2) data provider (e.g. PostProductsDataProvider, the negative method)
             builds one TestScenarioRecord per scenario, calling:
               HeaderFactory.<builder>()  ->  AuthUtils.login()  ->  AuthUtils.callLoginEndpointRaw()
               ProductsPayloadFactory.getValidProductsPayloadPOJO() / ProductsPayloadFactory.<BROKEN>_PAYLOAD.get()
               ExpectedStatusCode.expect(status, policy)
       (3) test method (e.g. ProductsNegativeTests.testPostProductsNegativeScenarios(record))
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
   - a payload from the payload factory of the resource (for example `getValidProductsPayloadPOJO()` or
     `INVALID_DATA_TYPE_PAYLOAD.get()`),
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

`ProductsPositiveTests` follows the same path, but calls `validateStatusCode`, `validateHeaders`, `validateBody` and
`validateSchema` one by one instead of `validateAll`.

### A workflow test

```
WorkflowPostProductTests.<test>()                      (extends BaseTest, so step (1) above applies)
  -> createProduct(payload)   -> sendRequestWithTimeoutHandling(reqSpec_base, "/products", POST, headers, payload, "Workflow: ...")
        -> APIClient.sendRequest(...)                   headers = HeaderFactory.bearerAuthHeadersForFullPermissionUser()
  -> extractProductId(response)                         reads "id" from the response
  -> getProduct(id)           -> sendRequestWithTimeoutHandling(...)    same call, other method and endpoint /products/{id}
  -> Assert.assertEquals(...) / .then().body(...)       compares the responses of different calls
```

### A login test

`LoginNegativeDataProvider` supplies (description, username, password).
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
| `APIClient.sendRequest()` | `BaseTest.sendRequestWithTimeoutHandling()` |
| `BaseTest.sendRequestWithTimeoutHandling()` | the test methods and the helper methods of the workflow classes |

## 8. How to add a scenario

### Add a negative scenario

1. Open the data provider of the operation, for example `PostProductsDataProvider` (negative method) for `POST /products`.
2. Copy the record that looks most like yours and change the fields:

| Field | What to put |
|---|---|
| description | Number plus name, for example `"3.6. Empty name-Parsing Layer"`. Use the layer number (table below) |
| endpoint, method | For example `"/products"`, `POST` |
| headers | A method from `HeaderFactory` (see the table below) |
| payload | The valid payload of the resource, or a broken one from its payload factory (add a new one there if needed) |
| expected | `expect(status, policy)` |

3. Choose the **policy** (what to check besides the status code), see [section 9](#9-choosing-the-validation-policy).
4. Break exactly **one** field or header rule in the scenario. Read the rules of a field from the **Schema** view of the
   operation in the contract (Swagger UI: the Schema button next to the example payload), not from the example payload:
   the example is only one valid sample. The rules to check are type, required, length, range, format, enum and unique.
   Write one negative scenario for each rule that can be broken and one positive scenario for a value on the edge
   (for example the longest allowed length).
5. Run the suite. If the backend answers with another status code, decide which one is wrong: the scenario or the
   backend.

A scenario that needs a **precondition** (something must exist first, like a product that is then duplicated) does
not fit a data row. Write a workflow test (see section 6).


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
| Wrong `Content-Type` (415) | `xmlContentTypeHeaders()` (the payload must be an XML **String**) |
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
a test, for example ProductsNegativeTests.testPostProductsNegativeScenarios(record)
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
- Do not commit or share `allure-results`, `test-output` or `target`. The `.gitignore` of the project lists them (and the
  `.allure` folder, which Maven fills with the Allure tool).

## 12. Known limitations

| Limitation | Effect |
|---|---|
| Tokens are reused for the whole run unless they are JWTs with an `exp` claim | If the backend issues non-JWT tokens that expire, late scenarios can get 401 |
| The timeout is 3 s (`RequestSpecFactory`), for the test requests and for login | A slow server can time out, and a timeout is a failure. The 11 MB payload of scenario 0.1 should be raised if the upload takes longer |
| The scenarios create data (products, users, orders) and do not delete it | Restart a local backend to get its seed data back; use unique data (as the tests do) on a shared one |
| Every account has one cart | Tests that use the same account's cart must not run in parallel (see section 2, Parallel execution) |
| The tests use the permission matrix and role accounts of the backend | A backend with other roles needs other role keys in `config.properties` |
| The "Request" attachment shows only the first 5000 characters of a body | For the oversized-payload scenario (about 12 million characters) the report shows the start of the body only |
| `TestScenarioRecord.toString()` prints the payload in full | For the oversized-payload scenario the `arg0` parameter is about 12 million characters long: the result JSON is large |
| Only the `Authorization` header is masked | Other secret headers are shown as they are (see [section 11](#11-hiding-secrets-in-the-reports)) |

## 13. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `CRITICAL: Server is DOWN!` | `shop-api` is not running (start it first), `baseUri` is wrong, or the server does not answer `GET /ping` with 200 |
| `'baseUri' is not configured` (or another key) | The key is missing in `config.properties` and no `-D` option was given. All keys of section 4 are required |
| `Login failed for user '...'` | Wrong username/password in `config.properties`, or the login endpoint is not reachable. It appears as a data-provider error |
| Many scenarios fail with 401 | The account in `auth.validUser` does not exist on this backend, or its password differs |
| A 403 scenario gets 200 | The role account of that key has the permission. Use an account that is denied that action |
| Scenario 0.1 gets 200 or 201 instead of 413 | The payload is not bigger than the backend's request-size limit, or the backend has no limit |
| Scenario 0.1 fails with a connection error | The server closed the connection while the client was still sending the large body. Let the server read and discard the rest of the body |
| A POST to an unknown path gets 405 instead of 404 | The backend answers unknown paths with its static-resource handler (Spring Boot: set `spring.web.resources.add-mappings=false`) |
| A happy-path scenario or a workflow test gets 409 | The `sku` or the username is not unique: build the payload with the factory method that makes a new value each time |
| `POST /orders` gets 400 | The header `Idempotency-Key` is missing, or it is not 8 to 64 characters long |
| `POST /orders` gets 409 | The cart is empty or a product has too little stock. Another test may have emptied the cart: check that the cart tests do not run in parallel |
| `mvn test` runs the smoke tests twice | `pom.xml` lists both suite files, and the smoke test class is in both. Pass `-Dsurefire.suiteXmlFiles=regression-test.xml` to run one suite only |
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
- `config.properties`: the comments explain the settings, the role keys and the permission matrix.
- `SchemaAndAllowHeaderValidationPolicy.java`: the comments explain the policy and why its fields are not static.
- `OpenApiSchemaGenerator.java`: the class comment explains how the schema files are made from `openapi.yaml`.
