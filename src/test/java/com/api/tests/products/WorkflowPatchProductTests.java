

package com.api.tests.products;

import static com.api.client.HttpMethods.GET;
import static com.api.client.HttpMethods.PATCH;
import static com.api.client.HttpMethods.POST;
import static com.api.utils.payload.PayloadUtils.toObjectNode;
import static com.api.utils.payload.ProductsPayloadFactory.getValidProductsPayloadPOJO;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.api.base.BaseTest;
import com.api.pojo.products.ProductsFields;
import com.api.pojo.products.ProductsPayloadPOJO;
import com.api.utils.HeaderFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.restassured.response.Response;

// Workflow tests of PATCH /products/{id}.
// A PATCH body is PARTIAL: only the fields that are sent are changed. The other fields must stay the same.
// Every test makes its own product first. So no test depends on another test or on seed data.
public class WorkflowPatchProductTests extends BaseTest {

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
	// (Why sendRequestWithTimeoutHandling() is used: see WorkflowProductsPostTests.)
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

	// The body of a PATCH is not a full product. It holds only the fields to change.
	private Response patchProduct(int productId, ObjectNode partialBody) {
		String endpoint = productEndpoint(productId);
		return sendRequestWithTimeoutHandling(reqSpec_base, endpoint, PATCH,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), partialBody,
				"Workflow: PATCH " + endpoint);
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

	// Checks that a response (of GET or PATCH) shows the expected product.
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

		// The price is compared with a small tolerance (see WorkflowProductsPostTests, Layer 5.1).
		Assert.assertEquals(response.jsonPath().getDouble(KEY_PRICE), expected.getPrice(), 0.001,
				step + ": price");
	}

	// LAYER 5.7 - Data Persistence Validation ('Database Write via PATCH' validation)
	// The heart of PATCH: the field that is sent changes, and every other field stays the same.
	@Test(enabled = true, priority = 56,
			description = "Layer 5.7: PATCH /products/{id} should change ONLY the field that was sent and keep all other fields the same, and a GET afterwards should show the same product — i.e. verifying a partial update was actually persisted")
	void shouldChangeOnlyTheSentField_andKeepTheOthers() {

		// 1. Make a product that only this test uses.
		ProductsPayloadPOJO original = getValidProductsPayloadPOJO();
		int productId = extractProductId(createProduct(original));

		// 2. Send ONLY the price.
		ObjectNode partialBody = toObjectNode(Map.of("price", 59.5));

		// 3. What the product should look like now: the new price, and all other fields as before.
		ProductsPayloadPOJO expected = new ProductsPayloadPOJO(
				original.getSku(), original.getName(), original.getCategory(), 59.5, original.getStock());

		// 4. The PATCH response must show it.
		assertProductMatches(patchProduct(productId, partialBody), productId, expected, "PATCH response");

		// 5. A new GET must show the same. This proves that the change was saved.
		assertProductMatches(getProduct(productId), productId, expected, "GET after PATCH");
	}

	// LAYER 5.8 - Idempotency Validation (PATCH)
	// The body sets a field to a value (it does not add to it). So sending it twice gives the same result.
	@Test(enabled = true, priority = 57,
			description = "Layer 5.8: Sending the same PATCH /products/{id} request twice should give the same result both times — i.e. verifying that setting a value with PATCH is idempotent")
	void shouldGiveSameResult_whenSamePatchIsSentTwice() {

		ProductsPayloadPOJO original = getValidProductsPayloadPOJO();
		int productId = extractProductId(createProduct(original));

		// One body, sent twice.
		ObjectNode partialBody = toObjectNode(Map.of("stock", 35));
		ProductsPayloadPOJO expected = new ProductsPayloadPOJO(
				original.getSku(), original.getName(), original.getCategory(), original.getPrice(), 35);

		assertProductMatches(patchProduct(productId, partialBody), productId, expected, "First PATCH");
		assertProductMatches(patchProduct(productId, partialBody), productId, expected, "Second PATCH (same request)");
		assertProductMatches(getProduct(productId), productId, expected, "GET after two PATCHes");
	}

	// LAYER 5.9 - Conflict / State Management (PATCH): the sku belongs to ANOTHER product
	// The contract of PATCH /products/{id} lists 409, and the sku must be unique.
	@Test(enabled = true, priority = 58,
			description = "Layer 5.9: PATCH /products/{id} with the sku of another existing product should be REFUSED (409), and the product should stay unchanged — i.e. verifying that the uniqueness rule also holds on a partial update")
	void shouldRejectPatch_whenSkuBelongsToAnotherProduct() {

		// Two products, both made by this test.
		ProductsPayloadPOJO firstProduct = getValidProductsPayloadPOJO();
		ProductsPayloadPOJO secondProduct = getValidProductsPayloadPOJO();
		assertProductCreated(createProduct(firstProduct));
		int secondId = extractProductId(createProduct(secondProduct));

		// The second product asks for the sku of the first product. Only the sku is sent.
		ObjectNode partialBody = toObjectNode(Map.of("sku", firstProduct.getSku()));

		Response response = patchProduct(secondId, partialBody);
		Assert.assertEquals(response.getStatusCode(), 409,
				"A sku that belongs to another product must be refused on PATCH. Response body: " + response.asString());

		// The refused PATCH must not change the product.
		assertProductMatches(getProduct(secondId), secondId, secondProduct, "GET after the refused PATCH");
	}
}
