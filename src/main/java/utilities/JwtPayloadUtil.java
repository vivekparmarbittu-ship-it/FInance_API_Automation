package utilities;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads common identity claims from a JWT access token (payload only; no signature verification).
 * Used so {@code userid} request headers match the user encoded in {@code Authorization}, which the
 * UI and backend typically require for editable records.
 */
public final class JwtPayloadUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtPayloadUtil() {
    }

    /**
     * @param authorizationOrRawJwt {@code Bearer eyJ...} or raw JWT string
     * @return {@code userId} / {@code userid} from payload, else {@code sub}, else null
     */
    public static String extractPreferredUserId(String authorizationOrRawJwt) {
        if (authorizationOrRawJwt == null) {
            return null;
        }
        String jwt = authorizationOrRawJwt.trim();
        if (jwt.regionMatches(true, 0, "Bearer ", 0, 7)) {
            jwt = jwt.substring(7).trim();
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] jsonBytes = base64UrlDecode(parts[1]);
            JsonNode root = MAPPER.readTree(new String(jsonBytes, StandardCharsets.UTF_8));
            if (root.hasNonNull("userId")) {
                return root.get("userId").asText();
            }
            if (root.hasNonNull("userid")) {
                return root.get("userid").asText();
            }
            if (root.hasNonNull("sub")) {
                return root.get("sub").asText();
            }
        } catch (Exception ignored) {
            // malformed token or payload
        }
        return null;
    }

    private static byte[] base64UrlDecode(String segment) {
        String padded = segment.replace('-', '+').replace('_', '/');
        int mod = padded.length() % 4;
        if (mod != 0) {
            padded += "====".substring(0, 4 - mod);
        }
        return Base64.getDecoder().decode(padded);
    }
}
