package base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.BeforeClass;

import io.restassured.RestAssured;
import utilities.ConfigReader;
import utilities.TokenManagerNetsingularityDemo;

public class BaseTestDemoNetSingularity {

	protected Logger logger = LogManager.getLogger(BaseTestDemoNetSingularity.class);

	protected String token;

	@BeforeClass

	public void setUp() {
		// Set Base URI from config.properties
		RestAssured.baseURI = ConfigReader.get("base.uriDemoNetSingularity");
		token = "Bearer " + TokenManagerNetsingularityDemo.getToken();


		// Generate Bearer Token
		logger.info("Base URI of DemoNetSingularity : " + RestAssured.baseURI);
		logger.info("Token of DemoNetSingularity: " + token);

	}

}
