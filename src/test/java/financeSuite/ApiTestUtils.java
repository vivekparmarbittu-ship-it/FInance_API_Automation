package financeSuite;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.testng.asserts.SoftAssert;

import com.aventstack.extentreports.Status;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import utilities.ExtentListener;
import utilities.AssertionTracker;

/**
 * Shared assertions and logging for Finance API tests.
 */
public final class ApiTestUtils {

    public static final long DEFAULT_RESPONSE_TIME_THRESHOLD_MS = 30_000L;

    private ApiTestUtils() {}

    public static void validateResponseTime(Response response, long thresholdMs) {
        long time = response.getTime();
        if (time > thresholdMs) {
            ExtentListener.test.log(Status.WARNING,
                "Response time " + time + " ms exceeded threshold " + thresholdMs + " ms");
        }
    }

    public static void assertContentType(Response response, SoftAssert softAssert) {
        String ct = response.getHeader("Content-Type");
        if (ct == null || (!ct.contains("json") && !ct.contains("text/plain"))) {
            softFail(softAssert, "Content-Type", "Expected JSON or text/plain Content-Type, got: " + ct);
        } else {
            softPass("Content-Type", "Content-Type: " + ct);
        }
    }

    public static void logRequestInfo(String method, String endpoint) {
        // Request method/full URL is already logged centrally by ExtentRestAssuredFilter.
        // Keeping this as no-op prevents duplicate request entries in report.
    }

    public static void logResponseBody(String body) {
        // Response bodies are logged automatically by `ExtentRestAssuredFilter`.
    }

    public static void logInfo(String label, String value) {
        if (ExtentListener.test == null) {
            // Called outside an active test (e.g. @BeforeClass) - Extent thread-local not yet bound.
            return;
        }
        ExtentListener.test.log(Status.INFO, "<b>" + label + ":</b> " + (value != null ? value : ""));
    }

    public static void softFail(SoftAssert softAssert, String assertionName, String message) {
        AssertionTracker.logSoftAssertFail(assertionName, message);
        softAssert.fail(message);
    }

    private static void softPass(String assertionName, String details) {
        AssertionTracker.logSoftAssertPass(assertionName, details);
    }

    public static void assertHttpStatus(Response response, SoftAssert softAssert, String testName, int... allowed) {
        int actual = response.getStatusCode();
        for (int a : allowed) {
            if (actual == a) {
              softPass(testName, "statusCode=" + actual);
              return;
            }
        }
        String allowedStr = java.util.Arrays.toString(allowed);
        softFail(softAssert, testName,
                testName + ": Expected one of " + allowedStr + ", got " + actual);
    }

    public static void assertPageSize(List<?> list, int expectedMax, SoftAssert softAssert, String testName) {
        int actualSize = (list != null) ? list.size() : -1;
        if (list != null && actualSize > expectedMax) {
            softFail(softAssert, testName,
                    testName + ": Page size " + actualSize + " exceeds requested " + expectedMax);
            return;
        }
        softPass(testName, "Page size=" + actualSize + " (expectedMax=" + expectedMax + ")");
    }

    public static void assertSortedDesc(JsonPath json, List<?> list, String field, SoftAssert softAssert, String testName) {
        if (list == null) {
            softPass(testName, "Sort check skipped: list=null");
            return;
        }
        if (list.size() < 2) {
            softPass(testName, "Sort check skipped: list.size()=" + list.size());
            return;
        }

        boolean anyFailure = false;
        for (int i = 0; i < list.size() - 1; i++) {
            Object a = json.get("[" + i + "]." + field);
            Object b = json.get("[" + (i + 1) + "]." + field);
            if (a instanceof Number && b instanceof Number) {
                long la = ((Number) a).longValue();
                long lb = ((Number) b).longValue();
                if (la < lb) {
                    anyFailure = true;
                    softFail(softAssert, testName,
                            testName + ": Sort order " + field + " DESC violated at index " + i
                                    + " (actual: " + la + " < " + lb + ")");
                }
                continue;
            }
            Instant ia = parseTimestamp(a);
            Instant ib = parseTimestamp(b);
            if (ia != null && ib != null && ia.isBefore(ib)) {
                anyFailure = true;
                softFail(softAssert, testName,
                        testName + ": Sort order " + field + " DESC violated at index " + i
                                + " (" + ia + " < " + ib + ")");
            }
        }

        if (!anyFailure) {
            softPass(testName, "Validated DESC sort for field='" + field + "' (size=" + list.size() + ")");
        }
    }

    public static void assertDeletedFalseForAll(JsonPath json, int size, String testName, SoftAssert softAssert) {
        boolean anyFailure = false;
        for (int i = 0; i < size; i++) {
            Boolean d = json.getBoolean("[" + i + "].deleted");
            if (Boolean.TRUE.equals(d)) {
                anyFailure = true;
                softFail(softAssert, testName,
                        testName + ": Record [" + i + "] has deleted=true");
            }
        }
        if (!anyFailure) {
            softPass(testName, "All records validated deleted=false (checked size=" + size + ")");
        }
    }

    public static void assertFieldNotNull(JsonPath json, String path, SoftAssert softAssert, String testName) {
        Object v = json.get(path);
        if (v == null || (v instanceof String && ((String) v).isEmpty())) {
            softFail(softAssert, testName, testName + ": Field " + path + " is null or empty");
            return;
        }
        softPass(testName, "Field not null: " + path + "=" + String.valueOf(v));
    }

    public static void assertPositiveId(Object idObj, String fieldName, SoftAssert softAssert, String testName) {
        if (idObj == null) {
            softFail(softAssert, testName, testName + ": " + fieldName + " is null");
            return;
        }
        if (idObj instanceof Number) {
            if (((Number) idObj).longValue() <= 0) {
                softFail(softAssert, testName,
                        testName + ": " + fieldName + " must be positive, got " + idObj);
                return;
            }
            softPass(testName, fieldName + "=" + idObj + " is positive");
            return;
        } else if (idObj instanceof String) {
            String s = ((String) idObj).trim();
            if (s.isEmpty()) {
                softFail(softAssert, testName, testName + ": " + fieldName + " is empty");
                return;
            }
            try {
                long v = Long.parseLong(s);
                if (v <= 0) {
                    softFail(softAssert, testName,
                            testName + ": " + fieldName + " must be positive, got " + idObj);
                    return;
                }
                softPass(testName, fieldName + "=" + idObj + " is positive");
                return;
            } catch (NumberFormatException e) {
                softFail(softAssert, testName, testName + ": " + fieldName + " must be numeric, got " + idObj);
            }
        }
        // Unsupported types: keep original behavior (no failure) but still log pass.
        softPass(testName, fieldName + " value type=" + idObj.getClass().getSimpleName());
    }

    public static void assertListNotEmpty(List<?> list, String label, SoftAssert softAssert) {
        if (list == null || list.isEmpty()) {
            softFail(softAssert, label, label + ": list is null or empty");
            return;
        }
        softPass(label, "list.size=" + list.size());
    }

    @SuppressWarnings("unchecked")
    public static void assertMapFieldNotNull(Map<String, Object> map, String key, SoftAssert softAssert, String testName) {
        if (map == null) {
            softFail(softAssert, testName, testName + ": map is null");
            return;
        }
        Object v = map.get(key);
        if (v == null || (v instanceof String && ((String) v).isEmpty())) {
            softFail(softAssert, testName, testName + ": map field '" + key + "' is null or empty");
            return;
        }
        softPass(testName, "map['" + key + "']=" + String.valueOf(v));
    }

    public static void assertFieldLength(String value, int min, int max, String fieldName, SoftAssert softAssert, String testName) {
        if (value == null) {
          value = "";
        }
        int len = value.length();
        if (len < min || len > max) {
            softFail(softAssert, testName,
                    testName + ": " + fieldName + " length " + len + " not in range [" + min + "," + max + "]");
            return;
        }
        softPass(testName, fieldName + " length=" + len + " within [" + min + "," + max + "]");
    }

    public static void assertFieldLength(Object valueObj, int min, int max, String fieldName, SoftAssert softAssert, String testName) {
        String value = valueObj != null ? valueObj.toString() : "";
        assertFieldLength(value, min, max, fieldName, softAssert, testName);
    }

    public static void assertSuccessEnvelope(JsonPath json, SoftAssert softAssert, String testName) {
        Object success = json.get("success");
        if (success != null && !Boolean.TRUE.equals(success)) {
            softFail(softAssert, testName, testName + ": Expected success envelope, got success=" + success);
            return;
        }
        softPass(testName, "success=" + success);
    }

    @SuppressWarnings("unchecked")
    public static void assertMapFieldEquals(Map<String, Object> map, String key, Object expected, SoftAssert softAssert, String testName) {
        if (map == null) {
            softFail(softAssert, testName, testName + ": map is null");
            return;
        }
        Object actual = map.get(key);
        if (expected == null && actual != null || expected != null && !expected.equals(actual)) {
            softFail(softAssert, testName,
                    testName + ": " + key + " expected '" + expected + "', got '" + actual + "'");
            return;
        }
        softPass(testName, key + "=" + String.valueOf(actual) + " matches expected=" + String.valueOf(expected));
    }

    public static void assertFieldMatchesPattern(String value, String regex, String fieldName, SoftAssert softAssert, String testName) {
        if (value == null || !Pattern.matches(regex, value)) {
            softFail(softAssert, testName,
                    testName + ": " + fieldName + " '" + value + "' does not match " + regex);
            return;
        }
        softPass(testName, fieldName + "='" + value + "' matches " + regex);
    }

    public static void assertErrorEnvelope(JsonPath json, SoftAssert softAssert, String testName, int statusCode) {
        Object err = json.get("error");
        if (err == null) {
          err = json.get("message");
        }
        if (err == null) {
            softFail(softAssert, testName,
                    testName + ": Expected error envelope for status " + statusCode);
            return;
        }
        softPass(testName, "Error envelope present for status " + statusCode + ": " + String.valueOf(err));
    }

    public static void validateResponseBody(Response response, String... requiredPaths) {
        JsonPath j = response.jsonPath();
        for (String path : requiredPaths) {
            if (j.get(path) == null) {
                throw new AssertionError("Missing expected path in response: " + path);
            }
        }
    }

    public static void validateNoErrorInResponse(Response response) {
        String body = response.getBody().asString();
        if (body != null && (body.contains("\"error\"") || body.contains("\"success\":false"))) {
            throw new AssertionError("Response contains error marker: "
                    + (body.length() > 200 ? body.substring(0, 200) + "..." : body));
        }
    }

    public static void assertFieldEquals(JsonPath json, String path, Object expected, SoftAssert softAssert, String testName) {
        Object actual = json.get(path);
        if (expected == null && actual != null || expected != null && !expected.equals(actual)) {
            softFail(softAssert, testName,
                    testName + ": " + path + " expected '" + expected + "', got '" + actual + "'");
            return;
        }
        softPass(testName, path + "=" + String.valueOf(actual) + " equals expected=" + String.valueOf(expected));
    }

    private static final long TIMESTAMP_TOLERANCE_MINUTES = 5;

    public static Instant parseTimestamp(Object value) {
        if (value == null) {
          return null;
        }
        if (value instanceof Number) {
            long num = ((Number) value).longValue();
            if (num > 1_000_000_000_000L) {
              return Instant.ofEpochMilli(num);
            }
            if (num > 1_000_000_000L) {
              return Instant.ofEpochSecond(num);
            }
            return null;
        }
        String str = value.toString().trim();
        if (str.isEmpty()) {
          return null;
        }
        try { return Instant.parse(str); } catch (DateTimeParseException ignored) {}
        String[] fmts = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ssZ",      "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSS",      "yyyy-MM-dd HH:mm:ss",
            "dd-MM-yyyy HH:mm:ss",          "MM/dd/yyyy HH:mm:ss"
        };
        for (String p : fmts) {
            try {
                return LocalDateTime.parse(str, DateTimeFormatter.ofPattern(p))
                        .atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {}
        }
        try {
            long millis = Long.parseLong(str);
            if (millis > 1_000_000_000_000L) {
              return Instant.ofEpochMilli(millis);
            }
            if (millis > 1_000_000_000L) {
              return Instant.ofEpochSecond(millis);
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    public static void assertTimestampIsRecent(Object value, String fieldName,
                                               SoftAssert softAssert, String testName) {
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
            softFail(softAssert, testName, testName + ": " + fieldName + " is null or empty");
            return;
        }
        Instant parsed = parseTimestamp(value);
        if (parsed == null) {
            softFail(softAssert, testName, testName + ": " + fieldName
                    + " could not be parsed as a timestamp — raw value: '" + value + "'");
            return;
        }
        Instant now = Instant.now();
        Instant lower = now.minusSeconds(TIMESTAMP_TOLERANCE_MINUTES * 60);
        Instant upper = now.plusSeconds(TIMESTAMP_TOLERANCE_MINUTES * 60);
        DateTimeFormatter display = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formatted = display.format(parsed.atZone(ZoneId.systemDefault()));
        ExtentListener.test.log(Status.INFO,
                "<b>" + fieldName + ":</b> " + formatted
                + " (expected within " + TIMESTAMP_TOLERANCE_MINUTES + " min of now)");
        if (parsed.isBefore(lower) || parsed.isAfter(upper)) {
            softFail(softAssert, testName, testName + ": " + fieldName + " = " + formatted
                    + " is NOT within " + TIMESTAMP_TOLERANCE_MINUTES + " minutes of current time "
                    + display.format(now.atZone(ZoneId.systemDefault())));
            return;
        }
        softPass(testName, fieldName + "=" + formatted + " is within " + TIMESTAMP_TOLERANCE_MINUTES + " minutes of now");
    }

    public static void assertTimestampIsToday(Object value, String fieldName,
                                              SoftAssert softAssert, String testName) {
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
            softFail(softAssert, testName, testName + ": " + fieldName + " is null or empty");
            return;
        }
        Instant parsed = parseTimestamp(value);
        if (parsed == null) {
            softFail(softAssert, testName, testName + ": " + fieldName
                    + " could not be parsed as a timestamp — raw value: '" + value + "'");
            return;
        }
        LocalDate today     = LocalDate.now();
        LocalDate fieldDate = parsed.atZone(ZoneId.systemDefault()).toLocalDate();
        ExtentListener.test.log(Status.INFO,
                "<b>" + fieldName + " date:</b> " + fieldDate + " (expected: " + today + ")");
        if (!fieldDate.equals(today)) {
            softFail(softAssert, testName, testName + ": " + fieldName + " date is " + fieldDate
                    + ", expected today " + today);
            return;
        }
        softPass(testName, fieldName + " date=" + fieldDate + " is today");
    }
}
