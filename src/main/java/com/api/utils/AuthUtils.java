package com.api.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * This class's only job: take a username and password, call the real
 * backend's login endpoint, and return the token it gives back.
 *
 * Why this is a SEPARATE class from HeaderUtils:
 * HeaderUtils just builds Map<String, String> headers — it does not make
 * network calls. Logging in IS a network call (a POST request), so that
 * logic lives here instead, keeping HeaderUtils simple.
 *
 * Why this does NOT use BaseTest's reqSpec_base:
 * HeaderUtils (and therefore this class) gets called while the DataProvider
 * is building test data — and TestNG may call the DataProvider BEFORE
 * @BeforeClass has run. So reqSpec_base might not exist yet at that point.
 * To avoid depending on that timing, this class builds its own simple
 * RestAssured call from scratch every time.
 */
public class AuthUtils {

	// Stores tokens we've already fetched, so we don't log in again and
	// again for the same user during one test run.
	// Key = username, Value = the token we got back for that username, plus the moment
	// it stops being usable.
	// ConcurrentHashMap (not HashMap): login() may be called by several threads at once
	// (e.g. TestNG parallel execution), and a plain HashMap is not safe for that.
	private static final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

	// A cached token is replaced this long BEFORE its "exp" time, so that it cannot
	// expire in the middle of a request.
	private static final long EXPIRY_MARGIN_MILLIS = 30_000;

	// Finds the numeric "exp" claim (expiry time, in seconds since 1970) in a JWT payload.
	private static final Pattern EXP_CLAIM = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");

	// A token together with the moment it stops being usable.
	//
	// This is only a small holder for two values. It has no static code, so nothing in it
	// runs by itself, and it does NOT run before AuthUtils: the JVM loads this class only after
	// AuthUtils is in use, during the first login(). From then on:
	//   - the constructor runs when fetchNewToken() has just received a token from the
	//     backend (once per username, plus once again after each expiry);
	//   - isUsable() runs on every later login() for the same username, to decide whether
	//     the cached token can be reused or must be replaced.
	private static final class CachedToken {
		private final String token;
		private final long expiresAtMillis; // Long.MAX_VALUE = no expiry known: reuse for the whole run

		private CachedToken(String token, long expiresAtMillis) {
			this.token = token;
			this.expiresAtMillis = expiresAtMillis;
		}

		private boolean isUsable() {
			return System.currentTimeMillis() < expiresAtMillis - EXPIRY_MARGIN_MILLIS;
		}
	}

	// Private constructor: nobody should create an AuthUtils object.
	// All methods here are static, called like AuthUtils.login(...).
	private AuthUtils() {
	}

	/**
	 * Returns a valid token for the given username/password. If we already
	 * logged in with this username before and the cached token is still usable,
	 * reuse it using Bearer auth instead of calling the login endpoint again.
	 *
	 * Caching is keyed by USERNAME only: the first call for a given username
	 * triggers a real login (network call); every later call with that SAME
	 * username — even from a different caller or a different role-key in
	 * config.properties — returns the cached token without logging in again.
	 * A different username always triggers its own, separate login.
	 *
	 * EXPIRY: if the token is a JWT with an "exp" claim, it is reused only until
	 * 30 seconds before that time and is then replaced by a fresh login. A token
	 * with no readable "exp" (not a JWT, or no expiry claim) is reused for the
	 * whole run.
	 *
	 * THREAD SAFETY: safe to call from several threads (e.g. TestNG parallel
	 * execution). Concurrent calls for the same username trigger only ONE login;
	 * the other callers wait for it and receive the same token.
	 *
	 * Use this for the "happy path" — when login is EXPECTED to succeed
	 * (e.g. HeaderUtils.bearerAuthHeadersForFullPermissionUser()). For tests that expect
	 * login to FAIL (wrong username/password), use callLoginEndpointRaw()
	 * instead — this method throws if no token comes back, which would be
	 * the wrong behavior for a negative-login test.
	 */
	
	/**
	 * Returns a valid token for the given username/password. If we already logged in with this
	 * username before and the cached token is still usable, the cached token is returned instead of
	 * calling the login endpoint again.
	 *
	 * CACHE: keyed by USERNAME only. The first call for a given username triggers a real login
	 * (network call); every later call with that SAME username, even from a different caller or a
	 * different role-key in config.properties, returns the cached token without logging in again.
	 * A different username always triggers its own, separate login.
	 * The password is NOT part of the key: once a token is cached, a call with the same username and
	 * a different password also gets the cached token, without checking the password.
	 *
	 * EXPIRY: if the token is a JWT with an "exp" claim, it is reused only until
	 * 30 seconds before that time and is then replaced by a fresh login. A token
	 * with no readable "exp" (not a JWT, or no expiry claim) is reused for the
	 * whole run.
	 *
	 * THREAD SAFETY: safe to call from several threads (e.g. TestNG parallel
	 * execution). Concurrent calls for the same username trigger only ONE login;
	 * the other callers wait for it and receive the same token.
	 *
	 * WHAT CAN BE THROWN: an IllegalStateException if no token comes back (the login failed). If the
	 * login request cannot be completed at all, callLoginEndpointRaw() throws an IllegalStateException
	 * too, and it passes through here.
	 *
	 * WHEN TO USE IT, AND WHO CALLS IT: for the "happy path", when login is EXPECTED to succeed.
	 * Today the only caller is HeaderFactory.bearerAuthHeaders(username, password), the one builder
	 * behind every Bearer header method (for example bearerAuthHeadersForFullPermissionUser()),
	 * which data providers and test methods (for example in UsersWorkflowTests) use to get a
	 * Bearer header.
	 * For tests that expect login to FAIL (for example wrong username/password in
	 * LoginNegativeTests), use callLoginEndpointRaw() instead: login() throws when no token comes
	 * back (see WHAT CAN BE THROWN below), which would be the wrong behavior for a negative-login test.
	 */
	public static String login(String username, String password) {

		// compute() runs atomically for one key: if two threads ask for the same username
		// at the same time, only one of them logs in and the other waits for its result.
		// If the function throws (login failed), nothing is stored in the cache.
		return tokenCache.compute(username, (user, cached) -> {

			// Step 1: have we already logged in as this user, and is that token still usable?
			if (cached != null && cached.isUsable()) {
				return cached;
			}

			// Step 2: no usable token — actually call the login endpoint.
			return fetchNewToken(user, password);
		}).token;
	}

	// Calls the login endpoint and wraps the token (and its expiry time, if it has one).
	// Throws if no token comes back.
	private static CachedToken fetchNewToken(String username, String password) {
		Response loginResponse = callLoginEndpointRaw(username, password);
		String token = loginResponse.jsonPath().getString("token");

		if (token == null || token.isBlank()) {
			throw new IllegalStateException(
					"Login failed for user '" + username + "'. Status code: " + loginResponse.getStatusCode()
							+ ", response body: " + loginResponse.asString());
		}

		return new CachedToken(token, readExpiryMillis(token));
	}

	// Reads the "exp" claim of a JWT (header.payload.signature; the payload is base64url JSON)
	// and returns it in milliseconds. Returns Long.MAX_VALUE ("no expiry known") when the
	// token is not a JWT or has no readable "exp" claim.
	private static long readExpiryMillis(String token) {
		try {
			String[] parts = token.split("\\.");
			if (parts.length != 3) {
				return Long.MAX_VALUE;
			}
			String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
			Matcher expClaim = EXP_CLAIM.matcher(payloadJson);
			if (!expClaim.find()) {
				return Long.MAX_VALUE;
			}
			return Math.multiplyExact(Long.parseLong(expClaim.group(1)), 1000L);
		} catch (IllegalArgumentException | ArithmeticException e) {
			// not valid base64, or the number does not fit: treat as "no expiry known"
			return Long.MAX_VALUE;
		}
	}
	
	
	/**
	 * Sends the login request (POST /auth/login with username and password) and returns  the
	 * RestAssured Response object (java response object) exactly as it comes back - success or failure.
	 *
	 * NAME: "callLoginEndpointRaw()" = this is the one place that sends the login request to the login
	 * endpoint. "Raw" = nothing is done with the response: no token is read from it, no success is
	 * checked, nothing is cached.
	 *
	 * DIFFERENCE FROM login():
	 *   login(user, pass)                 returns the token (String); caches it; throws an
	 *                                     IllegalStateException if no token comes back
	 *   callLoginEndpointRaw(user, pass)  returns the Response; caches nothing; an HTTP error status
	 *                                     such as 401 is returned normally, it is not thrown
	 *
	 * WHO CALLS IT: login() (through fetchNewToken) and every test where a FAILED login is the
	 * EXPECTED result (for example wrong username/password in LoginNegativeTests).
	 *
	 * WHAT CAN STILL BE THROWN: if the server cannot be reached or does not answer in time, there is
	 * no HTTP response and so no Response to return. Typical cases:
	 *   - the connection is refused (HostConnectException): the host machine is up, 
	 *     but the backend application is not listening/running on the port; this fails at once
	 *   - the connection attempt times out (ConnectTimeoutException): no answer from the host machine
	 *     at all, for example it is down or unreachable
	 *   - the connection is made, but no data arrives in time (SocketTimeoutException)
	 * The exception of the HTTP client is rethrown wrapped in an IllegalStateException that names the
	 * user (never the password); see the catch block below. It is not swallowed: it still fails the
	 * test, or the data provider, that called this method.
	 *
	 * The request uses loginRequestSpecification(): connection settings only (base URI, timeouts),
	 * no Allure filter and no logging, because the request body contains the password.
	 *
	 * TODO: change "/auth/login" below to match your real backend's actual login endpoint path.
	 */

	public static Response callLoginEndpointRaw(String username, String password) {

	    Map<String, String> loginRequestBody = new HashMap<>();
	    loginRequestBody.put("username", username);
	    loginRequestBody.put("password", password);

	    // loginRequestSpecification() holds only the connection settings: the base URI and the
	    // timeout limits (3 s to connect, 3 s to wait for data). It is not the whole request: the
	    // headers, the body, the method (POST) and the endpoint (/auth/login) are added here.
	    // If the server cannot be reached or does not answer in time, the HTTP client throws an
	    // exception; the catch block below rethrows it wrapped in a Runtime IllegalStateException.
		
	    try {
	        return RestAssured.given(RequestSpecFactory.loginRequestSpecification())
	                .header("content-type", "application/json")
	                .header("accept", "application/json")
	                .body(loginRequestBody)
	                .when()
	                .post("/auth/login");
	    } catch (Exception e) {
	        // Catches every Exception, for example:
	        //   - the connection is refused (ConnectException): 
	    	//     Host machine is running but the Backend App is not running/listening on the port
	        //   - the connection attempt times out (ConnectTimeOutException): 
	    	//     Both Host Machine & Backend App are not running
	    	//   - a read timeout (SocketTimeoutException): 
	    	//     the connection to the app is made, but no data arrives in time
	        //   - a NullPointerException or an IllegalArgumentException
	        // An Error (for example an AssertionError) is not an Exception and is not caught.
	        // An HTTP error status such as 401 is not an exception either: it comes back as a Response.
	        //
	        // The exception is rethrown wrapped in an IllegalStateException, with the username in the
	        // message and the original exception as its cause (so the stack trace is kept).
	        // The message NEVER contains the password or the request body: they must not reach the
	        // console or a report.
	        throw new IllegalStateException("Login request to /auth/login could not be completed for user '"
	                + username + "': " + e.getMessage(), e);
	    }
	}
}