package com.api.pojo.booking;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BookingDatesPOJO {
	
	@JsonProperty(BookingFields.KEY_CHECKIN)
	private String checkin;
	
	@JsonProperty(BookingFields.KEY_CHECKOUT)
    private String checkout;

    public BookingDatesPOJO() {} // Jackson-এর জন্য no-arg constructor
    
    public BookingDatesPOJO(String checkin, String checkout) {
        this.checkin = checkin;
        this.checkout = checkout;
    }

    /* 
     * Getters & Setters:
     * - Serialization: Uses Getters to read Request POJO Field values and build the JSON payload using those values.
     * - Deserialization: Uses Setters to populate POJO fields from incoming JSON response values.
     * - Serialization does not use setters and Deserialization does not use getters
     * 
     * Note: If this POJO is strictly used for JSON Request Payload Creation (Serialization), 
     * Setters can be safely removed as Request POJO Fields are initialized via Constructor.
     */
    
    // Getters & Setters
    
    // Setter is redundant for Serialization as Fields are initialized vis Constructor
    public void setCheckin(String checkin) { this.checkin = checkin; } 
    
    // Getter is required for Serialization
    public String getCheckin() { return checkin; }
   
    public void setCheckout(String checkout) { this.checkout = checkout; }
    public String getCheckout() { return checkout; }
    
    // Allure Report-এর Parameters সেকশন, TestNG assertion failure log এবং console log-এ 
    // মেমোরি লোকেশনের (যেমন: BookingDatesPOJO@44aaa987) বদলে checkin ও checkout তারিখ স্পষ্ট দেখার জন্য।
    // For the same reason, toString() is overridden in UsersPayloadPOJO & ExpectedStatusCode
    @Override
    public String toString() {
        return "BookingDatesPOJO{" +
                "checkin='" + checkin + '\'' +
                ", checkout='" + checkout + '\'' +
                '}';
    }
}


