package com.api.client;

// The HTTP methods (verbs) that this framework sends, written once.
//
// WHY: the HTTP method name is passed as a String ("POST", "PUT", ...) and written many times in the
// scenario rows of the data providers and in the tests. 
// A spelling mistake in a String (e.g. "PSOT") is not noticed when the code is compiled:
// it fails only when the scenario runs ("Unsupported or invalid HTTP Method provided: PSOT").
// A spelling mistake in a "constant name" (e.g. PSOT) does not compile,
// and the IDE marks it in red at once.
//
// WHERE: this class is in src/main, next to APIClient, because APIClient.sendRequest() uses the
// constants in its switch block. 
// src/test code (data providers, tests) can import a class that lives in src/main but 
// src/main code could not import a class that lives in src/test.
//
// HOW TO USE: import the constants statically and write the name instead of the String:
//
//   import static com.api.client.HttpMethods.*;
//   ...
//   new TestScenarioRecord("...", "/booking", POST, headers, payload, expected)
//
// The constants are plain Strings, so the signatures of TestScenarioRecord,
// BaseTest.sendRequestWithTimeoutHandling() and APIClient.sendRequest() do not change.
// A String written directly where an HTTP method is typed by hand ("PSOT" in a scenario row of a
// data provider, or in a helper that sends a request with a fixed method, such as the helpers of
// UsersWorkflowTests) still compiles, because the parameter is a String. 
// So write POST (the constant, no quotation marks), not "POST" (a String).
// Where the method comes from a TestScenarioRecord (record.httpMethod()), nothing is typed, so
// there is nothing to misspell.
//
// WHEN YOU NEED ANOTHER METHOD: add a constant here, and add it to the case list of the switch in
// APIClient.sendRequest(): APIClient rejects every method that is not in that list with an
// IllegalArgumentException.
public class HttpMethods {

	// A class of constants only: nobody creates an object of it.
	private HttpMethods() { }

	public static final String GET    = "GET";
	public static final String POST   = "POST";
	public static final String PUT    = "PUT";
	public static final String PATCH  = "PATCH";
	public static final String DELETE = "DELETE";

}
