package utilities;
 
import io.restassured.RestAssured;
import io.restassured.response.Response;
 
import static io.restassured.RestAssured.*;
 
public class TokenManagerNetsingularityDemo {
    static String usernamen = ConfigReader.get("unDemoNetSingularity");
    static String password = ConfigReader.get("pwdDemoNetSingularity");
    
 
    public static String getToken() {
        try {
            RestAssured.baseURI = ConfigReader.get("base.uriDemoNetSingularity");
 
            Response response = given()
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .formParam("grant_type", "password")
                    .formParam("client_id", "bluewaves")
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
}