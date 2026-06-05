package utilities;

import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EncryptionUtil {
    private static final Logger logger = LogManager.getLogger(EncryptionUtil.class);
	private static final String SECRET_KEY = "1234567890123456"; // 16-char key

	public static String encrypt(String strToEncrypt) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
			cipher.init(Cipher.ENCRYPT_MODE, key);
			return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes("UTF-8")));
		} catch (Exception e) {
			throw new RuntimeException("Error while encrypting: " + e);
		}
	}

	public static String decrypt(String strToDecrypt) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
			cipher.init(Cipher.DECRYPT_MODE, key);
			return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
		} catch (Exception e) {
			throw new RuntimeException("Error while decrypting: " + e);
		}
	}

    public static void main(String[] args) {
	        String originalString = "Ritik@1234";
	        String pwdD = "fBL8Z37F82QyecxLYVtlVA==";
	        String encryptedString = encrypt(originalString);
	        String decryptString = decrypt(pwdD);
	        System.out.println("encryptedString: " + encryptedString);
        logger.debug("Original String: " + originalString);
        logger.debug("Encrypted String: " + encryptedString);
        logger.debug("Decrypted String: " + decryptString);
	    }










}