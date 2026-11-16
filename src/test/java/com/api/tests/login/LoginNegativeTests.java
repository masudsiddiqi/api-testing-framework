package com.api.tests.login;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.api.base.BaseTest;
import com.api.dataProvider.login.LoginNegativeDataProvider;
import com.api.utils.AuthUtils;

import io.restassured.response.Response;

// This Test Method ignores TestScenarioRecord Object and takes data directly from its own data provider

public class LoginNegativeTests extends BaseTest {
	
	@Test(dataProvider = "invalidLoginCredentialsProvider", dataProviderClass = LoginNegativeDataProvider.class)
	void shouldRejectLogin_withInvalidCredentials(String scenarioDescription, String username, String password) {

		System.out.println("----Executing Scenario: " + scenarioDescription);

		Response response = AuthUtils.callLoginEndpointRaw(username, password);

		System.out.println(response.asPrettyString());

		Assert.assertEquals(response.getStatusCode(), 401, "Scenario: " + scenarioDescription);
	}
}
