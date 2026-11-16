package com.api.utils.payload;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import com.api.pojo.products.ProductsPayloadPOJO;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static com.api.utils.payload.PayloadUtils.*;

// Payloads for the scenarios of POST /products.
//
// How to read this file:
// - The layer names and the ids (3.2a, 4.2b ...) are the same as in the data provider.
//   So a constant can be found from a scenario, and a scenario from a constant.
// - The name of a constant starts with the TYPE of the problem:
//   MISSING_, LENGTH_, FORMAT_, RANGE_, INVALID_DATA_TYPE_, INVALID_ENUM_VALUE_, MASS_ASSIGNMENT_.
//   Type the start in the IDE and it lists the whole group.
// - Every negative payload breaks exactly ONE rule, except where a comment says it cannot.
public class ProductsPayloadFactory {

	public static ProductsPayloadPOJO getValidProductsPayloadPOJO() {

		// The sku must be new every time (the contract says "Unique").
		// 8 hex characters give more than 4 billion values.
		// The sku pattern needs capital letters, but a UUID has small letters, so toUpperCase() is needed.
		String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

		return new ProductsPayloadPOJO("BOOK-" + unique.toUpperCase(), "Dream-" + unique, "books", 39.9, 20);
	}

	// This supplier must be declared BEFORE the constants below: they use it when the class is made.
	public static final Supplier<ProductsPayloadPOJO> VALID_PRODUCTS_PAYLOAD_SUPPLIER = ProductsPayloadFactory::getValidProductsPayloadPOJO;


	// ============================================================================
	// Layer 0: Infrastructure Layer Exception Handling Validation
	// Expected: 413. The size limit answers before the payload is read.
	// ============================================================================

	// 0.1 Oversized Payload
	public static final Supplier<ObjectNode> OVERSIZE_PAYLOAD =
			withHugeText(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/category", OVER_10_MB);


	// ============================================================================
	// Layer 3: Parsing & Syntax Validation Layer Exception Handling Validation
	// Expected: 400. The server cannot read the body into the request object.
	// ============================================================================
	// 3.1 (missing required query param) is not used: POST /products has no query parameter.
	// 3.3 (malformed JSON) and 3.4 (missing body) are not payload constants:
	//     the provider uses a file and null for them.

	// 3.2 Unknown field. The contract does not allow extra fields (additionalProperties: false).
	// 3.2a: a harmless name.
	public static final Supplier<ObjectNode> UNKNOWN_KEY_PAYLOAD =
			withUnknownKey(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "unknown_field", "unknown_value");

	// 3.2b and 3.2c: a field that the SERVER makes itself (it is only in the response).
	// A client must not choose it (Mass Assignment / Over-Posting).
	public static final Supplier<ObjectNode> MASS_ASSIGNMENT_ID_PAYLOAD =
			withUnknownKey(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "id", 6);

	public static final Supplier<ObjectNode> MASS_ASSIGNMENT_CREATED_AT_PAYLOAD =
			withUnknownKey(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "createdAt", "2000-01-01T00:00:00Z");

	// 3.5 Invalid data type. One constant for each TYPE of field, not for each field.
	// The wrong value must be one that the JSON library cannot convert.
	// Example: a number or a boolean for a text field is accepted (turned into a text), so an array is used.

	// 3.5a: a text field (name, sku and description are the same type: name stands for the three).
	public static final Supplier<ObjectNode> INVALID_DATA_TYPE_NAME_AS_ARRAY_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/name", List.of(1, 2));

	// 3.5b: a number field. A text that looks like a number ("9.99") is accepted, so a real text is used.
	public static final Supplier<ObjectNode> INVALID_DATA_TYPE_PRICE_AS_TEXT_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/price", "not_a_number");

	// 3.5c: an integer field.
	public static final Supplier<ObjectNode> INVALID_DATA_TYPE_STOCK_AS_TEXT_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/stock", "not_a_number");

	// 3.5d: an integer field with a decimal. The contract says integer.
	// The JSON library may accept 1.5 and turn it into 1. Then this scenario fails and shows a gap.
	public static final Supplier<ObjectNode> INVALID_DATA_TYPE_STOCK_AS_DECIMAL_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/stock", 1.5);

	// 3.6 Invalid enum value. The value is not one of the values that the contract allows.
	// In this contract the answer is 400: the value cannot be read into the enum.
	public static final Supplier<ObjectNode> INVALID_ENUM_VALUE_CATEGORY_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/category", "cars");


	// ============================================================================
	// Layer 4: Constraint Validation Layer Exception Handling Validation
	// The body is readable. The rules of the values are checked.
	// Expected for all payloads in this layer (in this contract): 422
	// The groups: 4.1 Presence, 4.2 Length, 4.3 Format, 4.4 Range, 4.5 Cross-Field.
	// (A Format rule can be written as a pattern. The number of a group is the same in every provider.)
	// ============================================================================

	// ----- 4.1 Presence: a required field is missing. Every required field is tested alone. -----
	public static final Supplier<ObjectNode> MISSING_SKU_PAYLOAD =
			without(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/sku");

	public static final Supplier<ObjectNode> MISSING_NAME_PAYLOAD =
			without(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/name");

	public static final Supplier<ObjectNode> MISSING_CATEGORY_PAYLOAD =
			without(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/category");

	public static final Supplier<ObjectNode> MISSING_PRICE_PAYLOAD =
			without(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/price");

	public static final Supplier<ObjectNode> MISSING_STOCK_PAYLOAD =
			without(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/stock");

	// ----- 4.2 Length: the length of a text. -----
	// Boundary: the value next to the limit. Extreme: a value far from the limit (for example empty).

	// The sku rule is a pattern: capital letters, digits and "-", 3 to 20 characters.
	// The length is part of the pattern, so the contract has no minLength or maxLength for it.

	// 4.2a: one below the minimum (3).
	public static final Supplier<ObjectNode> LENGTH_SKU_TOO_SHORT_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/sku", "BK");

	// 4.2b: one above the maximum (20).
	public static final Supplier<ObjectNode> LENGTH_SKU_TOO_LONG_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/sku", "B".repeat(21));

	// 4.2c: extreme: empty (0 characters).
	public static final Supplier<ObjectNode> LENGTH_SKU_EMPTY_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/sku", "");

	// The name rule: 1 to 100 characters.

	// 4.2d: an empty name has 0 characters. The minimum length is 1, so this is the boundary
	// (one below the minimum) and also the extreme (empty): they are the same value here.
	public static final Supplier<ObjectNode> LENGTH_NAME_EMPTY_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/name", "");

	// 4.2e: one above the maximum (100).
	public static final Supplier<ObjectNode> LENGTH_NAME_TOO_LONG_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/name", "A".repeat(101));

	// ----- 4.3 Format: the form of a text (the allowed characters, a pattern). -----

	// 4.3a: a small letter is not allowed in the sku. The length is valid (7).
	public static final Supplier<ObjectNode> FORMAT_SKU_LOWERCASE_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/sku", "abc-123");

	// 4.3b: an underscore is not allowed in the sku. Example: a wrong pattern that uses
	// the word-character shortcut (\w) or the range A-z would accept it. The length is valid (7).
	public static final Supplier<ObjectNode> FORMAT_SKU_WITH_UNDERSCORE_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/sku", "ABC_123");

	// 4.3c: three decimals in the price. The contract says "At most 2 decimals" in words,
	// so a schema cannot check this. Only the backend does.
	public static final Supplier<ObjectNode> FORMAT_PRICE_THREE_DECIMALS_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/price", 1.005);

	// ----- 4.4 Range: the value of a number. -----
	// The price: the minimum is 0.01 and the maximum is 9999999.99. The step is 0.01 (two decimals).
	// The stock: the minimum is 0. It has no maximum, so it has no "one above the maximum".

	// 4.4a: price, one below the minimum: 0. (0.009 would break a second rule: the decimals.)
	public static final Supplier<ObjectNode> RANGE_PRICE_ONE_BELOW_MIN_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/price", 0);

	// 4.4b: price, one above the maximum. It also has 8 digits, so it breaks the digits rule too.
	// This cannot be avoided: 9999999.99 is the biggest number with 7 digits and 2 decimals.
	// Use an int, not 10000000.00 as a double: Jackson writes a double above 9999999 in scientific form
	// (Example: 10000000.99 becomes 1.000000099E7).
	public static final Supplier<ObjectNode> RANGE_PRICE_ONE_ABOVE_MAX_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/price", 10000000);

	// 4.4c: price, extreme: a negative price.
	public static final Supplier<ObjectNode> RANGE_PRICE_NEGATIVE_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/price", -150);

	// 4.4d: stock, one below the minimum.
	public static final Supplier<ObjectNode> RANGE_STOCK_ONE_BELOW_MIN_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/stock", -1);

	
	// ----- 4.5 Cross-Field -----
	// Not applicable for POST /products: no rule compares two fields.


	// ============================================================================
	// Layer 5: Controller/Service Layer Exception Handling Validation
	// ============================================================================
	// 5.1 (path parameter value not found) is not used: POST /products has no path parameter.
	// A duplicate sku (409) needs a product that exists first, so it is a stateful test:
	// it is done in the workflow tests and not with a payload constant.


	// ============================================================================
	// Valid boundaries (positive data provider). Expected: 201
	// ============================================================================
	// A valid request saves a product, and the sku must be unique (the contract says "Unique").
	// So the sku must be new on every get(), also when the same scenario runs again and again.
	// withValue() is fine for name, price and stock: it changes only that field, and
	// VALID_PRODUCTS_PAYLOAD_SUPPLIER makes a new sku on every get().
	// For the sku itself mutation() is needed: withValue() calculates its value only once.

	public static final Supplier<ObjectNode> VALID_NAME_MIN_LENGTH_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/name", "A");

	public static final Supplier<ObjectNode> VALID_NAME_MAX_LENGTH_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/name", "A".repeat(100));

	public static final Supplier<ObjectNode> VALID_PRICE_MIN_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/price", 0.01);

	public static final Supplier<ObjectNode> VALID_PRICE_MAX_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/price", 9999999.99);

	// The stock has no maximum, so there is only the minimum.
	public static final Supplier<ObjectNode> VALID_STOCK_MIN_PAYLOAD =
			withValue(VALID_PRODUCTS_PAYLOAD_SUPPLIER, "/stock", 0);

	// Valid boundary: the minimum length of the sku is 3 characters.
	// nextInt(1296, 46656) in base 36 always gives exactly 3 characters (0-9 and A-Z) that fit the pattern.
	public static final Supplier<ObjectNode> VALID_SKU_MIN_LENGTH_PAYLOAD =
			mutation(VALID_PRODUCTS_PAYLOAD_SUPPLIER, payloadTree -> payloadTree.put("sku",
					Integer.toString(ThreadLocalRandom.current().nextInt(1296, 46656), 36).toUpperCase()));

	// Valid boundary: the maximum length of the sku is 20 characters.
	// A UUID has 32 hex characters (0-9 and A-F after toUpperCase), so the first 20 are enough and never repeat.
	public static final Supplier<ObjectNode> VALID_SKU_MAX_LENGTH_PAYLOAD =
			mutation(VALID_PRODUCTS_PAYLOAD_SUPPLIER, payloadTree -> payloadTree.put("sku",
					UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 20)));
}