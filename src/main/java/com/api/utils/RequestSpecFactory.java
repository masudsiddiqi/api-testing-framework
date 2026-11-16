package com.api.utils;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.LogConfig;
import io.restassured.specification.RequestSpecification;

/**
 * Builds the REST Assured request specs used by the framework. Both specs get
 * their base URI and their timeouts from this one class, so the two cannot drift
 * apart.
 *
 * Why the login request has its own spec:
 * AuthUtils.callLoginEndpointRaw() sends the login request, and it can run while a
 * DataProvider is building its test data (HeaderUtils -> AuthUtils.login()). TestNG
 * may run a DataProvider BEFORE @BeforeClass, so BaseTest.reqSpec_base may not exist
 * yet at that moment. loginRequestSpecification() therefore does not depend on
 * BaseTest or on any test lifecycle: it only reads ConfigUtils.
 *
 * baseRequestSpecBuilder() has no such need: it is called from
 * BaseTest.setupBaseSpec(), which itself runs in @BeforeClass. It lives here only so
 * that the base URI and the timeouts are defined in one place.
 *
 * Naming rule: <purpose>RequestSpecification() returns a finished
 * RequestSpecification; <purpose>RequestSpecBuilder() returns a RequestSpecBuilder
 * that the caller can still extend and must call build() on.
 *
 * Two specs, on purpose:
 *  - baseRequestSpecBuilder(): for the requests the tests send. BaseTest adds the
 *    Allure filter to it, builds it and keeps the result in reqSpec_base.
 *  - loginRequestSpecification(): for the login request. It has NO Allure filter and
 *    NO request logging, because the login request body contains the password, which
 *    must not end up in the Allure report or in the console log.
 */

public final class RequestSpecFactory {

	// Used both for connecting and for waiting for a response.
	private static final int TIMEOUT_MILLIS = 3000;

	private RequestSpecFactory() {
		// Utility class: no instances.
	}

	// The timeouts, shared by baseRequestSpecBuilder() and loginRequestSpecification().
	// They are settings of the HTTP client library that RestAssured uses internally (Apache
	// HttpClient), and that library enforces them:
	//   - http.connection.timeout: the connection to the server must be established within this time.
	//   - http.socket.timeout:     after that, it waits at most this long for data to arrive
	//                              (it is not a limit for the whole response).
	//
	// WHO THROWS THE EXCEPTION: when a limit is exceeded, Apache HttpClient throws it (typically a
	// ConnectTimeoutException for the connection and a SocketTimeoutException while waiting for data).
	// It is not thrown by RestAssured's own code or by this framework.
	// RestAssured does not handle it, and neither does APIClient.sendRequest(): they let it pass.
	// The exception travels up the call chain (through sendRequest(), where it is used) until some
	// code catches it. If no code catches it, it ends in TestNG, which fails the test (or the data
	// provider, or the @BeforeClass method).
	//
	// WHO HANDLES THE EXCEPTION: it depends on from where the request is sent:
	//   - requests sent through BaseTest.sendRequestWithTimeoutHandling(): Exception is rethrown 
	//     as an AssertionError that names the scenario
	//   - the login request: AuthUtils.callLoginEndpointRaw() rethrows the Exception
	//     as an IllegalStateException with the username (never the password)   
	//   - the /ping check: BaseTest.setupBaseSpec() rethrows it as "CRITICAL: Server is DOWN!"
	// A request that is sent in any other way (a new place in the future) is not handled: the
	// original exception reaches TestNG unchanged.
	private static HttpClientConfig httpClientConfig() {
		return HttpClientConfig.httpClientConfig()
				.setParam("http.connection.timeout", TIMEOUT_MILLIS)
				.setParam("http.socket.timeout", TIMEOUT_MILLIS);
	}

	
	// The BASE request spec, kept in BaseTest.reqSpec_base. It is the default spec for every request
	// whose request and response may be shown in the Allure report and in the console log: the
	// requests sent through APIClient.sendRequest() and the /ping health check in
	// BaseTest.setupBaseSpec().
	// A request that must NOT be shown there gets its own spec instead. Today that is only the login
	// request (its body contains the password): see loginRequestSpecification() below.
	//
	// It sets the base URI, the timeouts (3 s to connect, 3 s to wait for the response) and
	// "log the request and the response to the console
	// if a RestAssured validation (.then()...), not TestNG Assertion fails".
	//
	// It returns a RequestSpecBuilder, NOT a finished RequestSpecification (build() is not called here),
	// so that the caller can still add its own parts before building the final RequestSpecification.
	// BaseTest.setupBaseSpec() adds the Allure filter, calls build() and keeps the result in reqSpec_base.
	//
	// What is NOT part of this spec: headers (content-type, accept, Authorization), payload, HTTP
	// method and endpoint. They differ from scenario to scenario, so APIClient.sendRequest() supplies
	// them. It starts a new request from the built spec (RestAssured.given(reqSpec)) and adds the
	// headers and the payload to it. Method and endpoint are never stored in a RequestSpecification
	// here: they are only arguments of the send call (request.when().request(method, endpoint)).
	// "sendRequest()" makes up the full Java request (final RequestSpecification object + method + endpoint)
    // which RestAssured then converts into the actual HTTP request to send it over the network.
	//
	// NOTE: the framework's own ResponseValidator asserts with TestNG, so the logging above does not
	// trigger for those checks. It applies only to .then() checks (for example UsersWorkflowTests).
	
	public static RequestSpecBuilder baseRequestSpecBuilder() {
		return new RequestSpecBuilder()
				.setBaseUri(ConfigUtils.getBaseUri())
				.setConfig(RestAssured.config()
						.httpClient(httpClientConfig())
						.logConfig(LogConfig.logConfig()
								.enableLoggingOfRequestAndResponseIfValidationFails()));
	}

	// Base URI + timeouts only. No Allure filter and no request logging/printing, on purpose:
	// the login request body contains the username and the PASSWORD.
	//   - The Allure filter (BaseTest adds it to the test spec) attaches the request body to
	//     the Allure report, so the password would be readable in the report.
	//   - The request logging in testSpecBuilder() prints the request, body included, to the
	//     console whenever a validation (.then()) fails, so the password would end up in the log.
	// Keep it this way, also if a .then() check is added to the login call later.
	//
	// Called from: AuthUtils.callLoginEndpointRaw(), the only place that sends POST /auth/login.
	// That method is reached through AuthUtils.login() (used by HeaderUtils.bearerAuthHeaders(),
	// that is, by every Bearer header) and directly by LoginNegativeTests.
	public static RequestSpecification loginRequestSpecification() {
		return new RequestSpecBuilder()
				.setBaseUri(ConfigUtils.getBaseUri())
				.setConfig(RestAssured.config()
						.httpClient(httpClientConfig()))
				.build();
	}
}
