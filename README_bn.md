# API Test Framework (real backend)

[English](README.md) | বাংলা

**`shop-api`** নামের একটা backend-এর জন্য data-driven REST API test (REST Assured + TestNG + Maven)। `shop-api` ছোট একটা
e-commerce API, যার resource হলো **users, products, cart ও orders** (সঙ্গে login)।

> **Test চালানোর আগে `shop-api` চালু করুন।** Test গুলো এমন একটা server-এ আসল request পাঠায় যা আগে থেকেই চলছে
> (default-এ `http://localhost:8081`)। Test নিজে server চালু করে না। Server চালু না থাকলে প্রতিটা test সঙ্গে সঙ্গে
> `CRITICAL: Server is DOWN!` বলে থেমে যায়।

প্রতিটা scenario-র একটাই expected status code আর একটাই validation policy।

## সূচি

1. [যা লাগবে](#1-যা-লাগবে)
2. [দ্রুত শুরু](#2-দ্রুত-শুরু)
3. [Backend-কে যা দিতে হবে](#3-backend-কে-যা-দিতে-হবে)
4. [Configuration](#4-configuration)
5. [Project-এর কাঠামো](#5-project-এর-কাঠামো)
6. [Resource অনুযায়ী test ও data provider](#6-resource-অনুযায়ী-test-ও-data-provider)
7. [Call flow: কোন method কাকে call করে](#7-call-flow-কোন-method-কাকে-call-করে)
8. [নতুন scenario যোগ করা](#8-নতুন-scenario-যোগ-করা)
9. [Validation policy বাছা](#9-validation-policy-বাছা)
10. [Report](#10-report)
11. [Report-এ secret লুকানো](#11-report-এ-secret-লুকানো)
12. [জানা সীমাবদ্ধতা](#12-জানা-সীমাবদ্ধতা)
13. [সমস্যা সমাধান](#13-সমস্যা-সমাধান)

---

## 1. যা লাগবে

| Tool | Version | কেন |
|---|---|---|
| JDK | 17 | project Java 17-এ compile হয় |
| Maven | 3.x | build ও `mvn test` |
| চালু একটা backend | e-commerce API (local `shop-api`) | যে server-কে test করা হয় (section 3 দেখুন) |

প্রধান library (সব `pom.xml` চালায়): TestNG 7.12.0, REST Assured 5.5.6 (JSON schema validator সহ),
Jackson 2.18.0, Allure 2.24.0 (এর সঙ্গে FreeMarker আসে, যা report-এর attachment আঁকে)।

Eclipse-এ: **File > Import > Existing Maven Projects**, তারপর এই folder বেছে নিন।

## 2. দ্রুত শুরু

**ধাপ ১. `shop-api` চালু করুন** এবং চালু রেখে দিন (এটা Spring Boot project: যেমন ওর folder-এ `mvn spring-boot:run`,
বা Eclipse থেকে এর main class run করুন)। ঠিক আছে কি না দেখুন: `http://localhost:8081/ping` অবশ্যই 200 দেবে।
এটা ধাপ ৩-এর **আগে** করুন।

**ধাপ ২. Setting দেখে নিন:** `src/test/resources/config.properties`-এ `baseUri` আর account গুলো (section 4)।

**ধাপ ৩. Test চালান:** project-এর root folder থেকে, আরেকটা terminal window-এ:

```
mvn clean test
```

`mvn clean` `allure-results`-ও খালি করে, তাই প্রতিটা run নতুন report দিয়ে শুরু হয়। কোনো file না বদলে একটা run অন্য
server-এ পাঠাতে: `mvn clean test -DbaseUri=http://localhost:8081`।

`pom.xml`-এ surefire plugin-এ দুটো suite file দেওয়া আছে (`suiteXmlFiles`): `regression-test.xml` আর
`smoke-test.-happyPath.xml`। তাই `mvn clean test` **দুটোই** চালায়, আর `ProductsPositiveTests` দুই suite-এই থাকায়
দুবার চলে। `pom.xml` না বদলে শুধু একটা suite file চালাতে:

```
mvn clean test -Dsurefire.suiteXmlFiles=regression-test.xml
mvn clean test -Dsurefire.suiteXmlFiles=smoke-test.-happyPath.xml
```

| Suite file | কী চালায় |
|---|---|
| `regression-test.xml` | সব test class: login, request schema, users, products, cart ও flow test |
| `smoke-test.-happyPath.xml` | শুধু happy-path test (`ProductsPositiveTests`) |

**ধাপ ৪. ফলাফল পড়ুন।** Console-এ প্রতিটা scenario (`Executing Scenario: ...`) আর তার ফল দেখায়।
Allure report খুলুন [Report](#10-report) অংশের নিয়মে।

Suite সবার আগে `GET /ping` call করে (`BaseTest.setupBaseSpec()`); 200 না এলে প্রতিটা test
`CRITICAL: Server is DOWN!` বলে থেমে যায়।

### Parallel execution (ঐচ্ছিক, default-এ বন্ধ)

Suite file গুলো test একটার পর একটা চালায়। Parallel চালাতে `regression-test.xml`-এর `<suite>` tag-এ
`parallel="tests" thread-count="3"` যোগ করুন: তখন প্রতিটা `<test>` tag নিজের thread-এ চলে, আর এক `<test>` tag-এর
ভেতরের class গুলো তখনও একটার পর একটা চলে। মনে রাখুন:

- যে class গুলো **একই account-এর cart** ব্যবহার করে (`WorkflowPutCartItemTests`, `CreateOrderFlowTest`), সেগুলো
  **একই** `<test>` tag-এ থাকতে হবে। প্রতি account-এর একটাই cart, আর এই test গুলো শুরুতে cart খালি করে। দুটো একসঙ্গে
  চললে একে অপরের cart মুছে দেবে।
- `parallel="classes"` বা `"methods"` দিতে হলে আগে এই class গুলোর জন্য আলাদা account লাগবে (আর cart test-এ
  `"methods"` কোনোভাবেই চলে না: এক class-এর test গুলো একই cart ভাগ করে)।
- প্রতিটা test নিজের data নিজে বানায় (নতুন `sku`, নতুন username), তাই test গুলো একে অপরের data-র ওপর নির্ভর করে না।
- Run-এর সময় সমস্যা হলে তবেই parallel যোগ করুন। এতে এমন test-এর ঝুঁকি বাড়ে যা মাঝে মাঝে fail করে।

## 3. Backend-কে যা দিতে হবে

Test গুলো `shop-api`-র জন্য লেখা। এগুলো নিচের বিষয় ধরে নেয়। অন্য backend-এ চালালে যে scenario এগুলোর ওপর নির্ভর করে
সেগুলো fail করবে, যা কাজের তথ্য, সবসময় framework-এর bug নয়।

| যা দরকার | কে ব্যবহার করে |
|---|---|
| `GET /ping` 200 দেয় | `BaseTest`-এর health check |
| `POST /auth/login` বৈধ user-কে bearer token দেয় | `AuthUtils`, প্রতিটা Bearer scenario |
| `config.properties`-এর account গুলো আছে: প্রতি role-এর (admin, seller, customer) একটা করে account | happy path ও permission scenario |
| Error গুলো contract-এর Problem Details body-তে (`type`, `title`, `status`, `detail`, `instance`) ফেরত আসে | error scenario-র schema validation |
| Backend-এর size limit-এর বড় request body-র উত্তর 413 (security ও routing-এর আগে) | scenario 0.1: payload ওই limit-এর চেয়ে বড় হতে হবে |
| Product-এর `sku` আর username unique; duplicate হলে 409 | duplicate ও uniqueness test |
| `POST /orders`-এ `Idempotency-Key` header লাগে; cart খালি বা stock কম হলে 409 | `CreateOrderFlowTest` |
| Backend restart না হওয়া পর্যন্ত data থাকে (`shop-api` data memory-তে রাখে) | happy path ও workflow test data বানায় কিন্তু মোছে না |

## 4. Configuration

সব setting `src/test/resources/config.properties`-এ। একটা run-এর জন্য `-Dkey=value` দিয়ে মান বদলানো যায়।
অগ্রাধিকার: **`-D` option > `config.properties`**। এই key গুলোর কোনো **built-in default নেই**: মান না থাকলে run
সঙ্গে সঙ্গে থামে, আর বার্তায় key-র নাম বলে দেয়।

| Key | মানে |
|---|---|
| `baseUri` | যে server-কে test করা হচ্ছে তার base URL |
| `auth.validUser`, `auth.validPassword` | পূর্ণ permission-এর account, যা authentication বা authorization নিয়ে **নয়** এমন প্রতিটা scenario ব্যবহার করে (এখানে `admin` role-এর সেই একই account) |
| `auth.account.<role>.username` / `.password` | Backend-এর প্রতিটা role-এর জন্য একটা account (এখানে `admin`, `seller`, `customer`)। Permission matrix এগুলো ব্যবহার করে |

মনে রাখুন:

- `config.properties`-এর ভেতরের comment-এ role key, permission matrix আর (permission জানা না থাকা backend-এর জন্য) বিকল্প key
  style `auth.role.<resource>No<Action>Role` বোঝানো আছে। নতুন role যোগ করার আগে পড়ে নিন।
- Token **প্রতি username-এ** cache হয়। একই username ব্যবহারকারী দুই key একটা token ভাগ করে।
- Request timeout (3 সেকেন্ড) কোনো setting নয়: এটা `RequestSpecFactory`-র একটা constant।
- `config.properties`-এর password গুলো local throw-away server-এর জন্য। শেয়ার করা বা production system-এর আসল credential
  commit করবেন না। শেয়ার করা server-এর জন্য command line থেকে `-D` দিয়ে পাঠান।

## 5. Project-এর কাঠামো

```
pom.xml                    build, dependency, test run configuration (কোন suite file চলবে)
regression-test.xml        সব test class-সহ suite
smoke-test.-happyPath.xml  smoke suite
src/main/java/com/api/
  client/                      APIClient (REST Assured দিয়ে HTTP request পাঠায়), HttpMethods (GET, POST, ... constant)
  pojo/<resource>/             payload class ও field-নামের constant: users, products, order
  utils/
    ConfigUtils.java           config.properties ও -D option পড়ে; প্রতিটা মান বাধ্যতামূলক (না থাকলে সঙ্গে সঙ্গে fail)
    RequestSpecFactory.java    REST Assured request spec বানায়: base URI আর ৩ সেকেন্ডের timeout এখানে;
                               test request-এর জন্য একটা spec, login request-এর জন্য একটা
    AuthUtils.java             login করে ও token cache করে (thread-safe; JWT মেয়াদ শেষ হওয়ার আগে নতুন করে)
    HeaderFactory.java         request header বানানোর method (বৈধ ও ইচ্ছাকৃত ভাঙা); HeaderUtils_MethodList.txt
                               হলো "কোন scenario-তে কোন method" তালিকা
    payload/                   PayloadUtils (ভাঙা payload বানানোর helper), ProductsPayloadFactory,
                               UsersPayloadFactory: বৈধ payload ও ইচ্ছাকৃত ভাঙা payload
src/test/java/com/api/
  base/BaseTest.java           একবারের setup: reqSpec_base বানায় (Allure filter ও masking template সহ),
                               server health check, request helper
  dataProvider/<resource>/     scenario গুলো, প্রতি operation-এর জন্য একটা data provider class:
                               products (Post, Put, Patch), users (Post; `GetUsersDataProvider` এখনও খালি),
                               login (LoginNegativeDataProvider)
  dto/                         TestScenarioRecord (একটা scenario: description, endpoint, method, header, payload,
                               expected; এর toString() report-এ Authorization header লুকায়) ও
                               ExpectedStatusCode (expected status code ও validation policy)
  validation/                  ResponseValidator (status code, header, body, schema), validation policy,
                               SensitiveInfoLeakValidator (response-এ ভেতরের তথ্য ফাঁস হওয়া চলবে না)
  tests/<resource>/            প্রতি resource-এর test class: products, users, cart, login;
                               tests/flows-এ আছে একাধিক resource জুড়ে চলা flow;
                               tests/requestSchema বৈধ payload দিয়ে request schema মেলায়
  schemaPathConstants/         JSON schema-র path (ManualSchemaPathConstants, GeneratedSchemaPathConstants)
  tools/OpenApiSchemaGenerator.java   openapi.yaml-এর components/schemas থেকে JSON schema file বানায়
src/test/resources/
  config.properties            setting
  openapi.yaml                 API contract
  manualSchemas/               হাতে লেখা response schema
  malformedPayload/            ভাঙা JSON payload-এর file
  tpl/http-request-masked.ftl  Allure "Request" attachment-এর template: Authorization header লুকায়,
                               খুব বড় body ছাঁটে, Curl block বাদ দেয়
```

## 6. Resource অনুযায়ী test ও data provider

Test গুলো **resource অনুযায়ী** সাজানো। প্রতিটা resource-এর `tests/`-এর নিচে নিজের package আছে (যেমন
`com.api.tests.products`)। নামের নিয়ম:

| কী | নিয়ম | Example |
|---|---|---|
| Data provider class | প্রতি **operation**-এর জন্য একটা। এতে ওই operation-এর negative ও positive scenario দুটো আলাদা method-এ থাকে | `PutProductDataProvider` |
| Positive ও negative test class | প্রতি **resource**-এর জন্য এক জোড়া | `ProductsPositiveTests`, `ProductsNegativeTests` |
| Workflow test class | প্রতি **operation**-এর জন্য একটা, resource-এর package-এ | `WorkflowPutProductTests` |
| Payload factory | প্রতি resource-এর জন্য একটা | `ProductsPayloadFactory` |

| Resource ও operation | ধরন | Data provider (provider-এর নাম) | Test class ও method |
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
| Products-এর request schema | contract যাচাই | test-এর নিজের provider (`validPayloads`) | `ProductRequestSchemaTest` |

মনে রাখুন:

- Data provider-এর নাম সে সাধারণত যে operation ঢাকে তার নামে, কিন্তু endpoint ও method ঠিক করে প্রতিটা record
  নিজেই। তাই একটা provider-এ এমন কিছু scenario থাকতে পারে যা ওই operation-এর নয়।
- `UsersNegativeTests`-এ `GET /users`-এর negative test comment করা আছে, যতক্ষণ না তার data provider লেখা হয়।
- `LoginNegativeTests` `TestScenarioRecord` ব্যবহার করে না: এর data provider সাধারণ মান (description, username,
  password) দেয়, আর test সবসময় 401 আশা করে।
- Data provider-এ খালি row (`{}`) রাখা যাবে না: TestNG test method-কে কোনো argument ছাড়া call করবে আর
  `Data provider mismatch` দিয়ে fail করবে ([সমস্যা সমাধান](#13-সমস্যা-সমাধান) দেখুন)।
- যে class এখনও কোনো suite-এ নেই তা চালাতে suite file-এ একটা `<class>` লাইন যোগ করুন।

### কোন নিয়ম কোথায় test হয়

একই schema-র নিয়ম **একবারই** test হয়। পুরো negative scenario-র সেট (প্রতিটা constraint, একাধিক ভুল মান সহ) থাকে
একটা resource-এর একটা operation-এর data provider-এ, সাধারণত `POST`-এ। ওই resource-এর অন্য operation-এ (`PUT`,
`PATCH`) contract-এর প্রতিটা error code-এর জন্য একটা করে scenario থাকে, আর প্রতিটা negative scenario ঠিক **একটা**
field বা header-এর নিয়ম ভাঙে। Data বদলায় এমন operation-এর (`PUT`, `PATCH`) scenario এমন payload পাঠায় যা সত্যিই
data বদলায়।

### Duplicate scenario কেন test, row নয়

Duplicate মানে "এই resource আগে থেকেই আছে"। একটা data row একটাই request পাঠায়, তাই সে আগে resource বানাতে পারে না।
Duplicate scenario গুলো (একই `sku` দুবার, একই username দুবার) তাই workflow test, যা দুই ধাপে করে: resource বানাও
(সফল আশা করো), তারপর একই data আবার পাঠাও (409 আশা করো)। এগুলো অন্য কোনো test আগে চলার ওপর নির্ভর করে না।

### Workflow test আলাদা রাখা হয়

**Data-driven scenario** একটা request পাঠিয়ে একটা response যাচাই করে: status code, header, body, schema
(`ResponseValidator`)। **Workflow test** পরপর কয়েকটা call পাঠায়, যেখানে এক call-এর ফল পরের call-এর input।
যেমন `POST /products` থেকে পাওয়া `id` `GET /products/{id}`-তে লাগে, আর test দেখে পাঠানো data অপরিবর্তিত ফিরে আসে।
একটা response এই প্রশ্নের উত্তর দিতে পারে না, তাই workflow test:

- নিজস্ব class-এ থাকে, প্রতি operation-এর জন্য একটা class (`WorkflowPostProductTests`, `WorkflowPutProductTests`,
  `WorkflowPatchProductTests`), resource-এর package-এ,
- data provider, `TestScenarioRecord`, `ExpectedStatusCode` বা `ResponseValidator` **ব্যবহার করে না**,
- ছোট helper method call করে (যেমন `createProduct`, `getProduct`, `extractProductId`) এবং TestNG `Assert` ও
  REST Assured `.then()` দিয়ে যাচাই করে,
- প্রতিটা call পাঠায় `sendRequestWithTimeoutHandling(...)` দিয়ে, যার শেষ argument ধাপের নাম বলে, তাই timeout হলে
  কোন call fail করল তা বার্তায় থাকে।

| Class | কী যাচাই করে |
|---|---|
| `WorkflowPostProductTests` | তৈরি করা product অপরিবর্তিত পড়া যায়; duplicate `sku` প্রত্যাখ্যাত হয় (409) |
| `WorkflowPutProductTests` | আপডেট করা product পড়া যায়; একই `PUT` দুবার একই ফল দেয়; অন্য product-এর `sku` প্রত্যাখ্যাত হয় |
| `WorkflowPatchProductTests` | শুধু পাঠানো field বদলায়; একই `PATCH` দুবার একই ফল দেয়; অন্য product-এর `sku` প্রত্যাখ্যাত হয় |
| `UsersWorkflowTests` | নিবন্ধিত user পড়া যায়; একই user-এর দ্বিতীয় নিবন্ধন প্রত্যাখ্যাত হয়; শুধু অক্ষরের case আলাদা এমন username প্রত্যাখ্যাত হয় |
| `WorkflowPutCartItemTests` | `PUT /cart/items/{productId}`-এর পর `GET /cart` একই item দেখায়; একই `PUT` দুবার পাঠালে quantity দ্বিগুণ হয় না; দ্বিতীয় `PUT` quantity বদলে দেয় |
| `CreateOrderFlowTest` (package `flows`) | তিন resource জুড়ে একটা flow: product বানাও, cart-এ রাখো, order দাও; order-এ cart-এর product থাকে, আর stock কমে |

`ResourceWorkflowTestsTemplate` একটা placeholder class, এতে কোনো test নেই। এর comment-এ বোঝানো আছে workflow test কীভাবে
সাজানো হয়। নতুন একটা যোগ করতে: একটা workflow class কপি করুন, endpoint, payload ও helper বদলান, আর class-টা suite file-এ
যোগ করুন।

Workflow test যা বানায় তা মোছে না: `shop-api` data memory-তে রাখে, তাই restart করলে সব পরিষ্কার হয়ে যায়।

নম্বর নিয়ে একটা কথা: workflow test-এর বর্ণনায় "Layer 5.x" লেবেল তাদের নিজস্ব নম্বর। এগুলোর সঙ্গে
[section 8, "নতুন scenario যোগ করা"](#8-নতুন-scenario-যোগ-করা)-এর **Layers** table-এর scenario layer (0, 1A, 1B, 2A-2D, 3, 4, 5)
-এর কোনো সম্পর্ক নেই।

## 7. Call flow: কোন method কাকে call করে

### একটা data-driven scenario, ধাপে ধাপে

```
suite file (যেমন regression-test.xml)
  -> ProductsNegativeTests / ProductsPositiveTests
       (1) BaseTest.setupBaseSpec()                         @BeforeClass: reqSpec_base (RequestSpecFactory + Allure filter), GET /ping
       (2) data provider (যেমন PostProductsDataProvider, negative method)
             প্রতি scenario-র জন্য একটা TestScenarioRecord বানায় এবং call করে:
               HeaderFactory.<builder>()  ->  AuthUtils.login()  ->  AuthUtils.callLoginEndpointRaw()
               ProductsPayloadFactory.getValidProductsPayloadPOJO() / ProductsPayloadFactory.<BROKEN>_PAYLOAD.get()
               ExpectedStatusCode.expect(status, policy)
       (3) test method (যেমন ProductsNegativeTests.testPostProductsNegativeScenarios(record))
             -> BaseTest.sendRequestWithTimeoutHandling(...)  ->  APIClient.sendRequest(...)
             -> ResponseValidator.validateAll(response, record)
```

1. **`BaseTest.setupBaseSpec()`** (`@BeforeClass`) `RequestSpecFactory.baseRequestSpecBuilder()` থেকে `reqSpec_base` বানায়
   (base URI আসে `ConfigUtils.getBaseUri()` থেকে, ৩ সেকেন্ড timeout, "validation fail করলে request ও response log করো"),
   masking template-সহ Allure filter যোগ করে ([section 11](#11-report-এ-secret-লুকানো)), `GET /ping` পাঠায় আর 200 যাচাই করে
   (না হলে `CRITICAL: Server is DOWN!`)।
2. **Data provider** (test-এর `@Test(dataProvider = ...)`-এ নাম দেওয়া) row গুলো বানায়। প্রতিটা row একটা
   `TestScenarioRecord`, যা বানানো হয়:
   - `HeaderFactory`-র একটা header method দিয়ে। Bearer method private `HeaderFactory.bearerAuthHeaders(user, password)`
     call করে, যা `AuthUtils.login(user, password)` call করে। কোনো user-এর প্রথম call-এ এটা
     `AuthUtils.callLoginEndpointRaw()`-এ (`POST /auth/login`) পৌঁছায়; পরের call গুলো cache-এর token নেয়,
   - resource-এর payload factory থেকে একটা payload দিয়ে (যেমন `getValidProductsPayloadPOJO()` বা
     `INVALID_DATA_TYPE_PAYLOAD.get()`),
   - `ExpectedStatusCode.expect(status, policy)` থেকে expected result দিয়ে, যেখানে policy বানানো হয়
     `SchemaAndAllowHeaderValidationPolicy.validateSchemaOnly(...)`, `skipSchemaAndAllowHeaderValidation(...)` বা
     `validateAllowHeaderOnly(...)` দিয়ে।

   TestNG data provider `@BeforeClass`-এর **আগেও** চালাতে পারে। এই কারণেই `AuthUtils` `reqSpec_base` ব্যবহার করে না:
   সে নিজের spec বানায় `RequestSpecFactory.loginRequestSpecification()` দিয়ে (একই base URI ও timeout, কিন্তু Allure
   filter নেই)। তাই header builder-এর ভেতরে login fail হলে সেটা fail হওয়া scenario হিসেবে নয়, data-provider error
   হিসেবে দেখা যায়।
3. **Test method** একটা record পায় এবং `BaseTest.sendRequestWithTimeoutHandling(reqSpec_base, endpoint,
   method, headers, payload, description)` call করে। সেটা `APIClient.sendRequest(...)` (REST Assured) call করে। Request
   পাঠানোর সময় timeout বা অন্য exception হলে scenario fail করে এবং বার্তায় তার নাম থাকে।
4. **`ResponseValidator.validateAll(response, record)`** response এই ক্রমে যাচাই করে:

| ধাপ | Method | Assertion | কী করে |
|---|---|---|---|
| a | `validateStatusCode` | hard | status code-কে `ExpectedStatusCode.status`-এর সঙ্গে মেলায় |
| b | `validateHeaders` | hard | `Content-Type` JSON বা problem+json; `SensitiveInfoLeakValidator.assertNoSensitiveHeadersPresent`; policy চাইলে 405-এর `Allow` header আছে |
| c | `validateBody` | soft | body যাচাই, `SensitiveInfoLeakValidator.assertNoSensitiveKeysInResponseBody` সহ |
| d | `validateSchema` | soft | JSON schema যাচাই, policy চাইলে |
| e | `softAssert.assertAll()` | | সব soft failure একসঙ্গে জানায় |

`ProductsPositiveTests` একই পথে চলে, কিন্তু `validateAll`-এর বদলে `validateStatusCode`, `validateHeaders`,
`validateBody` ও `validateSchema` একটা একটা করে call করে।

### একটা workflow test

```
WorkflowPostProductTests.<test>()                      (BaseTest-কে extend করে, তাই ওপরের (1) ধাপ প্রযোজ্য)
  -> createProduct(payload)   -> sendRequestWithTimeoutHandling(reqSpec_base, "/products", POST, headers, payload, "Workflow: ...")
        -> APIClient.sendRequest(...)                   headers = HeaderFactory.bearerAuthHeadersForFullPermissionUser()
  -> extractProductId(response)                         response থেকে "id" পড়ে
  -> getProduct(id)           -> sendRequestWithTimeoutHandling(...)    একই call, অন্য method ও endpoint /products/{id}
  -> Assert.assertEquals(...) / .then().body(...)       আলাদা call-এর response মেলায়
```

### একটা login test

`LoginNegativeDataProvider` (description, username, password) দেয়।
`LoginNegativeTests.shouldRejectLogin_withInvalidCredentials` `AuthUtils.callLoginEndpointRaw(username, password)` call
করে এবং 401 যাচাই করে।

### Shared helper-কে কে call করে

| Helper | কে call করে |
|---|---|
| `ConfigUtils.getBaseUri()` | `BaseTest` (`BASE_URI` constant), `RequestSpecFactory` (দুটো method-ই) |
| `RequestSpecFactory.baseRequestSpecBuilder()` | শুধু `BaseTest.setupBaseSpec()` |
| `RequestSpecFactory.loginRequestSpecification()` | শুধু `AuthUtils.callLoginEndpointRaw()` |
| `AuthUtils.login()` | শুধু `HeaderFactory.bearerAuthHeaders()` |
| `AuthUtils.callLoginEndpointRaw()` | `AuthUtils.login()`, `LoginNegativeTests` |
| `APIClient.sendRequest()` | `BaseTest.sendRequestWithTimeoutHandling()` |
| `BaseTest.sendRequestWithTimeoutHandling()` | test method গুলো ও workflow class-এর helper method গুলো |

## 8. নতুন scenario যোগ করা

### একটা negative scenario যোগ করা

1. Operation-এর data provider খুলুন, যেমন `POST /products`-এর জন্য `PostProductsDataProvider` (negative method)।
2. আপনার scenario-র সবচেয়ে কাছের record কপি করুন এবং field গুলো বদলান:

| Field | কী দেবেন |
|---|---|
| description | নম্বর ও নাম, যেমন `"3.6. Empty name-Parsing Layer"`। Layer-এর নম্বর ব্যবহার করুন (নিচের table) |
| endpoint, method | যেমন `"/products"`, `POST` |
| headers | `HeaderFactory`-র একটা method (নিচের table দেখুন) |
| payload | resource-এর বৈধ payload, বা তার payload factory থেকে একটা ভাঙা payload (দরকার হলে সেখানে নতুন একটা যোগ করুন) |
| expected | `expect(status, policy)` |

3. **Policy** বাছুন (status code ছাড়া আর কী যাচাই হবে), [section 9](#9-validation-policy-বাছা) দেখুন।
4. Scenario-তে ঠিক **একটা** field বা header-এর নিয়ম ভাঙুন। Field-এর নিয়ম পড়ুন contract-এ operation-এর **Schema** view
   থেকে (Swagger UI-তে example payload-এর পাশের Schema button), example payload থেকে নয়: example শুধু একটা বৈধ নমুনা।
   যে নিয়ম গুলো দেখতে হবে: type, required, length, range, format, enum ও unique। ভাঙা যায় এমন প্রতিটা নিয়মের জন্য একটা
   negative scenario লিখুন, আর সীমার ধারের একটা মানের জন্য একটা positive scenario (যেমন অনুমোদিত সর্বোচ্চ length)।
5. Suite চালান। Backend অন্য status code দিলে ঠিক করুন কোনটা ভুল: scenario না backend।

যে scenario-র **আগে কিছু থাকা দরকার** (যেমন একটা product আগে থাকতে হবে, যাকে পরে duplicate করা হবে), তা data row-তে
মানায় না। একটা workflow test লিখুন (section 6 দেখুন)।

### Layers (scenario-র নম্বরে ব্যবহৃত)

| Layer | কী test করে | Status code |
|---|---|---|
| 0 | Infrastructure: request size limit (gateway) | 413 |
| 1A | Authentication: credential নেই বা অবৈধ | 401 |
| 1B | Authorization: বৈধ user কিন্তু permission নেই | 403 |
| 2A | Routing, path resolution: অজানা path, path parameter নেই | 404 |
| 2B | Routing, method resolution: path আছে, method নেই | 405 |
| 2C | Request media type (`Content-Type`) | 415 |
| 2D | Content negotiation (`Accept`) | 406 |
| 3 | Parsing: ভাঙা body, body নেই, ভুল type, অজানা field, query parameter নেই | 400 |
| 4 | Constraint validation: field নেই, range, format, cross-field | 422 |
| 5 | Controller/service: record নেই, duplicate | 404, 409 |

### কোন scenario-তে কোন header method

সব method `HeaderFactory`-তে। নাম শুরু হয় authentication scheme দিয়ে, তাই IDE-র auto-complete এগুলো একসঙ্গে দেখায়।
একই তালিকা `HeaderUtils_MethodList.txt`-এও আছে।

| Scenario | Method |
|---|---|
| Happy path | `bearerAuthHeadersForFullPermissionUser()` বা `basicAuthHeadersForFullPermissionUser()` |
| Authorization header নেই (401) | `noAuthenticationHeaders()` |
| জাল Bearer token (401) | `bearerAuthHeadersWithInvalidToken()` |
| ভুল Basic credential (401) | `basicAuthHeadersWithInvalidCredentials()` |
| ভাঙা Basic header (401) | `basicAuthHeadersWithoutBase64Encoding()` বা `basicAuthHeadersWithoutColon()` |
| Permission ছাড়া role (403) | `bearerAuthHeadersForRole(roleName)` বা `basicAuthHeadersForRole(roleName)` |
| ভুল `Content-Type` (415) | `xmlContentTypeHeaders()` (payload একটা XML **String** হতে হবে) |
| ভুল `Accept` (406) | `xmlAcceptHeaders()` |
| বাকি সব (422, 404, 409, 400 ...) | happy-path header: এই error গুলো backend request-এর data থেকে তৈরি করে |

## 9. Validation policy বাছা

একটা scenario-র expected result হলো `expect(status, policy)`। Policy বলে status code ছাড়া **আর** কী যাচাই হবে।
নিয়ম: জিজ্ঞেস করুন **response-এর body কে ঠিক করে**।

| Response... | Policy | Example |
|---|---|---|
| API contract ঠিক করে (spec-এ তার schema আছে) | `validateSchemaOnly(SCHEMA, "reason")` | 200, 401, 403, 406, 409, 415, 422, validation থেকে 400 |
| server বা gateway বানায়, contract নয় | `skipSchemaAndAllowHeaderValidation("reason")` | 413, routing থেকে 404 বা "record not found" |
| একটা 405 | `validateAllowHeaderOnly("reason")` | যে operation নেই তার response spec ঠিক করতে পারে না; RFC 9110 তবু `Allow` header চায় |

`reason` একটা বাক্যে লিখুন যা বেছে নেওয়ার কারণ বোঝায়। এটা console-এ ছাপা হয়, Allure report-এ দেখায়, আর fail করা
assertion-এর বার্তায় যোগ হয়।

## 10. Report

ফলাফল লেখা হয় `allure-results`-এ (project-এর root-এর একটা folder)। একটা test থেকে একটা `<uuid>-result.json` file হয়,
আর ওই test-এর প্রতিটা attachment-এর জন্য একটা `.html` file ("Request" ও response, যেমন "HTTP/1.1 401 Unauthorized")।

Report খুলতে:

- Maven দিয়ে (Allure Maven plugin `pom.xml`-এ সেট করা): `mvn allure:serve`

একটা test-এর report পাতায় কী দেখায় এবং প্রতিটা অংশ কোথা থেকে আসে:

| পাতার অংশ | কোথা থেকে |
|---|---|
| **Parameters**, `arg0: TestScenarioRecord[...]` লাইন | test-এর argument (record), যা `TestScenarioRecord.toString()` দিয়ে লেখা হয়। এই লেখা result JSON-এর **ভেতরে** থাকে |
| **Test body > "Request"** | পাঠানো HTTP request-এর HTML ছবি (URL ও method, body, header), `BaseTest`-এর যোগ করা Allure filter বানায় এবং `tpl/http-request-masked.ftl` দিয়ে আঁকা হয়। একটা **আলাদা** `.html` file-এ থাকে; JSON শুধু সেটার দিকে ইঙ্গিত করে |
| **Test body > "HTTP/1.1 ..."** | response, Allure-এর default template-এ আঁকা |

ফলাফল যেভাবে লেখা হয়েছিল সেভাবেই থাকে: report যা দেখায় তা বদলালে (যেমন masking), আগে পুরোনো `allure-results` মুছুন
(`mvn clean` এটা করে)।

## 11. Report-এ secret লুকানো

`Authorization` header-এ একটা secret থাকে: Bearer token, বা Basic হলে `username:password`-এর base64 (base64 encryption
নয়: যে কেউ decode করতে পারে)। কিছু না করলে এটা report-এ ছাপা হয়ে যেত। Framework নিচের জায়গাগুলোতে এটা লুকায়। শুধু
**report যা দেখায়** তা বদলায়: server-এ যাওয়া request-এ আসল header-ই থাকে।

### Secret কোথায় দেখা যেত, আর কে লুকায়

```
একটা test, যেমন ProductsNegativeTests.testPostProductsNegativeScenarios(record)
 |
 |-- Allure পাতার "Parameters" অংশ (arg0), TestNG console, test-output
 |       লেখা বানায়  TestScenarioRecord.toString()                        -> header লুকায়
 |
 |-- যে request পাঠানো হয়: APIClient.sendRequest(reqSpec_base, ...)    আসল header বহন করে
 |       '-- Allure filter (BaseTest-এ যোগ করা) "Request" attachment বানায়
 |               আঁকে  src/test/resources/tpl/http-request-masked.ftl      -> header লুকায়
 |
 '-- login request (AuthUtils): RequestSpecFactory.loginRequestSpecification()
         এতে Allure filter নেই, তাই report-এ কখনও আসে না
```

| Secret যেখানে দেখা যেত | কে লুকায় | বদলে কী দেখায় |
|---|---|---|
| Allure পাতা, **Test body > "Request"**, `authorization: ...` লাইন (Allure-এর default template এটা দুবার ছাপে: Headers তালিকায় ও Curl block-এ) | `src/test/resources/tpl/http-request-masked.ftl` template, যা `BaseTest.setupBaseSpec()`-এ সেট করা | `authorization: Bearer [masked]`; Curl block নেই |
| Allure পাতা, **Parameters > `arg0`**, এবং TestNG console ও `test-output` | `TestScenarioRecord.toString()` | `headers={authorization=Bearer [masked], ...}` |
| Login request (এর body-তে password থাকে) | লুকানো হয় না, কিন্তু কখনও record-ই হয় না: `AuthUtils` `RequestSpecFactory.loginRequestSpecification()` ব্যবহার করে, যাতে Allure filter বা request logging নেই | কিছুই না |

দুটো সমাধান পরস্পর স্বাধীন। কেউ কারো জায়গা ঢাকে না, তাই দুটোই দরকার:

| আপনার কাছে কী আছে | "Request" attachment | `arg0` ও `test-output` |
|---|---|---|
| শুধু template | লুকানো | **token দেখা যায়** |
| শুধু `toString()` | **token দেখা যায়** | লুকানো |
| দুটোই (framework যা করে) | লুকানো | লুকানো |

### .ftl file কী

FTL মানে FreeMarker Template Language। Template হলো একটা নির্দিষ্ট নকশা যার ফাঁকা জায়গা প্রতিবার ভরা হয়, অনেকটা
mail-merge চিঠির মতো। Allure FreeMarker সঙ্গে আনে এবং প্রতিটা attachment এভাবে আঁকে। Allure-এর default request
template তার jar-এর ভেতরে থাকে; আমাদেরটা সেটার জায়গা নেয়। Template নিজে থেকে চলে না: প্রতিটা request-এর জন্য Allure filter
request-এর data (URL, method, header, body, cookie) FreeMarker-কে দেয়, সে নকশা ভরে HTML ফেরত দেয়। সেই HTML-ই
"Request" attachment হিসেবে সংরক্ষিত হয়। Template কখনও `TestScenarioRecord` দেখে না, তাই `toString()`-ও দরকার।

আমাদের template Allure-এর default থেকে তিন দিকে আলাদা:

1. `Authorization` header-এর মান লুকায় এবং scheme রাখে: `Bearer [masked]`, `Basic [masked]`।
2. Curl block বাদ দেয়, যা প্রতিটা header (token-সহ) আর পুরো body দ্বিতীয়বার ছাপে।
3. `maxBodyLength` (5000) অক্ষরের চেয়ে লম্বা body ছাঁটে এবং আসল দৈর্ঘ্য বলে। Oversized-payload scenario প্রায় ১২ মিলিয়ন
   অক্ষর পাঠায়; সব দেখাতে গেলে browser tab-এর memory শেষ হয়ে যায় (`STATUS_BREAKPOINT`)।

এটা `BaseTest.setupBaseSpec()`-এ সেট করা:

```java
.addFilter(new AllureRestAssured().setRequestTemplate("http-request-masked.ftl"))
```

নামটা `tpl` folder-এর সাপেক্ষে (Allure classpath-এর `tpl` folder-এ খোঁজে), তাই `"tpl/http-request-masked.ftl"`
লিখবেন **না**: তাহলে `tpl/tpl/http-request-masked.ftl` খোঁজা হবে এবং পাওয়া যাবে না।

### Masking বদলানো

| কী চান | কোথায় |
|---|---|
| অন্য header লুকাতে (যেমন cookie বা API key) | `.ftl`-এর `name?lower_case == "authorization"` শর্তে **এবং** `TestScenarioRecord.toString()`-এর যাচাইয়ে। দুটোই বদলান এবং output-এর ধরন একই রাখুন |
| লম্বা body-র কম বা বেশি অক্ষর দেখাতে | `.ftl`-এর ওপরের `maxBodyLength` |
| Response-ও লুকাতে | filter-কে `setResponseTemplate("...")` দিয়ে response template দিন, সেটাও `tpl` folder-এ |

### কাজ করছে কি না দেখা

1. একটা test চালান এবং report-এ তার পাতা খুলুন।
2. **Test body > "Request"**: `authorization: Bearer [masked]`, আর কোনো "Curl" block নেই।
3. **Parameters > `arg0`** (লাইনটা "..." দিয়ে ছাঁটা: ক্লিক করুন): `headers={authorization=Bearer [masked], ...}`।
4. Windows-এ project-এর root থেকে; কোনো file-এর নাম ছাপা উচিত নয় (এটা JWT token-এর শুরু খোঁজে, তাই শুধু JWT token-এর জন্য
   কাজ করে):

```
findstr /s /i /m "Bearer eyJ" allure-results\* test-output\*
```

### যা ঢাকা হয় না

- শুধু `Authorization` header লুকানো হয়। অন্য secret header (Cookie, API key) এর জন্য ওপরের পরিবর্তন লাগে।
- Response attachment Allure-এর default template ব্যবহার করে। যে আসল backend secret ফেরত দেয় (`Set-Cookie` header, body-তে
  token), তার জন্য response template লাগে।
- Request-এর **body**-র ভেতরের secret লুকানো হয় না। আজ শুধু login request-এ আছে, যা কখনও record হয় না।
- `TestScenarioRecord.toString()` payload পুরোটা ছাপে ([জানা সীমাবদ্ধতা](#12-জানা-সীমাবদ্ধতা) দেখুন)।

### গোছগাছ

- পুরোনো ফলাফলে পুরোনো content-ই থাকে: masking যোগ বা বদলানোর পর `allure-results` (`mvn clean` করে) আর `test-output`
  (হাতে) মুছে আবার চালান।
- `allure-results`, `test-output` বা `target` commit বা শেয়ার করবেন না। Project-এর `.gitignore` এগুলো (আর `.allure` folder,
  যা Maven Allure tool দিয়ে ভরে) তালিকাভুক্ত করেছে।

## 12. জানা সীমাবদ্ধতা

| সীমাবদ্ধতা | প্রভাব |
|---|---|
| Token পুরো run-এ আবার ব্যবহার হয়, যদি না সেগুলো `exp` claim-সহ JWT হয় | Backend মেয়াদ-শেষ হওয়া non-JWT token দিলে পরের দিকের scenario 401 পেতে পারে |
| Timeout ৩ সেকেন্ড (`RequestSpecFactory`), test request ও login-এর জন্য | ধীর server timeout করতে পারে, আর timeout একটা failure। Upload বেশি সময় নিলে scenario 0.1-এর 11 MB payload-এর জন্য সময় বাড়ানো উচিত |
| Scenario গুলো data (product, user, order) বানায় কিন্তু মোছে না | Seed data ফিরে পেতে local backend restart করুন; শেয়ার করা backend-এ unique data ব্যবহার করুন (test গুলো যেমন করে) |
| প্রতি account-এর একটাই cart | একই account-এর cart ব্যবহারকারী test parallel চলতে পারে না (section 2, Parallel execution দেখুন) |
| Test গুলো backend-এর permission matrix আর role account ব্যবহার করে | অন্য role-ওয়ালা backend-এর জন্য `config.properties`-এ অন্য role key লাগবে |
| "Request" attachment body-র প্রথম 5000 অক্ষরই দেখায় | Oversized-payload scenario-র (প্রায় ১২ মিলিয়ন অক্ষর) জন্য report body-র শুধু শুরুটুকু দেখায় |
| `TestScenarioRecord.toString()` payload পুরোটা ছাপে | Oversized-payload scenario-তে `arg0` parameter প্রায় ১২ মিলিয়ন অক্ষর লম্বা: result JSON বড় হয় |
| শুধু `Authorization` header লুকানো হয় | অন্য secret header যেমন আছে তেমন দেখায় ([section 11](#11-report-এ-secret-লুকানো) দেখুন) |

## 13. সমস্যা সমাধান

| লক্ষণ | সম্ভাব্য কারণ ও সমাধান |
|---|---|
| `CRITICAL: Server is DOWN!` | `shop-api` চালু নেই (আগে চালু করুন), `baseUri` ভুল, বা server `GET /ping`-এ 200 দিচ্ছে না |
| `'baseUri' is not configured` (বা অন্য key) | Key `config.properties`-এ নেই এবং কোনো `-D` option দেওয়া হয়নি। Section 4-এর সব key বাধ্যতামূলক |
| `Login failed for user '...'` | `config.properties`-এ username/password ভুল, বা login endpoint-এ পৌঁছানো যাচ্ছে না। এটা data-provider error হিসেবে আসে |
| অনেক scenario 401-এ fail | `auth.validUser`-এর account এই backend-এ নেই, বা password আলাদা |
| একটা 403 scenario 200 পাচ্ছে | ওই key-র role account-এর ওই permission আছে। যে account-এর ওই action নিষিদ্ধ সেটা ব্যবহার করুন |
| Scenario 0.1 413-এর বদলে 200 বা 201 পাচ্ছে | Payload backend-এর request-size limit-এর চেয়ে বড় নয়, বা backend-এ কোনো limit নেই |
| Scenario 0.1 connection error-এ fail করছে | Client বড় body পাঠানোর মাঝেই server connection বন্ধ করে দিয়েছে। Server-কে বাকি body পড়ে ফেলে দিতে দিন |
| অজানা path-এ POST 404-এর বদলে 405 পাচ্ছে | Backend অজানা path-এ static-resource handler দিয়ে উত্তর দিচ্ছে (Spring Boot: `spring.web.resources.add-mappings=false` দিন) |
| Happy-path scenario বা workflow test 409 পাচ্ছে | `sku` বা username unique নয়: payload factory-র সেই method দিয়ে payload বানান যা প্রতিবার নতুন মান দেয় |
| `POST /orders` 400 পাচ্ছে | `Idempotency-Key` header নেই, বা তার দৈর্ঘ্য 8 থেকে 64 অক্ষরের মধ্যে নয় |
| `POST /orders` 409 পাচ্ছে | Cart খালি বা কোনো product-এর stock কম। অন্য কোনো test cart খালি করে থাকতে পারে: cart-এর test গুলো parallel চলছে না তো, দেখুন |
| `mvn test` smoke test দুবার চালাচ্ছে | `pom.xml`-এ দুটো suite file-ই দেওয়া, আর smoke test class দুটোতেই আছে। এক suite চালাতে `-Dsurefire.suiteXmlFiles=regression-test.xml` দিন |
| `setupBaseSpec` fail করে এবং প্রতিটা test skip হয়, সঙ্গে `AttachmentRenderException: Could't render http attachment file` | `Caused by:` লাইন দেখুন। `TemplateNotFoundException` হলে template-এর নাম ভুল: এটা `tpl` folder-এর সাপেক্ষে (`http-request-masked.ftl`, `tpl/` ছাড়া) |
| একটা test-এর "Request" খুললে browser tab crash করে (`STATUS_BREAKPOINT`) | খুব বড় body (oversized-payload scenario)। লম্বা body ছাঁটা template সেট আছে কি না দেখুন ([section 11](#11-report-এ-secret-লুকানো)) |
| Report এখনও token দেখাচ্ছে | ওই ফলাফল masking আসার আগে লেখা। `allure-results` ও `test-output` মুছে আবার চালান |
| `Data provider mismatch ... Arguments: []` | কোনো data provider-এ খালি row (`{}`) আছে। মুছুন বা একটা scenario দিয়ে ভরুন |

## আরও পড়ার জায়গা

- `HeaderFactory.java`: ওপরের comment-এ method-এর নাম রাখার নিয়ম বোঝানো আছে।
- `RequestSpecFactory.java`: class-এর comment-এ দুটো request spec আর login spec-এ Allure filter কেন নেই তা বোঝানো আছে।
- `BaseTest.java`: `.addFilter(...)`-এর ওপরের comment-এ Allure filter আর token দেখা যাওয়ার দুটো জায়গা বোঝানো আছে।
- `TestScenarioRecord.java`: `toString()`-এর ওপরের comment-এ record-এর masking বোঝানো আছে।
- `tpl/http-request-masked.ftl`: ওপরের comment-এ Allure-এর default template থেকে কী আলাদা তার তালিকা আছে।
- `config.properties`: comment-এ setting, role key ও permission matrix বোঝানো আছে।
- `SchemaAndAllowHeaderValidationPolicy.java`: comment-এ policy আর এর field গুলো static নয় কেন তা বোঝানো আছে।
- `OpenApiSchemaGenerator.java`: class-এর comment-এ `openapi.yaml` থেকে schema file কীভাবে বানানো হয় তা বোঝানো আছে।
