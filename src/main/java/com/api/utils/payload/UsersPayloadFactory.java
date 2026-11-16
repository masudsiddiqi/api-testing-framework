package com.api.utils.payload;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.api.pojo.users.UsersPayloadPOJO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import static com.api.utils.payload.PayloadUtils.*;

public final class UsersPayloadFactory {
	
	public UsersPayloadFactory() {};
	
	// A valid registration payload for a customer. Every call gives a NEW payload:
	// the username is different each time, and the email is built from it.
	//
	// Why: a backend may treat two accounts with the same username as duplicates.
	//   - A backend that REJECTS duplicates answers 409 to the second request with the same username.
	//	     A fixed payload is accepted once, and then rejected on every later run.
	//   - A backend that ACCEPTS duplicates creates a new account every time.
	//	     A fixed payload works, but all tests share the same data.
	// A payload that is new on every call is safe for both kinds of backend.
	//
	// Which fields make two accounts "the same" depends on the backend.
	// This method changes the username and the email. The password stays the same.
	// If a backend uses other fields for the duplicate check, change those fields here too.
	//
	// The values must follow the contract, or the payload is not valid (422).
	// Example: the username has letters, digits and underscore only (no hyphen), 3 to 30 characters.
	// Example: the password has 8 to 72 characters.
	//
	// In a duplicate test, call this method ONCE and send the same payload twice.
	// If a test needs a known value, set the field after the call: payload.setUsername("known_user").
	
	// Rule of the username (example): letters, digits and underscore, 3 to 30 characters. The email is built from it.
	public static UsersPayloadPOJO getValidUsersPayloadPOJO() {
		String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		return new UsersPayloadPOJO("customer_" + unique, "password123", "customer_" + unique + "@shop.example");
	}

	// Supplier<UsersPayloadPOJO> VALID_USERS_PAYLOAD_SUPPLIER holds no payload object like UsersPayloadPOJO Object. 
	// It holds only the reference to Supplier<> object and 
	// that reference is passed to mutation()'s "validPayloadSupplier" parameter
	// inside mutation()'s body (another supplier expression), using the reference to this lambda object
	// get() is called which calls getValidUsersPayloadPOJO() inside its body 
	// (method reference to the right of "UsersPayloadFactory::")
	// Each time, VALID_USERS_PAYLOAD_SUPPLIER is passed to a different method like withValue(...) 
	// to the right of a static variable like INVALID_DATA_TYPE_PAYLOAD, get() is called on the same 
	// "VALID_USERS_PAYLOAD_SUPPLIER" lambda object reference but as its get() body calls getValidUsersPayloadPOJO()
	// which create random role and random email, a different UsersPayloadPOJO object is created for each constant.
	// inside mutation(), get
	// the knows which method to call: getValidUsersPayloadPOJO().
	// Every get() on it runs that method and gives a NEW valid payload (Example: a new username and a new email).
	//
	// The PayloadUtils methods (withValue, without, mutation, ...) keep this Supplier.
	// Every time you call get() on the Supplier that they return, they first call this get(),
	// and then they change the NEW payload. So this Supplier itself is never changed,
	// and one scenario never changes another.
	//
	// It must be declared BEFORE the constants below, which use it.
	public static final Supplier<UsersPayloadPOJO> VALID_USERS_PAYLOAD_SUPPLIER = UsersPayloadFactory::getValidUsersPayloadPOJO;
	
	
	// ===== Request payload size (not field value length) check (web server / API gateway layer) =====
	// The length of a field is made so long that the whole body size becomes bigger than the payload size limit.
	// See PayloadUtils.OVER_10_MB. A field is used only to make the body big.
	//
	// Value size is counted in BYTES, but value length in characters:
	//   body size (bytes) = length (characters) x width (bytes per character) + the other bytes of the body
	// In UTF-8 the width of "A" is 1 byte, so 12,000,000 characters are about 12,000,000 bytes.
	// Other characters are wider. Example: an accented letter has 2 bytes, and the euro sign has 3 bytes.
	// The size limit counts bytes. The length rule of a field counts characters.
	//
	// At the web server or API gateway layer, the server checks the size of the whole body,
	// not the length of a field value.
	// This check usually comes first. A payload that is too big is stopped here with 413,
	// before the parsing and the constraint validation below.
	// (The length rule of a field is a rule of the constraint validation.)
	public static final Supplier<ObjectNode> OVERSIZE_PAYLOAD = withHugeText(VALID_USERS_PAYLOAD_SUPPLIER, "/username", OVER_10_MB);

	
	// The next two layers (Parsing & Constraint Validation)
	// are about the CONTENT of the request payload: its fields and their values.
	// The SIZE of the whole payload "in bytes" is not content. The size check is above: it answers 413.
	// An error in the content returns either 400 (PARSING layer) or 422 (CONSTRAINT VALIDATION layer).
	// Every payload below breaks exactly ONE rule, so the status shows which rule was checked.
	
	
	// ========== PARSING LAYER ==========
	// Expected for all payloads in this layer: 400
	
	// Type mismatch: an array for a text field. 
	// A number or a boolean would NOT work here: Jackson turns it into a text without an error.
	public static final Supplier<ObjectNode> INVALID_DATA_TYPE_PAYLOAD = 
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/password", List.of(1,2)); 
	
	// An unknown key: a field that the contract does not have. The server must reject it (400).
	// The key has a harmless name.
	public static final Supplier<ObjectNode> UNKWON_KEY_PAYLOAD = 
			withUnknownKey(VALID_USERS_PAYLOAD_SUPPLIER, "unknown_field", "unknown_value");
	
	// Fields that the SERVER makes and persist in the database. 
	// They should be only in the response, not in the request. Expected: 400
	// A client must not choose them. This protects against Mass Assignment (also called Over-Posting).
	// The role: if the server accepted it, the client could make itself an admin.
	// The key has a harmful & sensitive name
	public static final Supplier<ObjectNode> MASS_ASSIGNMENT_ROLE_PAYLOAD  = 
			withUnknownKey(VALID_USERS_PAYLOAD_SUPPLIER, "role", "admin");
	
	// The id: if the server accepted it, the client could pick an id that is already used.
	public static final Supplier<ObjectNode> MASS_ASSIGNMENT_ID_PAYLOAD  = 
			withUnknownKey(VALID_USERS_PAYLOAD_SUPPLIER, "id", "1");
	
	// ========== CONSTRAINT VALIDATION LAYER ==========
	// The rules of the values: Presence, Nullability, Length, Range, Format, Pattern, Cross-Field Validation
	// Expected for all payloads in this layer (in this contract): 422
	
	
	// Presence constraint: one required field is missing
	public static final Supplier<ObjectNode> MISSING_USERNAME_PAYLOAD = without(VALID_USERS_PAYLOAD_SUPPLIER, "/username");
	public static final Supplier<ObjectNode> MISSING_PASSWORD_PAYLOAD = without(VALID_USERS_PAYLOAD_SUPPLIER, "/password");
	public static final Supplier<ObjectNode> MISSING_EMAIL_PAYLOAD = without(VALID_USERS_PAYLOAD_SUPPLIER, "/email");
	
	
	
	// Boundary: one below the minimum (minimum 3 characters)
	public static final Supplier<ObjectNode> USERNAME_TOO_SHORT_PAYLOAD = 
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/username", "ab");
	
	// Boundary: one above the maximum (maximum 30 characters)
	public static final Supplier<ObjectNode> USERNAME_TOO_LONG_PAYLOAD = 
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/username", "A".repeat(31));
	
	// The extreme of the length rule: the lowest possible length, an empty text (0 characters).
	// It is not a boundary. The boundary below the minimum is 2 (one below the minimum).
	// It breaks two rules at once:
	//   - the length (below the minimum), and
	//   - the pattern (the pattern needs one or more characters).	
	public static final Supplier<ObjectNode> USERNAME_EMPTY_PAYLOAD =
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/username", "");
	
	// Validation Layer: Range Constraint
	// Password Rule (example): 8 to 72 characters. 
	// Boundary: one below the minimum (7), one above the maximum (73).
	public static final Supplier<ObjectNode> PASSWORD_TOO_SHORT_PAYLOAD =
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/password", "A".repeat(7));
	
	public static final Supplier<ObjectNode> PASSWORD_TOO_LONG_PAYLOAD =
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/password", "A".repeat(73));
	
	public static final Supplier<ObjectNode> PASSWORD_EMPTY_PAYLOAD =
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/password", "");
	
	// Validation Layer- Format Constraint
	
	// Username Rule (example): letters, digits and underscore, 3 to 30 characters are allowed.
	// A character that is not allowed: a hyphen
	public static final Supplier<ObjectNode> USERNAME_WITH_HYPHEN_PAYLOAD = 
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/username", "user-name");
	
	// A letter that is not A-Z or a-z is not allowed either. 
	// Example: an accented letter (e.g. é) or a Bengali letter.
	// The rule allows only A-Z, a-z, 0-9 and underscore. A check with a word-character shortcut
	// (like \w) may accept such a letter in some languages, so this payload tests that.
	public static final  Supplier<ObjectNode> USERNAME_WITH_ACCENT_PAYLOAD  = 
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/username", "usernamé");

	
	// Email Format Constraint: an email has a local part an @ and a domain name
	public static final Supplier<ObjectNode> EMAIL_WITHOUT_AT_SIGN_PAYLOAD =
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/email", "customer_example.com"); // no @
	
	public static final Supplier<ObjectNode> EMAIL_WITHOUT_DOMAIN_PAYLOAD =
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/email", "customer@"); // nothing after @
	
	
	
	// ========== CONFLICT (the state of the data) ==========
	// The username of an account that already exists. Expected: 409
	// These payloads depend on seed data: the account "customer1" must already exist
	// in the data of the system under test (a database, or the memory of a mock server).
	// If it does not exist, the server creates the account and answers 201. 
	// The test then fails because of the data, not because of the API.
	// To test a duplicate of an account that the test creates itself, use a workflow test.
	public static final Supplier<ObjectNode> EXISTING_USERNAME_PAYLOAD =
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/username", "customer1");
	
	
	// Only the letter case differs from a username that already exists.
	// The contract says letter case does not matter in a username.
	// So the server must treat this as a duplicate too.
	public static final Supplier<ObjectNode> EXISTING_USERNAME_UPPER_CASE_PAYLOAD =
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/username", "CUSTOMER1");

	
	// ========== VALID BOUNDARIES (positive scenarios). Expected: 201 ==========
	// These payloads are valid. The server creates an account and saves it.
	// The same scenario runs many times: in every regression run, and maybe in more than one test.
	// The server rejects a second account with the same username with 409.
	// So the username must be NEW at every get().
	//
	// Why a positive scenario uses mutation() and not withValue():
	//   - Every get() first makes a new valid payload with a new username and email.
	//   - withValue() takes three arguments. The second one is a pointer.
	//     A pointer is a path to a value in the payload.
	//     Example: "/username" points to the value of the key "username".
	//     The last argument is the new value to write at that place.
	//     This value is finished: Java makes it one time, when the constant is created.
	//     The Consumer lambda object saves this value in a captured field. The captured field value never changes.
	//   - The Consumer lambda body inside withValue() runs at every get() call on the Supplier object.
	//     It calls replaceAt(). replaceAt() changes the username in the new payload.
	//     It replaces the new username with the saved value.
	//     The saved value is in the captured field "value" of the Consumer lambda object.
	//     So the new username is lost. Every get() gives a payload with the same username.
	//   - Example 1: a fixed text such as "abc". It is the same name in every run.
	//     On a server that keeps its data, the 2nd run gets 409.
	//   - Example 2: a random name made in the argument of withValue(). It is made one time per run.
	//     The 2nd get() in the same run gets 409.
	//   - mutation() takes a lambda. The code that makes the name is inside the lambda body.
	//     The lambda body runs at every get(). So every get() makes a new name.
	//
	// Why withValue() is fine for the negative scenarios:
	//   - The server rejects a negative request and saves nothing.
	//     Example: 422 for a username that is too short.
	//   - So the same body can be sent many times. It gets the same error each time.
	//   - A fixed value is what these scenarios need. Example: "ab" is always too short.
	//
	// How the username is made:
	// The username is made from a UUID: letters and digits (0-9 and a-f). A UUID has 32 of them at most.
	// A short name has few possible values (Example: 3 characters give 4,096 names).
	// On a server that keeps its data, such a name can be used twice after many runs. A longer name is safer.
	
	public static final Supplier<ObjectNode> VALID_USERNAME_MIN_LENGTH_PAYLOAD =
			mutation(VALID_USERS_PAYLOAD_SUPPLIER, payloadTree -> payloadTree.put("username",
					UUID.randomUUID().toString().replace("-", "").substring(0, 3)));
	
	public static final Supplier<ObjectNode> VALID_USERNAME_MAX_LENGTH_PAYLOAD =
			mutation(VALID_USERS_PAYLOAD_SUPPLIER, payloadTree -> payloadTree.put("username",
					UUID.randomUUID().toString().replace("-", "").substring(0, 30)));
	
	
	// A password does not have to be unique, so a ready value is fine. The username of VALID is new on every get().
	public static final Supplier<ObjectNode> VALID_PASSWORD_MIN_LENGTH_PAYLOAD = 
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/password", "a".repeat(8));
	
	public static final Supplier<ObjectNode> VALID_PASSWORD_MAX_LENGTH_PAYLOAD=
			withValue(VALID_USERS_PAYLOAD_SUPPLIER, "/password", "a".repeat(72));
}
