package com.api.dataProvider.users;


import static com.api.client.HttpMethods.POST;
import static com.api.dto.ExpectedStatusCode.expect;
import static com.api.schemaPathConstants.ManualSchemaPathConstants.*;
import static com.api.utils.HeaderFactory.bearerAuthHeadersForFullPermissionUser;
import static com.api.utils.HeaderFactory.noAuthenticationHeaders;
import static com.api.utils.HeaderFactory.xmlAcceptHeaders;
import static com.api.utils.HeaderFactory.xmlContentTypeHeaders;
import static com.api.utils.payload.PayloadUtils.readClasspathFile;
import static com.api.utils.payload.PayloadUtils.toXml;
import static com.api.utils.payload.UsersPayloadFactory.*;
import static com.api.validation.SchemaAndAllowHeaderValidationPolicy.skipSchemaAndAllowHeaderValidation;
import static com.api.validation.SchemaAndAllowHeaderValidationPolicy.validateAllowHeaderOnly;
import static com.api.validation.SchemaAndAllowHeaderValidationPolicy.validateSchemaOnly;

import org.testng.annotations.DataProvider;

import com.api.dto.TestScenarioRecord;

public class PostUsersDataProvider {
	
	@DataProvider(name="PostUsers-NegativeTestDataProvider")
	public Object[][] getPostUsersNegativeTestData(){
		
		// 413, 404 and 405 are not schema bound, rather server generated.
		// Therefore, these error codes must be tested for each operation 
		// even they are not listed in the OpenAPI Contract. 
		// As these error codes are not specified in the contract,
		// no schema validation is required for them.
		// "allow" header is valid only for 405.
		// No other status code has this rule.
		
		
		// WHICH SECURITY SCENARIOS APPLY TO AN OPERATION
		// If an operation is public, Authentication (401) and Authorization (403) are skipped.
		// If an operation has a security scheme but no permission rule, only Authentication (401) is tested.
		// If an operation has a security scheme and a permission rule, both 401 and 403 are tested.
		// A 403 response in the contract usually means that the operation has an access rule.
		// The rule can be about the role (a permission) or about the owner of a record.
		// Ask the developers which one it is, and which roles are denied.
		// A missing 403 does not prove that there is no rule: the contract can be incomplete.
		
		return new Object[][] {

			// new TestScenarioRecord(ScenarioDescription, Endpoint, HTTPMethod, Headers, Payload, ExpectedStatusCode)

			// Why the TestScenarioRecord takes 6 parameters:
			// Without a record, each 1D Object Array row holds 6 raw elements: 
			// [Description, Endpoint, Method, Headers, Payload, StatusCode].
			// To replace those 6 loose elements with a single record object element in the 1D array, 
			// all 6 elements must be transferred directly as the record's constructor parameter list.
			// Result: Object[6] (raw values) -> Object[1] containing TestScenarioRecord(6 parameters).
			// Test Method Signature: Since the 1D Array now contains only 1 element (the TestScenarioRecord object)
			// the test method must have a single parameter of type "TestScenarioRecord"

	
			// Layer 0: Infrastructure Layer Exception Handling Validation
			//
			// 413 (Payload Too Large) is answered by a body-size limit in front of the application code.
			// In many APIs it runs before security, routing and any business logic.
			// So the answer usually does not depend on the Authorization header or on the payload content.
			// The limit can still differ per path or per operation, and the test is cheap.
			// So every operation that takes a body tests it, and not only one operation of the API.
			{ new TestScenarioRecord(
					"0.1. Oversized Payload-API Gateway",
					"/users",
					POST,
					noAuthenticationHeaders(),
					// The payload must be bigger than the request-size limit of the API (Example: 10 MB).
					// A smaller body passes the limit and is processed normally, NOT rejected with 413.
					// OVERSIZE_PAYLOAD is a Supplier type object,
					// so its "get()" builds a new payload for every scenario
					// instead of sharing one object.
					OVERSIZE_PAYLOAD.get(),
					expect(413, skipSchemaAndAllowHeaderValidation(
							"413 is an infrastructure/gateway-layer response; its body can differ from the contract"))) },

			//------------------------------------------------------------------------------------------------
			// Layer 1: Security Chain Layer: Authentication and Authorization
			// This operation is public (the contract has no security rule for it).
			// So the Authentication (401) and Authorization (403) scenarios are skipped here:
			// the server does not check them for this operation.
			// Because of that, the scenarios of this provider send NO Authorization header.
			// Exception: the routing scenarios (2A, 2B) use a logged-in caller. See Layer 2A.
			//------------------------------------------------------------------------------------------------


			// Layer 2A: Routing & DispatcherServlet/HandlerMapping Layer Exception Handling Validation - path resolution
			//
			// A logged-in caller is used in Layer 2A and 2B on purpose. Why not an anonymous caller:
			// Many APIs are "secure by default": a path that matches no public rule needs a login.
			// Security runs before routing, and it does not know if a handler exists for the path.
			// So an anonymous caller gets 401 for such a path. The 401 also hides which paths exist.
			// A logged-in caller passes security. Then routing finds no handler, and the answer is 404 (or 405).
			// Some APIs answer 404 to everyone. A logged-in caller gets the same answer in both designs,
			// so these scenarios do not depend on that choice.
			// Example: in the shop-api, only a few paths are public. Any path except public ones needs a login.
			//
			// 2A.1: a path that the contract does not define at all.
			{ new TestScenarioRecord(
					"2A.1. Endpoint undefined in Spec - DispatcherServlet Routing Layer-'path' resolution",
					"/undefinedPath",
					POST,
					bearerAuthHeadersForFullPermissionUser(),
					getValidUsersPayloadPOJO(),
					expect(404, skipSchemaAndAllowHeaderValidation(
							"404 routing error response is generated by the server/gateway; not by the api contract"))) } ,

			// 2A.2: a path with a trailing slash is a different path from the one in the contract.
			// Example: the contract defines "/users", and the server has no handler for "/users/".
			// This depends on the web framework: some accept both paths, some do not.
			// Keep this scenario only if the team wants the strict behavior.
			// (It is not a "missing path parameter" scenario: this operation has no path parameter.)
			{ new TestScenarioRecord(
					"2A.2. Trailing Slash on the Path-DispatcherServlet Routing Layer-'path' resolution",
					"/users/",
					POST,
					bearerAuthHeadersForFullPermissionUser(),
					getValidUsersPayloadPOJO(),
					expect(404, skipSchemaAndAllowHeaderValidation(
							"No handler for '/users/': the 404 is generated by the server's routing, not by the API contract"))) } ,

			// Layer 2B: Routing & DispatcherServlet/HandlerMapping Layer Exception Handling Validation - method resolution
			//
			// 405 = the path is defined, but the HTTP method is not: an undefined operation (operation = method + path).
			// The spec defines responses per operation, so it cannot define a response for an undefined operation
			// (the spec has no 405 and no "default"). The body is generated by the server, so there is no schema to validate.
			// But RFC 9110 requires a 405 response to include the "Allow" header listing the supported methods,
			// so only the "Allow" header is validated.
			// A logged-in caller is used (see Layer 2A).
			{ new TestScenarioRecord(
					"2B.1. Unsupported HTTP Method for the schema defined endpoint-DispatcherServlet Routing-Method resolution",
					"/users/me",
					POST,
					bearerAuthHeadersForFullPermissionUser(),
					getValidUsersPayloadPOJO(),
					expect(405, validateAllowHeaderOnly(
							"405 body has no fixed contract, but RFC requires the Allow header to list supported methods"))) } ,


			// Layer 2C: Routing & DispatcherServlet/HandlerMapping Layer Exception Handling Validation - Incorrect Request Media Type (Content-Type)
			{ new TestScenarioRecord(
					"2C.1. Unsupported 'Content-Type' (XML)-Request Media Type",
					"/users",
					POST,
					xmlContentTypeHeaders(),
					toXml(getValidUsersPayloadPOJO()),
					expect(415, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


			// NOT TESTED in Layer 2C: a request WITH a body and "NO Content-Type" header.
			// RestAssured does not support it with its normal API. With a body it always adds a Content-Type:
			// if we pass none, it adds "text/plain; charset=ISO-8859-1" (visible in the Allure request
			// attachment), so the server receives a non-JSON Content-Type, not a missing one. Asking for none
			// with noContentType() fails for a request with a body ("Don't know how to encode ... as a byte
			// stream"). A request without a body can go out without a Content-Type - no payload, no content-type.
			// To test "no Content-Type" for real, send that request with another client (for example Java's
			// java.net.http.HttpClient or curl).
			// A non-JSON Content-Type is already covered by 2C.1 (application/xml).


			// Layer 2D: Routing & DispatcherServlet/HandlerMapping Layer Exception Handling Validation - Content Negotiation (Accept header) Matching
			//
			// "Content Negotiation" covers only the "Accept" header, NOT the "Content-Type" header.
			// Accept is a request header that states which media type the client wants for the
			// RESPONSE; the server picks one it can produce, or rejects with 406 if it cannot.
			{ new TestScenarioRecord(
					"2D.1. Unsupported 'Accept' Header-Content Negotiation",
					"/users",
					POST,
					xmlAcceptHeaders(),
					getValidUsersPayloadPOJO(),
					expect(406, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


			// Layer 3: Parsing & Syntax Validation Layer Exception Handling Validation
			//
			// Scenario 3.1 (missing required query param) is not used in this provider.
			// The reason: this operation has no query parameter at all.
			// The number 3.1 stays free, so the same number means the same scenario in every provider.
			// Example: in the shop-api, no operation has a required query parameter.

			// Layer 3: Parsing Layer - Unknown field
			// If the contract does not allow extra fields (additionalProperties: false),
			// the server must reject them while it reads the body (400). It must not ignore them.
			// 3.2a: a harmless name.
			// 3.2b and 3.2c: a name that the server sets itself (Mass Assignment / Over-Posting).
			{ new TestScenarioRecord(
					"3.2a. Unknown/Extra Field with a Harmless Name in Payload-Parsing Layer",
					"/users",
					POST,
					noAuthenticationHeaders(),
					UNKWON_KEY_PAYLOAD.get(),
					expect(400, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			{ new TestScenarioRecord(
					"3.2b. Extra Server-Made Sensitive Field 'role' in Payload (Mass Assignment / Over-Posting) in Payload-Parsing Layer",
					"/users",
					POST,
					noAuthenticationHeaders(),
					MASS_ASSIGNMENT_ROLE_PAYLOAD.get(),
					expect(400, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			{ new TestScenarioRecord(
					"3.2c. Extra Server-Made Sensitive Field 'id' in Payload (Mass Assignment / Over-Posting) in Payload-Parsing Layer",
					"/users",
					POST,
					noAuthenticationHeaders(),
					MASS_ASSIGNMENT_ID_PAYLOAD.get(),
					expect(400, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			// Layer 3: Parsing & Syntax Validation Layer Exception Handling Validation - Malformed JSON Syntax
			//
			// Syntactically invalid JSON cannot be read, so Spring rejects it while it reads the body
			// (HttpMessageNotReadableException): Request Parsing layer, before validation and the controller.
			// The backend answers with the spec-defined ProblemDetails (400), so the schema is validated.
			{ new TestScenarioRecord(
					"3.3. Malformed JSON Syntax-Parsing Layer",
					"/users",
					POST,
					noAuthenticationHeaders(),
					readClasspathFile("malformedPayload/malformed_json_request_payload.json"),
					expect(400, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			// Layer 3: Parsing Layer - Missing Request Body
			// No request body at all: there is nothing to deserialize,
			// so it fails at the PARSING stage, NOT at the Constraint validation Layer
			// Only "Missing Field", NOT "Missing Body" fails at the Constraint validation Layer
			{ new TestScenarioRecord(
					"3.4. Missing Request Body-Parsing Layer",
					"/users",
					POST,
					noAuthenticationHeaders(),
					null,
					expect(400, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			// Layer 3: Parsing Layer - Invalid Field Type
			// The JSON is valid, but a field has the wrong type.
			// Note: a number or a boolean for a text field may be accepted (converted to text) by the JSON library,
			// so a type that cannot be converted is used (for example an array for a text field).
			{ new TestScenarioRecord(
					"3.5. Invalid Data Types-Parsing Layer",
					"/users",
					POST,
					noAuthenticationHeaders(),
					INVALID_DATA_TYPE_PAYLOAD.get(),
					expect(400, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


			// Layer 4: Constraint Validation Layer Exception Handling Validation: Field Presence Constraint
			// The JSON is readable. Every required field is tested alone: the payload is valid in all other fields.
			{ new TestScenarioRecord(
					"4.1a. Missing Required Field 'username'-Presence Constraint Validation",
					"/users",
					POST,
					noAuthenticationHeaders(),
					MISSING_USERNAME_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			{ new TestScenarioRecord(
					"4.1b. Missing Required Field 'password'-Presence Constraint Validation",
					"/users",
					POST,
					noAuthenticationHeaders(),
					MISSING_PASSWORD_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			{ new TestScenarioRecord(
					"4.1c. Missing Required Field 'email'-Presence Constraint Validation",
					"/users",
					POST,
					noAuthenticationHeaders(),
					MISSING_EMAIL_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			// Layer 4: Constraint Validation Layer Exception Handling Validation: Length Constraint
			// The rule is about the length of a text. (A Range constraint is about the value of a number.)
			// Boundary: the value next to the limit. Extreme: a value far from the limit (for example empty).
			// The valid boundaries (the minimum and the maximum itself) are in the positive data provider.
			//
			// Username
			{ new TestScenarioRecord(
					"4.2a. Username One Below the Minimum Length-Length Constraint Validation (boundary)",
					"/users",
					POST,
					noAuthenticationHeaders(),
					USERNAME_TOO_SHORT_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			{ new TestScenarioRecord(
					"4.2b. Username One Above the Maximum Length-Length Constraint Validation (boundary)",
					"/users",
					POST,
					noAuthenticationHeaders(),
					USERNAME_TOO_LONG_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			// An empty username has 0 characters. It breaks two rules at once: the length (minimum)
			// and the format (at least one character). It cannot break only one of them.
			// Example: the shop-api has @Size(min = 3) and @Pattern("[A-Za-z0-9_]+") on the username.
			{ new TestScenarioRecord(
					"4.2c. Empty Username-Length Constraint Validation (extreme)",
					"/users",
					POST,
					noAuthenticationHeaders(),
					USERNAME_EMPTY_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			// Password
			{ new TestScenarioRecord(
					"4.2d. Password One Below the Minimum Length-Length Constraint Validation (boundary)",
					"/users",
					POST,
					noAuthenticationHeaders(),
					PASSWORD_TOO_SHORT_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			{ new TestScenarioRecord(
					"4.2e. Password One Above the Maximum Length-Length Constraint Validation (boundary)",
					"/users",
					POST,
					noAuthenticationHeaders(),
					PASSWORD_TOO_LONG_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			{ new TestScenarioRecord(
					"4.2f. Empty Password-Length Constraint Validation (extreme)",
					"/users",
					POST,
					noAuthenticationHeaders(),
					PASSWORD_EMPTY_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			// Layer 4: Constraint Validation Layer Exception Handling Validation: Format Constraint
			// The rule is about the form of a text: the allowed characters, or the form of an email.
			//
			// Username Format Validation
			{ new TestScenarioRecord(
					"4.3a. Username with a Hyphen-Format Constraint Validation (character not allowed)",
					"/users",
					POST,
					noAuthenticationHeaders(),
					USERNAME_WITH_HYPHEN_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			{ new TestScenarioRecord(
					"4.3b. Username with an Accented Letter-Format Constraint Validation (character not allowed)",
					"/users",
					POST,
					noAuthenticationHeaders(),
					USERNAME_WITH_ACCENT_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			// Email Format Validation
			{ new TestScenarioRecord(
					"4.3c. Email without an @-Format Constraint Validation",
					"/users",
					POST,
					noAuthenticationHeaders(),
					EMAIL_WITHOUT_AT_SIGN_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

			// The payload has nothing after the @ (Example: "customer@").
			// A domain without a dot (Example: "customer@example") is valid for the contract and
			// for the email check of the backend, so it is not used here.
			{ new TestScenarioRecord(
					"4.3d. Email without a Domain-Format Constraint Validation",
					"/users",
					POST,
					noAuthenticationHeaders(),
					EMAIL_WITHOUT_DOMAIN_PAYLOAD.get(),
					expect(422, validateSchemaOnly(
							ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


			// Layer 4: Constraint Validation Layer Exception Handling Validation: Cross-Field Constraint
			// Not applicable for the payload of "POST /users": no rule compares two fields.


			// Layer 5: Controller/Service Layer Exception Handling Validation
			//
			// Scenario 5.1 (path parameter value not found in the database) is not used in this provider.
			// The reason: this operation has no path parameter.
			//
			// Layer 5: Conflict / State Management - Duplicate Resource Creation Attempt (409)
			// These scenarios are NOT rows of this data provider: a duplicate needs a precondition
			// (the account must exist first), so they are stateful tests. They are done separately in UsersWorkflowTests:
			//   - shouldRejectSecondRegistration_whenSameUserSentTwice()
			//   - shouldRejectUsername_whenOnlyTheLetterCaseDiffers()
	};
			}
	
	
	
	
	@DataProvider(name = "PostUsers-PositiveTestDataProvider")
	public Object[][] getPostUsersPositiveTestData() {
		return new Object[][] {

			// The endpoint is public: no Authorization header is needed.
			// Every payload gives a NEW username on every get(), so a second run does not get 409.
			{ new TestScenarioRecord(
					"1. Create account with a valid payload (no Authorization header)",
					"/users",
					POST,
					noAuthenticationHeaders(),
					getValidUsersPayloadPOJO(),
					expect(201, validateSchemaOnly(
							USER_SUCCESS_SCHEMA, "Standard success-response schema applies"))) },

			// Valid boundaries
			{ new TestScenarioRecord(
					"2. Create account with the minimum username length (boundary)",
					"/users", POST, noAuthenticationHeaders(),
					VALID_USERNAME_MIN_LENGTH_PAYLOAD.get(),
					expect(201, validateSchemaOnly(
							USER_SUCCESS_SCHEMA, "Standard success-response schema applies"))) },

			{ new TestScenarioRecord(
					"3. Create account with the maximum username length (boundary)",
					"/users", POST, noAuthenticationHeaders(),
					VALID_USERNAME_MAX_LENGTH_PAYLOAD.get(),
					expect(201, validateSchemaOnly(
							USER_SUCCESS_SCHEMA, "Standard success-response schema applies"))) },

			{ new TestScenarioRecord(
					"4. Create account with the minimum password length (boundary)",
					"/users", POST, noAuthenticationHeaders(),
					VALID_PASSWORD_MIN_LENGTH_PAYLOAD.get(),
					expect(201, validateSchemaOnly(
							USER_SUCCESS_SCHEMA, "Standard success-response schema applies"))) },

			{ new TestScenarioRecord(
					"5. Create account with the maximum password length (boundary)",
					"/users", POST, noAuthenticationHeaders(),
					VALID_PASSWORD_MAX_LENGTH_PAYLOAD.get(),
					expect(201, validateSchemaOnly(
							USER_SUCCESS_SCHEMA, "Standard success-response schema applies"))) }
		};
	}


}
