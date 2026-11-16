
package com.api.client;

import java.util.Locale;
import java.util.Map;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import static com.api.client.HttpMethods.*;


/**
 * FRAMEWORK EXECUTION FLOW (where APIClient sits)
 * =========================================================================================================
 *
 *  mvn clean test  (or Eclipse: Run As > TestNG)
 *       │
 *       ▼
 *  TestNG   reads testng.xml and creates one object per test class
 *       │
 *       │  Before the @Test method runs, TestNG does both of the following.
 *       │  Which one comes first is decided by TestNG, not by this framework:
 *       │
 *       │   ├── @BeforeClass  BaseTest.setupBaseSpec()    (once per test class)
 *       │   │      builds reqSpec_base and checks /ping
 *       │   │
 *       │   └── @DataProvider method                      (once per @Test method)
 *       │          builds one TestScenarioRecord per row
 *       │          (headers via HeaderFactory, payload, expected status)
 *       │          and returns Object[][] to TestNG
 *       ▼
 *  TestNG calls the @Test method once per row and passes
 *  the TestScenarioRecord to it as the method argument
 *       │
 *       ▼
 *  @Test method
 *       │
 *       ▼
 *  BaseTest.sendRequestWithTimeoutHandling()      (a BaseTest method, not an APIClient method)
 *       │   any exception is turned into an AssertionError with the scenario description
 *       ▼
 *  APIClient.sendRequest()                        (this class)
 *       │   receives these arguments from the BaseTest wrapper:
 *       │     reqSpec                       = reqSpec_base: the base RequestSpecification
 *       │                                     built in @BeforeClass BaseTest.setupBaseSpec()
 *       │     endpoint, httpMethod,
 *       │     headers, payload              = data from the TestScenarioRecord
 *       │                                     (the data provider built it, TestNG handed it to the @Test method)
 *       │
 *       │   assembles the full Java request = final RequestSpecification object + method + endpoint
 *       │   (three parts, not one object; "full Java request" is only the name used in this diagram):
 *       │     RestAssured.given(reqSpec)                  base URI, 3 s timeouts, Allure filter (all from reqSpec_base)
 *       │     request.headers(headers)                    only if headers are given
 *       │     request.body(payload)                       only if a payload is given (still a Java object / String)
 *       │     request.when().request(method, endpoint)    method and endpoint are given here, at the send call;
 *       │                                                 this call also sends the request
 *       ▼
 *  RestAssured converts the full Java request into the actual HTTP request
 *       │   (a POJO payload is serialised to JSON, a String is sent as is;
 *       │    method + base URI + endpoint -> request line; headers are added),
 *       │   runs the filters (for example Allure) and sends it
 *       ▼
 *  HTTP Request  ──►  Server / API Under Test  ──►  HTTP Response
 *       │              - receives and processes the HTTP request
 *       │              - returns an HTTP response
 *       ▼
 *  RestAssured reads the HTTP response into a Java object
 *       ▼
 *  RestAssured Response object   (io.restassured.response.Response)
 *       │   an in-memory Java object that represents the server's HTTP response:
 *       │     ├── Status code & status line
 *       │     ├── Headers & cookies
 *       │     ├── Body (read as String / JSON / POJO on demand)
 *       │     └── Response time (measured by RestAssured, not part of the HTTP message)
 *       │
 *       │   sendRequest() returns this object. It travels back up the same call chain
 *       │   (APIClient -> BaseTest wrapper -> @Test method).
 *       ▼
 *  @Test method passes (response, record) to ResponseValidator
 *       │   negative tests: ResponseValidator.validateAll(response, record)
 *       │   positive test:  the four validate...() methods + softAssert.assertAll()
 *       ▼
 *  ResponseValidator   compares the Response object with the expected values held in the record
 *       │   - HARD assertions (fail fast):          status code, headers
 *       │   - SOFT assertions (reported together):  body, schema
 *       ▼
 *  TestNG records pass / fail for this row (the Allure listener writes the result)
 *
 * =========================================================================================================
 */



public class APIClient {


	/**
         * Generic request execution method for sending both Valid and Invalid HTTP requests.
         * Uses RestAssured dynamic request invocation to support all standard verbs.
         * 
         * @param reqSpec    Base RequestSpecification instance
         * @param endpoint   Target API endpoint path
         * @param httpMethod HTTP Verb (GET, POST, PUT, PATCH, DELETE)
         * @param headers    Request headers map (Null-safe handled)
         * @param payload    Object body payload (Null-safe handled)
         * @return Response object returned from RestAssured execution
         */
        public static Response sendRequest(RequestSpecification reqSpec, String endpoint,
                                           String httpMethod, Map<String, String> headers,
                                           Object payload) {
            
            // 1. Validate HTTP Method input
            if (httpMethod == null || httpMethod.isBlank()) {
                throw new IllegalArgumentException("HTTP Method cannot be null or blank");
            }

            // 2. Initialize RestAssured Request Specification
            RequestSpecification request = RestAssured.given(reqSpec);

            // 3. Null-safe headers attachment
            if (headers != null && !headers.isEmpty()) {
                request.headers(headers);
            }
            
            // 4. Null-safe payload attachment
            if (payload != null) {
                request.body(payload);
            }

            // 5. Normalize HTTP method string and execute dynamically
            String method = httpMethod.trim().toUpperCase(Locale.ROOT);

            return switch (method) {
                case GET, POST, PUT, PATCH, DELETE -> request.when().request(method, endpoint);
                default -> throw new IllegalArgumentException("Unsupported or invalid HTTP Method provided: " + httpMethod);
            };
        }
}
        