package com.api.tests.products;

import static com.api.client.HttpMethods.GET;
import static com.api.client.HttpMethods.POST;
import static com.api.utils.payload.ProductsPayloadFactory.getValidProductsPayloadPOJO;
import static org.hamcrest.Matchers.equalTo;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.api.base.BaseTest;
import com.api.pojo.products.ProductsPayloadPOJO;
import com.api.utils.HeaderFactory;
import com.api.pojo.products.ProductsFields;

import io.restassured.response.Response;

// ============================================================================
// LAYER 5 - BUSINESS LOGIC / WORKFLOW & STATE VALIDATION
// ============================================================================
// Resource-based organization: this class is scoped to one resource (Products).
// A resource's life cycle is one concern, however many URL paths it uses:
// POST /products (create) and GET /products/{id} (read the state back).
// A new resource gets its own class with the same convention (Example: OrdersWorkflowTests).
//
// Why this class exists separately:
// The negative and positive data providers check ONE response at a time (via ResponseValidator).
// The tests here compare the results of SEVERAL calls: create a product and read it back,
// or create the same product twice. That is a question of state across calls,
// so it is outside the scope of ResponseValidator.
//
// Which scenarios of the data provider are done here:
// 5.2 (a duplicate sku, 409) needs a product that exists first, so it is a stateful test.
//
// What is NOT here yet: the tests that use PUT, PATCH and DELETE on /products/{id}
// (PUT must be idempotent, PATCH must change only the sent field, GET must give 404 after DELETE).
// They belong to this class, and are added when the data providers of those operations exist.
//
// The products stay on the server: the tests do not delete them.
// Example: the shop-api keeps its data in memory, so a restart clears it.
//
// Status: add this class to the suite file (Example: testng-ecommerce-api.xml).
// Check each test against the backend. It needs the server to be running.
// ============================================================================

public class WorkflowPostProductTests extends BaseTest {

	// The only lines to change when this class is pasted for another resource:
	private static final String ENDPOINT = "/products";
	
	// Example: when a ProductsFields class exists (like UsersFields), use its KEY_ constants here.
	private static final String KEY_ID = "id";
	private static final String KEY_SKU = ProductsFields.KEY_SKU;
	private static final String KEY_NAME = ProductsFields.KEY_NAME;
	private static final String KEY_CATEGORY = ProductsFields.KEY_CATEGORY;
	private static final String KEY_PRICE = ProductsFields.KEY_PRICE;
	private static final String KEY_STOCK = ProductsFields.KEY_STOCK;

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
	//    for example "Timeout for Scenario: Workflow: GET /products/42 -> ...".
	//
	// A workflow test sends several requests one after another. When one of them
	// fails, the message must say WHICH step it was. That is why every helper below
	// passes a description such as "Workflow: GET /products/42".
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
	// The full-permission account is used: these tests check the state, not the permissions.
	// ------------------------------------------------------------------------------

	// The endpoint of one product: /products/{id} with the id filled in. The path
	// is built in this one place, so a change of the URL structure is made once.
	private static String productEndpoint(int productId) {
		return ENDPOINT + "/" + productId;
	}

	private Response createProduct(ProductsPayloadPOJO payload) {
		return sendRequestWithTimeoutHandling(reqSpec_base, ENDPOINT, POST,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), payload,
				"Workflow: POST " + ENDPOINT);
	}

	private Response getProduct(int productId) {
		String endpoint = productEndpoint(productId);
		return sendRequestWithTimeoutHandling(reqSpec_base, endpoint, GET,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), null,
				"Workflow: GET " + endpoint);
	}

	private void assertProductCreated(Response response) {
		// Check the status first. Then the failure message says what really happened.
		Assert.assertEquals(response.getStatusCode(), 201,
				"The product was not created. Response body: " + response.asString());
	}

	private int extractProductId(Response createResponse) {
		// If the product was not created, there is no id to read.
		assertProductCreated(createResponse);
		return createResponse.jsonPath().getInt(KEY_ID);
	}

	// LAYER 5.1 - Data Persistence Validation ('Database Write via POST' validation)
	@Test(enabled = true, priority = 50,
			description = "Layer 5.1: After creating a product via POST, calling GET /products/{id} should return the same data — i.e. verifying the product was actually persisted")
	void shouldPersistCreatedProduct_andMatchOnGet() {

		ProductsPayloadPOJO requestPayload = getValidProductsPayloadPOJO();

		int productId = extractProductId(createProduct(requestPayload));

		Response getResponse = getProduct(productId);

		Assert.assertEquals(getResponse.getStatusCode(), 200);

		getResponse.then()
				.body(KEY_SKU, equalTo(requestPayload.getSku()))
				.body(KEY_NAME, equalTo(requestPayload.getName()))
				.body(KEY_CATEGORY, equalTo(requestPayload.getCategory()))
				.body(KEY_STOCK, equalTo(requestPayload.getStock()));

		// The price is a decimal number. RestAssured reads it as a float, and a float is not equal to a double
		// (Example: 39.9f is not 39.9). So it is compared with a small tolerance, and not with equalTo().
		Assert.assertEquals(getResponse.jsonPath().getDouble(KEY_PRICE), requestPayload.getPrice(), 0.001,
				"The price of the product that was read back is not the price that was sent");
	}

	// DUPLICATE POST: ONLY THE "REFUSE" VARIANT IS HERE
	// The contract of POST /products lists 409, and the sku must be unique ("Unique" in the contract).
	// So the backend must refuse a duplicate, and only the tests that expect 409 apply (Layer 5.2 and 5.3).
	// A backend that ACCEPTS duplicates would need the opposite test.

	// LAYER 5.2 - Duplicate Resource Creation Request Validation (POST-specific)
	@Test(enabled = true, priority = 51,
			description = "Layer 5.2: The Server should REFUSE the second request when two POST /products requests have the exact same payload")
	void shouldRejectSecondProduct_whenSameProductSentTwice() {

		// Make the payload only once. Send the same payload twice.
		// The first request always works, because the sku was never used before.
		ProductsPayloadPOJO requestPayload = getValidProductsPayloadPOJO();

		assertProductCreated(createProduct(requestPayload));

		Response secondResponse = createProduct(requestPayload);
		Assert.assertEquals(secondResponse.getStatusCode(), 409,
				"The same product sent twice must be refused. Response body: " + secondResponse.asString());
	}

	// LAYER 5.3 - Duplicate Resource Creation Request Validation (only the unique field is the same)
	@Test(enabled = true, priority = 52,
			description = "Layer 5.3: A product with the same sku as an existing product should be REFUSED, even when all other fields are different — i.e. verifying that the uniqueness rule is about the sku")
	void shouldRejectProduct_whenOnlyTheSkuIsTheSame() {

		ProductsPayloadPOJO firstProduct = getValidProductsPayloadPOJO();
		assertProductCreated(createProduct(firstProduct));

		// A second, valid product. Only the sku is the same, so the sku is the only possible reason for a refusal.
		ProductsPayloadPOJO sameSkuOtherData = new ProductsPayloadPOJO(
				firstProduct.getSku(), "Other-Name", "books", 10.5, 5);

		Response secondResponse = createProduct(sameSkuOtherData);
		Assert.assertEquals(secondResponse.getStatusCode(), 409,
				"A product with an existing sku must be refused. Response body: " + secondResponse.asString());
	}
}
