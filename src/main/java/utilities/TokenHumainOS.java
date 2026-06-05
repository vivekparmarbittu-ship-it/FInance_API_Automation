package utilities;

import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.response.Response;

public class TokenHumainOS {

    static String usernamen = ConfigReader.get("unHumainos");
    static String password = getDecryptedPassword("pwdHumainos");

    private static String getDecryptedPassword(String key) {
        String pwd = ConfigReader.get(key);
        if (pwd != null && !pwd.trim().isEmpty()) {
            try {
                return EncryptionUtil.decrypt(pwd);
            } catch (Exception e) {
                return pwd;
            }
        }
        return pwd;
    }

    public static String getToken() {
        try {
            RestAssured.useRelaxedHTTPSValidation();
            RestAssured.baseURI = ConfigReader.get("base.uriHumainos");

            Response response = given()
            		.relaxedHTTPSValidation()
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .formParam("grant_type", "password")
                    .formParam("client_id", "humainos")
                    .formParam("username", usernamen)
                    .formParam("password", password)
                    .post("/auth/realms/BNTV/protocol/openid-connect/token");

            if (response.getStatusCode() == 200) {
                return response.jsonPath().getString("access_token");
            } else {
                System.out.println("Token request failed: " + response.statusLine());
                return null;
            }

        } catch (Exception e) {
            System.out.println("Failed to fetch access token: " + e.getMessage());
            return null;
        }
    }
	
	private static String getTokenForUser(String usernameKey, String passwordKey) {
        try {
            RestAssured.useRelaxedHTTPSValidation();
            String baseUri = ConfigReader.get("base.uriHumainos");
            if (baseUri == null || baseUri.trim().isEmpty()) {
                baseUri = "https://one-sit.humain.ai";
            }
            RestAssured.baseURI = baseUri;

            String un = ConfigReader.get(usernameKey);
            String pwd = getDecryptedPassword(passwordKey);

            Response response = given()
            		.relaxedHTTPSValidation()
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .formParam("grant_type", "password")
                    .formParam("client_id", "humainos")
                    .formParam("username", un)
                    .formParam("password", pwd)
                    .post("/auth/realms/BNTV/protocol/openid-connect/token");

            if (response.getStatusCode() == 200) {
                return response.jsonPath().getString("access_token");
            } else {
                System.out.println("Token request failed for " + usernameKey + ": " + response.statusLine());
                return null;
            }
        } catch (Exception e) {
            System.out.println("Failed to fetch access token for " + usernameKey + ": " + e.getMessage());
            return null;
        }
    }

    public static String getTokenAccountant() {
        return getTokenForUser("unHumainosAccountant", "pwdHumainosAccountant");
    }

    public static String getTokenSeniorAccountant() {
        return getTokenForUser("unHumainosSeniorAccountant", "pwdHumainosSeniorAccountant");
    }

    public static String getTokenFinanceController() {
        return getTokenForUser("unHumainosFinanceController", "pwdHumainosFinanceController");
    }

    public static String getTokenSeniorGroupAccountingManager() {
        return getTokenForUser("unHumainosSeniorGroupAccountingManager", "pwdHumainosSeniorGroupAccountingManager");
    }

    public static String getTokenGroupAccountingManager() {
        return getTokenForUser("unHumainosGroupAccountingManager", "pwdHumainosGroupAccountingManager");
    }

    public static String getTokenAdmin() {
        return getTokenForUser("unHumainosAdmin", "pwdHumainosAdmin");
    }
}
