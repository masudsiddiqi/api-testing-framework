package com.api.validation;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static com.api.validation.SensitiveInfoLeakValidator.assertNoSensitiveHeadersPresent;
import static com.api.validation.SensitiveInfoLeakValidator.assertNoSensitiveKeysInResponseBody;

import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.StringDescription;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;

import com.api.dto.ExpectedStatusCode;
import com.api.dto.TestScenarioRecord;

import io.qameta.allure.Allure;
import io.restassured.response.Response;

/**
 * Stateless response validation utilities, called independently as separate
 * layers directly from the test class.
 */
public class ResponseValidator {

	// Private constructor ensures this is a utility class and no instances are
	// allowed.
	private ResponseValidator() {

	}

	// ------------------------------------------------------------------------------
	// Layer 1: Status Code Validation (Hard Assertion)
	//
	// Why hard: every later layer expects the response that was planned for this
	// scenario.
	// If the status code is not the expected one, the headers, body and schema of
	// that response
	// mean nothing, and checking them would only add confusing extra failures.
	// So the test stops here, at the first failure.
	// The method also returns the actual status code, which Layer 3 uses.
	// ------------------------------------------------------------------------------
	public static int validateStatusCode(Response response, ExpectedStatusCode expected, String testDescription) {
		int expectedStatus = expected.status;
		// Every HTTP response has a status line, so the status code is always there and
		// "no value"
		// (null) cannot happen. getStatusCode() therefore returns a plain int, and no
		// Integer is needed.
		// (Layer 3 uses an Integer for the "status" field in the body, because that
		// field can be missing.)
		int actualStatus = response.getStatusCode();
		Assert.assertEquals(actualStatus, expectedStatus, "Failed Status Code for Scenario: " + testDescription);
		return actualStatus;
	}

	// ------------------------------------------------------------------------------
	// Layer 2: Header Validation (Hard Assertion)
	//
	// Why hard: Layer 3 and Layer 4 read the body as JSON. If the Content-Type
	// header is missing or
	// is not JSON, the body cannot be trusted to be JSON, and those layers would
	// fail with parsing
	// errors that hide the real problem. A leaked debug header (2.2) is a security
	// defect,
	// so it also stops the test.
	// 2.3: the "Allow" header of a 405 response is a hard check (only when the
	// scenario's policy asks for it).
	// ------------------------------------------------------------------------------
	public static void validateHeaders(Response response, ExpectedStatusCode expected, String testDescription) {

		// 2.1 Response Content-Type Header Validation
		String contentType = response.getHeader("Content-Type");
		
		// Check if the value is missing, null, empty, or only whitespace.
		// - null: The header/key itself is missing, or its value is null.
		// - Blank: The header exists, but its value is "" or only spaces (e.g. "   ").
		boolean isMissingNullEmpty = (contentType == null || contentType.isBlank());
		
		Assert.assertFalse(isMissingNullEmpty, 
				"Content-Type header is missing, or its value is null or empty: " +testDescription);
		
		boolean isJsonContentType = contentType.contains("application/json")
				|| contentType.contains("application/problem+json");

		Assert.assertTrue(isJsonContentType, "Expected application/json or application/problem+json, but got: "
				+ contentType + ": " + testDescription);

		// 2.2 Security Leakage via Sensitive Custom Headers Validation
		assertNoSensitiveHeadersPresent(response);

		// 2.3 "Allow" Header Validation only for 405 Method Not Allowed
		// RFC 9110: a 405 (Method Not Allowed) response must contain an "Allow" header
		// that lists the
		// supported HTTP methods. The server (Spring's DispatcherServlet) follows this
		// RFC.
		validateAllowHeader(response, expected);

	}

// ------------------------------------------------------------------------------------------------
// The policy of validating schema and allow header is defined in SchemaAndAllowHeaderValidationPolicy class
// but which policy (validating schema/allow header or not) will be applied
// is decided in Data provider based on scenario. 
// Whether validateAllowHeader() expected.policy.validateAllowHeader).
// -------------------------------------------------------------------------------------------------
	private static void validateAllowHeader(Response response, ExpectedStatusCode expected) {

		if (!expected.policy.validateAllowHeader) {
			// Policy is off. So remaining statements below "return" inside this method must
			// not execute.
			// Without this "return;" the code below would still check the
			// "Allow" header (and fail on the real server) although the policy asked to
			// skip it.
			// In short, the "return" is restricting the execution of this method.
			return;
		}

		String allowHeader = response.getHeader("Allow");

		// The server must comply with the RFC: a hard requirement
		Assert.assertNotNull(allowHeader,
				"405 Response must contain 'Allow' header specifying supported HTTP methods. Reason: "
						+ expected.policy.reason);
		System.out.println("[INFO] Supported HTTP Methods -> " + allowHeader);
	}

	// ------------------------------------------------------------------------------
	// Layer 3: Body Validation (Soft Assertion)
	//
	// Why soft: each body check looks at a different field (or part) of the body, and no check
	// needs the result of another. So one failed field check should not hide the others.
	// A soft assertion records the failure and lets the test go on. All recorded failures
	// (Layer 3 and Layer 4) are reported together by softAssert.assertAll() at the end.
	//
	// Exception: 3.2 (a leaked key in the body) is a HARD assertion.
	// A leaked sensitive key is a security defect, so the test stops at once.
	// This has two effects:
	//   - Layer 4 (schema) does not run.
	//   - softAssert.assertAll() is not reached, so the soft failures that were
    //	   already recorded (for example 3.1) are not reported.
	// ------------------------------------------------------------------------------

	// 3.1 Body Status Code should be the same as the Response Line HTTP Status
	// Code.
	//
	// Using getObject("status", Integer.class) instead of getInt("status"):
	// getInt() has a primitive `int` return type, so when the "status" field is
	// absent
	// in the response body, RestAssured's internal conversion returns null, and
	// unboxing that null to int throws a NullPointerException *inside* getInt()
	// itself — before this line even finishes executing. Declaring the variable
	// as `Integer` on the left-hand side does NOT prevent this, because the
	// unboxing happens on the method's return value, not on our assignment.
	// getObject(path, Integer.class) returns a proper Integer (or null) without
	// any unboxing, so the null-check below actually gets a chance to run.
	//
	// Note: success (2xx) response bodies for this API do not include a "status"
	// field at all (e.g. POST /booking's happy-path response only returns
	// {bookingid, booking: {...}}) — only some of this API's error responses
	// happen to echo the HTTP status in the body. So bodyStatusCode is expected
	// to be null for happy-path scenarios, and this check is skipped for them;
	// it only applies when the response body itself carries a status field.

	public static void validateBody(Response response, int responseLineStatusCode, String testDescription,
			SoftAssert softAssert) {

		// 3.1 Body Status Code should be the same as the Response Line HTTP Status Code

		// The "status" field inside the JSON body can be missing (success bodies do not
		// have it),
		// so it is read as an Integer, which can be null.
		// The status line of the response is a different thing: it can never be
		// missing,
		// and Layer 1 reads it as a plain int.
		Integer bodyStatusCode = response.jsonPath().getObject("status", Integer.class);
		// Integer bodyStatusCode = response.jsonPath().getInt("status"); throws null
		// pointer exception if "status" field is absent

		if (bodyStatusCode != null) {
			softAssert.assertEquals((int) bodyStatusCode, responseLineStatusCode,
					"Response Line Status Code & Response Body Status Code do not match: " + testDescription);
		}

		// 3.2 Security Leakage via Sensitive Keys in Error Response Body
		assertNoSensitiveKeysInResponseBody(response);

	}

	// ------------------------------------------------------------------------------
	// Layer 4: Schema Validation (Soft Assertion)
	//
	// Why soft: this is the last layer and nothing depends on it. A soft assertion
	// lets a schema breach be reported together with the Layer 3 failures, in the same run.
	//
	// What a hard assertion would do here: it would throw at the first schema breach,
	// so softAssert.assertAll() would never run. The Layer 3 failures that are already
	// saved in softAssert would be lost, and only the schema breach would be reported.
	// ------------------------------------------------------------------------------
	public static void validateSchema(Response response, ExpectedStatusCode expected, String testDescription,
			SoftAssert softAssert) {

		// Some responses have no body that the contract defines (example: a 413 from a
		// gateway).
		// For them the policy turns the schema check off, and the method stops here.
		if (!expected.policy.validateSchema) {
			System.out.println(
					"[INFO] Schema validation skipped for '" + testDescription + "' — " + expected.policy.reason);
			return; // If Schema Validation is False, execution ends at this line
		}

		String body = response.asString();
		Matcher<?> schemaMatcher = matchesJsonSchemaInClasspath(expected.policy.schemaPath);
		boolean isValid = schemaMatcher.matches(body);

		if (isValid) {
			return; // execution ends here if matches(body) returns true.
		}

		// নিচের কোড শুধু তখনই চলে, যখন "response body" response schema-র কোনো নিয়ম লঙ্ঘন করে।
		// অর্থাৎ schemaMatcher.matches(body) false ফেরত দেয়।
		// তাই failure message শুধু schema matching ব্যর্থ হলে তৈরি হয়।

		// matches() body-টাকে schema-র সাথে মিলিয়ে দেখে।
		// মিল না পেলে JsonSchemaValidator object কারণটা নিজের ভেতরে জমা রাখে।
		// describeTo() সেই কারণ StringDescription object-এ লিখে দেয়: কোন নিয়ম ভেঙেছে আর কোন field-এ।
		// কারণটা matches() চলার সময়ই মনে রাখা হয়, তাই আগে matches(), তারপর describeTo()।

		StringDescription report = new StringDescription();
		schemaMatcher.describeTo(report);

		String  message = "Schema breach detected in: " + testDescription + "\nSchema: " + expected.policy.schemaPath + " ("
				+ expected.policy.reason + ")" + "\nReport: " + report + "\nActual body: " + body;
		

		// The server must strictly honor the contract.
		//
		// softAssert.fail() is used, not softAssert.assertTrue(): 
		// - assertTrue() adds "expected [true] but found [false]" to the message.
		//
		// MatcherAssert.assertThat() can make the report by itself, but it is a HARD assertion.
		// It would throw Assertion Error at once, so softAssert.assertAll() would never run,
		// and the Layer 3 failures that are already saved in softAssert would be lost.
		// So the report is built by hand here and given to softAssert.fail().
		
		// এটা soft assertion: এই assertion failure SoftAssert object-এ জমা থাকে, test এখানে থামে না।
		// অন্যান্য soft assertion failure-ও একই object-এ জমা থাকে।
		// softAssert.assertAll() চললে SoftAssert object-এ জমা সব soft assertion failure একসাথে report হয়।
		// আর Allure report-এ দেখা যায়।
		softAssert.fail(message);
	}

	/**
	 * Runs the full validation sequence (status → headers → body → schema) in the
	 * correct order, with a single SoftAssert shared across the soft-assert layers
	 * (body, schema) so their failures are reported together.
	 *
	 * This is a convenience wrapper for the common case where all four layers are
	 * validated in sequence. For scenarios whose response genuinely has no body to
	 * validate — e.g. an HTTP 204 No Content response, where parsing or
	 * schema-matching an empty body would be meaningless or error-prone — call
	 * validateStatusCode/validateHeaders directly instead of validateAll(),
	 * skipping validateBody/validateSchema for that scenario.
	 */
	public static void validateAll(Response response, TestScenarioRecord record) {
		/**
		 * VALIDATION STRATEGY (Hybrid Approach):
		 * 
		 * - HARD ASSERTIONS (Status Code & Headers): Acts as a fail-fast mechanism. If
		 * the fundamental response (e.g., status code or core header) is incorrect,
		 * further validation is unnecessary because an unexpected response payload is
		 * likely invalid or malformed. Halting execution immediately prevents false
		 * positives and saves useless test execution time.
		 * 
		 * - SOFT ASSERTIONS (Body & Schema): Used for comprehensive data validation.
		 * Even if a specific body field or schema attribute fails, soft assertions
		 * allow the test execution to continue so that all response body defects and
		 * schema violations are captured and reported together at the end.
		 */

		// 1. Response Line Status Code Validation (Hard Assertion)
		int responseLineStatusCode = validateStatusCode(response, record.expectedStatusCode(),
				record.scenarioDescription());

		// 2. Response Headers Validation (Hard Assertion)
		validateHeaders(response, record.expectedStatusCode(), record.scenarioDescription());

		// 3. Response Body Data Validation (Soft Assertion)
		SoftAssert softAssert = new SoftAssert();
		ResponseValidator.validateBody(response, responseLineStatusCode, record.scenarioDescription(), softAssert);

		// 4. Response Schema Validation (Soft Assertion)
		ResponseValidator.validateSchema(response, record.expectedStatusCode(), record.scenarioDescription(),
				softAssert);

		// assertAll() collects and reports all soft assertion failures;
		// Without it, soft assertion failures would not be recorded or shown in the
		// TestNG report.
		softAssert.assertAll();
	}
}