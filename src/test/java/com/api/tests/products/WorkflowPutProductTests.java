package com.api.tests.products;

import static com.api.client.HttpMethods.GET;
import static com.api.client.HttpMethods.POST;
import static com.api.client.HttpMethods.PUT;
import static com.api.utils.payload.ProductsPayloadFactory.getValidProductsPayloadPOJO;
import static org.hamcrest.Matchers.equalTo;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.api.base.BaseTest;
import com.api.pojo.products.ProductsFields;
import com.api.pojo.products.ProductsPayloadPOJO;
import com.api.utils.HeaderFactory;

import io.restassured.response.Response;

// Workflow tests of PUT /products/{id}.
// Every test makes its own product first. So no test depends on another test or on seed data.
public class WorkflowPutProductTests extends BaseTest {

	// The only lines to change when this class is pasted for another resource:
	private static final String ENDPOINT = "/products";

	private static final String KEY_ID = "id";
	private static final String KEY_SKU = ProductsFields.KEY_SKU;
	private static final String KEY_NAME = ProductsFields.KEY_NAME;
	private static final String KEY_CATEGORY = ProductsFields.KEY_CATEGORY;
	private static final String KEY_PRICE = ProductsFields.KEY_PRICE;
	private static final String KEY_STOCK = ProductsFields.KEY_STOCK;

	// ------------------------------------------------------------------------------
	// Reusable request-execution helpers.
	// (Why sendRequestWithTimeoutHandling() is used: see PostProductsWorkflowTests.)
	// The last argument of each call names the step. It is used only when the request throws
	// an exception (Example: a timeout). The full-permission account is used:
	// these tests check the state, not the permissions.
	// ------------------------------------------------------------------------------

	// The endpoint of one product: /products/{id} with the id filled in.
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

	private Response updateProduct(int productId, ProductsPayloadPOJO payload) {
		String endpoint = productEndpoint(productId);
		return sendRequestWithTimeoutHandling(reqSpec_base, endpoint, PUT,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), payload,
				"Workflow: PUT " + endpoint);
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

	// Checks that a response (of GET or PUT) shows the expected product.
	// "step" names the step, so a failure message says where it happened.
	private void assertProductMatches(Response response, int productId, ProductsPayloadPOJO expected, String step) {

		Assert.assertEquals(response.getStatusCode(), 200,
				step + ": unexpected status. Response body: " + response.asString());

		response.then()
				.body(KEY_ID, equalTo(productId))
				.body(KEY_SKU, equalTo(expected.getSku()))
				.body(KEY_NAME, equalTo(expected.getName()))
				.body(KEY_CATEGORY, equalTo(expected.getCategory()))
				.body(KEY_STOCK, equalTo(expected.getStock()));

		// The price is compared with a small tolerance (see PostProductsWorkflowTests, Layer 5.1).
		Assert.assertEquals(response.jsonPath().getDouble(KEY_PRICE), expected.getPrice(), 0.001,
				step + ": price");
	}

	// A PUT replaces ALL fields. To change some fields and keep the others,
	// the body has the new values for the changed fields and the old values for the rest.

	// LAYER 5.4 - Data Persistence Validation ('Database Write via PUT' validation)
	@Test(enabled = true, priority = 53,
			description = "Layer 5.4: PUT /products/{id} should change the fields that were changed and keep the other fields the same, and a GET afterwards should show the same product — i.e. verifying the update was actually persisted")
	void shouldPersistUpdatedProduct_andMatchOnGet() {

		// 1. Make a product that only this test uses.
		ProductsPayloadPOJO original = getValidProductsPayloadPOJO();
		int productId = extractProductId(createProduct(original));

		// 2. Change two fields (price and stock). The sku, name and category stay the same.
		ProductsPayloadPOJO updated = new ProductsPayloadPOJO(
				original.getSku(), original.getName(), original.getCategory(), 59.5, 35);

		// 3. The PUT response must show the new product.
		assertProductMatches(updateProduct(productId, updated), productId, updated, "PUT response");

		// 4. A new GET must show the same product. This proves that the change was saved.
		assertProductMatches(getProduct(productId), productId, updated, "GET after PUT");
	}

	// LAYER 5.5 - Idempotency Validation (PUT)
	@Test(enabled = true, priority = 54,
			description = "Layer 5.5: Sending the same PUT /products/{id} request twice should give the same result both times — i.e. verifying that PUT is idempotent")
	void shouldGiveSameResult_whenSamePutIsSentTwice() {

		ProductsPayloadPOJO original = getValidProductsPayloadPOJO();
		int productId = extractProductId(createProduct(original));

		// One body, sent twice. It has the product's own sku, and that must not be a 409.
		ProductsPayloadPOJO updated = new ProductsPayloadPOJO(
				original.getSku(), "Renamed-" + original.getName(), original.getCategory(), 59.5, 35);

		assertProductMatches(updateProduct(productId, updated), productId, updated, "First PUT");
		assertProductMatches(updateProduct(productId, updated), productId, updated, "Second PUT (same request)");
		assertProductMatches(getProduct(productId), productId, updated, "GET after two PUTs");
	}

	// LAYER 5.6 - Conflict / State Management (PUT): the sku belongs to ANOTHER product
	// The contract of PUT /products/{id} lists 409, and the sku must be unique.
	@Test(enabled = true, priority = 55,
			description = "Layer 5.6: PUT /products/{id} with the sku of another existing product should be REFUSED (409), and the product should stay unchanged — i.e. verifying that the uniqueness rule also holds on update")
	void shouldRejectUpdate_whenSkuBelongsToAnotherProduct() {

		// Two products, both made by this test.
		ProductsPayloadPOJO firstProduct = getValidProductsPayloadPOJO();
		ProductsPayloadPOJO secondProduct = getValidProductsPayloadPOJO();
		assertProductCreated(createProduct(firstProduct));
		int secondId = extractProductId(createProduct(secondProduct));

		// The second product asks for the sku of the first product. Only the sku is wrong.
		ProductsPayloadPOJO withTakenSku = new ProductsPayloadPOJO(
				firstProduct.getSku(), secondProduct.getName(), secondProduct.getCategory(),
				secondProduct.getPrice(), secondProduct.getStock());

		Response response = updateProduct(secondId, withTakenSku);
		Assert.assertEquals(response.getStatusCode(), 409,
				"A sku that belongs to another product must be refused on update. Response body: " + response.asString());

		// The refused update must not change the product.
		assertProductMatches(getProduct(secondId), secondId, secondProduct, "GET after the refused PUT");
	}
}