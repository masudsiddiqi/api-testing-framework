package com.api.pojo.order;


//This final class keeps the JSON keys of the order payload as constants.
//The keys are written once here. The POJO and the tests use these names.
//It has the same role as BookingFields for the booking payload.
//
//The keys are EXAMPLES. Change them to match the API under test.

public final class OrderFields { // "final" prevents inheritance
	private OrderFields() {} // "private" prevents instantiation

	public static final String KEY_REFERENCE = "reference";
	public static final String KEY_CUSTOMER  = "customer";
	public static final String KEY_ITEM      = "item";
	public static final String KEY_QUANTITY  = "quantity";
}
