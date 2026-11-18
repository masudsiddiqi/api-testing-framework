package com.api.base;

import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import com.api.client.APIClient;
import com.api.utils.ConfigUtils;
import com.api.utils.RequestSpecFactory;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class BaseTest {

	// public static final String BASE_URI = System.getProperty("baseUri",
	// "http://127.0.0.1:4010");
	// Resolved from the active "env" (see ConfigUtils), so URI and expectations
	// always match.
	public static final String BASE_URI = ConfigUtils.getBaseUri();

	protected RequestSpecification reqSpec_base; // Never make it static

	@BeforeClass(alwaysRun = true)
	public void setupBaseSpec() {

		// The request spec used by every test request. Base URI, 3 s timeouts and "log
		// the request and
		// the response if a validation fails" come from RequestSpecFactory.
		reqSpec_base = RequestSpecFactory.baseRequestSpecBuilder().

		// ALLURE REPORT INTEGRATION. Adding the AllureRestAssured filter is all that is
		// needed to put every
		// request sent with reqSpec_base into the Allure report. For each request the
		// filter creates an
		// attachment named "Request": an HTML snapshot of the whole HTTP request that
		// was sent (URL and
		// method, Body, Headers; Allure's default template also adds Cookies, if any,
		// and a Curl block).
		// The response is a second, separate attachment.
		//
		// WHY OUR OWN TEMPLATE. setRequestTemplate("http-request-masked.ftl") makes
		// Allure draw the
		// "Request" attachment with src/test/resources/tpl/http-request-masked.ftl
		// instead of its default
		// template. The default prints the Authorization header (a Bearer token, or the
		// base64 of
		// "username:password" for Basic) twice: in the Headers list and again in the
		// Curl block. Ours prints
		// "Bearer [masked]" / "Basic [masked]" and leaves the Curl block out. This only
		// changes what the
		// REPORT shows: the request that is sent still carries the real header.
		//
		// THE TOKEN CAN APPEAR IN TWO PLACES IN THE REPORT, and each one has its own
		// masking:
		// 1. "Request" attachment (Test body). It is an HTML snapshot of the whole HTTP
		// request that was
		// sent: Request Line, Headers, and Body (Allure's default template also adds
		// Cookies, if any, and a Curl block). The Allure filter on the next line
		// (AllureRestAssured)
		// creates it for every request sent with reqSpec_base, and the template decides
		// how it is
		// drawn. The token is in its line "authorization: Bearer ..." -> masked HERE,
		// by the template.
		// (The response is a second, separate attachment.)
		// 2. "Parameters" section of a test, line "arg0: TestScenarioRecord[...
		// headers={authorization=Bearer ...}]"
		// ("arg0" is the first argument of the test method, which is the
		// TestScenarioRecord)
		// -> masked by TestScenarioRecord.toString(), in TestScenarioRecord.java.
		// Each one hides only its own place; keep both, with the same output ("Bearer
		// [masked]").
		//
		// LOGIN. The login request appears in neither place: it uses
		// RequestSpecFactory.loginRequestSpecification(), which has no Allure filter at
		// all.
				addFilter(new AllureRestAssured().setRequestTemplate("http-request-masked.ftl")).build();

		// Health Check
		Response response;
		try {
			response = RestAssured.given(reqSpec_base).when().get("/ping");
		} catch (Exception e) {
			// the server could not be reached at all (connection refused, timeout, ...)
			throw new AssertionError(
					"CRITICAL: Server is DOWN! GET /ping could not reach " + BASE_URI + " -> " + e.getMessage(), e);
		}

		// Change 200 to 204 or some other code depending on the API Contract
		Assert.assertEquals(response.getStatusCode(), 200,
				"CRITICAL: Server is reachable but NOT healthy: GET /ping did not return 200");
	}

	/**
	 * Centralized execution helper with error handling: a timeout or any other
	 * exception while the request is sent fails the scenario with a message that
	 * names it.
	 */

	protected Response sendRequestWithTimeoutHandling(RequestSpecification reqSpec, String endpoint, String httpMethod,
			Map<String, String> headers, Object payload, String testDescription) {
		try {
			return APIClient.sendRequest(reqSpec, endpoint, httpMethod, headers, payload);

		} catch (Exception e) {
			String errorMsg = e.getMessage() != null ? e.getMessage() : "";

			// Read Timeout হয়েছে কি না চেক করি।
			// Message-এ "read timed out" থাকলে বুঝি connection হয়েছিল, কিন্তু response
			// আসেনি।
			boolean isReadTimeout = errorMsg != null && errorMsg.toLowerCase().contains("read timed out");

			// Transport Level Error (Read Timeout):
			// Connection তৈরি হয়েছে এবং request সার্ভারে পৌঁছেছে।
			// কিন্তু সার্ভার নির্দিষ্ট সময়ের মধ্যে response পাঠায়নি অর্থাৎ client কোনো response পায়নি। 
			// কারণ হতে পারে: সার্ভারে অতিরিক্ত load, ধীর processing, বড় payload, বা network-এ ধীরগতি। 
			if (isReadTimeout) {
				throw new AssertionError("Timeout for Scenario: " + testDescription + " -> " + errorMsg, e);
			}
			
			// Transport Level Error (Connection Failure):
			// Connection তৈরি হয়নি, তাই request সার্ভারে পৌঁছায়নি এবং client কোনো response পায়নি।
			// উদাহরণ: সার্ভার বন্ধ (Connection refused), নির্দিষ্ট সময়ে connection না
			// হওয়া (Connect Timeout),
			// ইন্টারনেট নেই, ভুল domain (UnknownHostException), বা SSL handshake ব্যর্থ।
			throw new AssertionError("Request execution failed for Scenario: " + testDescription + " -> " + errorMsg,
					e);
		}

	}

}
