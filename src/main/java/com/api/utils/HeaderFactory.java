package com.api.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class HeaderFactory {

	// ==============================================================================
	// HOW THIS CLASS IS ORGANISED
	// ==============================================================================
	// Sections, in order: NO AUTHENTICATION -> BEARER PATH -> BASIC PATH -> CONTENT NEGOTIATION.
	//
	// The Bearer and Basic sections follow the same order: valid identities first, then
	// deliberately broken headers (the Basic section ends with the structurally malformed
	// ones).
	//
	// Naming rules. Every builder starts with its authentication scheme, so IDE
	// auto-complete groups all Bearer methods together and all Basic methods together:
	//   <scheme>AuthHeadersFor<Who>()            valid credentials of a known identity
	//   <scheme>AuthHeadersWith<Defect>()        a deliberately broken Authorization header
	//   <scheme>AuthHeadersWithout<Element>()    an Authorization header missing a required element
	//                                            (e.g. WithoutBase64Encoding, WithoutColon)

	// Only used to represent "a credential pair the backend should reject" —
	// exact values don't matter, they just need to not match any real account.
	private static final String WRONG_USERNAME = "nonexistent-user";
	private static final String WRONG_PASSWORD = "wrong-password";

	// ------------------------------------------------------------------------------
	// NO AUTHENTICATION
	// ------------------------------------------------------------------------------

	// Only the JSON content-type and accept headers; no Authorization header at all.
	public static Map<String, String> defaultJsonHeaders() {
		Map<String, String> headers = new HashMap<>();
		headers.put("content-type", "application/json");
		headers.put("accept", "application/json");
		return headers;
	}
	

	// Alias of defaultJsonHeaders() whose name states the intent: the request carries NO
	// authentication, so it is expected to trigger 401.
	//
	// The name says "Authentication", not "Authorization": both schemes (Bearer and Basic)
	// are considered here from the Authentication standpoint ("who are you?"), not from the
	// Authorization viewpoint ("are you allowed?"). This method is used to validate
	// Authentication, not authorization.
	public static Map<String, String> noAuthenticationHeaders() {
		return defaultJsonHeaders();
	}
	

	// ------------------------------------------------------------------------------
	// BEARER (JWT) PATH
	// ------------------------------------------------------------------------------

	// Shared Bearer builder, the Bearer-path counterpart of basicAuthHeaders(username, password).
	// "Auth" in the method name covers both Authentication and Authorization: a Bearer
	// token carries identity and role claims, so the caller decides WHICH account to log in with.
	// login() caches tokens per username, so reusing an account costs no extra login call.
	private static Map<String, String> bearerAuthHeaders(String username, String password) {
		Map<String, String> headers = defaultJsonHeaders();
		String token = AuthUtils.login(username, password); // login() returns token
		headers.put("authorization", "Bearer " + token);
		return headers;
	}

	// --- Valid Bearer identities ---

	// Valid, full-permission user: the "happy path" auth profile used by every
	// scenario NOT specifically testing authentication/authorization failure.
	public static Map<String, String> bearerAuthHeadersForFullPermissionUser() {
		return bearerAuthHeaders(ConfigUtils.getValidUsername(), ConfigUtils.getValidPassword());
	}

	// Valid Bearer token for the named role (role name = lookup key in config.properties;
	// a new role needs only a new config.properties entry, no Java change).
	// Whether the request succeeds depends on that role's permission for the action under
	// test; a role that is DENIED for the action gets 403.
	public static Map<String, String> bearerAuthHeadersForRole(String roleName) {
		return bearerAuthHeaders(ConfigUtils.getRoleUsername(roleName), ConfigUtils.getRolePassword(roleName));
	}

	// --- Deliberately broken Bearer header ---

	// A garbage/forged token, sent directly -> expected to trigger 401.
	// Tests TOKEN-VERIFICATION on the protected endpoint itself — no login
	// call happens here, because a forged token never comes from a real
	// login. Distinct from Basic Auth's basicAuthHeadersWithInvalidCredentials() below —
	// two separate backend code-paths per the contract's
	// "bearerAuth OR basicAuth" security requirement.
	public static Map<String, String> bearerAuthHeadersWithInvalidToken() {
		Map<String, String> headers = defaultJsonHeaders();
		headers.put("authorization", "Bearer invalid.garbage.token"); // Garbage Token
		return headers;
	}

	// ------------------------------------------------------------------------------
	// BASIC AUTH PATH
	// ------------------------------------------------------------------------------
	// Sent directly on the resource request — no login call involved, since
	// Basic Auth (per the contract) is verified independently, per request,
	// via a server-side username/password + role lookup (no token, no
	// claims embedded — see ConfigUtils for how role credentials are read).

	// Builds a standard HTTP Basic Auth header value:
	// Authorization: Basic base64(username:password)
	private static Map<String, String> basicAuthHeaders(String username, String password) {
		Map<String, String> headers = defaultJsonHeaders();
		String rawCredentials = username + ":" + password; // colon (:) separator is mandatory in Basic Auth
		String base64Credentials = Base64.getEncoder().encodeToString(rawCredentials.getBytes(StandardCharsets.UTF_8));
		headers.put("authorization", "Basic " + base64Credentials);
		return headers;
	}

	// --- Valid Basic identities ---

	// Valid, full-permission user via Basic Auth: the Basic-path counterpart of
	// bearerAuthHeadersForFullPermissionUser(). Sends username:password
	// (base64-encoded) directly, with no login call and no token.
	public static Map<String, String> basicAuthHeadersForFullPermissionUser() {
		return basicAuthHeaders(ConfigUtils.getValidUsername(), ConfigUtils.getValidPassword());
	}

	// Valid Basic Auth header for the named role. The role name is the same
	// config.properties lookup key used by bearerAuthHeadersForRole(); only the
	// mechanism differs: Basic sends the role's username:password directly
	// (base64-encoded) on every request, with no login call and no token.
	// Whether the request succeeds depends on that role's permission for the action under
	// test; a role that is DENIED for the action gets 403.
	public static Map<String, String> basicAuthHeadersForRole(String roleName) {
		return basicAuthHeaders(ConfigUtils.getRoleUsername(roleName), ConfigUtils.getRolePassword(roleName));
	}

	// --- Deliberately broken Basic headers: wrong credentials ---

	// Wrong Basic-Auth credentials, but STRUCTURALLY well-formed (valid
	// base64, colon present) -> expected to trigger 401. Tests the
	// Basic-path's own credential-verification (DB lookup), independently
	// from bearerAuthHeadersWithInvalidToken() above, which tests the Bearer/JWT path.
	public static Map<String, String> basicAuthHeadersWithInvalidCredentials() {
		return basicAuthHeaders(WRONG_USERNAME, WRONG_PASSWORD); // wrong credentials but base64 String
	}

	// --- Deliberately broken Basic headers: malformed structure ---
	// (parsing robustness, NOT credential-verification — distinct from
	// basicAuthHeadersWithInvalidCredentials() above, which sends well-formed-but-wrong
	// credentials).

	// Raw "username:password" sent WITHOUT base64-encoding, with the correct
	// "Basic" scheme prefix -> expected to trigger 401. Plain text containing ':'
	// is never valid base64 (':' is outside the base64 alphabet), so this reliably
	// exercises the FIRST parsing step (base64-decoding): does the backend crash
	// (500) or gracefully reject with 401?
	
	public static Map<String, String> basicAuthHeadersWithoutBase64Encoding() {
		Map<String, String> headers = defaultJsonHeaders();
		// intentionally NOT base64-encoded
		String nonBase64ColonValidCredentials = ConfigUtils.getValidUsername() + ":" + ConfigUtils.getValidPassword(); 
		headers.put("authorization", "Basic " + nonBase64ColonValidCredentials);
		return headers;
	}

	// Valid base64, but decodes to a string with NO colon, so the backend
	// cannot split it into username/password at all. Fails one parsing step
	// LATER than basicAuthHeadersWithoutBase64Encoding(): base64-decoding succeeds here,
	// but the username:password split that follows fails.
	public static Map<String, String> basicAuthHeadersWithoutColon() {
		Map<String, String> headers = defaultJsonHeaders();
		// colon separator intentionally missing
		String rawCredentials = ConfigUtils.getValidUsername() + ConfigUtils.getValidPassword(); 
		String base64Credentials = Base64.getEncoder().encodeToString(rawCredentials.getBytes(StandardCharsets.UTF_8));
		headers.put("authorization", "Basic " + base64Credentials);
		return headers;
	}

	// ------------------------------------------------------------------------------
	// CONTENT NEGOTIATION
	// ------------------------------------------------------------------------------
	// Both start from the full-permission Bearer user, so that the media type is the
	// only thing under test.

	// Request body format: Content-Type is declared as XML. Content-Type describes the
	// request body and is not negotiated; an unsupported one is rejected with 415.
	public static Map<String, String> xmlContentTypeHeaders() {
		Map<String, String> headers = bearerAuthHeadersForFullPermissionUser();
		headers.put("content-type", "application/xml");
		return headers;
	}

	// Response format: Accept asks for XML. This is content negotiation: the server picks a
	// response media type it can produce, or rejects the request with 406.
	public static Map<String, String> xmlAcceptHeaders() {
		Map<String, String> headers = bearerAuthHeadersForFullPermissionUser();
		headers.put("accept", "application/xml");
		return headers;
	}
	
	
	
	// Headers for a request WITHOUT a body (GET, DELETE).
	//
	// Returns a COPY of the given headers without the "content-type" entry.
	//
	// The following "withoutContentType()" method is OPTIONAL for GET and DELETE requests 
	// as a GET/DELETE request even sent WITH the "content-type" header throws no error.  
	// The server ignores the "content-type" header, 
	// because there is no body to describe (our GET helper in UsersWorkflowTests sends it)
	// Use this method when a scenario wants the request
	// to go out WITHOUT a Content-Type, as a real client usually sends a GET.
	//
	// It takes the headers as a parameter, so it can be combined with any headers builder of this class
	// (Bearer, Basic, an invalid token, a role, ...). That makes it usable in any other scenario whose
	// request has no body.
	//
	// Never use it for a request WITH a body (POST, PUT, PATCH). RestAssured would add its own default
	// Content-Type, "text/plain; charset=ISO-8859-1", so the server would receive a non-JSON
	// Content-Type, not a missing one. RestAssured does not support "a body but no Content-Type" with
	// its normal API.
	//
	// The key is removed whatever its letter case ("content-type", "Content-Type", ...). The headers
	// passed in are not changed.
	//
	// Example (a GET scenario row: no body, so the payload is null and no "content-type" is needed):
	//   new TestScenarioRecord(
	//       "...", "/booking/1", "GET",
	//       withoutContentType(bearerAuthHeadersForFullPermissionUser()),   <- no content-type
	//       null,                                                           <- no body
	//       expect(200, ...))
	
	public static Map<String, String> withoutContentType(Map<String, String> headers) {

	    // Creates a new copy of the input map to avoid modifying the original headers map
	    Map<String, String> copy = new HashMap<>(headers); 

	    // keySet(): Returns a Set of all keys present in the map.
	    // removeIf(): Iterates through the keySet and removes any key 
	    // for which the lambda conditional expression returns true.
	    // How 'key' gets its value: 
	    //   removeIf() automatically loops through keySet and 
	    //   passes each key one by one into the 'key' parameter (the name before the arrow ->).
	    // equalsIgnoreCase("content-type"): 
	    //   Checks if the key matches "content-type", regardless of uppercase or lowercase letters.
	    copy.keySet().removeIf(key -> key.equalsIgnoreCase("content-type"));

	    return copy;
	}
		
		
}