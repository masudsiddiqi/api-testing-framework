package com.api.tests.products;

import static com.api.validation.ResponseValidator.validateAll;

import org.testng.annotations.Test;

import com.api.base.BaseTest;
import com.api.dataProvider.products.PatchProductDataProvider;
import com.api.dataProvider.products.PostProductsDataProvider;
import com.api.dataProvider.products.PutProductDataProvider;
import com.api.dto.TestScenarioRecord;

import io.qameta.allure.Allure;
import io.restassured.response.Response;

public class ProductsPositiveTests extends BaseTest {

	@Test(groups = "smoke", dataProvider="PostProducts-PositiveTestDataProvider", dataProviderClass = PostProductsDataProvider.class)
	void testPostProductssHappyPathScenarios(TestScenarioRecord record) {

		// All data provider columns are encapsulated within a Record object named
		// "TestScenarioRecord"
		// instead of listing all its columns as a parameter list of the test method

		// Adds full scenario details as a formatted JSON attachment
		// under the Test Body / Steps section in the Allure Report.
		// It should be the first line of each Test method.
		Allure.addAttachment("DataProvider Row Details", "application/json", record.toString(), ".json");

		// System.out.println prints only in the Eclipse Console, never in Allure
		System.out.println("----Executing Scenario: " + record.scenarioDescription());

		Response response = sendRequestWithTimeoutHandling(reqSpec_base, record.endpoint(), record.httpMethod(),
				record.headers(), record.payload(), record.scenarioDescription());

		
		validateAll(response, record);
	}
	
	
	
	@Test(dataProvider="PutProduct-PositiveTestDataProvider", dataProviderClass = PutProductDataProvider.class)
	void testPutProductssHappyPathScenarios(TestScenarioRecord record) {

		// All data provider columns are encapsulated within a Record object named
		// "TestScenarioRecord"
		// instead of listing all its columns as a parameter list of the test method

		// Adds full scenario details as a formatted JSON attachment
		// under the Test Body / Steps section in the Allure Report.
		// It should be the first line of each Test method.
		Allure.addAttachment("DataProvider Row Details", "application/json", record.toString(), ".json");

		// System.out.println prints only in the Eclipse Console, never in Allure
		System.out.println("----Executing Scenario: " + record.scenarioDescription());

		Response response = sendRequestWithTimeoutHandling(reqSpec_base, record.endpoint(), record.httpMethod(),
				record.headers(), record.payload(), record.scenarioDescription());

		
		validateAll(response, record);
	}
	
	@Test(dataProvider="PatchProduct-PositiveTestDataProvider", dataProviderClass = PatchProductDataProvider.class)
	void testPatchProductssHappyPathScenarios(TestScenarioRecord record) {

		// All data provider columns are encapsulated within a Record object named
		// "TestScenarioRecord"
		// instead of listing all its columns as a parameter list of the test method

		// Adds full scenario details as a formatted JSON attachment
		// under the Test Body / Steps section in the Allure Report.
		// It should be the first line of each Test method.
		Allure.addAttachment("DataProvider Row Details", "application/json", record.toString(), ".json");

		// System.out.println prints only in the Eclipse Console, never in Allure
		System.out.println("----Executing Scenario: " + record.scenarioDescription());

		Response response = sendRequestWithTimeoutHandling(reqSpec_base, record.endpoint(), record.httpMethod(),
				record.headers(), record.payload(), record.scenarioDescription());

		
		validateAll(response, record);
	}

}
