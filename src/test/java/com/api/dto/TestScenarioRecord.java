package com.api.dto;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Data Transfer Object carrying a single test scenario's input parameters
 * and expected outcome from the DataProvider to the @Test method.
 *
 * Lives in the "dto" package because it holds only data (no behavior, apart from
 * toString(), which only controls how the record is printed).
 *
 * without Record, adding a new test attribute to the data provider
 * would require updating the parameter list of every @Test method
 * that consumes this DataProvider. With this record/DTO,
 * new fields are added only here and in the DataProvider — @Test method
 * signatures stay untouched.
 *
 * Records are also implicitly immutable (all fields final, no setters),
 * which matches the intent here: a scenario's data should never change
 * after the DataProvider builds it.
 */
public record TestScenarioRecord(
    String scenarioDescription,
    String endpoint,
    String httpMethod,
    Map<String, String> headers,
    Object payload,
    ExpectedStatusCode expectedStatusCode
) {

	// TestNG and Allure print a record by calling toString(): Allure stores the text as the test
	// parameter ("arg0"), and TestNG prints it in its reports and in the console.
	// The default toString() of a record prints every component, so the Authorization header
	// (a Bearer token, or the base64 of "username:password" for Basic) would be printed too.
	// This version prints the same text, but with the value of the Authorization header masked
	// (the scheme stays visible, for example "Bearer [masked]"). It only changes how the record is
	// PRINTED: the real headers map is not modified, so the request is still sent with the real header.
	//
	// RELATION TO THE ALLURE REPORT. Where the real token could appear in the report, and what hides it:
	//
	//   1. Allure report > test page > Overview tab > "Parameters" section (the top part of the test page,
	//      above "Execution"), line:  arg0: { "scenarioDescription" : "...", "headers" : { "authorization" : "Bearer [masked]", ... }, ... }
	//      "arg0" is the first argument of the test method, which is this record object. Allure cuts the long
	//      line with "..."; click it to open the whole text.
	//      To print the record as a text, Allure calls this toString() method, and the token is masked inside it.
	//      The same toString() text is used in the following places too, so the token is masked in all of them:
	//        - the TestNG console and the TestNG reports (test-output)
	//        - a test that saves it: Allure.addAttachment(..., record.toString(), ...)
	//
	//   2. Allure report > test page > Execution > Test body > "Request" attachment: a copy of the HTTP request
	//      that was sent (request line, headers, body), saved as an HTML text. How it is made:
	//        - reqSpec_base (built in BaseTest.setupBaseSpec()) has the Allure filter,
	//          so every request goes through the filter.
	//        - The filter takes the request data (URL, method, headers, body) and gives it to the template
	//          src/test/resources/tpl/http-request-masked.ftl. The template decides how the HTML looks.
	//        - Allure saves that HTML as the "Request" attachment.
	//      In that HTML the line "authorization: Bearer eyJ..." is printed as "authorization: Bearer [masked]"
	//      by the template. The request that goes to the server still has the real header.
	//
	// Each one hides only its own place; keep both, with the same output ("Bearer [masked]").
	// The login request shows up in neither place: it has no Allure filter.
	//
	// Parameters section in Allure Report, TestNG assertion failure log, & Eclipse Console log 
	// print the full JSON because this toString() method uses ObjectMapper inside it.
	@Override
	public String toString() {

		// A copy of the headers, used only for printing. LinkedHashMap keeps the original header order.
		Map<String, String> safeHeaders = new LinkedHashMap<>();

		// headers can be null (a scenario that sets no headers at all)
		if (headers != null) {

			// Map.forEach calls the lambda once for every entry of the map:
			//   name  = the header name   (for example "authorization", "content-type")
			//   value = the header value  (for example "Bearer eyJ...", "application/json")
			// For the Authorization header the value is masked by maskAuthorization(); every other header keeps
			// its own value. Names are compared ignoring upper/lower case, because HTTP header names
			// are case-insensitive ("authorization" and "Authorization" are the same header).
			// Only Authorization is masked: if another secret header is ever used (for example a
			// cookie or an API key), add its name here too.
			headers.forEach((name, value) -> safeHeaders.put(name, "authorization".equalsIgnoreCase(name) ? maskAuthorization(value) : value));
		}
		

		// A very long payload (the oversized-payload scenario is about 12 million characters)
		// makes the console and the report very slow, so a long payload is cut after 10 repeats of "John".
		// A very long text value in the payload (the oversized-payload scenario has a "firstname" of
		// about 12 million characters) makes the console and the report very slow.
		// So such a value is cut to its first 40 characters (10 repeats of "John") and the real length is added.
		// The other fields of the payload are printed as they are.
		// printedPayload is only what is PRINTED: the real payload (used for the request) is never changed.
		
		Object printedPayload = payload; // Coming from each scenario of the Data Provider

		if (payload instanceof ObjectNode) {

			// deepCopy() makes a copy, so the real payload is not changed when a value is cut below.
			ObjectNode shortened = ((ObjectNode) payload).deepCopy();

			// "fields()" gives every top-level field of the payload as a name/value pair
			// (e.g. "firstname" -> "JohnJohn..."). forEachRemaining calls the lambda once for each pair.
			// Only the top-level fields are checked: a long value inside a nested object is not cut.
			shortened.fields().forEachRemaining(field -> { // each name/value pair i.e. Map.Entry<String, JsonNode> 
				                                           // is passed to "field" parameter by forEachRemaining()
				JsonNode value = field.getValue();

				// Only a text value longer than 2000 characters is cut.
				// Numbers, booleans, nested objects and short texts stay as they are.
				if (value.isTextual() && value.textValue().length() > 2000) {

					// First 40 characters + a note with the real length. It is wrapped in a TextNode because
					// an ObjectNode can hold only JsonNode values, not a plain String. setValue() puts it
					// back into the copy, in place of the long value.
					field.setValue(TextNode.valueOf(value.textValue().substring(0, 30) + " ... [value cut here: it has " + value.textValue().length() + " characters, only the first 40 are shown]"));
				}
			});
			
			printedPayload = shortened;

		} else {

			// Any other payload type (a String, a POJO): the whole payload is turned into a text.
			// If that text is longer than 2000 characters, the payload is replaced by a short start of it
			// (54 characters) and a note with the real length. The result is a String, so ObjectMapper
			// prints it between quotes and writes every quote inside it as \".
			String payloadText = String.valueOf(payload);
			if (payloadText.length() > 2000) {
				printedPayload = payloadText.substring(0, 54) + " ... [body cut here: it has " + payloadText.length() + " characters, only the first 54 are shown]";
			}
		}

		// Multiline formatting for clean readability in 
		// Parameters section in Allure Report, TestNG assertion failure log, & console log
		try {
			// Jackson ObjectMapper generates JSON using POJO getters/fields, not from the toString() method.
	        ObjectMapper mapper = new ObjectMapper();
	        mapper.enable(SerializationFeature.INDENT_OUTPUT); // Pretty Print Enable

	        Map<String, Object> recordMap = new LinkedHashMap<>();
	        recordMap.put("scenarioDescription", scenarioDescription);
	        recordMap.put("endpoint", endpoint);
	        recordMap.put("httpMethod", httpMethod);
	        recordMap.put("headers", safeHeaders); // masked headers
	        recordMap.put("payload", printedPayload);
	        recordMap.put("expectedStatusCode", expectedStatusCode);

	        return mapper.writeValueAsString(recordMap);
	    } catch (Exception e) {
	        return "TestScenarioRecord [scenario=" + scenarioDescription + "]";
	    }
		
	}

	// "Bearer eyJ..." becomes "Bearer [masked]" and "Basic dXNlcjpwYXNz" becomes "Basic [masked]":
	// the scheme (the text before the first space) stays visible, the secret after it is dropped.
	// A value without a scheme (no space, or a space only at the very start) becomes just "[masked]",
	// and so does a null value.
	// It is used only by toString() above, so it masks the Authorization header in the text that toString()
	// makes: the Allure "Parameters" section, the TestNG console and reports, and an attachment made from record.toString().
	// It does NOT mask the Allure "Request" attachment: the template http-request-masked.ftl does that.
	private static String maskAuthorization(String value) {
		int space = (value == null) ? -1 : value.indexOf(' ');
		return (space > 0) ? value.substring(0, space) + " [masked]" : "[masked]";
	}
	
	
	
}