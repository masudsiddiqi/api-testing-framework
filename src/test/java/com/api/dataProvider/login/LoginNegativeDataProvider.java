package com.api.dataProvider.login;

import org.testng.annotations.DataProvider;

import com.api.utils.ConfigUtils;

public class LoginNegativeDataProvider {
	
	@DataProvider(name = "invalidLoginCredentialsProvider")
	public Object[][] getInvalidLoginScenarios() {
		return new Object[][] {
				{ "Wrong Username", "nonexistent-user", ConfigUtils.getValidPassword() },
				{ "Wrong Password", ConfigUtils.getValidUsername(), "wrong-password" },
				{ "Both Wrong", "nonexistent-user", "wrong-password" } };
	}

}
