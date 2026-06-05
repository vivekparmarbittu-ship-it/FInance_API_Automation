package utilities;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;

/**
 * Tracks soft assertion pass/fail counts per TestNG test method (thread-local),
 * and logs per-assertion outcomes to the active ExtentTest.
 */
public final class AssertionTracker {

    private static final ThreadLocal<Summary> SUMMARY = ThreadLocal.withInitial(Summary::new);

    private static final class Summary {
        int total;
        int pass;
        int fail;

        void reset() {
            total = 0;
            pass = 0;
            fail = 0;
        }
    }

    private AssertionTracker() {}

    public static void reset() {
        SUMMARY.get().reset();
    }

    public static void logSoftAssertPass(String assertionName, String details) {
        Summary s = SUMMARY.get();
        s.total++;
        s.pass++;
        // PASS-level assertion details are intentionally suppressed in report output.
    }

    public static void logSoftAssertFail(String assertionName, String details) {
        Summary s = SUMMARY.get();
        s.total++;
        s.fail++;
        log(Status.FAIL, "❌ Fail: " + assertionName, details);
    }

    public static void logSummary(ExtentTest t) {
        Summary s = SUMMARY.get();
        if (t == null) {
            return;
        }
        t.info("<b>Soft assertion summary</b>: total=" + s.total + ", passed=" + s.pass + ", failed=" + s.fail);
    }

    private static void log(Status status, String headline, String details) {
        ExtentTest t = ExtentListener.test;
        if (t == null) {
            return;
        }
        String msg = "<b>" + headline + "</b>";
        if (details != null && !details.trim().isEmpty()) {
            msg += "<br/>" + details;
        }
        t.log(status, msg);
    }
}
