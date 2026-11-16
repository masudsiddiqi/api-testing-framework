package com.api.pojo.products;

import com.api.pojo.products.ProductsFields;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonInclude(JsonInclude.Include.NON_NULL) // Important for patch request when all POJO fields are not initialized
public class ProductsPayloadPOJO {
	
	@JsonProperty(ProductsFields.KEY_SKU)
	private String sku = null;
	
	@JsonProperty(ProductsFields.KEY_NAME)
	private String name = null;
	
	@JsonProperty(ProductsFields.KEY_CATEGORY)
	private String category = null;
	
	@JsonProperty(ProductsFields.KEY_PRICE)
	private double price = 0;
	
	@JsonProperty(ProductsFields.KEY_STOCK)
	private int stock = 0;

	public ProductsPayloadPOJO(String sku, String name, String category, double d, int stock) {
		super();
		this.sku = sku;
		this.name = name;
		this.category = category;
		this.price = d;
		this.stock = stock;
	}

	
	public String getSku() {
		return sku;
	}


	public void setSku(String sku) {
		this.sku = sku;
	}


	public String getName() {
		return name;
	}

	
	public void setName(String name) {
		this.name = name;
	}


	public String getCategory() {
		return category;
	}


	public void setCategory(String category) {
		this.category = category;
	}


	public double getPrice() {
		return price;
	}

	
	public void setPrice(double price) {
		this.price = price;
	}


	public int getStock() {
		return stock;
	}


	public void setStock(int stock) {
		this.stock = stock;
	}




	// Allure Report-এর Parameters সেকশন, TestNG assertion failure log এবং console log-এ 
    // Memory Address (যেমন UsersPayloadPOJO@3af49f1c)দেখানোর বদলে 
	// JSON Payload-এর প্রকৃত ফিল্ড ভ্যালুগুলো স্পষ্ট দেখার জন্য toString() ব্যবহার করা দরকার। 
    // এর ফলে টেস্ট ফেইল করলে মেমোরি রেফারেন্সের বদলে কোন ডাটা পাঠানো হয়েছিল তা সহজেই চিহ্নিত করা যায়।
	// For the same reason, toString() is overridden in BookingDatesPOJO & ExpectedStatusCode    
    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this); // "this" refers to the POJO object of this class
        } catch (JsonProcessingException e) {
            return super.toString();
        }
    }

}
