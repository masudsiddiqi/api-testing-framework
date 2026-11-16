package com.api.dataProvider.products;


import static com.api.client.HttpMethods.PUT;
import static com.api.dto.ExpectedStatusCode.expect;
import static com.api.schemaPathConstants.GeneratedSchemaPathConstants.*;
import static com.api.utils.HeaderFactory.*;
import static com.api.utils.payload.PayloadUtils.readClasspathFile;
import static com.api.utils.payload.PayloadUtils.toObjectNode;
import static com.api.utils.payload.PayloadUtils.toXml;
import static com.api.utils.payload.ProductsPayloadFactory.MISSING_PRICE_PAYLOAD;
import static com.api.utils.payload.ProductsPayloadFactory.OVERSIZE_PAYLOAD;
import static com.api.utils.payload.ProductsPayloadFactory.RANGE_PRICE_NEGATIVE_PAYLOAD;
import static com.api.utils.payload.ProductsPayloadFactory.UNKNOWN_KEY_PAYLOAD;
import static com.api.validation.SchemaAndAllowHeaderValidationPolicy.skipSchemaAndAllowHeaderValidation;
import static com.api.validation.SchemaAndAllowHeaderValidationPolicy.validateSchemaOnly;

import java.util.LinkedHashMap;
import java.util.Map;

import org.testng.annotations.DataProvider;

import com.api.dto.TestScenarioRecord;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class PutProductDataProvider {

	// The product that these rows try to change. It must exist in the database.
	// Example: a product that the application creates at start-up (seed data).
	private static final int EXISTING_PRODUCT_ID = 2;
	private static final String PRODUCT_PATH = "/products/" + EXISTING_PRODUCT_ID;

	// An id that no product has. Used only by the "record not found" row.
	private static final String MISSING_PRODUCT_PATH = "/products/999999999";

	// The current data of the existing product. A PUT replaces ALL fields,
	// so every request sends all of them. These are the real values of the seed data.
	// If your product is different, change only these lines.
	private static final String EXISTING_SKU = "BOOK-002";
	private static final String EXISTING_NAME = "The Pragmatic Programmer";
	private static final String EXISTING_DESCRIPTION = "Your journey to mastery";
	private static final String EXISTING_CATEGORY = "books";
	private static final double EXISTING_PRICE = 34.5;
	private static final int EXISTING_STOCK = 30;

	// The payload for the existing product, with the given field(s) changed.
	// All other fields keep the real values of the product.
	// Example: existingProductWith(Map.of("price", 24.99)) changes only the price.
	// A new object is built on every call, so the rows do not share one object.
	private static ObjectNode existingProductWith(Map<String, Object> changes) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("sku", EXISTING_SKU);
		body.put("name", EXISTING_NAME);
		body.put("description", EXISTING_DESCRIPTION);
		body.put("category", EXISTING_CATEGORY);
		body.put("price", EXISTING_PRICE);
		body.put("stock", EXISTING_STOCK);
		body.putAll(changes);
		return toObjectNode(body);
	}

	// A valid payload for the existing product, with ONE field changed (the price).
	// Why the change is needed:
	// A PUT with the same data as the database changes nothing.
	// If a security or routing rule fails and the request gets through by mistake,
	// the database stays the same and the test cannot see the mistake.
	// With a changed field, an accepted request leaves a visible change.
	private static ObjectNode updatedExistingProductPayload() {
		return existingProductWith(Map.of("price", 24.99));
	}

	@DataProvider(name = "PutProduct-NegativeTestDataProvider")
	public Object[][] getPutProductsNegativeTestData() {

		return new Object[][] {

		// new TestScenarioRecord(ScenarioDescription, Endpoint, HTTPMethod, Headers, Payload, ExpectedStatusCode)

		// WHICH SCENARIOS ARE HERE
		// Only the scenarios that the contract lists for this operation: one row for each response code,
		// plus the main causes of 400 and 422.
		// The contract lists: 400, 401, 403, 404, 406, 409, 413, 415 and 422.
		// The 409 (the sku belongs to another product) is NOT here. It needs two products and a state check,
		// so it is a workflow test: WorkflowPostProductTests (Layer 5.6). That test makes its own products,
		// so it does not depend on the seed data.
		//
		// WHAT IS NOT REPEATED
		// The payload rules (length, format, range, data type) are the same as for POST /products,
		// because both operations use the same request schema. They are tested in detail in PostProductsDataProvider.
		// Here, one example of each kind is enough. The same applies to the extra security variants.
		// The payloads come from ProductsPayloadFactory (no separate PUT factory).
		//
		// WHAT IS NEW IN PUT /products/{id}
		// - The path has a parameter (the id), so there are scenarios for a wrong id (3.7) and a missing record (5.1).
		// - PUT replaces ALL fields, so a missing field is an error (4.1d) and a partial body is an error (4.1f).
		// - The permission is different from POST (here "product:update"), so the 403 row uses a role without THIS permission.
		//
		// THE PAYLOAD OF EACH ROW
		// - Rows for security, routing and the id use updatedExistingProductPayload():
		//   a valid payload with one changed field. See the comment on that method.
		// - Rows for a payload rule use a payload from ProductsPayloadFactory.
		//   It is a full valid payload with one broken rule, and its values differ from the product,
		//   so a wrongly accepted request would change the product.
		//
		// Every row that does not test Authentication or Authorization uses the full-permission account,
		// so that 401 and 403 never hide the result.
		// All rows here should be rejected. A wrongly accepted row would change the product.


		// Layer 0: Infrastructure Layer
		// 413 is answered by the body-size limit before the application code (the reasons are in PostProductsDataProvider).
		{ new TestScenarioRecord(
				"0.1. Oversized Payload-API Gateway",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				OVERSIZE_PAYLOAD.get(),
				expect(413, skipSchemaAndAllowHeaderValidation(
						"413 is an infrastructure/gateway-layer response; its body can differ from the contract"))) } ,


		// Layer 1: Security Chain Layer. A valid payload is sent: only the security part is wrong.
		// 1A.1: 401, the caller is not known.
		{ new TestScenarioRecord(
				"1A.1. Missing Authorization Header-Security/Authentication Layer",
				PRODUCT_PATH,
				PUT,
				noAuthenticationHeaders(),
				updatedExistingProductPayload(),
				expect(401, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 1B.1: 403, the caller is known, but the role does not have the permission of THIS operation.
		// Example: "customer" does not have "product:update" (admin and seller do).
		{ new TestScenarioRecord(
				"1B.1. Unauthorized Access via Bearer Auth-Security/Authorization Layer",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForRole("customer"),
				updatedExistingProductPayload(),
				expect(403, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// Layer 2: Routing Layer. Only the media types are here, because the contract lists 415 and 406.
		// 2C.1: 415, the body is XML, but the contract accepts only JSON.
		{ new TestScenarioRecord(
				"2C.1. Unsupported 'Content-Type' (XML)-Request Media Type",
				PRODUCT_PATH,
				PUT,
				xmlContentTypeHeaders(),
				toXml(updatedExistingProductPayload()),
				expect(415, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 2D.1: 406, the client asks for XML, but the contract answers only in JSON.
		{ new TestScenarioRecord(
				"2D.1. Unsupported 'Accept' Header-Content Negotiation",
				PRODUCT_PATH,
				PUT,
				xmlAcceptHeaders(),
				updatedExistingProductPayload(),
				expect(406, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// Layer 3: Parsing & Syntax Layer. Expected: 400.
		// 3.2a: a field that the contract does not define.
		{ new TestScenarioRecord(
				"3.2a. Unknown/Extra Field with a Harmless Name in Payload-Parsing Layer",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				UNKNOWN_KEY_PAYLOAD.get(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 3.3: the JSON itself is broken.
		{ new TestScenarioRecord(
				"3.3. Malformed JSON Syntax-Parsing Layer",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				readClasspathFile("malformedPayload/malformed_json_request_payload.json"),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 3.7: the path parameter is wrong. The payload is valid: only the id is wrong.
		// The contract says that the id is an integer of 1 or more.
		// 3.7a: the wrong type. 3.7b: below the minimum.
		{ new TestScenarioRecord(
				"3.7a. Path Parameter 'id' with Invalid Data Type-Parsing Layer",
				"/products/abc",
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				updatedExistingProductPayload(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		{ new TestScenarioRecord(
				"3.7b. Path Parameter 'id' Below the Minimum (0)-Parsing Layer",
				"/products/0",
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				updatedExistingProductPayload(),
				expect(400, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// Layer 4: Constraint Validation Layer. The JSON is readable, but a rule of the values is broken. Expected: 422.
		// 4.1d: a required field is missing. A PUT replaces all fields, so every required field must be sent.
		{ new TestScenarioRecord(
				"4.1d. Missing Required Field 'price'-Presence Constraint Validation",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				MISSING_PRICE_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 4.1f: a PARTIAL body. Only the changed field is sent.
		// This is the usual mistake of a client that uses PUT like PATCH. For a partial change, PATCH is the right method.
		{ new TestScenarioRecord(
				"4.1f. Partial Body with Only the Changed Field 'price'-Presence Constraint Validation",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				toObjectNode(Map.of("price", 24.99)),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,

		// 4.4c: one example of a value rule (range). The other value rules are tested in PostProductsDataProvider.
		{ new TestScenarioRecord(
				"4.4c. Negative Price-Range Constraint Validation (extreme)",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				RANGE_PRICE_NEGATIVE_PAYLOAD.get(),
				expect(422, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) } ,


		// Layer 5: Controller/Service Layer. The request is well formed, but the data does not allow it.
		// 5.1: 404. The payload is valid, but no product has this id.
		// This 404 is made by the API itself (the contract lists it), so the error schema IS validated.
		// (The routing 404 for an undefined path is different: the server makes it, so there is no schema.)
		{ new TestScenarioRecord(
				"5.1. Path Parameter Value Not Found in the Database-Service Layer",
				MISSING_PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				updatedExistingProductPayload(),
				expect(404, validateSchemaOnly(
						ERROR_RESPONSE_SCHEMA, "Standard error-response schema applies"))) }

		};
	}

	// POSITIVE ROWS: a valid PUT to a product that EXISTS in the database. Expected: 200.
	// Each row changes only the field(s) named in its description. All other fields are sent again
	// with their current values, because a PUT replaces ALL fields.
	//
	// What these rows check: the status and the shape of the response (one response at a time).
	// What they do NOT check: that the change is really saved, and that the other fields stay the same.
	// That needs a second call (GET), so it is a workflow test: WorkflowPostProductTests (Layer 5.4).
	//
	// SIDE EFFECT: these rows really change the existing product. Every row starts from the same
	// base values, so the rows do not depend on each other. But after the run, the product keeps the
	// values of the row that ran last.
	// Because of this, the rows do not change the sku or the category: other tests may rely on them.
	// Those fields are changed in the workflow test, on a product that the test makes itself.
	// Do not run other tests at the same time that read this product (Example: a price check).
	@DataProvider(name = "PutProduct-PositiveTestDataProvider")
	public Object[][] getPutProductsPositiveTestData() {
		return new Object[][] {

		// Rows 1 and 2: one happy-path scenario for each security scheme of the contract
		// ("bearerAuth OR basicAuth"). The request is the same. Only the scheme is different.
		{ new TestScenarioRecord(
				"1. Update the existing product: change only the price, via Bearer Auth",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				existingProductWith(Map.of("price", 59.5)),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"2. Update the existing product: change only the price, via Basic Auth",
				PRODUCT_PATH,
				PUT,
				basicAuthHeadersForFullPermissionUser(),
				existingProductWith(Map.of("price", 59.5)),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"3. Update the existing product: change only the stock",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				existingProductWith(Map.of("stock", 35)),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"4. Update the existing product: change only the name",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				existingProductWith(Map.of("name", "The Pragmatic Programmer (Updated)")),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"5. Update the existing product: change only the description",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				existingProductWith(Map.of("description", "A new description")),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"6. Update the existing product: change two fields (price and stock)",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				existingProductWith(Map.of("price", 59.5, "stock", 35)),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) },

		{ new TestScenarioRecord(
				"7. Update the existing product: send its current data without any change",
				PRODUCT_PATH,
				PUT,
				bearerAuthHeadersForFullPermissionUser(),
				existingProductWith(Map.of()),
				expect(200, validateSchemaOnly(
						PRODUCT_SCHEMA, "Standard success-response schema applies"))) }

		};
	}
}