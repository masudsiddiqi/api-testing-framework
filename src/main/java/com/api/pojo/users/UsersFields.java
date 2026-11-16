package com.api.pojo.users;


//Final utility class is used to store JSON "keys" as constants.
//Enums are used to store a limited set of allowed JSON "values" 
//(e.g., "paid", "pending") for specific keys (e.g. status).

public final class UsersFields { // "final" prevents inheritance 
	
	private UsersFields() {};    // "private" prevents instantiation 
	
	public static final String KEY_USERNAME = "username";
	
	public static final String KEY_PASSWROD = "password";
	
	public static final String KEY_EMAIL    = "email";

}
