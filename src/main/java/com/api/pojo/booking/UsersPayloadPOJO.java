package com.api.pojo.booking;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the request payload for Booking operations (POST, PUT, PATCH).
 *
 * WHY @JsonInclude(NON_NULL) & WRAPPER CLASSES ARE REQUIRED:
 * 
 * 1. Partial Payload Serialization (PATCH Support):
 *    - By default, Jackson serializes all fields, converting unassigned fields into `null` or default primitives.
 *    - `@JsonInclude(JsonInclude.Include.NON_NULL)` instructs Jackson to omit any field evaluated as `null` 
 *      from the generated JSON string.
 *
* 2. Mandatory Use of Wrapper Classes (Integer, Boolean) over Primitives (int, boolean):
 *    - Primitive types automatically initialize to non-null default values 
 *    	(e.g., `int` defaults to 0, `boolean` defaults to false).
 *    - Because primitives are never `null`, 
 *    	`@JsonInclude(NON_NULL)` CANNOT suppress them. 
 *    	Jackson will serialize these default values into the JSON payload
 *      (e.g., `{"totalprice": 0, "depositpaid": false}`) even when left unassigned.
 *    - Wrapper classes (Integer, Boolean, Double) default to `null` when unassigned.
 *    - Combined with `@JsonInclude(NON_NULL)`, unassigned wrapper fields are evaluated as `null` 
 *      and completely omitted from the HTTP request body—enabling clean partial payloads for PATCH operations.
 */

@JsonInclude(JsonInclude.Include.NON_NULL) // Important for patch request when all POJO fields are not initialized
public class UsersPayloadPOJO {
	
	@JsonProperty(BookingFields.KEY_FIRSTNAME)
	private String firstname;
	
	@JsonProperty(BookingFields.KEY_LASTNAME)
    private String lastname;
	
	@JsonProperty(BookingFields.KEY_TOTALPRICE)
    private Integer totalprice;
	
	@JsonProperty(BookingFields.KEY_DEPOSITPAID)
    private Boolean depositpaid;
	
	@JsonProperty(BookingFields.KEY_BOOKINGDATES)
    private BookingDatesPOJO bookingdates;
	
	@JsonProperty(BookingFields.KEY_ADDITIONALNEEDS)
    private String additionalneeds;

    public UsersPayloadPOJO() {}
    
    public UsersPayloadPOJO(String firstname, String lastname, Integer totalprice, Boolean depositpaid, BookingDatesPOJO bookingdates, String additionalneeds) {
        this.firstname = firstname;
        this.lastname = lastname;
        this.totalprice = totalprice;
        this.depositpaid = depositpaid;
        this.bookingdates = bookingdates;
        this.additionalneeds = additionalneeds;
    }
	
    /* 
     * Getters & Setters:
     * - Serialization (POJO to JSON): Jackson uses Getters to read field values and build the JSON payload. Setters are completely ignored during serialization.
     * - Deserialization (JSON to POJO): Jackson uses Setters (or Creator constructors) to populate fields from incoming JSON response values. Getters are not used during deserialization.
     * 
     * Note: If this POJO is strictly used for JSON Request Payload Creation (Serialization) 
     * and initialized via Constructor, Setters can be safely removed because Jackson's serialization 
     * engine only relies on Getters to extract values and never invokes Setters.
     */
    
    // Getters & Setters
    
 
    // Getter is required for Serialization
	public String getFirstname() {
		return firstname;
	}
	
	// This POJO is serialized to JSON to send requests over the network.
    // For serialization, Jackson uses GETTERS, not SETTERS.
    // On top of that, fields of this POJO object
    // are initialized using the constructor.
    // So we might have an illusion that
    // setters in this class are redundant.
    // Although fields are initialized via the constructor when building the initial payload, 
    // SETTERS are still needed to dynamically modify field values later on (e.g., during 
    // negative testing or data-driven test variations). 
    // Therefore, SETTERS are not redundant; rather, they are required for field mutation.
	public void setFirstname(String firstname) {
		this.firstname = firstname;
	}

	public String getLastname() {
		return lastname;
	}

	public void setLastname(String lastname) {
		this.lastname = lastname;
	}

	public Integer getTotalprice() {
		return totalprice;
	}

	public void setTotalprice(Integer totalprice) {
		this.totalprice = totalprice;
	}

	public Boolean getDepositpaid() {
		return depositpaid;
	}

	public void setDepositpaid(Boolean depositpaid) {
		this.depositpaid = depositpaid;
	}

	public BookingDatesPOJO getBookingdates() {
		return bookingdates;
	}

	public void setBookingdates(BookingDatesPOJO bookingdates) {
		this.bookingdates = bookingdates;
	}

	public String getAdditionalneeds() {
		return additionalneeds;
	}

	public void setAdditionalneeds(String additionalneeds) {
		this.additionalneeds = additionalneeds;
	}

	// Allure Report-এর Parameters সেকশন, TestNG assertion failure log এবং console log-এ 
    // Memory Address (যেমন UsersPayloadPOJO@3af49f1c)দেখানোর বদলে 
	// JSON Payload-এর প্রকৃত ফিল্ড ভ্যালুগুলো স্পষ্ট দেখার জন্য toString() ব্যবহার করা দরকার। 
    // এর ফলে টেস্ট ফেইল করলে মেমোরি রেফারেন্সের বদলে কোন ডাটা পাঠানো হয়েছিল তা সহজেই চিহ্নিত করা যায়।
	// For the same reason, toString() is overridden in BookingDatesPOJO & ExpectedStatusCode    
    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this); // "this" means this pojo object
        } catch (JsonProcessingException e) {
            return super.toString();
        }
    }

}
