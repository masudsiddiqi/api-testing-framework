package com.api.dataProvider.products;

import static com.api.client.HttpMethods.PATCH;
import static com.api.dto.ExpectedStatusCode.expect;
import static com.api.schemaPathConstants.GeneratedSchemaPathConstants.*;
import static com.api.utils.HeaderFactory.basicAuthHeadersForFullPermissionUser;
import static com.api.utils.HeaderFactory.bearerAuthHeadersForFullPermissionUser;
import static com.api.utils.HeaderFactory.bearerAuthHeadersForRole;
import static com.api.utils.HeaderFactory.noAuthenticationHeaders;
import static com.api.utils.HeaderFactory.xmlAcceptHeaders;
import static com.api.utils.HeaderFactory.xmlContentTypeHeaders;
import static com.api.utils.payload.PayloadUtils.readClasspathFile;
import static com.api.utils.payload.PayloadUtils.toObjectNode;
import static com.api.utils.payload.PayloadUtils.toXml;
import static com.api.utils.payload.ProductsPayloadFactory.OVERSIZE_PAYLOAD;
import static com.api.validation.SchemaAndAllowHeaderValidationPolicy.skipSchemaAndAllowHeaderValidation;
import static com.api.validation.SchemaAndAllowHeaderValidationPolicy.validateSchemaOnly;

import java.util.Map;

import org.testng.annotations.DataProvider;

import com.api.dto.TestScenarioRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class PatchProductDataProvider {

	// The product that these rows try to change. It must exist in the database.
	// Example: a product that the application creates at start-up (seed data).
	// It is the same product as in PutProductDataProvider.
	private static final int EXISTING_PRODUCT_ID = 2;
	private static final String PRODUCT_PATH = "/products/" + EXISTING_PRODUCT_ID;

	// An id that no product has. Used only by the "record not found" row.
	private static final String MISSING_PRODUCT_PATH = "/products/999999999";

	// A valid partial body: it changes ONE field (the price).
	// Why the change is needed:
	// A PATCH that changes nothing leaves the database the same.
	// If a security or routing rule fails and the request gets through by mistake,
	// the test cannot see the mistake.
	// With a changed field, an accepted request leaves a visible change.
	// A new object is built on every call.
	private static ObjectNode validPartialBody() {
		return new ObjectMapper().valueToTree(Map.of("price", 24.99));
	}

	@DataProvider(name = "PatchProduct-NegativeTestDataProvider")
	public Object[][] getPatchProductsNegativeTestData() {

		return new Object[][] {

		// new TestScenarioRecord(ScenarioDescription, Endpoint, HTTPMethod, Headers, Payload, ExpectedStatusCode)

		// WHICH SCENARIOS ARE HERE
		// Only the scenarios that the contract lists for this operation: one row for each response code,
		// plus the main causes of 400 and 422.
		// The contract lists: 400, 401, 403, 404, 406, 409, 413, 415 and 422.
		// The 409 (the sku belongs to another product) is NOT here. It needs two products and a state check,
		// so it is a workflow test.
		//
		// WHAT IS DIFFERENT FROM PUT
		// A PATCH body is PARTIAL: every field is optional, and only the fields that are sent are changed.
		// So there is no "missing required field" scenario (4.1). A missing field is valid here.
		// A field that IS sent follows the same rules as in POST and PUT.
		// The payload rules are tested in detail in PostProductsDataProvider. Here, one example of each kind is enough.
		//
		// THE PAYLOAD OF EACH ROW
		// There is no separate PATCH factory. The body is a small partial body, built in the row:
		// toObjectNode(Map.of("price", 24.99)) means: change only the price.
		// - Rows for security, routing and the id use validPartialBody() (a valid body that changes the price).
		// - Rows for a payload rule send ONE bad field. So one scenario breaks one rule.
		//
		// Every row that does not test Authentication or Authorization uses the full-permission account,
		// so that 401 and 403 never hide the result.
		// All rows here should be rejected. A wrongly accepted row would change the product.


		// Layer 0: Infrastructure Layer
		// 413 is answered by the body-size limit before the application code (the reasons are in PostProductsDataProvider).
		{ new TestScenarioRecord(
				"0.1. Oversized Payload-API Gateway",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				OVERSIZE_PAYLOAD.get(),
				expect(413, skipSchemaAndAllowHeaderValidation(
						"413 is an infrastructure/gateway-layer response; its body can differ from the contract"))) } ,


		// Layer 1: Security Chain Layer. A valid body is sent: only the security part is wrong.
		// 1A.1: 401, the caller is not known.
		{ new TestScenarioRecord(
				"1A.1. Missing Authorization Header-Security/Authentication Layer",
				PRODUCT_PATH,
				PATCH,
				noAuthenticationHeaders(),
				validPartialBody(),
				expect(401, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// 1B.1: 403, the caller is known, but the role does not have the permission of THIS operation.
		// Example: "customer" does not have "product:update" (admin and seller do).
		{ new TestScenarioRecord(
				"1B.1. Unauthorized Access via Bearer Auth-Security/Authorization Layer",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForRole("customer"),
				validPartialBody(),
				expect(403, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// Layer 2: Routing Layer. Only the media types are here, because the contract lists 415 and 406.
		// 2C.1: 415, the body is XML, but the contract accepts only JSON.
		{ new TestScenarioRecord(
				"2C.1. Unsupported 'Content-Type' (XML)-Request Media Type",
				PRODUCT_PATH,
				PATCH,
				xmlContentTypeHeaders(),
				toXml(validPartialBody()),
				expect(415, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// 2D.1: 406, the client asks for XML, but the contract answers only in JSON.
		{ new TestScenarioRecord(
				"2D.1. Unsupported 'Accept' Header-Content Negotiation",
				PRODUCT_PATH,
				PATCH,
				xmlAcceptHeaders(),
				validPartialBody(),
				expect(406, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// Layer 3: Parsing & Syntax Layer. Expected: 400.
		// 3.2a: a field that the contract does not define. The body also has a valid change,
		// so the unknown field is the only problem.
		{ new TestScenarioRecord(
				"3.2a. Unknown/Extra Field with a Harmless Name in Payload-Parsing Layer",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("price", 24.99, "unknown_field", "unknown_value")),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// 3.3: the JSON itself is broken.
		{ new TestScenarioRecord(
				"3.3. Malformed JSON Syntax-Parsing Layer",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				readClasspathFile("malformedPayload/malformed_json_request_payload.json"),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// 3.5b: a wrong data type. A text that is not a number cannot be read as a price.
		{ new TestScenarioRecord(
				"3.5b. Invalid Data Type for a Number Field ('price')-Parsing Layer",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("price", "not_a_number")),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// 3.6: an invalid enum value. The value cannot be read into the enum.
		{ new TestScenarioRecord(
				"3.6. Invalid Enum Value ('category')-Parsing Layer",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("category", "invalid_category")),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// 3.7: the path parameter is wrong. The body is valid: only the id is wrong.
		// The contract says that the id is an integer of 1 or more.
		// 3.7a: the wrong type. 3.7b: below the minimum.
		{ new TestScenarioRecord(
				"3.7a. Path Parameter 'id' with Invalid Data Type-Parsing Layer",
				"/products/abc",
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				validPartialBody(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"3.7b. Path Parameter 'id' Below the Minimum (0)-Parsing Layer",
				"/products/0",
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				validPartialBody(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// Layer 4: Constraint Validation Layer. The JSON is readable, but a rule of a sent value is broken. Expected: 422.
		// There is no 4.1 (a missing field is valid in a PATCH). One example for each kind of rule:
		// 4.2d: length. 4.3a: format. 4.4c: range.
		{ new TestScenarioRecord(
				"4.2d. Empty Name-Length Constraint Validation (boundary)",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("name", "")),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.3a. Sku with a Small Letter-Format Constraint Validation (character not allowed)",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("sku", "book-002")),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"4.4c. Negative Price-Range Constraint Validation (extreme)",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("price", -150)),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// Layer 5: Controller/Service Layer. The request is well formed, but the data does not allow it.
		// 5.1: 404. The body is valid, but no product has this id.
		// This 404 is made by the API itself (the contract lists it), so the error schema IS validated.
		{ new TestScenarioRecord(
				"5.1. Path Parameter Value Not Found in the Database-Service Layer",
				MISSING_PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				validPartialBody(),
				expect(404, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) }

		};
	}

	// POSITIVE ROWS: a valid PATCH to a product that EXISTS in the database. Expected: 200.
	// Each row sends ONLY the field(s) named in its description. The other fields are not sent,
	// so the server keeps them. (A PUT would need all fields.)
	//
	// What these rows check: the status and the shape of the response (one response at a time).
	// What they do NOT check: that ONLY the sent fields changed, and that the change is saved.
	// That needs a second call (GET), so it is a workflow test.
	//
	// SIDE EFFECT: these rows really change the existing product. Each row changes different fields,
	// so after a run the product has ALL these changes together (different from the PUT rows, where each row
	// starts again from the same base values).
	// The rows do not change the category. Other tests may rely on the product.
	// Do not run other tests at the same time that read this product (Example: a price check).
	@DataProvider(name = "PatchProduct-PositiveTestDataProvider")
	public Object[][] getPatchProductsPositiveTestData() {
		return new Object[][] {

		// Rows 1 and 2: one happy-path scenario for each security scheme of the contract
		// ("bearerAuth OR basicAuth"). The request is the same. Only the scheme is different.
		{ new TestScenarioRecord(
				"1. Change only the price of the existing product, via Bearer Auth",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("price", 59.5)),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"2. Change only the price of the existing product, via Basic Auth",
				PRODUCT_PATH,
				PATCH,
				basicAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("price", 59.5)),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"3. Change only the stock of the existing product",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("stock", 35)),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"4. Change only the name of the existing product",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("name", "The Pragmatic Programmer (Updated)")),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"5. Change only the description of the existing product",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("description", "A new description")),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"6. Change two fields of the existing product (price and stock)",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("price", 59.5, "stock", 35)),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"7. Send the product's own sku (it must not be a conflict)",
				PRODUCT_PATH,
				PATCH,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("sku", "BOOK-002")),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) }

		};
	}
}