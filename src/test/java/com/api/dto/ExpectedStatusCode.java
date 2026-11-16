

// This class only carries data: the status code and the validation policy of a scenario.
// It has no business logic. It is a part of TestScenarioRecord,
// so it lives in the same package: "dto".

package com.api.dto;

import com.api.validation.SchemaAndAllowHeaderValidationPolicy;

public class ExpectedStatusCode {

	// The status code the server must answer with for this scenario.
	// Primitive int: it is mandatory.
	public final int status;

	// Validation policies applicable for this response scenario
	public final SchemaAndAllowHeaderValidationPolicy policy;

	// Private constructor to enforce object creation via the static factory method.
	private ExpectedStatusCode(int status, SchemaAndAllowHeaderValidationPolicy policy) {
		this.status = status;
		this.policy = policy;
	}

	// Factory method: the expected status code together with the validation policy of the scenario.
	// Usage in a DataProvider row:  expect(401, validateSchemaOnly(ERROR_RESPONSE_SCHEMA, "..."))
	public static ExpectedStatusCode expect(int status, SchemaAndAllowHeaderValidationPolicy policy) {
		return new ExpectedStatusCode(status, policy);
	}

	// Allure Report-এর Parameters সেকশন, TestNG assertion failure log এবং console log-এ
	// Memory Address (যেমন ExpectedStatusCode@3af49f1c) দেখানোর বদলে
	// টেস্টের মূল ডাটা (status, policy) পরিষ্কারভাবে রিড করার জন্য toString() মেথডটি আবশ্যক।
	// এটি Policy ক্লাসের ভেতর থাকা সুন্দর toString()-কেও রিপোর্ট বা লগে যুক্ত করতে সাহায্য করে।
	// For the same reason, toString() is overridden in UsersPayloadPOJO & BookingDatesPOJO
	@Override
	public String toString() {
		return "ExpectedStatusCode{" +
				"status=" + status +
				", policy=" + policy +
				'}';
	}
}
