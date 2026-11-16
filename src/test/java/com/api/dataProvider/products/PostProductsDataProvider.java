package com.api.dataProvider.products;

import static com.api.client.HttpMethods.POST;
import static com.api.client.HttpMethods.PUT;
import static com.api.dto.ExpectedStatusCode.expect;
import static com.api.schemaPathConstants.GeneratedSchemaPathConstants.*;
import static com.api.schemaPathConstants.ManualSchemaPathConstants.ERROR_RESPONSE_SCHEMA;
import static com.api.utils.HeaderFactory.basicAuthHeadersForFullPermissionUser;
import static com.api.utils.HeaderFactory.basicAuthHeadersForRole;
import static com.api.utils.HeaderFactory.basicAuthHeadersWithInvalidCredentials;
import static com.api.utils.HeaderFactory.basicAuthHeadersWithoutBase64Encoding;
import static com.api.utils.HeaderFactory.basicAuthHeadersWithoutColon;
import static com.api.utils.HeaderFactory.bearerAuthHeadersForFullPermissionUser;
import static com.api.utils.HeaderFactory.bearerAuthHeadersForRole;
import static com.api.utils.HeaderFactory.bearerAuthHeadersWithInvalidToken;
import static com.api.utils.HeaderFactory.noAuthenticationHeaders;
import static com.api.utils.HeaderFactory.xmlAcceptHeaders;
import static com.api.utils.HeaderFactory.xmlContentTypeHeaders;
import static com.api.utils.payload.PayloadUtils.readClasspathFile;
import static com.api.utils.payload.PayloadUtils.toXml;
import static com.api.utils.payload.ProductsPayloadFactory.*;
import static com.api.validation.SchemaAndAllowHeaderValidationPolicy.skipSchemaAndAllowHeaderValidation;
import static com.api.validation.SchemaAndAllowHeaderValidationPolicy.validateAllowHeaderOnly;
import static com.api.validation.SchemaAndAllowHeaderValidationPolicy.validateSchemaOnly;

import org.testng.annotations.DataProvider;

import com.api.dto.TestScenarioRecord;

public class PostProductsDataProvider {

	@DataProvider(name = "PostProducts-NegativeTestDataProvider")
	public Object[][] getPostProductsNegativeTestData() {

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

		// WHICH SECURITY SCENARIOS APPLY TO AN OPERATION
		// If an operation is public, Authentication (401) and Authorization (403) are skipped.
		// If an operation has a security scheme but no permission rule, only Authentication (401) is tested.
		// If an operation has a security scheme and a permission rule, both 401 and 403 are tested.
		// A 403 response in the contract usually means that the operation has an access rule.
		// The rule can be about the role (a permission) or about the owner of a record.
		// Ask the developers which one it is, and which roles are denied.
		// A missing 403 does not prove that there is no rule: the contract can be incomplete.
		// Example: POST /products has a security scheme (Bearer or Basic) and a permission rule (product:create).

		// 413, 404 and 405 are often made by the server stack (gateway, web server or router)
		// before the code of the API runs. So their body can differ from the error format of the contract.
		// For this reason, the body of these three is NOT checked against a schema.
		// Example: a contract may list 413 with a standard error body. A gateway can still answer with its own body.
		//
		// They are tested for every operation, whether the contract lists them or not.
		// (413 needs an operation that takes a body. 404 and 405 are about the path and the method.)
		//
		// The Allow header is checked for 405 only.
		// The HTTP RFC says that a 405 response must list the supported methods in the Allow header.
		// No other status code has this rule.

		// In this provider every scenario that does not test Authentication or Authorization
		// uses the full-permission account, so that 401 and 403 never hide the result.


		// Layer 0: Infrastructure Layer Exception Handling Validation
		//
		// 413 (Payload Too Large) is answered by a body-size limit in front of the application code.
		// In many APIs it runs before security, routing and any business logic.
		// So the answer usually does not depend on the Authorization header or on the payload content.
		// The limit can still differ per path or per operation, and the test is cheap.
		// So every operation that takes a body tests it, and not only one operation of the API.
		{ new TestScenarioRecord(
				"0.1. Oversized Payload-API Gateway",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				// The payload must be bigger than the request-size limit of the API (Example: 10 MB).
				// A smaller body passes the limit and is processed normally, NOT rejected with 413.
				// OVERSIZE_PAYLOAD is a Supplier type object,
				// so its "get()" builds a new payload for every scenario
				// instead of sharing one object.
				OVERSIZE_PAYLOAD.get(),
				expect(413, skipSchemaAndAllowHeaderValidation(
						"413 is an infrastructure/gateway-layer response; its body can differ from the contract"))) } ,


		// Layer 1: Security Chain Layer Exception Handling Validation
		// This operation has a security scheme and a permission rule, so both Authentication (401)
		// and Authorization (403) are tested. A valid payload is sent: only the security part is wrong.
		//
		// Layer 1A: Authentication (401). The caller is not known.
		//
		// 1A.1: no Authorization header at all. Scheme-agnostic: whichever scheme (Bearer or Basic)
		// the backend expects, nothing was sent. In the OpenAPI Contract, 2 Security Schemes (Basic and Bearer)
		// are defined. Only the Authorization header is missing: Content-Type and Accept are sent.
		{ new TestScenarioRecord(
				"1A.1. Missing Authorization Header-Security/Authentication Layer",
				"/products",
				POST,
				noAuthenticationHeaders(),
				getValidProductsPayloadPOJO(),
				expect(401, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 1A.2: a properly formed Bearer header, but a token that the server never made.
		// This tests the token check of the Bearer path.
		{ new TestScenarioRecord(
				"1A.2. Properly formed Bearer Auth but invalid Bearer Token-Security/Authentication Layer",
				"/products",
				POST,
				bearerAuthHeadersWithInvalidToken(),
				getValidProductsPayloadPOJO(),
				expect(401, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 1A.3 to 1A.5: the Basic path has its own code. Each scenario breaks one thing of it.
		// 1A.3: properly formed Basic header, but the credentials are wrong.
		{ new TestScenarioRecord(
				"1A.3. Properly formed Basic Auth but invalid Credentials-Security/Authentication Layer",
				"/products",
				POST,
				basicAuthHeadersWithInvalidCredentials(),
				getValidProductsPayloadPOJO(),
				expect(401, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 1A.4: the Basic value is not Base64 (the credentials themselves are valid).
		{ new TestScenarioRecord(
				"1A.4. Malformed Basic Auth (Non-Base64 String) but valid Credentials-Security/Authentication Layer",
				"/products",
				POST,
				basicAuthHeadersWithoutBase64Encoding(),
				getValidProductsPayloadPOJO(),
				expect(401, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 1A.5: the decoded Basic value has no colon between the username and the password.
		{ new TestScenarioRecord(
				"1A.5. Malformed Basic Auth (No Colon) but valid Credentials-Security/Authentication Layer",
				"/products",
				POST,
				basicAuthHeadersWithoutColon(),
				getValidProductsPayloadPOJO(),
				expect(401, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// Layer 1B: Authorization (403). The caller is known, but the role does not have the permission.
		// The answer is 403 and not 401: only the permission is missing.
		// Use a role that is confirmed to lack the permission of THIS operation.
		// Example: POST /products needs "product:create". admin and seller have it, customer does not.
		// The Bearer path and the Basic path check the permission separately, so each has a scenario.
		{ new TestScenarioRecord(
				"1B.1. Unauthorized Access via Bearer Auth-Security/Authorization Layer",
				"/products",
				POST,
				bearerAuthHeadersForRole("customer"),
				getValidProductsPayloadPOJO(),
				expect(403, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"1B.2. Unauthorized Access via Basic Auth-Security/Authorization Layer",
				"/products",
				POST,
				basicAuthHeadersForRole("customer"),
				getValidProductsPayloadPOJO(),
				expect(403, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// Layer 2A: Routing & DispatcherServlet/HandlerMapping Layer Exception Handling Validation - path resolution
		//
		// Security runs before routing, and it does not know if a handler exists for the path.
		// So the routing scenarios (2A and 2B) use a logged-in caller. With the full-permission account,
		// security is passed and the answer comes from routing (404 or 405).
		//
		// 2A.1: a path that the contract does not define at all.
		{ new TestScenarioRecord(
				"2A.1. Endpoint undefined in Spec - DispatcherServlet Routing Layer-'path' resolution",
				"/undefinedPath",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				getValidProductsPayloadPOJO(),
				expect(404, skipSchemaAndAllowHeaderValidation(
						"404 routing error response is generated by the server/gateway; not by the api contract"))) } ,

		// 2A.2: a path with a trailing slash is a different path from the one in the contract.
		// Example: the contract defines "/products", and the server has no handler for "/products/".
		// This depends on the web framework: some accept both paths, some do not.
		// Keep this scenario only if the team wants the strict behavior.
		// (It is not a "missing path parameter" scenario: POST /products has no path parameter.)
		{ new TestScenarioRecord(
				"2A.2. Trailing Slash on the Path-DispatcherServlet Routing Layer-'path' resolution",
				"/products/",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				getValidProductsPayloadPOJO(),
				expect(404, skipSchemaAndAllowHeaderValidation(
						"No handler for '/products/': the 404 is generated by the server's routing, not by the API contract"))) } ,

		// Layer 2B: Routing & DispatcherServlet/HandlerMapping Layer Exception Handling Validation - method resolution
		//
		// 405 = the path is defined, but the HTTP method is not: an undefined operation (operation = method + path).
		// The spec defines responses per operation, so it cannot define a response for an undefined operation
		// (the spec has no 405 and no "default"). The body is generated by the server, so there is no schema to validate.
		// But RFC 9110 requires a 405 response to include the "Allow" header listing the supported methods,
		// so only the "Allow" header is validated.
		// Example: the path "/products" has GET and POST, but no PUT.
		// PUT is used on purpose (and not DELETE): if a server accepted it by mistake, nothing would be deleted.
		{ new TestScenarioRecord(
				"2B.1. Unsupported HTTP Method for the schema defined endpoint-DispatcherServlet Routing-Method resolution",
				"/products",
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				getValidProductsPayloadPOJO(),
				expect(405, validateAllowHeaderOnly(
						"405 body has no fixed contract, but RFC requires the Allow header to list supported methods"))) } ,


		// Layer 2C: Routing & DispatcherServlet/HandlerMapping Layer Exception Handling Validation - Incorrect Request Media Type (Content-Type)
		// (xmlContentTypeHeaders() carries the login of the full-permission account.)
		{ new TestScenarioRecord(
				"2C.1. Unsupported 'Content-Type' (XML)-Request Media Type",
				"/products",
				POST,
				xmlContentTypeHeaders(),
				toXml(getValidProductsPayloadPOJO()),
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
		// (xmlAcceptHeaders() carries the login of the full-permission account.)
		{ new TestScenarioRecord(
				"2D.1. Unsupported 'Accept' Header-Content Negotiation",
				"/products",
				POST,
				xmlAcceptHeaders(),
				getValidProductsPayloadPOJO(),
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
		// 3.2b and 3.2c: a field that the server makes itself, and that only the response has
		// (Mass Assignment / Over-Posting). A client must not choose it.
		{ new TestScenarioRecord(
				"3.2a. Unknown/Extra Field with a Harmless Name in Payload-Parsing Layer",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				UNKNOWN_KEY_PAYLOAD.get(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"3.2b. Extra Server-Made Field 'id' in Payload (Mass Assignment / Over-Posting) in Payload-Parsing Layer",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				MASS_ASSIGNMENT_ID_PAYLOAD.get(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"3.2c. Extra Server-Made Field 'createdAt' in Payload (Mass Assignment / Over-Posting) in Payload-Parsing Layer",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				MASS_ASSIGNMENT_CREATED_AT_PAYLOAD.get(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// Layer 3: Parsing & Syntax Validation Layer Exception Handling Validation - Malformed JSON Syntax
		//
		// Syntactically invalid JSON cannot be read, so Spring rejects it while it reads the body
		// (HttpMessageNotReadableException): Request Parsing layer, before validation and the controller.
		// The backend answers with the spec-defined ProblemDetails (400), so the schema is validated.
		{ new TestScenarioRecord(
				"3.3. Malformed JSON Syntax-Parsing Layer",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				readClasspathFile("malformedPayload/malformed_json_request_payload.json"),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// Layer 3: Parsing Layer - Missing Request Body
		// No request body at all: there is nothing to deserialize,
		// so it fails at the PARSING stage, NOT at the Constraint validation Layer
		// Only "Missing Field", NOT "Missing Body" fails at the Constraint validation Layer
		{ new TestScenarioRecord(
				"3.4. Missing Request Body-Parsing Layer",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				null,
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// Layer 3: Parsing Layer - Invalid Data Type
		// One scenario for each TYPE of field, not for each field.
		// The JSON is valid, but a field has the wrong type.
		// Note: a number or a boolean for a text field may be accepted (converted to text) by the JSON library,
		// so a type that cannot be converted is used (for example an array for a text field).
		// 3.5a: a text field. name, sku and description are the same type: name stands for the three.
		{ new TestScenarioRecord(
				"3.5a. Invalid Data Type for a Text Field ('name')-Parsing Layer",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				INVALID_DATA_TYPE_NAME_AS_ARRAY_PAYLOAD.get(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 3.5b: a number field.
		{ new TestScenarioRecord(
				"3.5b. Invalid Data Type for a Number Field ('price')-Parsing Layer",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				INVALID_DATA_TYPE_PRICE_AS_TEXT_PAYLOAD.get(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 3.5c: an integer field.
		{ new TestScenarioRecord(
				"3.5c. Invalid Data Type for an Integer Field ('stock')-Parsing Layer",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				INVALID_DATA_TYPE_STOCK_AS_TEXT_PAYLOAD.get(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 3.5d: an integer field with a decimal (1.5). The contract says integer.
		// The JSON library may accept it and turn it into 1. Then this scenario fails: it shows a gap
		// between the contract and the backend. Keep it as a known gap until the backend is fixed.
		{ new TestScenarioRecord(
				"3.5d. Decimal for an Integer Field ('stock')-Parsing Layer",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				INVALID_DATA_TYPE_STOCK_AS_DECIMAL_PAYLOAD.get(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// Layer 3: Parsing Layer - Invalid Enum Value
		// The value is not one of the values that the contract allows. In this contract the answer is 400:
		// the value cannot be read into the enum.
		{ new TestScenarioRecord(
				"3.6. Invalid Enum Value ('category')-Parsing Layer",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				INVALID_ENUM_VALUE_CATEGORY_PAYLOAD.get(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// Layer 4: Constraint Validation Layer Exception Handling Validation
		// The JSON is readable. The rules of the values are checked. Expected: 422.
		// The groups: 4.1 Presence, 4.2 Length, 4.3 Format, 4.4 Range, 4.5 Cross-Field.
		// The number of a group is the same in every provider.
		//
		// 4.1 Presence: every required field is tested alone. The payload is valid in all other fields.
		{ new TestScenarioRecord(
				"4.1a. Missing Required Field 'sku'-Presence Constraint Validation",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				MISSING_SKU_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.1b. Missing Required Field 'name'-Presence Constraint Validation",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				MISSING_NAME_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.1c. Missing Required Field 'category'-Presence Constraint Validation",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				MISSING_CATEGORY_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.1d. Missing Required Field 'price'-Presence Constraint Validation",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				MISSING_PRICE_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.1e. Missing Required Field 'stock'-Presence Constraint Validation",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				MISSING_STOCK_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 4.2 Length: the length of a text.
		// The rule is about the length of a text. (A Range constraint is about the value of a number.)
		// Boundary: the value next to the limit. Extreme: a value far from the limit (for example empty).
		// The valid boundaries (the minimum and the maximum itself) are in the positive data provider.
		//
		// The sku rule is a pattern, and the length (3 to 20) is part of it.
		// 4.2a to 4.2c: sku.
		{ new TestScenarioRecord(
				"4.2a. Sku One Below the Minimum Length-Length Constraint Validation (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				LENGTH_SKU_TOO_SHORT_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.2b. Sku One Above the Maximum Length-Length Constraint Validation (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				LENGTH_SKU_TOO_LONG_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.2c. Empty Sku-Length Constraint Validation (extreme)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				LENGTH_SKU_EMPTY_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 4.2d and 4.2e: name. The minimum length is 1, so an empty name is the boundary (one below the minimum)
		// and also the extreme: they are the same value here.
		{ new TestScenarioRecord(
				"4.2d. Empty Name-Length Constraint Validation (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				LENGTH_NAME_EMPTY_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.2e. Name One Above the Maximum Length-Length Constraint Validation (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				LENGTH_NAME_TOO_LONG_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 4.3 Format: the form of a text (the allowed characters, a pattern).
		{ new TestScenarioRecord(
				"4.3a. Sku with a Small Letter-Format Constraint Validation (character not allowed)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				FORMAT_SKU_LOWERCASE_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.3b. Sku with an Underscore-Format Constraint Validation (character not allowed)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				FORMAT_SKU_WITH_UNDERSCORE_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// The contract says "At most 2 decimals" in words only, so only the backend checks it.
		{ new TestScenarioRecord(
				"4.3c. Price with Three Decimals-Format Constraint Validation",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				FORMAT_PRICE_THREE_DECIMALS_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 4.4 Range: the value of a number.
		// The price: the minimum is 0.01 and the maximum is 9999999.99. The stock: the minimum is 0.
		// The stock has no maximum, so it has no "one above the maximum".
		{ new TestScenarioRecord(
				"4.4a. Price One Below the Minimum-Range Constraint Validation (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				RANGE_PRICE_ONE_BELOW_MIN_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// One above the maximum also breaks the digits rule of the backend (8 digits). This cannot be avoided.
		{ new TestScenarioRecord(
				"4.4b. Price One Above the Maximum-Range Constraint Validation (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				RANGE_PRICE_ONE_ABOVE_MAX_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.4c. Negative Price-Range Constraint Validation (extreme)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				RANGE_PRICE_NEGATIVE_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.4d. Stock One Below the Minimum-Range Constraint Validation (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				RANGE_STOCK_ONE_BELOW_MIN_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 4.5 Cross-Field: not applicable for POST /products. No rule compares two fields.


		// Layer 5: Controller/Service Layer Exception Handling Validation
		//
		// Scenario 5.1 (path parameter value not found in the database) is not used in this provider.
		// The reason: this operation has no path parameter.
		//
		// 5.2: Conflict / State Management - Duplicate Resource Creation Attempt (409).
		// This scenario is NOT a row of this data provider: a duplicate needs a precondition
		// (the product must exist first), so it is a stateful test. It is done separately in WorkflowPostProductTests:
		//   - shouldRejectSecondProduct_whenSameProductSentTwice()
		//   - shouldRejectProduct_whenOnlyTheSkuIsTheSame()
		};
	}


	@DataProvider(name = "PostProducts-PositiveTestDataProvider")
	public Object[][] getPostProductsPositiveTestData() {
		return new Object[][] {

		// One happy-path scenario per Security scheme in the OpenAPI Specification
		// ("bearerAuth OR basicAuth"). Same request; only the scheme differs.
		{ new TestScenarioRecord(
				"1. Create product with a valid payload via Bearer Auth",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(), // "Bearer" Scheme
				getValidProductsPayloadPOJO(),
				expect(201, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"2. Create product with a valid payload via Basic Auth",
				"/products",
				POST,
				basicAuthHeadersForFullPermissionUser(), // "Basic" Scheme
				getValidProductsPayloadPOJO(),
				expect(201, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) } ,

		// Valid boundaries: the minimum and the maximum themselves (the values next to them are negative scenarios).
		// Every valid request saves a product. The payloads make a new sku on every get(), so a second run does not get 409.
		{ new TestScenarioRecord(
				"3. Create product with the minimum name length (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				VALID_NAME_MIN_LENGTH_PAYLOAD.get(),
				expect(201, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4. Create product with the maximum name length (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				VALID_NAME_MAX_LENGTH_PAYLOAD.get(),
				expect(201, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"5. Create product with the minimum sku length (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				VALID_SKU_MIN_LENGTH_PAYLOAD.get(),
				expect(201, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"6. Create product with the maximum sku length (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				VALID_SKU_MAX_LENGTH_PAYLOAD.get(),
				expect(201, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"7. Create product with the minimum price (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				VALID_PRICE_MIN_PAYLOAD.get(),
				expect(201, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"8. Create product with the maximum price (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				VALID_PRICE_MAX_PAYLOAD.get(),
				expect(201, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) } ,

		// The stock has no maximum, so there is only the minimum.
		{ new TestScenarioRecord(
				"9. Create product with the minimum stock (boundary)",
				"/products",
				POST,
				bearerAuthHeadersForFullPermissionUser(),
				VALID_STOCK_MIN_PAYLOAD.get(),
				expect(201, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) }
		};
	}

}
