package com.api.utils.payload.booking;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.api.pojo.booking.BookingDatesPOJO;
import com.api.pojo.booking.UsersPayloadPOJO;
import com.api.utils.payload.PayloadUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

// The payloads of the booking resource.
// What is the same for every resource (converting POJO to ObjectNode, reading a file, breaking a payload)
// is in PayloadUtils. Only what belongs to a user is here:
// the valid payload, and which field is broken in which scenario.
public final class BookingPayloadFactory {

	private BookingPayloadFactory() {} // only static members: no instance is needed

	// A valid booking. Every call gives a NEW booking: the lastname is different each time.
	//
	// Why: a backend may treat two bookings with the same data as duplicates.
	//   - A backend that REJECTS duplicates answers 409 to the second booking with the same data.
	//     A fixed payload is accepted once, and then rejected on every later run.
	//   - A backend that ACCEPTS duplicates creates a new booking every time.
	//     A fixed payload works, but all tests share the same data.
	// A payload that is new on every call is safe for both kinds of backend.
	//
	// Which fields make two bookings "the same" depends on the backend.
	// This method changes only the lastname.
	// If a backend uses other fields for the duplicate check, change those fields here too.
	//
	// In a duplicate test, call this method ONCE and send the same payload twice.
	// If a test needs a known value, set the field after the call: payload.setLastname("Smith").
	public static UsersPayloadPOJO getValidBookingPayloadPOJO() {
		String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		return new UsersPayloadPOJO("Jim", "Brown" + unique,
				111, true, new BookingDatesPOJO("2026-09-01", "2026-09-05"), "Breakfast");
	}

	// The valid booking in the form that the PayloadUtils methods need: something that gives a new one on every call.
	private static final Supplier<UsersPayloadPOJO> VALID = BookingPayloadFactory::getValidBookingPayloadPOJO;

	// ===== Request payload size (not field value length) check (web server / API gateway layer) =====
	// The length of a field is made so long that the whole body size becomes bigger than the payload size limit.
	// See PayloadUtils.OVER_10_MB. A field is used only to make the body big.
	//
	// Size is counted in BYTES, not in characters:
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
	public static final Supplier<ObjectNode> OVERSIZED_PAYLOAD =
			PayloadUtils.withHugeText(VALID, "/firstname", PayloadUtils.OVER_10_MB);

	// The next two layers (Parsing & Constraint Validation)
	// are about the CONTENT of the request payload: its fields and their values.
	// The SIZE of the whole payload "in bytes" is not content. The size check is above: it answers 413.
	// An error in the content returns either 400 (PARSING layer) or 422 (CONSTRAINT VALIDATION layer).
	// Every payload below breaks exactly ONE rule, so the status shows which rule was checked.
	
	
	// ========== PARSING LAYER ==========

	// Type mismatch: a number field gets a text
	public static final Supplier<ObjectNode> INVALID_DATA_TYPE_PAYLOAD =
			PayloadUtils.withValue(VALID, "/totalprice", "not-a-number");

	// An unknown key: a field that the contract does not have. The server must reject it (400).
	// The key has a harmless name.
	public static final Supplier<ObjectNode> UNKNOWN_KEY_PAYLOAD =
			PayloadUtils.withUnknownKey(VALID, "unknown_field", "unknown_value");

	// A field that the SERVER makes: the id of the booking. It is only in the response, not in the request.
	// A client must not choose it. The server must reject it (400).
	// This protects against Mass Assignment (also called Over-Posting):
	// if the server accepted it, the client could pick an id that is already used and overwrite another booking.
	public static final Supplier<ObjectNode> MASS_ASSIGNMENT_PAYLOAD =
			PayloadUtils.withUnknownKey(VALID, "bookingid", 1);

	// ========== CONSTRAINT VALIDATION LAYER ==========

	// Presence constraint: a required field is missing
	public static final Supplier<ObjectNode> MISSING_REQUIRED_PAYLOAD = PayloadUtils.without(VALID, "/bookingdates");

	// Range constraint: a number below the allowed range
	public static final Supplier<ObjectNode> NEGATIVE_PRICE_PAYLOAD = PayloadUtils.withValue(VALID, "/totalprice", -150);

	// Format constraint: a date in the wrong format
	public static final Supplier<ObjectNode> INVALID_DATE_FORMAT_PAYLOAD =
			PayloadUtils.withValue(VALID, "/bookingdates/checkin", "01-09-2026");

	// Cross-field constraint: the check-in date is after the check-out date
	public static final Supplier<ObjectNode> INVALID_DATE_LOGIC_PAYLOAD = PayloadUtils.withValues(VALID,
			Map.of("/bookingdates/checkin", "2026-09-10", "/bookingdates/checkout", "2026-09-05"));
}