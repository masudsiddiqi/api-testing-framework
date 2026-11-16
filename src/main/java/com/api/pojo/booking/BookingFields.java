package com.api.pojo.booking;


//Final utility class is used to store JSON "keys" as constants.
//Enums are used to store a limited set of allowed JSON "values" 
// (e.g., "paid", "pending") for specific keys (e.g. status).

public final class  BookingFields {  // "final" prevents inheritance 
	
	 private BookingFields() {}   	// "private" prevents instantiation 

	    public static final String KEY_FIRSTNAME      = "firstname";
	    public static final String KEY_LASTNAME        = "lastname";
	    public static final String KEY_TOTALPRICE      = "totalprice";
	    public static final String KEY_DEPOSITPAID     = "depositpaid";
	    public static final String KEY_BOOKINGDATES   = "bookingdates";
	    public static final String KEY_CHECKIN         = "checkin";
	    public static final String KEY_CHECKOUT       = "checkout";
	    public static final String KEY_ADDITIONALNEEDS= "additionalneeds";
}
