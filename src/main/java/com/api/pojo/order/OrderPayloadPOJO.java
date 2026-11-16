package com.api.pojo.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The request payload of an order (POST, PUT, PATCH).
 * The fields are EXAMPLES. Change them to match the API under test.
 *
 * It follows the same rules as UsersPayloadPOJO:
 *
 * 1. @JsonInclude(NON_NULL): a field that is null is left out of the JSON.
 *    So a PATCH payload can contain only the fields that are set.
 *
 * 2. Wrapper types (Integer, not int):
 *    An int is never null. It starts as 0, and Jackson would send "quantity": 0
 *    even when nobody set it. An Integer starts as null, so NON_NULL leaves it out.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderPayloadPOJO {

	@JsonProperty(OrderFields.KEY_REFERENCE)
	private String reference;

	@JsonProperty(OrderFields.KEY_CUSTOMER)
	private String customer;

	@JsonProperty(OrderFields.KEY_ITEM)
	private String item;

	@JsonProperty(OrderFields.KEY_QUANTITY)
	private Integer quantity;

	// Empty constructor: used for a partial payload (set only the fields you need).
	public OrderPayloadPOJO() {}

	public OrderPayloadPOJO(String reference, String customer, String item, Integer quantity) {
		this.reference = reference;
		this.customer = customer;
		this.item = item;
		this.quantity = quantity;
	}

	// Getters are needed to build the JSON (serialization).
	// Setters are needed to change one field after the object is created, for example in a PATCH test.
	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public String getCustomer() {
		return customer;
	}

	public void setCustomer(String customer) {
		this.customer = customer;
	}

	public String getItem() {
		return item;
	}

	public void setItem(String item) {
		this.item = item;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}

	// Without toString(), a report and a failure log show a memory address (OrderPayloadPOJO@3af49f1c).
	// With it, they show the real field values, so you can see which data was sent.
	@Override
	public String toString() {
		return "OrderPayloadPOJO{" +
				"reference='" + reference + '\'' +
				", customer='" + customer + '\'' +
				", item='" + item + '\'' +
				", quantity=" + quantity +
				'}';
	}
}
