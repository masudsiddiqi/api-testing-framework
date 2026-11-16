package com.api.tests.flows;

import static com.api.client.HttpMethods.DELETE;
import static com.api.client.HttpMethods.GET;
import static com.api.client.HttpMethods.POST;
import static com.api.client.HttpMethods.PUT;
import static com.api.utils.payload.ProductsPayloadFactory.getValidProductsPayloadPOJO;

import java.util.Map;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.api.base.BaseTest;
import com.api.pojo.products.ProductsPayloadPOJO;
import com.api.utils.HeaderFactory;

import io.restassured.response.Response;

// ============================================================================
// FLOW TEST - ONE FLOW THAT USES MORE THAN ONE RESOURCE
// ============================================================================
// A workflow test class (Example: WorkflowPostProductTests) belongs to one resource.
// This class is different. The flow touches three resources, so it has its own folder: flows.
//
// The flow:
//   1. POST /products              create a product
//   2. PUT  /cart/items/{id}       put the product in the cart
//   3. POST /orders                place the order from the cart
//   4. GET  /orders/{id}           read the order back
//   5. GET  /products/{id}         read the product back (the stock must go down)
//
// Each step needs the result of the step before it. This is why one test sends all the calls.
//
// The tests do not delete what they create. Example: the shop-api keeps its data in memory,
// so a restart clears it.
//
// Status: add this class to the suite file (Example: regression-test.xml).
// The server must be running.
// ============================================================================

public class CreateOrderFlowTest extends BaseTest {

	private static final String PRODUCTS = "/products";
	private static final String CART = "/cart";
	private static final String ORDERS = "/orders";

	// How many pieces the test puts in the cart.
	private static final int QUANTITY = 2;

	// A valid order body. The address follows the rules of the contract.
	private static final String ORDER_BODY = "{\"shippingAddress\": {\"street\": \"12 Market Street\", "
			+ "\"city\": \"Dhaka\", \"postalCode\": \"1212\", \"country\": \"BD\"}, "
			+ "\"note\": \"Leave at the door\"}";

	// ------------------------------------------------------------------------------
	// Small helpers. Each helper sends ONE request.
	// The last argument of each call names the step. It is used only when the request
	// cannot be sent (for example a timeout), so the message says which step failed.
	// The full-permission account is used, because these tests check the flow, not the permissions.
	// ------------------------------------------------------------------------------

	private Response createProduct(ProductsPayloadPOJO payload) {
		return sendRequestWithTimeoutHandling(reqSpec_base, PRODUCTS, POST,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), payload,
				"Flow: POST " + PRODUCTS);
	}

	private Response getProduct(int productId) {
		String endpoint = PRODUCTS + "/" + productId;
		return sendRequestWithTimeoutHandling(reqSpec_base, endpoint, GET,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), null,
				"Flow: GET " + endpoint);
	}

	private Response emptyCart() {
		return sendRequestWithTimeoutHandling(reqSpec_base, CART, DELETE,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), null,
				"Flow: DELETE " + CART);
	}

	private Response putProductInCart(int productId, int quantity) {
		String endpoint = CART + "/items/" + productId;
		String body = "{\"quantity\": " + quantity + "}";
		return sendRequestWithTimeoutHandling(reqSpec_base, endpoint, PUT,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), body,
				"Flow: PUT " + endpoint);
	}

	private Response placeOrder() {
		// POST /orders needs the header Idempotency-Key.
		// A new value for every order, so the server never sees the same key twice.
		Map<String, String> headers = HeaderFactory.bearerAuthHeadersForFullPermissionUser();
		headers.put("Idempotency-Key", UUID.randomUUID().toString());
		return sendRequestWithTimeoutHandling(reqSpec_base, ORDERS, POST, headers, ORDER_BODY,
				"Flow: POST " + ORDERS);
	}

	private Response getOrder(int orderId) {
		String endpoint = ORDERS + "/" + orderId;
		return sendRequestWithTimeoutHandling(reqSpec_base, endpoint, GET,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), null,
				"Flow: GET " + endpoint);
	}

	// Creates a product and returns its id. The test stops here if the product was not created.
	private int createProductAndGetId(ProductsPayloadPOJO payload) {
		Response response = createProduct(payload);
		Assert.assertEquals(response.getStatusCode(), 201,
				"The product was not created. Response body: " + response.asString());
		return response.jsonPath().getInt("id");
	}

	// Puts the product in an empty cart.
	private void fillCartWithProduct(int productId) {
		// The cart belongs to the account, and an old test may have left items in it.
		// So the cart is emptied first. The answer is 204 (no body).
		Response clearResponse = emptyCart();
		Assert.assertEquals(clearResponse.getStatusCode(), 204,
				"The cart was not emptied. Response body: " + clearResponse.asString());

		Response cartResponse = putProductInCart(productId, QUANTITY);
		Assert.assertEquals(cartResponse.getStatusCode(), 200,
				"The product was not put in the cart. Response body: " + cartResponse.asString());
	}

	// Places the order and returns its id. The test stops here if the order was not placed.
	private int placeOrderAndGetId() {
		Response response = placeOrder();
		Assert.assertEquals(response.getStatusCode(), 201,
				"The order was not placed. Response body: " + response.asString());
		return response.jsonPath().getInt("id");
	}

	// ------------------------------------------------------------------------------
	// TEST 1: the order has the product that was put in the cart
	// ------------------------------------------------------------------------------
	@Test(enabled = true, priority = 60,
			description = "Flow: create a product, put it in the cart, place an order. GET /orders/{id} must show the same product and quantity")
	void shouldPlaceOrder_withTheProductFromTheCart() {

		ProductsPayloadPOJO product = getValidProductsPayloadPOJO();

		int productId = createProductAndGetId(product);
		fillCartWithProduct(productId);
		int orderId = placeOrderAndGetId();

		// Read the order back from the server.
		Response orderResponse = getOrder(orderId);
		Assert.assertEquals(orderResponse.getStatusCode(), 200,
				"The order could not be read. Response body: " + orderResponse.asString());

		Assert.assertEquals(orderResponse.jsonPath().getString("status"), "placed",
				"A new order must have the status placed");
		Assert.assertEquals(orderResponse.jsonPath().getInt("items.size()"), 1,
				"The order must have exactly one item");
		Assert.assertEquals(orderResponse.jsonPath().getInt("items[0].productId"), productId,
				"The order has a different product than the one in the cart");
		Assert.assertEquals(orderResponse.jsonPath().getInt("items[0].quantity"), QUANTITY,
				"The order has a different quantity than the one in the cart");

		// The total is a decimal number, so it is compared with a small tolerance.
		double expectedTotal = product.getPrice() * QUANTITY;
		Assert.assertEquals(orderResponse.jsonPath().getDouble("total"), expectedTotal, 0.001,
				"The total of the order is not price x quantity");
	}

	// ------------------------------------------------------------------------------
	// TEST 2: the order empties the cart and lowers the stock
	// ------------------------------------------------------------------------------
	@Test(enabled = true, priority = 61,
			description = "Flow: after the order is placed, the stock of the product goes down and the cart is empty")
	void shouldLowerStockAndEmptyCart_afterOrderIsPlaced() {

		ProductsPayloadPOJO product = getValidProductsPayloadPOJO();
		int stockBefore = product.getStock();

		int productId = createProductAndGetId(product);
		fillCartWithProduct(productId);
		placeOrderAndGetId();

		// The stock of the product must go down by the quantity in the order.
		Response productResponse = getProduct(productId);
		Assert.assertEquals(productResponse.getStatusCode(), 200,
				"The product could not be read. Response body: " + productResponse.asString());
		Assert.assertEquals(productResponse.jsonPath().getInt("stock"), stockBefore - QUANTITY,
				"The stock did not go down by the quantity in the order");

		// The cart must be empty after the order.
		Response cartResponse = sendRequestWithTimeoutHandling(reqSpec_base, CART, GET,
				HeaderFactory.bearerAuthHeadersForFullPermissionUser(), null,
				"Flow: GET " + CART);
		Assert.assertEquals(cartResponse.getStatusCode(), 200,
				"The cart could not be read. Response body: " + cartResponse.asString());
		Assert.assertEquals(cartResponse.jsonPath().getInt("items.size()"), 0,
				"The cart must be empty after the order is placed");
	}
}
