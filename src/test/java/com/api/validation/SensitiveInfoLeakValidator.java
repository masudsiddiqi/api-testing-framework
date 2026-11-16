package com.api.validation;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.testng.Assert;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;

public class SensitiveInfoLeakValidator {

	    // Security Leakage via "Response Headers" (NOT Response Body) Validation
		// Verify if Sensitive Response Headers
		// are exposed that can leak Stack Trace or other application internal details
		public static void assertNoSensitiveHeadersPresent(Response response) {
			List<String> sensitiveHeaders = List.of("X-Debug-Trace", "X-Debug-StackTrace", "X-Exception-Trace",
					"X-Stack-Trace", "X-Error-StackTrace");

			for (String headerName : sensitiveHeaders) {
				boolean isPresent = response.getHeaders().hasHeaderWithName(headerName);
				Assert.assertFalse(isPresent,
						"Security leak: Sensitive header '" + headerName + "' is present in response!");
			}
		}

		private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

		// Key names that must not appear in a response body. Written in lower case: a key from the
		// body is changed to lower case before it is compared.
		private static final List<String> SENSITIVE_BODY_KEYS = List.of("stacktrace", "exception", "trace", "class");

		// Security Leakage via "Response Body" (NOT Response Headers) Validation
		// Verify that no Sensitive Key is present in the Response Body
		// that can leak Stack Trace or other application internal details.
		// It works for a success response and for an error response:
		//   - the body is read as a JSON tree, so a JSON object, a JSON array and an object inside
		//     another object are all checked
		//   - key names are compared ignoring upper/lower case ("stackTrace" and "stacktrace" are the same key)
		//   - an empty body, or a body that is not JSON (for example an HTML page), has no JSON keys: nothing to check
		public static void assertNoSensitiveKeysInResponseBody(Response response) {
			String bodyText = response.asString();
			if (bodyText == null || bodyText.isBlank()) {
				return;
			}

			JsonNode body;
			try {
				body = JSON_MAPPER.readTree(bodyText);
			} catch (JsonProcessingException e) { 
				// The body is not JSON (for example an HTML page): there are no JSON keys to check.
				// "return;" ends the method here, and the check passes. It gives back no value because
				// the method is void. Without "return;", the code below would use "body" that has no value:
				// it would not compile, and "body = null" would crash with a NullPointerException.
				return; 
			}

			String leakedKeyPath = findSensitiveKeyPath(body, "");
			Assert.assertNull(leakedKeyPath,
					"Security leak: Sensitive key '" + leakedKeyPath + "' is present in Response Body!");
		}

		// Looks for a Sensitive Key in the JSON tree and returns its path (for example "error.trace" or
		// "[0].exception"), or null if there is none. For an object it checks every key and then goes
		// into the value of that key; for an array it goes into every item.
		private static String findSensitiveKeyPath(JsonNode node, String path) {
			if (node.isObject()) {
				Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
				while (fields.hasNext()) {
					Map.Entry<String, JsonNode> field = fields.next();
					String fieldPath = path.isEmpty() ? field.getKey() : path + "." + field.getKey();

					if (SENSITIVE_BODY_KEYS.contains(field.getKey().toLowerCase(Locale.ROOT))) {
						return fieldPath;
					}
					String deeper = findSensitiveKeyPath(field.getValue(), fieldPath);
					if (deeper != null) {
						return deeper;
					}
				}
			} else if (node.isArray()) {
				for (int i = 0; i < node.size(); i++) {
					String deeper = findSensitiveKeyPath(node.get(i), path + "[" + i + "]");
					if (deeper != null) {
						return deeper;
					}
				}
			}
			return null;
		}
}