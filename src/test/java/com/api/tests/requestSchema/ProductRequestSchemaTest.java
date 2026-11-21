package com.api.tests.requestSchema;

import static com.api.schemaPathConstants.GeneratedSchemaPathConstants.*;
import static com.api.utils.payload.ProductsPayloadFactory.*;
import io.restassured.module.jsv.JsonSchemaValidator;
import org.hamcrest.MatcherAssert;

import java.util.function.Supplier;

import org.hamcrest.Matcher;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Contract check (offline: no request is sent to the server).
 * It checks that every VALID Request Payload passes the request schema.
 *
 * WHY run it before any test that uses these payloads:
 * if a valid payload breaks the request schema, we would send a buggy request,
 * and the test would fail for a wrong reason. This check finds it first.
 * It needs no server and is fast, so it can run first, even before the smoke test.
 * (Negative payloads are not checked here: they break the schema on purpose.)
 *
 * This class does not call the server, so it does not extend BaseTest.
 * The schema file is generated from the OpenAPI file.
 * Run "mvn process-test-classes" if it is missing.
 */

public class ProductRequestSchemaTest {

	@DataProvider(name = "validPayloads")
	public Object[][] validPayloads() {

		return new Object[][] { 
				{ "valid payload", VALID_PRODUCTS_PAYLOAD_SUPPLIER },
				{ "Valid payload with minimum-length name", VALID_NAME_MIN_LENGTH_PAYLOAD },
				{ "Valid payload with maximum-length name", VALID_NAME_MAX_LENGTH_PAYLOAD },
				{ "Valid payload with minimum-length sku", VALID_SKU_MIN_LENGTH_PAYLOAD },
				{ "Valid payload with maximum-length sku", VALID_SKU_MAX_LENGTH_PAYLOAD },
				{ "Valid payload with minimum price", VALID_PRICE_MIN_PAYLOAD },
				{ "Valid payload with maximum price", VALID_PRICE_MAX_PAYLOAD },
				{ "Valid payload with minimum stock", VALID_STOCK_MIN_PAYLOAD }, 
				};
	}

	@Test(dataProvider = "validPayloads", description = "Contract check: a valid payload must pass the request schema")
	public void validPayloadShouldPassTheRequestSchema(String scenarioName, Supplier<?> payloadSupplier) {

		// ১. Supplier থেকে নতুন ObjectNode ইনস্ট্যান্স তৈরি করা
		
		// get() runs the the lambda body and returns what that body builds.
		// With Supplier<?>, Java only knows the result is an Object.
		// The real type can be a POJO or an ObjectNode, so it is converted below.
		// Every call builds a NEW object.
		Object payload = payloadSupplier.get(); 
		ObjectNode payloadNode = new ObjectMapper().valueToTree(payload);

		// ২. ObjectNode কে বৈধ JSON স্ট্রিংয়ে রূপান্তর করা
		// (Jackson-এর ObjectNode.toString() সবসময় valid JSON string রিটার্ন করে)
		
		// In a request, REST Assured's body() converts a POJO / ObjectNode / Map 
		// to a JSON String by itself, if the Content-Type is application/json.
		// A schema matcher does not do this. It takes a JSON String.
		// So the ObjectNode is converted to a String here.
		// (ObjectNode.toString() always gives a valid JSON string.)
		String jsonPayload = payloadNode.toString();

		// ৩. সার্ভারে রিকোয়েস্ট পাঠানোর আগেই request Payload-কে রিকোয়েস্ট স্কিমার বিপরীতে match করে নেয়া উচিৎ 

		// matchesJsonSchemaInClasspath() makes a "JsonSchemaValidator" Object from the given schema file.
		// It only knows the path of the schema file. No validation happens here.
		Matcher<String> jsonSchemaValidator = JsonSchemaValidator.matchesJsonSchemaInClasspath(PRODUCT_REQUEST_SCHEMA);

		// Match the actual payload against the expected schema.
		// MatcherAssert.assertThat() is a HARD assertion: it internally calls schemaMatcher.matches(jsonPayload).
		// If the payload passes, nothing happens and the test passes.
		// If a schema rule is broken, the test stops at once and throws an AssertionError.
		// The error message is made automatically (we do not write it). It has two parts:
		//   - our text: "Schema validation failed for scenario: ..."
		//   - the report from the matcher: which schema rule broke and which field.
		// No request is sent to the server in this test.
		//
		// WHY hard Assertion:
		// A request payload that breaks the request schema is not a valid request payload.
		// Sending a request with it is meaningless: it would be a buggy request.
		// So the test must stop at once, and it must not go on as if the payload were usable.
		// (This test also has only one check per payload, so stopping loses no other failure.)
		// (In a response validator, a hard assertion would hide failures that are already saved
		// in a SoftAssert, so a soft assertion is used there.)
		MatcherAssert.assertThat("Schema validation failed for scenario: " + scenarioName, jsonPayload, jsonSchemaValidator);
	}

}
