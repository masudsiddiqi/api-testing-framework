package com.api.tests.users;

import static com.api.client.HttpMethods.GET;
import static com.api.client.HttpMethods.POST;
import static com.api.utils.payload.UsersPayloadFactory.getValidUsersPayloadPOJO;
import static org.hamcrest.Matchers.equalTo;

import java.util.Locale;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.api.base.BaseTest;
import com.api.pojo.users.UsersFields;
import com.api.pojo.users.UsersPayloadPOJO;
import com.api.utils.HeaderFactory;

import io.restassured.response.Response;

// ============================================================================
// LAYER 5 - BUSINESS LOGIC / WORKFLOW & STATE VALIDATION
// ============================================================================
// Resource-based organization: this class is scoped to one resource (Users).
// A resource's life cycle is one concern, however many URL paths it uses here:
// POST /users (register), POST /auth/login and GET /users/me (read the state back).
// A new resource gets its own class with the same convention (Example: OrdersWorkflowTests).
//
// Why this class exists separately:
// UsersNegativeTests and UsersPositiveTests (via ResponseValidator) check ONE response at a time.
// The tests here compare the results of SEVERAL calls: register a user, then log in
// and read the profile, or register the same user twice. That is a question of state
// across calls, so it is outside the scope of ResponseValidator.
//
// What is NOT here: the Users API has no PUT, PATCH, DELETE or GET /users/{id}
// (see the spec). So the delete, PUT-idempotency and PATCH-partial-update tests of
// BookingWorkflowTests have no counterpart in this class.
//
// ============================================================================

public class UsersWorkflowTests extends BaseTest {

	// The only lines to change when this class is pasted for another resource:
	private static final String ENDPOINT = "/users";
	private static final String KEY_USERNAME = UsersFields.KEY_USERNAME;
	private static final String KEY_PASSWORD = UsersFields.KEY_PASSWROD;
	private static final String KEY_EMAIL = UsersFields.KEY_EMAIL;

	// The other calls of this workflow. They belong to the same user life cycle.
	private static final String LOGIN_ENDPOINT = "/auth/login";
	private static final String ME_ENDPOINT = ENDPOINT + "/me";
	private static final String KEY_TOKEN = "token";

	// ------------------------------------------------------------------------------
	// WHY sendRequestWithTimeoutHandling() AND NOT APIClient.sendRequest()
	// ------------------------------------------------------------------------------
	// Both send the same request. The difference is what happens when the request
	// cannot be completed at all (timeout, connection refused, ...):
	//
	//  - APIClient.sendRequest() lets the exception of the HTTP client pass
	//    unchanged. The test then fails with a bare "SocketTimeoutException: Read
	//    timed out" that does not say which call it was. Allure usually shows such a
	//    test as "broken".
	//
	//  - BaseTest.sendRequestWithTimeoutHandling() catches that exception and rethrows
	//    an AssertionError whose message names the call we pass as the last argument,
	//    for example "Timeout for Scenario: Workflow: GET /users/me -> ...".
	//
	// A workflow test sends several requests one after another. When one of them
	// fails, the message must say WHICH step it was. That is why every helper below
	// passes a description such as "Workflow: GET /users/me".
	//
	// The wrapper does not change the request that is sent. An HTTP error status
	// (404, 500, ...) is not an exception: it comes back as a normal Response, as
	// with sendRequest(). The wrapper is inherited from BaseTest.
	// ------------------------------------------------------------------------------

	// ------------------------------------------------------------------------------
	// Reusable request-execution helpers.
	// The last argument of each call is a description of the step. It is used only
	// when sending the request throws an exception (for example a timeout or a refused
	// connection). It is not used when the server answers, even with an error status
	// such as 404 or 409.
	// ------------------------------------------------------------------------------

	// POST /users is public (security: [] in the contract), so no Authorization header.
	private Response registerUser(UsersPayloadPOJO payload) {
		return sendRequestWithTimeoutHandling(reqSpec_base, ENDPOINT, POST,
				HeaderFactory.noAuthenticationHeaders(), payload,
				"Workflow: POST " + ENDPOINT);
	}

	// POST /auth/login is public too. The body keys (username, password) are the same
	// names as in the registration body of this API.
	private Response login(String username, String password) {
		Map<String, String> loginBody = Map.of(KEY_USERNAME, username, KEY_PASSWORD, password);
		return sendRequestWithTimeoutHandling(reqSpec_base, LOGIN_ENDPOINT, POST,
				HeaderFactory.noAuthenticationHeaders(), loginBody,
				"Workflow: POST " + LOGIN_ENDPOINT);
	}

	// GET /users/me needs the token of the user who logged in. The headers are the plain
	// public ones plus one Authorization header.
	private Response getCurrentUser(String token) {
		Map<String, String> headers = HeaderFactory.noAuthenticationHeaders();
		headers.put("Authorization", "Bearer " + token);
		return sendRequestWithTimeoutHandling(reqSpec_base, ME_ENDPOINT, GET,
				headers, null,
				"Workflow: GET " + ME_ENDPOINT);
	}

	private void assertUserCreated(Response response) {
		// Check the status first. Then the failure message says what really happened.
		Assert.assertEquals(response.getStatusCode(), 201,
				"The user was not created. Response body: " + response.asString());
	}

	private String extractToken(Response loginResponse) {
		// If the login failed, there is no token to read.
		Assert.assertEquals(loginResponse.getStatusCode(), 200,
				"The login failed. Response body: " + loginResponse.asString());
		return loginResponse.jsonPath().getString(KEY_TOKEN);
	}

	// LAYER 5.1 - Data Persistence Validation ('Database Write via POST/users' validation)
	@Test(enabled = true, priority = 40,
			description = "Layer 5.1: After registering a user via POST /users, logging in with the same credentials and calling GET /users/me should return the same username and email — i.e. verifying the account was actually persisted")
	void shouldPersistRegisteredUser_andMatchOnProfile() {

		UsersPayloadPOJO requestPayload = getValidUsersPayloadPOJO();

		assertUserCreated(registerUser(requestPayload));

		String token = extractToken(login(requestPayload.getUsername(), requestPayload.getPassword()));

		Response profileResponse = getCurrentUser(token);

		Assert.assertEquals(profileResponse.getStatusCode(), 200);

		profileResponse.then()
				.body(KEY_USERNAME, equalTo(requestPayload.getUsername()))
				.body(KEY_EMAIL, equalTo(requestPayload.getEmail()));
	}

	// DUPLICATE POST: ONLY THE "REFUSE" VARIANT IS HERE
	// The contract/OpenAPI doc of POST /users lists 409. So the backend must refuse a duplicate,
	// and only the test that expects 409 applies (Layer 5.2 and 5.3 below).
	// A backend that ACCEPTS duplicates would need the opposite test, as in BookingWorkflowTests (5.3b).

	// LAYER 5.2 - Duplicate Resource Creation Request Validation (POST-specific)
	@Test(enabled = true, priority = 41,
			description = "Layer 5.2: The Server should REFUSE the second request when two POST /users requests have the exact same payload")
	void shouldRejectSecondRegistration_whenSameUserSentTwice() {

		// Make the payload only once. Send the same payload twice.
		// The first request always works, because the username was never used before.
		UsersPayloadPOJO requestPayload = getValidUsersPayloadPOJO();

		assertUserCreated(registerUser(requestPayload));

		Response secondResponse = registerUser(requestPayload);
		Assert.assertEquals(secondResponse.getStatusCode(), 409,
				"The same user sent twice must be refused. Response body: " + secondResponse.asString());
	}

	// LAYER 5.3 - Duplicate Resource Creation Request Validation (case-insensitive username)
	@Test(enabled = true, priority = 42,
			description = "Layer 5.3: A username that differs from an existing username only in letter case should be REFUSED — i.e. verifying that the case of a username does not matter")
	void shouldRejectUsername_whenOnlyTheLetterCaseDiffers() {

		UsersPayloadPOJO firstUser = getValidUsersPayloadPOJO();
		assertUserCreated(registerUser(firstUser));

		// A second, valid user. Its email is new, so the username is the only possible reason for a refusal.
		// Locale.ROOT: the result must not depend on the language setting of the machine.
		UsersPayloadPOJO sameNameInOtherCase = getValidUsersPayloadPOJO();
		sameNameInOtherCase.setUsername(firstUser.getUsername().toUpperCase(Locale.ROOT));

		Response secondResponse = registerUser(sameNameInOtherCase);
		Assert.assertEquals(secondResponse.getStatusCode(), 409,
				"A username that differs only in letter case must be refused. Response body: " + secondResponse.asString());
	}
}