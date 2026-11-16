package com.api.tests.cart;

import static com.api.client.HttpMethods.DELETE;
import static com.api.client.HttpMethods.GET;
import static com.api.client.HttpMethods.POST;
import static com.api.client.HttpMethods.PUT;
import static com.api.utils.payload.ProductsPayloadFactory.getValidProductsPayloadPOJO;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.api.base.BaseTest;
import com.api.pojo.products.ProductsPayloadPOJO;
import com.api.utils.HeaderFactory;

import io.restassured.response.Response;

// ============================================================================
// WORKFLOW TEST - PUT /cart/items/{productId}
// ============================================================================
// The contract says: PUT "adds the product, or replaces its quantity".
// So PUT SETS the quantity. It does not ADD to the quantity.
// One response cannot show this. The tests here send several calls and compare the results.
//
// The tests:
//   1. PUT a product, then GET /cart. The cart must show the product with the same quantity.
//   2. PUT the same request twice. The quantity must stay the same (not doubled).
//   3. PUT a product, then PUT it again with a new quantity. The new quantity replaces the old one.
//
// The cart belongs to the account, and an old test may have left items in it.
// So every test empties the cart first (DELETE /cart).
//
// A product is needed for the cart, so every test creates its own product.
// The tests do not delete what they create.
// Example: the shop-api keeps its data in memory, so a restart clears it.
//
// Status: add this class to the suite file (Example: regression-test.xml).
// The server must be running.
// ============================================================================

public class WorkflowPutCartItemTests extends BaseTest {

	private static final String CART = "/cart";
	private static final String PRODUCTS = "/products";

	// ------------------------------------------------------------------------------
	// Small helpers. Each helper sends ONE request.
	// The last argument of each call names the step. It is used only when the request
	// cannot be sent (for example a timeout), so the message says which step failed.
	// The full-permission account is used, because these tests check the state, not the permissions.
	// ------------------------------------------------------------------------------

	private Response createProduct(ProductsPayloadPOJO payload) {
		return sendRequestWithTimeoutHandling(reqSpec_base, PRODUCTS, POST,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), payload,
				"Workflow: POST " + PRODUCTS);
	}

	private Response emptyCart() {
		return sendRequestWithTimeoutHandling(reqSpec_base, CART, DELETE,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), null,
				"Workflow: DELETE " + CART);
	}

	private Response putProductInCart(int productId, int quantity) {
		String endpoint = CART + "/items/" + productId;
		String body = "{\"quantity\": " + quantity + "}";
		return sendRequestWithTimeoutHandling(reqSpec_base, endpoint, PUT,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), body,
				"Workflow: PUT " + endpoint);
	}

	private Response getCart() {
		return sendRequestWithTimeoutHandling(reqSpec_base, CART, GET,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), null,
				"Workflow: GET " + CART);
	}

	// Creates a product and returns its id. The test stops here if the product was not created.
	private int createProductAndGetId(ProductsPayloadPOJO payload) {
		Response response = createProduct(payload);
		Assert.assertEquals(response.getStatusCode(), 201,
				"The product was not created. Response body: " + response.asString());
		return response.jsonPath().getInt("id");
	}

	// Empties the cart. The answer is 204 (no body).
	private void makeCartEmpty() {
		Response response = emptyCart();
		Assert.assertEquals(response.getStatusCode(), 204,
				"The cart was not emptied. Response body: " + response.asString());
	}

	// Sends the PUT and checks that it worked (200).
	private void putAndCheck(int productId, int quantity) {
		Response response = putProductInCart(productId, quantity);
		Assert.assertEquals(response.getStatusCode(), 200,
				"The product was not put in the cart. Response body: " + response.asString());
	}

	// Reads the cart and checks that it has ONE item: the given product with the given quantity.
	private void assertCartHasOnlyThisItem(int productId, int quantity) {
		Response cartResponse = getCart();
		Assert.assertEquals(cartResponse.getStatusCode(), 200,
				"The cart could not be read. Response body: " + cartResponse.asString());

		Assert.assertEquals(cartResponse.jsonPath().getInt("items.size()"), 1,
				"The cart must have exactly one item");
		Assert.assertEquals(cartResponse.jsonPath().getInt("items[0].productId"), productId,
				"The cart has a different product");
		Assert.assertEquals(cartResponse.jsonPath().getInt("items[0].quantity"), quantity,
				"The cart has a different quantity");
	}

	// ------------------------------------------------------------------------------
	// TEST 1: the cart keeps what PUT sent
	// ------------------------------------------------------------------------------
	@Test(enabled = true, priority = 70,
			description = "Workflow: After PUT /cart/items/{productId}, GET /cart must show the same product and quantity")
	void shouldPersistCartItem_andMatchOnGet() {

		int productId = createProductAndGetId(getValidProductsPayloadPOJO());
		makeCartEmpty();

		putAndCheck(productId, 2);

		assertCartHasOnlyThisItem(productId, 2);
	}

	// ------------------------------------------------------------------------------
	// TEST 2: the same PUT twice gives the same cart (idempotent)
	// ------------------------------------------------------------------------------
	@Test(enabled = true, priority = 71,
			description = "Workflow: The same PUT /cart/items/{productId} sent twice must leave the quantity unchanged, not doubled")
	void shouldKeepSameQuantity_whenSamePutIsSentTwice() {

		int productId = createProductAndGetId(getValidProductsPayloadPOJO());
		makeCartEmpty();

		putAndCheck(productId, 2);
		putAndCheck(productId, 2);

		// If PUT added to the quantity, it would be 4 now. It must still be 2.
		assertCartHasOnlyThisItem(productId, 2);
	}

	// ------------------------------------------------------------------------------
	// TEST 3: a second PUT with a new quantity replaces the old quantity
	// ------------------------------------------------------------------------------
	@Test(enabled = true, priority = 72,
			description = "Workflow: A second PUT /cart/items/{productId} with a new quantity must replace the old quantity")
	void shouldReplaceQuantity_whenPutIsSentWithNewQuantity() {

		int productId = createProductAndGetId(getValidProductsPayloadPOJO());
		makeCartEmpty();

		putAndCheck(productId, 2);
		putAndCheck(productId, 5);

		// If PUT added to the quantity, it would be 7 now. It must be 5.
		assertCartHasOnlyThisItem(productId, 5);
	}
}