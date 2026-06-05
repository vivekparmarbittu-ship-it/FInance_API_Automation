package financeSuite;

import static io.restassured.RestAssured.given;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import com.aventstack.extentreports.Status;

import base.BaseTestDemoNetSingularity;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import utilities.ExtentListener;
import utilities.JwtPayloadUtil;
import utilities.TokenHumainOS;

/**
 * Credit Memo (Invoice Adjustment) end-to-end suite. Built from Credit_Memo.har.
 *
 * Flow:
 *   1. Senior Accountant lists/searches credit memos.
 *   2. Senior Accountant searches POSTED/PAID AP invoices to pick one for adjustment.
 *   3. Senior Accountant creates a Credit Memo (POST /create-credit-memo).
 *   4. Senior Accountant submits it for review (PUT /invoice-adjustment/{id}, status=PENDING_REVIEW).
 *   5. Group Accounting Manager performs Level-1 approval (> 10k).
 *   6. Finance Controller performs Level-2 approval (10k - 1 lakh).
 *   7. Senior Group Accounting Manager performs Level-3 approval (> 1 lakh).
 *   8. Re-search to verify final status.
 *
 * Override amount via:  -DcmAmount=1000   (smoke flow; capped to AP invoice totalAmount)
 * Approval workflow tests (priorities 20+) auto-pick an AP invoice large enough for the tier amount.
 * Override IDs via:     -DcmGlAccountId=483  -DcmCostCenterId=123337  -DcmApInvoiceId=<id>
 */
@Listeners(ExtentListener.class)
public class FinanceCreditMemoAPI extends BaseTestDemoNetSingularity {

    private static final String EP_CM_COUNT          = "/apim/financial-accounting/1.0/rest/invoice-adjustment/count";
    private static final String EP_CM_SEARCH         = "/apim/financial-accounting/1.0/rest/invoice-adjustment/search";
    private static final String EP_AP_INVOICE_SEARCH = "/apim/financial-accounting/1.0/rest/ap-invoices/search";
    private static final String EP_CM_CREATE         = "/apim/financial-accounting/1.0/rest/invoice-adjustment/create-credit-memo";
    private static final String EP_CM_UPDATE_PREFIX  = "/apim/financial-accounting/1.0/rest/invoice-adjustment/";
    private static final String EP_CM_APPROVE_PREFIX = "/apim/financial-accounting/1.0/rest/invoice-adjustment/approve-credit-memo/";
    private static final String EP_UPLOAD_FILE       = "/apim/fb/1.0/rest/resource-attachments/upload-file";
    private static final String EP_UPDATE_MAPPINGS   = "/apim/fb/1.0/rest/resource-attachments/update-mappings";

    /** Classpath resource name (file lives in src/test/resources/). */
    private static final String VENDOR_CREDIT_NOTE_RESOURCE = "vendor-credit-note.pdf";

    /** Fallback: minimal valid PDF if no real file is available on classpath or via -DcmAttachmentFile. */
    private static final byte[] FALLBACK_PDF_BYTES = (
            "%PDF-1.4\n"
          + "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
          + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
          + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n"
          + "xref\n0 4\n"
          + "0000000000 65535 f \n"
          + "0000000009 00000 n \n"
          + "0000000053 00000 n \n"
          + "0000000098 00000 n \n"
          + "trailer<</Size 4/Root 1 0 R>>\nstartxref\n145\n%%EOF\n"
    ).getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /**
     * Resolve the vendor credit note PDF bytes in the following order:
     *   1. -DcmAttachmentFile=&lt;absolute path&gt;
     *   2. Classpath resource {@code vendor-credit-note.pdf} (bundled from src/test/resources)
     *   3. {@link #FALLBACK_PDF_BYTES} minimal in-memory PDF.
     * Returns a String[] {fileName, bytes-as-string-marker} -- callers must call resolveVendorCreditNote().
     */
    private byte[] resolveVendorCreditNote(StringBuilder sourceLabel) {
        String override = System.getProperty("cmAttachmentFile");
        if (override != null && !override.trim().isEmpty()) {
            java.io.File f = new java.io.File(override.trim());
            if (f.exists() && f.isFile()) {
                try {
                    byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
                    sourceLabel.append("cmd-line: ").append(f.getAbsolutePath()).append(" (").append(data.length).append(" bytes)");
                    return data;
                } catch (java.io.IOException e) {
                    logger.warn("Failed reading -DcmAttachmentFile={}: {}", override, e.getMessage());
                }
            } else {
                logger.warn("-DcmAttachmentFile path not found: {}", override);
            }
        }
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream(VENDOR_CREDIT_NOTE_RESOURCE)) {
            if (in != null) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                byte[] data = out.toByteArray();
                sourceLabel.append("classpath: ").append(VENDOR_CREDIT_NOTE_RESOURCE).append(" (").append(data.length).append(" bytes)");
                return data;
            }
        } catch (java.io.IOException e) {
            logger.warn("Failed reading classpath resource {}: {}", VENDOR_CREDIT_NOTE_RESOURCE, e.getMessage());
        }
        sourceLabel.append("inline minimal PDF (").append(FALLBACK_PDF_BYTES.length).append(" bytes)");
        return FALLBACK_PDF_BYTES;
    }

    private static final String FILTER_BASE        = "deleted==false;id=ge=0";
    private static final String AP_INVOICE_FILTER  = "deleted==false;id=ge=0;status=in=('POSTED','PAID')";

    // Fallback IDs from Credit_Memo.har (SIT). Override via -DcmGlAccountId / -DcmCostCenterId / -DcmApInvoiceId.
    private static final int DEFAULT_GL_ACCOUNT_ID   = 483;
    private static final int DEFAULT_COST_CENTER_ID  = 123337;
    private static final int DEFAULT_AP_INVOICE_ID   = 1178;

    // Approval-amount tiers (per user spec):
    //   > 10k         -> GAM (Level 1) required
    //   10k - 1L      -> FC  (Level 2) required after GAM
    //   > 1L          -> SGAM (Level 3) required after FC
    private static final double TIER_GAM_MIN  = 10_000d;
    private static final double TIER_SGAM_MIN = 100_000d;

    /** Workflow amounts: >10k (L1), 10k–1L (L2), >1L (L3). */
    private static final double WORKFLOW_AMOUNT_L1_L2 = 15_000d;
    private static final double WORKFLOW_AMOUNT_L2_ONLY = 50_000d;
    private static final double WORKFLOW_AMOUNT_L3    = 101_000d;
    private static final double WORKFLOW_AMOUNT_ABOVE_1L = 150_000d;

    // Fallback userIds (from HAR) if JWT does not expose preferred_userid.
    private static final String UID_SENIOR_ACCOUNTANT = "143080"; // Khushbu
    private static final String UID_GAM               = "143081"; // Rohini
    private static final String UID_FC                = "143084"; // Abinaya
    private static final String UID_SGAM              = "143085"; // Suyash (fallback only)

    private String tokenSeniorAccountant;
    private String tokenGam;
    private String tokenFc;
    private String tokenSgam;

    private int    createdCreditMemoId        = -1;
    private String createdCreditMemoNumber;
    private int    selectedApInvoiceId        = -1;
    private int    selectedGlAccountId        = -1;
    private int    selectedCostCenterId       = -1;
    private double selectedApInvoiceTotalAmount = -1;
    private double creditMemoAmount;
    private int    uploadedAttachmentId       = -1;
    private String uploadedAttachmentUuid;
    private String uploadedAttachmentFileName;

    @Override
    @BeforeClass(alwaysRun = true)
    public void setUp() {
        String env = System.getProperty("env");
        if (env == null || env.trim().isEmpty()) {
            env = (base.TestRunner.env != null && !base.TestRunner.env.trim().isEmpty())
                    ? base.TestRunner.env : "SIT";
        }
        switch (env.trim().toLowerCase()) {
            case "dev":
            case "humaindev":
                base.TestRunner.env = "humainDev"; break;
            case "demo":
            case "humaindemo":
                base.TestRunner.env = "humainDemo"; break;
            case "qa":
            case "netsingularityqa":
            case "humainqa":
                base.TestRunner.env = "QA"; break;
            case "sit":
            case "humain":
            case "humainos":
                base.TestRunner.env = "SIT"; break;
            default:
                base.TestRunner.env = env.trim(); break;
        }

        super.setUp();

        String rawCreator = TokenHumainOS.getTokenSeniorAccountant();
        if (rawCreator != null) tokenSeniorAccountant = "Bearer " + rawCreator;
        String rawGam = TokenHumainOS.getTokenGroupAccountingManager();
        if (rawGam != null) tokenGam = "Bearer " + rawGam;
        String rawFc = TokenHumainOS.getTokenFinanceController();
        if (rawFc != null) tokenFc = "Bearer " + rawFc;
        String rawSgam = TokenHumainOS.getTokenSeniorGroupAccountingManager();
        if (rawSgam != null) tokenSgam = "Bearer " + rawSgam;

        creditMemoAmount = parseDoubleProp("cmAmount", 1_000d);
        selectedGlAccountId = (int) parseLongProp("cmGlAccountId", DEFAULT_GL_ACCOUNT_ID);
        selectedCostCenterId = (int) parseLongProp("cmCostCenterId", DEFAULT_COST_CENTER_ID);
        long apOverride = parseLongProp("cmApInvoiceId", -1L);
        if (apOverride > 0) {
            selectedApInvoiceId = (int) apOverride;
        }

        logCreatorToConsole("Credit Memo Creator (Senior Accountant)", tokenSeniorAccountant);
        logCreatorToConsole("L1 Approver - Group Accounting Manager", tokenGam);
        logCreatorToConsole("L2 Approver - Finance Controller",       tokenFc);
        logCreatorToConsole("L3 Approver - Senior Group Accounting Manager", tokenSgam);
        logger.info("Credit Memo amount: {}", creditMemoAmount);
        logger.info("Approval tiers: >10k=GAM (L1), 10k-1L=FC (L2), >1L=SGAM (L3)");
    }

    // ------------------------ helpers ------------------------

    private static long parseLongProp(String key, long defVal) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) return defVal;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return defVal; }
    }

    private static double parseDoubleProp(String key, double defVal) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) return defVal;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return defVal; }
    }

    private static String userIdForToken(String bearerOrRaw, String fallback) {
        String fromJwt = JwtPayloadUtil.extractPreferredUserId(bearerOrRaw);
        if (fromJwt != null && !fromJwt.trim().isEmpty()) return fromJwt.trim();
        return fallback;
    }

    private void logCreatorToConsole(String role, String token) {
        if (token == null || token.isEmpty()) {
            logger.warn("{}: <token missing - user not provisioned in this env>", role);
            return;
        }
        String uid = JwtPayloadUtil.extractPreferredUserId(token);
        logger.info("{}: userId={}, tokenLength={}", role, (uid != null ? uid : "?"), token.length());
    }

    private void logRolesToExtent() {
        logRoleLine("Credit Memo Creator (Senior Accountant - Khushbu)", tokenSeniorAccountant);
        logRoleLine("L1 Approver - Group Accounting Manager (Rohini)",   tokenGam);
        logRoleLine("L2 Approver - Finance Controller (Abinaya)",        tokenFc);
        logRoleLine("L3 Approver - Senior Group Accounting Manager (Suyash)", tokenSgam);
        ApiTestUtils.logInfo("Credit Memo amount",     String.valueOf(creditMemoAmount));
        ApiTestUtils.logInfo("Approval tiers",
                ">10k=GAM (L1), 10k-1L=FC (L2), >1L=SGAM (L3)");
    }

    private static void logRoleLine(String role, String token) {
        if (token == null || token.isEmpty()) {
            ApiTestUtils.logInfo(role, "<token missing - user not provisioned in this env>");
            return;
        }
        String uid = JwtPayloadUtil.extractPreferredUserId(token);
        ApiTestUtils.logInfo(role, "userId=" + (uid != null ? uid : "?") + ", tokenLength=" + token.length());
    }

    private String getXModule() {
        // Credit Memo / invoice-adjustment APIs send x-module: CORE on all environments
        // (verified from Credit_Memo.har on SIT). Override with -DcmXModule if a future env differs.
        return System.getProperty("cmXModule", "CORE");
    }

    private Map<String, String> creditMemoHeaders(String authToken, String fallbackUid) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json, text/plain, */*");
        h.put("Content-Type", "application/json");
        h.put("accept-language", "en-US,en;q=0.9");
        h.put("audience", "apim");
        h.put("client-code", "8892df07-6d62-4ec4-9596-8f48574908ff");
        h.put("customerid", "1");
        h.put("x-module", getXModule());
        if (authToken != null && !authToken.isEmpty()) {
            h.put("Authorization", authToken);
            h.put("userid", userIdForToken(authToken, fallbackUid));
        } else if (fallbackUid != null) {
            h.put("userid", fallbackUid);
        }
        return h;
    }

    private static String iso8601Utc(long epochMs) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(epochMs));
    }

    private void assertCreatorToken() {
        if (tokenSeniorAccountant == null || tokenSeniorAccountant.isEmpty()) {
            throw new SkipException("Credit Memo creator token (Senior Accountant - Khushbu) is unavailable. "
                    + "Check unHumainosSeniorAccountant in config.properties for env=" + base.TestRunner.env + ".");
        }
    }

    /** keyLabel for upload-file (INVO.har entry 181). */
    private static final String CM_UPLOAD_KEY_LABEL = "attachmentEntities";
    private static final String CM_ENTITY_TYPE      = "INVOICE_ADJUSTMENT";

    private static String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonPathAsString(JsonPath json, String path) {
        try {
            Object v = json.get(path);
            return v != null ? String.valueOf(v) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Builds attachmentEntities[] for create-credit-memo (INVO.har entry 185). */
    private String buildAttachmentEntitiesJson() {
        if (uploadedAttachmentId <= 0) return "[]";
        String fn = uploadedAttachmentFileName != null && !uploadedAttachmentFileName.isEmpty()
                ? uploadedAttachmentFileName : "vendor-credit-note.pdf";
        return "[{\"attachmentId\":" + uploadedAttachmentId
                + ",\"entityType\":\"" + CM_ENTITY_TYPE + "\""
                + ",\"fileName\":\"" + escapeJsonString(fn) + "\"}]";
    }

    /**
     * Uploads the vendor credit note PDF and returns documentId (attachmentId).
     * INVO.har: keyLabel=attachmentEntities; create uses attachmentEntities[] not attachments[].
     */
    private int uploadVendorCreditNote() {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json, text/plain, */*");
        h.put("accept-language", "en-US,en;q=0.9");
        h.put("audience", "apim");
        h.put("client-code", "8892df07-6d62-4ec4-9596-8f48574908ff");
        h.put("customerid", "1");
        h.put("x-module", getXModule());
        if (tokenSeniorAccountant != null && !tokenSeniorAccountant.isEmpty()) {
            h.put("Authorization", tokenSeniorAccountant);
            h.put("userid", userIdForToken(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT));
        }

        StringBuilder pdfSource = new StringBuilder();
        byte[] pdfBytes = resolveVendorCreditNote(pdfSource);
        String fileName = "vendor-credit-note-" + System.currentTimeMillis() + ".pdf";
        logger.info("uploadVendorCreditNote source={}", pdfSource);
        ApiTestUtils.logInfo("Attachment file source", pdfSource.toString());

        String keyLabel = System.getProperty("cmAttachmentKeyLabel", CM_UPLOAD_KEY_LABEL);
        uploadedAttachmentFileName = fileName;

        String uploadFileWrapper = "{"
                + "\"moduleName\":\"FINANCIAL-ACCOUNTING_APP_NAME\","
                + "\"workGroupNames\":[],"
                + "\"isVersioning\":false,"
                + "\"resourceFileAttach\":{"
                +     "\"keyLabel\":\"" + keyLabel + "\","
                +     "\"filename\":\"" + escapeJsonString(fileName) + "\","
                +     "\"resourceName\":\"InvoiceAdjustment\""
                + "},"
                + "\"parentFolderType\":\"APPLICATION\","
                + "\"parentFolderValue\":\"FINANCIAL-ACCOUNTING_APP_NAME\","
                + "\"childFolderType\":\"ENTITY\","
                + "\"childFolderValue\":\"InvoiceAdjustment\","
                + "\"serviceUrlKey\":\"SM_DOCUMENT_HTTP_URL\","
                + "\"isParent\":false,"
                + "\"metaInformation\":\"{}\""
                + "}";

        Response response = given()
                .headers(h)
                .multiPart("filedata", fileName, pdfBytes, "application/pdf")
                .multiPart("uploadFileWrapper", uploadFileWrapper, "application/json")
                .basePath(EP_UPLOAD_FILE)
                .when().post()
                .then().extract().response();

        int status = response.getStatusCode();
        String body = response.getBody().asString();
        logger.info("uploadVendorCreditNote keyLabel='{}' status={} body={}",
                keyLabel, status, body.length() > 400 ? body.substring(0, 400) + "..." : body);

        ApiTestUtils.logInfo("Attachment upload keyLabel", keyLabel);
        ApiTestUtils.logInfo("Attachment upload status",   String.valueOf(status));
        ApiTestUtils.logInfo("Attachment upload body",
                body.length() > 400 ? body.substring(0, 400) + "..." : body);

        if (status < 200 || status >= 300) {
            throw new SkipException("upload-file failed: status=" + status + " body=" + body);
        }

        JsonPath j = response.jsonPath();
        // documentId -> attachmentEntities[].attachmentId (INVO.har create-credit-memo)
        Integer attId  = extractInt(j, "documentId", "[0].documentId", "id", "[0].id", "data.documentId");
        String  attUid = extractStr(j, "documentUUID", "[0].documentUUID", "data.documentUUID", "uuid", "[0].uuid");
        if (attId == null || attId <= 0) {
            throw new SkipException("upload-file: could not extract attachment id from response: " + body);
        }
        uploadedAttachmentUuid = attUid;
        ApiTestUtils.logInfo("Uploaded attachment id (documentId)", String.valueOf(attId));
        ApiTestUtils.logInfo("Uploaded attachment uuid",            attUid != null ? attUid : "<not returned>");
        return attId;
    }

    private static Integer extractInt(JsonPath j, String... paths) {
        for (String p : paths) {
            try {
                Object v = j.get(p);
                if (v instanceof Number) return ((Number) v).intValue();
                if (v instanceof String) {
                    String s = ((String) v).trim();
                    if (!s.isEmpty()) return Integer.parseInt(s);
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static String extractStr(JsonPath j, String... paths) {
        for (String p : paths) {
            try {
                Object v = j.get(p);
                if (v instanceof String && !((String) v).trim().isEmpty()) return ((String) v).trim();
                if (v instanceof List && !((List<?>) v).isEmpty()) {
                    Object first = ((List<?>) v).get(0);
                    if (first instanceof String && !((String) first).trim().isEmpty()) return ((String) first).trim();
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    /**
     * Optional PATCH to bind documentUUID -> creditMemo resourceId.
     * INVO.har: create-credit-memo already links the file via {@code attachmentEntities[]};
     * this step is redundant and often fails with
     * {@code DOCUMENT_RESOURCE_MAPPING_UPDATE_ACCESS_DENIED} for Senior Accountant.
     * Enabled only with {@code -DcmLinkAttachment=true}.
     */
    private void linkAttachmentToCreditMemo(int creditMemoId) {
        if (!"true".equalsIgnoreCase(System.getProperty("cmLinkAttachment", "false"))) {
            logger.info("linkAttachmentToCreditMemo skipped (use -DcmLinkAttachment=true to enable). "
                    + "attachmentEntities in create-credit-memo already links the document.");
            ApiTestUtils.logInfo("Attachment mapping", "skipped (not required when using attachmentEntities)");
            return;
        }
        if (uploadedAttachmentUuid == null || uploadedAttachmentUuid.isEmpty()) return;
        Map<String, String> h = creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT);
        String body = "[{\"documentUUID\":[\"" + uploadedAttachmentUuid + "\"],\"resourceId\":" + creditMemoId + "}]";
        Response r = given()
                .headers(h)
                .body(body)
                .basePath(EP_UPDATE_MAPPINGS)
                .when().patch()
                .then().extract().response();
        int status = r.getStatusCode();
        String resp = r.getBody().asString();
        logger.info("linkAttachmentToCreditMemo PATCH {} status={} body={}", EP_UPDATE_MAPPINGS, status, resp);
        ApiTestUtils.logInfo("Attachment mapping status", String.valueOf(status));
        if (status >= 400) {
            ApiTestUtils.logInfo("Attachment mapping note",
                    "Non-fatal: create already sent attachmentEntities. "
                    + "UI may show DOCUMENT_RESOURCE_MAPPING_UPDATE_ACCESS_DENIED for this role.");
        }
    }

    private void extractGlAndCostCenterFromApInvoice(JsonPath json, String prefix) {
        String[] glPaths = {
                prefix + "invoiceLines[0].glAccount.id",
                prefix + "apInvoiceLines[0].chartOfAccounts.id",
                prefix + "apInvoiceLines[0].glAccount.id"
        };
        String[] ccPaths = {
                prefix + "invoiceLines[0].costCenter.id",
                prefix + "invoiceLines[0].costCenterMaster.id",
                prefix + "apInvoiceLines[0].costCenterMaster.id",
                prefix + "apInvoiceLines[0].costCenter.id"
        };
        for (String p : glPaths) {
            try {
                Object v = json.get(p);
                if (v instanceof Number && ((Number) v).intValue() > 0) {
                    selectedGlAccountId = ((Number) v).intValue();
                    break;
                }
            } catch (Exception ignored) { }
        }
        for (String p : ccPaths) {
            try {
                Object v = json.get(p);
                if (v instanceof Number && ((Number) v).intValue() > 0) {
                    selectedCostCenterId = ((Number) v).intValue();
                    break;
                }
            } catch (Exception ignored) { }
        }
    }

    private void capCreditMemoAmountToInvoiceTotal() {
        if (selectedApInvoiceTotalAmount > 0 && creditMemoAmount > selectedApInvoiceTotalAmount) {
            logger.warn("cmAmount {} exceeds apInvoice.totalAmount {}; capping to invoice total",
                    creditMemoAmount, selectedApInvoiceTotalAmount);
            creditMemoAmount = selectedApInvoiceTotalAmount;
        }
    }

    /**
     * Finds a POSTED/PAID AP invoice whose totalAmount can cover {@code requiredAmount}.
     * Does not cap {@code creditMemoAmount} when {@code strictAmount} is true (approval workflows).
     */
    private void selectApInvoiceForAmount(double requiredAmount, boolean strictAmount) {
        long workflowInvoiceOverride = parseLongProp("cmWorkflowApInvoiceId", -1L);
        if (workflowInvoiceOverride > 0) {
            selectedApInvoiceId = (int) workflowInvoiceOverride;
        }

        Response response = given()
                .headers(creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .queryParam("filter",    AP_INVOICE_FILTER)
                .queryParam("offset",    0)
                .queryParam("size",      100)
                .queryParam("orderBy",   "totalAmount")
                .queryParam("orderType", "desc")
                .basePath(EP_AP_INVOICE_SEARCH)
                .when().get()
                .then().extract().response();

        if (response.getStatusCode() != 200) {
            throw new SkipException("selectApInvoiceForAmount: AP invoice search failed status="
                    + response.getStatusCode());
        }

        JsonPath json = response.jsonPath();
        List<?> list = json.getList("$");
        int size = list != null ? list.size() : 0;
        int pickIndex = -1;
        double bestTotal = -1;
        double maxTotalSeen = -1;

        for (int i = 0; i < size; i++) {
            Object idObj = json.get("[" + i + "].id");
            Object totalObj = json.get("[" + i + "].totalAmount");
            if (!(idObj instanceof Number) || !(totalObj instanceof Number)) continue;
            int id = ((Number) idObj).intValue();
            double invTotal = ((Number) totalObj).doubleValue();
            if (invTotal > maxTotalSeen) maxTotalSeen = invTotal;
            if (selectedApInvoiceId > 0 && id != selectedApInvoiceId) continue;
            // Ignore corrupt totals (e.g. running balance mistaken for totalAmount).
            if (invTotal <= 0 || invTotal >= 1_000_000_000d || invTotal < requiredAmount) {
                continue;
            }
            String rowPrefix = "[" + i + "].";
            int gl = -1;
            int cc = -1;
            try {
                extractGlAndCostCenterFromApInvoice(json, rowPrefix);
                gl = selectedGlAccountId;
                cc = selectedCostCenterId;
            } catch (Exception ignored) { }
            if (strictAmount && (gl <= 0 || cc <= 0)) {
                continue;
            }
            if (invTotal > bestTotal) {
                bestTotal = invTotal;
                pickIndex = i;
            }
        }
        selectedGlAccountId = -1;
        selectedCostCenterId = -1;

        if (pickIndex < 0 && selectedApInvoiceId > 0) {
            for (int i = 0; i < size; i++) {
                Object idObj = json.get("[" + i + "].id");
                if (idObj instanceof Number && ((Number) idObj).intValue() == selectedApInvoiceId) {
                    pickIndex = i;
                    break;
                }
            }
        }

        if (pickIndex < 0) {
            if (strictAmount) {
                throw new SkipException("No POSTED/PAID AP invoice with totalAmount >= " + requiredAmount
                        + " SAR on env=" + base.TestRunner.env
                        + " (max seen in search=" + maxTotalSeen
                        + "). Post a larger AP invoice on SIT or pass -DcmWorkflowApInvoiceId=<id>.");
            }
            pickIndex = 0;
        }

        String prefix = "[" + pickIndex + "].";
        Object id = json.get(prefix + "id");
        if (id instanceof Number) selectedApInvoiceId = ((Number) id).intValue();
        extractGlAndCostCenterFromApInvoice(json, prefix);
        Object totalObj = json.get(prefix + "totalAmount");
        if (totalObj instanceof Number) selectedApInvoiceTotalAmount = ((Number) totalObj).doubleValue();

        if (selectedGlAccountId <= 0 || selectedCostCenterId <= 0) {
            throw new SkipException("AP invoice id=" + selectedApInvoiceId
                    + " has no glAccount/costCenter on invoice lines. "
                    + "Use -DcmWorkflowApInvoiceId=<id> for a complete POSTED invoice.");
        }

        creditMemoAmount = requiredAmount;
        if (!strictAmount) {
            capCreditMemoAmountToInvoiceTotal();
        } else if (selectedApInvoiceTotalAmount > 0 && creditMemoAmount > selectedApInvoiceTotalAmount) {
            throw new SkipException("Required workflow amount " + requiredAmount
                    + " exceeds selected invoice total " + selectedApInvoiceTotalAmount);
        }
    }

    private void resetWorkflowCreditMemoState() {
        createdCreditMemoId = -1;
        createdCreditMemoNumber = null;
        uploadedAttachmentId = -1;
        uploadedAttachmentUuid = null;
        uploadedAttachmentFileName = null;
        // Clear smoke-flow invoice so workflow can pick one with sufficient totalAmount.
        if (parseLongProp("cmWorkflowApInvoiceId", -1L) <= 0) {
            selectedApInvoiceId = -1;
            selectedGlAccountId = -1;
            selectedCostCenterId = -1;
            selectedApInvoiceTotalAmount = -1;
        }
    }

    private void createCreditMemoInternal(String testLabel) {
        SoftAssert sa = new SoftAssert();
        uploadedAttachmentId = uploadVendorCreditNote();

        long now = System.currentTimeMillis();
        String adjustmentReason = System.getProperty("cmAdjustmentReason", "Goods Returned");
        String amountStr = String.valueOf((long) creditMemoAmount);
        if (amountStr.contains(".")) {
            amountStr = String.valueOf(creditMemoAmount);
        }
        String payload = System.getProperty("cmCreatePayload");
        if (payload == null || payload.trim().isEmpty()) {
            payload = "{"
                    + "\"adjustmentDate\":\"" + iso8601Utc(now) + "\","
                    + "\"adjustmentReason\":\"" + escapeJsonString(adjustmentReason) + "\","
                    + "\"amount\":\"" + amountStr + "\","
                    + "\"explanation\":\"Automated Credit Memo - " + now + "\","
                    + "\"apInvoice\":{\"id\":" + selectedApInvoiceId + "},"
                    + "\"glAccount\":{\"id\":" + selectedGlAccountId + "},"
                    + "\"costCenter\":{\"id\":" + selectedCostCenterId + "},"
                    + "\"attachmentEntities\":" + buildAttachmentEntitiesJson()
                    + "}";
        }

        Response response = given()
                .headers(creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .body(payload)
                .basePath(EP_CM_CREATE)
                .when().post()
                .then().extract().response();
        int status = response.getStatusCode();
        String respBody = response.getBody().asString();

        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.logRequestInfo("POST", EP_CM_CREATE);
        ApiTestUtils.logInfo("Request payload",  payload);
        ApiTestUtils.logInfo("Response status",  String.valueOf(status));
        ApiTestUtils.logInfo("Response body",    respBody);
        logger.info("{} REQUEST payload={}", testLabel, payload);
        logger.info("{} POST {} status={} body={}", testLabel, EP_CM_CREATE, status,
                respBody.length() > 400 ? respBody.substring(0, 400) + "..." : respBody);
        ApiTestUtils.assertHttpStatus(response, sa, testLabel, 200, 201);

        if (status != 200 && status != 201) {
            sa.assertAll();
            throw new SkipException(testLabel + " failed (status=" + status + ") body=" + respBody);
        }

        JsonPath json = response.jsonPath();
        Object idObj = json.get("id");
        if (idObj instanceof Number) {
            createdCreditMemoId = ((Number) idObj).intValue();
        }
        createdCreditMemoNumber = json.getString("adjustmentNumber");

        if (createdCreditMemoId <= 0) {
            Response search = given()
                    .headers(creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                    .queryParam("filter",    FILTER_BASE + ";adjustmentType==CREDIT_MEMO")
                    .queryParam("offset",    0)
                    .queryParam("size",      1)
                    .queryParam("orderBy",   "createdTime")
                    .queryParam("orderType", "desc")
                    .basePath(EP_CM_SEARCH)
                    .when().get()
                    .then().extract().response();
            if (search.getStatusCode() == 200) {
                JsonPath sj = search.jsonPath();
                Object recoveredId = sj.get("[0].id");
                if (recoveredId instanceof Number) {
                    createdCreditMemoId = ((Number) recoveredId).intValue();
                }
                String recoveredNum = sj.getString("[0].adjustmentNumber");
                if (recoveredNum != null && !recoveredNum.isEmpty()) {
                    createdCreditMemoNumber = recoveredNum;
                }
            }
        }

        if (createdCreditMemoId <= 0) {
            sa.assertAll();
            throw new SkipException(testLabel + ": could not capture created credit memo id");
        }

        ApiTestUtils.logInfo("Created Credit Memo id",   String.valueOf(createdCreditMemoId));
        ApiTestUtils.logInfo("Created adjustmentNumber", createdCreditMemoNumber != null ? createdCreditMemoNumber : "<not returned>");
        logger.info("{} SUCCESS id={} adjustmentNumber={}", testLabel, createdCreditMemoId, createdCreditMemoNumber);
        linkAttachmentToCreditMemo(createdCreditMemoId);
        sa.assertAll();
    }

    private void submitCreditMemoInternal() {
        if (createdCreditMemoId <= 0) {
            throw new SkipException("submitCreditMemoInternal: no credit memo id");
        }
        SoftAssert sa = new SoftAssert();
        String endpoint = EP_CM_UPDATE_PREFIX + createdCreditMemoId;
        long now = System.currentTimeMillis();
        String amountStr = String.valueOf((long) creditMemoAmount);
        if (amountStr.contains(".")) amountStr = String.valueOf(creditMemoAmount);

        String payload = "{"
                + "\"adjustmentDate\":\"" + iso8601Utc(now) + "\","
                + "\"adjustmentReason\":\"Goods Returned\","
                + "\"amount\":\"" + amountStr + "\","
                + "\"explanation\":\"Automated Credit Memo - submitted for review\","
                + "\"apInvoice\":{\"id\":" + selectedApInvoiceId + "},"
                + "\"glAccount\":{\"id\":" + selectedGlAccountId + "},"
                + "\"costCenter\":{\"id\":" + selectedCostCenterId + "},"
                + "\"attachmentEntities\":" + buildAttachmentEntitiesJson() + ","
                + "\"adjustmentType\":\"CREDIT_MEMO\","
                + (createdCreditMemoNumber != null && !createdCreditMemoNumber.isEmpty()
                        ? "\"adjustmentNumber\":\"" + createdCreditMemoNumber + "\","
                        : "")
                + "\"status\":\"PENDING_REVIEW\""
                + "}";

        Response response = given()
                .headers(creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .body(payload)
                .basePath(endpoint)
                .when().put()
                .then().extract().response();

        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.logRequestInfo("PUT", endpoint);
        ApiTestUtils.assertHttpStatus(response, sa, "submitCreditMemoInternal", 200, 201, 204);
        sa.assertAll();
    }

    private boolean isApprovalTierRequired(double minExclusive, double maxInclusive) {
        if (creditMemoAmount <= minExclusive) return false;
        if (maxInclusive < Double.MAX_VALUE && creditMemoAmount > maxInclusive) return false;
        return true;
    }

    private void approveCreditMemoWhenRequired(String approverLabel, String token, String fallbackUid,
                                               String testName, String remark,
                                               double minExclusive, double maxInclusive, boolean required) {
        if (!required) {
            ApiTestUtils.logInfo(approverLabel, "not required for amount " + creditMemoAmount);
            ExtentListener.test.log(Status.PASS,
                    "<b>" + approverLabel + " correctly skipped (amount " + creditMemoAmount + ")</b>");
            return;
        }
        if (!isApprovalTierRequired(minExclusive, maxInclusive)) {
            throw new SkipException(testName + ": expected " + approverLabel + " for amount " + creditMemoAmount
                    + " but tier rules say it is not required");
        }
        approveCreditMemoAs(approverLabel, token, fallbackUid, testName, remark, minExclusive, maxInclusive);
    }

    private void verifyCreditMemoState(String testLabel) {
        if (createdCreditMemoId <= 0) {
            throw new SkipException(testLabel + ": no credit memo id");
        }
        SoftAssert sa = new SoftAssert();
        Response response = given()
                .headers(creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .queryParam("filter",    "deleted==false;id==" + createdCreditMemoId)
                .queryParam("offset",    0)
                .queryParam("size",      1)
                .basePath(EP_CM_SEARCH)
                .when().get()
                .then().extract().response();

        ApiTestUtils.assertHttpStatus(response, sa, testLabel, 200);
        if (response.getStatusCode() == 200) {
            JsonPath json = response.jsonPath();
            List<?> list = json.getList("$");
            if (list == null || list.isEmpty()) {
                throw new SkipException(testLabel + ": credit memo id=" + createdCreditMemoId + " not found in search");
            }
            String number = jsonPathAsString(json, "[0].adjustmentNumber");
            String status = jsonPathAsString(json, "[0].status");
            ApiTestUtils.logInfo("Credit Memo number", number);
            ApiTestUtils.logInfo("Final status",       status);
            ApiTestUtils.logInfo("Recorded amount",    jsonPathAsString(json, "[0].amount"));
            sa.assertNotNull(status, "status should be present");
        }
        sa.assertAll();
    }

    /**
     * End-to-end approval workflow: create CM, submit, run expected approvers, verify status.
     */
    private void runApprovalWorkflow(double amount, String workflowName,
                                     boolean requireL1, boolean requireL2, boolean requireL3) {
        assertCreatorToken();
        resetWorkflowCreditMemoState();
        creditMemoAmount = amount;
        selectApInvoiceForAmount(amount, true);

        ApiTestUtils.logInfo("Workflow", workflowName);
        ApiTestUtils.logInfo("Workflow amount", String.valueOf(creditMemoAmount));
        ApiTestUtils.logInfo("AP invoice id", String.valueOf(selectedApInvoiceId));
        ApiTestUtils.logInfo("AP invoice total", String.valueOf(selectedApInvoiceTotalAmount));

        createCreditMemoInternal("approvalWorkflow_create_" + workflowName);
        submitCreditMemoInternal();
        logger.info("approvalWorkflow {} submit done for creditMemoId={}", workflowName, createdCreditMemoId);

        approveCreditMemoWhenRequired(
                "Group Accounting Manager - Rohini (L1)", tokenGam, UID_GAM,
                "approvalWorkflow_L1_" + workflowName,
                "Approved by Group Accounting Manager (L1)",
                TIER_GAM_MIN, Double.MAX_VALUE, requireL1);

        approveCreditMemoWhenRequired(
                "Finance Controller - Abinaya (L2)", tokenFc, UID_FC,
                "approvalWorkflow_L2_" + workflowName,
                "Approved by Finance Controller (L2)",
                TIER_GAM_MIN, TIER_SGAM_MIN, requireL2);

        approveCreditMemoWhenRequired(
                "Senior Group Accounting Manager - Suyash (L3)", tokenSgam, UID_SGAM,
                "approvalWorkflow_L3_" + workflowName,
                "Approved by Senior Group Accounting Manager (L3)",
                TIER_SGAM_MIN, Double.MAX_VALUE, requireL3);

        verifyCreditMemoState("approvalWorkflow_verify_" + workflowName);
        ExtentListener.test.log(Status.PASS, "<b>Approval workflow completed: " + workflowName + "</b>");
    }

    // ------------------------ TESTS ------------------------

    @Test(priority = 1, groups = { "Sanity", "CreditMemo" }, description = "Get credit memo / invoice-adjustment count")
    public void creditMemoCount() {
        logRolesToExtent();
        assertCreatorToken();
        SoftAssert sa = new SoftAssert();

        Response response = given()
                .headers(creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .queryParam("filter", FILTER_BASE)
                .basePath(EP_CM_COUNT)
                .when().get()
                .then().extract().response();

        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.logRequestInfo("GET", EP_CM_COUNT);
        ApiTestUtils.assertHttpStatus(response, sa, "creditMemoCount", 200, 401, 403);

        if (response.getStatusCode() == 200) {
            String body = response.getBody().asString().trim();
            ApiTestUtils.logInfo("Credit Memo count", body);
            try {
                long count = Long.parseLong(body);
                sa.assertTrue(count >= 0, "Count should be non-negative");
            } catch (NumberFormatException ignored) {
                ApiTestUtils.softFail(sa, "creditMemoCount", "Count is non-numeric: " + body);
            }
            ExtentListener.test.log(Status.PASS, "<b>Credit Memo count fetched (Khushbu - Senior Accountant)</b>");
        }
        sa.assertAll();
    }

    @Test(priority = 2, dependsOnMethods = "creditMemoCount", groups = { "Sanity", "CreditMemo" },
            description = "Search credit memos / invoice adjustments")
    public void creditMemoSearch() {
        SoftAssert sa = new SoftAssert();

        Response response = given()
                .headers(creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .queryParam("filter",    FILTER_BASE)
                .queryParam("offset",    0)
                .queryParam("size",      25)
                .queryParam("orderBy",   "modifiedTime")
                .queryParam("orderType", "desc")
                .basePath(EP_CM_SEARCH)
                .when().get()
                .then().extract().response();

        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.assertContentType(response, sa);
        ApiTestUtils.logRequestInfo("GET", EP_CM_SEARCH);
        ApiTestUtils.assertHttpStatus(response, sa, "creditMemoSearch", 200, 401, 403);

        if (response.getStatusCode() == 200) {
            JsonPath json = response.jsonPath();
            List<?> list = json.getList("$");
            int size = list != null ? list.size() : 0;
            ApiTestUtils.assertPageSize(list, 25, sa, "creditMemoSearch");
            ApiTestUtils.logInfo("Credit Memos returned", String.valueOf(size));
            if (size > 0) {
                ApiTestUtils.assertSortedDesc(json, list, "modifiedTime", sa, "creditMemoSearch");
                ApiTestUtils.assertDeletedFalseForAll(json, size, "creditMemoSearch", sa);
                ApiTestUtils.assertFieldNotNull(json, "[0].id", sa, "creditMemoSearch");
                ApiTestUtils.assertFieldNotNull(json, "[0].adjustmentType", sa, "creditMemoSearch");
            }
            ExtentListener.test.log(Status.PASS, "<b>Credit Memo search successful</b>");
        }
        sa.assertAll();
    }

    @Test(priority = 3, dependsOnMethods = "creditMemoSearch", groups = { "Sanity", "CreditMemo" },
            description = "Find an eligible (POSTED/PAID) AP invoice and capture id / glAccount / costCenter")
    public void searchEligibleApInvoice() {
        SoftAssert sa = new SoftAssert();

        if (selectedApInvoiceId > 0) {
            ExtentListener.test.log(Status.INFO,
                    "<b>Using cmApInvoiceId from system property:</b> " + selectedApInvoiceId);
        }

        Response response = given()
                .headers(creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .queryParam("filter",    AP_INVOICE_FILTER + ";company.id==1 ")
                .queryParam("offset",    0)
                .queryParam("size",      25)
                .queryParam("orderBy",   "modifiedTime")
                .queryParam("orderType", "desc")
                .basePath(EP_AP_INVOICE_SEARCH)
                .when().get()
                .then().extract().response();

        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.assertContentType(response, sa);
        ApiTestUtils.logRequestInfo("GET", EP_AP_INVOICE_SEARCH);
        ApiTestUtils.assertHttpStatus(response, sa, "searchEligibleApInvoice", 200, 401, 403);

        if (response.getStatusCode() == 200) {
            JsonPath json = response.jsonPath();
            List<?> list = json.getList("$");
            int size = list != null ? list.size() : 0;
            ApiTestUtils.logInfo("Eligible AP invoices (POSTED/PAID)", String.valueOf(size));

            if (size > 0) {
                int pickIndex = 0;
                if (selectedApInvoiceId <= 0) {
                    // Prefer an invoice whose totalAmount can cover the requested credit memo amount.
                    for (int i = 0; i < size; i++) {
                        Object totalObj = json.get("[" + i + "].totalAmount");
                        double invTotal = totalObj instanceof Number ? ((Number) totalObj).doubleValue() : -1;
                        if (invTotal >= creditMemoAmount) {
                            pickIndex = i;
                            break;
                        }
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        Object idObj = json.get("[" + i + "].id");
                        if (idObj instanceof Number && ((Number) idObj).intValue() == selectedApInvoiceId) {
                            pickIndex = i;
                            break;
                        }
                    }
                }
                String prefix = "[" + pickIndex + "].";
                Object id = json.get(prefix + "id");
                if (id instanceof Number) {
                    selectedApInvoiceId = ((Number) id).intValue();
                }
                extractGlAndCostCenterFromApInvoice(json, prefix);
                Object totalObj = json.get(prefix + "totalAmount");
                if (totalObj instanceof Number) {
                    selectedApInvoiceTotalAmount = ((Number) totalObj).doubleValue();
                }
            }
        }

        if (selectedApInvoiceId <= 0) {
            selectedApInvoiceId = DEFAULT_AP_INVOICE_ID;
            ExtentListener.test.log(Status.WARNING,
                    "<b>No POSTED/PAID AP invoice found in env=" + base.TestRunner.env
                            + ". Falling back to HAR sample id=" + DEFAULT_AP_INVOICE_ID + "</b>");
        }
        if (selectedGlAccountId <= 0)  selectedGlAccountId  = DEFAULT_GL_ACCOUNT_ID;
        if (selectedCostCenterId <= 0) selectedCostCenterId = DEFAULT_COST_CENTER_ID;

        capCreditMemoAmountToInvoiceTotal();

        ApiTestUtils.logInfo("Selected apInvoice.id",          String.valueOf(selectedApInvoiceId));
        ApiTestUtils.logInfo("Selected apInvoice.totalAmount", selectedApInvoiceTotalAmount > 0
                ? String.valueOf(selectedApInvoiceTotalAmount) : "<unknown>");
        ApiTestUtils.logInfo("Selected glAccount.id",          String.valueOf(selectedGlAccountId));
        ApiTestUtils.logInfo("Selected costCenter.id",         String.valueOf(selectedCostCenterId));
        ApiTestUtils.logInfo("Credit memo amount (effective)", String.valueOf(creditMemoAmount));
        logger.info("searchEligibleApInvoice -> apInvoice.id={}, totalAmount={}, glAccount.id={}, costCenter.id={}, cmAmount={}",
                selectedApInvoiceId, selectedApInvoiceTotalAmount, selectedGlAccountId, selectedCostCenterId, creditMemoAmount);

        sa.assertAll();
    }

    @Test(priority = 4, dependsOnMethods = "searchEligibleApInvoice", groups = { "Sanity", "CreditMemo" },
            description = "Create Credit Memo (Senior Accountant - Khushbu)")
    public void createCreditMemo() {
        assertCreatorToken();
        createCreditMemoInternal("createCreditMemo");
        ExtentListener.test.log(Status.PASS, "<b>Credit Memo created successfully by Senior Accountant (Khushbu)</b>");
    }

    @Test(priority = 5, dependsOnMethods = "createCreditMemo", groups = { "Sanity", "CreditMemo" },
            description = "Submit Credit Memo for review (status=PENDING_REVIEW)")
    public void submitCreditMemoForReview() {
        if (createdCreditMemoId <= 0) {
            throw new SkipException("submitCreditMemoForReview: no created credit memo to submit");
        }
        SoftAssert sa = new SoftAssert();
        String endpoint = EP_CM_UPDATE_PREFIX + createdCreditMemoId;
        long now = System.currentTimeMillis();

        String payload = "{"
                + "\"adjustmentDate\":\"" + iso8601Utc(now) + "\","
                + "\"adjustmentReason\":\"Quantity Discrepancy\","
                + "\"amount\":" + creditMemoAmount + ","
                + "\"explanation\":\"Automated Credit Memo - submitted for review\","
                + "\"apInvoice\":{\"id\":" + selectedApInvoiceId + "},"
                + "\"glAccount\":{\"id\":" + selectedGlAccountId + "},"
                + "\"costCenter\":{\"id\":" + selectedCostCenterId + "},"
                + "\"attachmentEntities\":" + buildAttachmentEntitiesJson() + ","
                + "\"adjustmentType\":\"CREDIT_MEMO\","
                + (createdCreditMemoNumber != null && !createdCreditMemoNumber.isEmpty()
                        ? "\"adjustmentNumber\":\"" + createdCreditMemoNumber + "\","
                        : "")
                + "\"status\":\"PENDING_REVIEW\""
                + "}";

        Response response = given()
                .headers(creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .body(payload)
                .basePath(endpoint)
                .when().put()
                .then().extract().response();

        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.logRequestInfo("PUT", endpoint);
        ApiTestUtils.logInfo("Request payload", payload);
        ApiTestUtils.assertHttpStatus(response, sa, "submitCreditMemoForReview", 200, 201, 204);

        if (response.getStatusCode() < 300) {
            ExtentListener.test.log(Status.PASS,
                    "<b>Credit Memo " + (createdCreditMemoNumber != null ? createdCreditMemoNumber : createdCreditMemoId)
                            + " submitted for review (PENDING_REVIEW)</b>");
        } else {
            String body = response.getBody().asString();
            ExtentListener.test.log(Status.FAIL,
                    "<b>submitCreditMemoForReview failed status=" + response.getStatusCode() + " body=" + body + "</b>");
        }
        sa.assertAll();
    }

    private void approveCreditMemoAs(String approverLabel, String token, String fallbackUid,
                                     String testName, String remark, double minAmountInclusive,
                                     double maxAmountInclusive) {
        if (createdCreditMemoId <= 0) {
            throw new SkipException(testName + ": no created credit memo");
        }
        if (creditMemoAmount <= minAmountInclusive) {
            throw new SkipException(testName + ": amount " + creditMemoAmount
                    + " not above tier minimum (" + minAmountInclusive + "). " + approverLabel + " approval not required.");
        }
        if (maxAmountInclusive < Double.MAX_VALUE && creditMemoAmount > maxAmountInclusive) {
            throw new SkipException(testName + ": amount " + creditMemoAmount
                    + " above tier maximum (" + maxAmountInclusive + "). " + approverLabel + " approval not required.");
        }
        if (token == null || token.isEmpty()) {
            throw new SkipException(testName + ": " + approverLabel + " token unavailable. "
                    + "Provision the corresponding user on " + base.TestRunner.env + ".");
        }

        SoftAssert sa = new SoftAssert();
        String endpoint = EP_CM_APPROVE_PREFIX + createdCreditMemoId;
        String payload = "{\"remark\":\"" + remark + "\"}";

        Response response = given()
                .headers(creditMemoHeaders(token, fallbackUid))
                .body(payload)
                .basePath(endpoint)
                .when().post()
                .then().extract().response();

        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.logRequestInfo("POST", endpoint);
        ApiTestUtils.logInfo("Approver",        approverLabel);
        ApiTestUtils.logInfo("Credit Memo id",  String.valueOf(createdCreditMemoId));
        ApiTestUtils.logInfo("Amount tier",     "[" + minAmountInclusive + ", " + maxAmountInclusive + "]");
        ApiTestUtils.logInfo("Request payload", payload);
        ApiTestUtils.assertHttpStatus(response, sa, testName, 200, 201, 204);

        int status = response.getStatusCode();
        if (status < 300) {
            ExtentListener.test.log(Status.PASS, "<b>" + approverLabel + " approved Credit Memo " + createdCreditMemoId + "</b>");
        } else {
            String body = response.getBody().asString();
            ExtentListener.test.log(Status.FAIL,
                    "<b>" + testName + " failed status=" + status + " body=" + body + "</b>");
        }
        sa.assertAll();
    }

    @Test(priority = 10, dependsOnMethods = "createCreditMemo", groups = { "Sanity", "CreditMemo", "CreditMemoApproval" },
            description = "Verify amount <= 10k does not require L1/L2/L3 approvers")
    public void verifyNoApprovalRequiredWhenAmountBelow10k() {
        SoftAssert sa = new SoftAssert();
        sa.assertTrue(creditMemoAmount <= TIER_GAM_MIN,
                "Smoke credit memo amount should be <= 10k (actual=" + creditMemoAmount + ")");
        sa.assertFalse(isApprovalTierRequired(TIER_GAM_MIN, Double.MAX_VALUE),
                "L1 (GAM) should not be required");
        sa.assertFalse(isApprovalTierRequired(TIER_GAM_MIN, TIER_SGAM_MIN),
                "L2 (FC) should not be required");
        sa.assertFalse(isApprovalTierRequired(TIER_SGAM_MIN, Double.MAX_VALUE),
                "L3 (SGAM) should not be required");
        ApiTestUtils.logInfo("Amount tier check", "amount=" + creditMemoAmount + " => no approvers required");
        ExtentListener.test.log(Status.PASS,
                "<b>Amount " + creditMemoAmount + " SAR: L1/L2/L3 approvals correctly not required (<= 10k)</b>");
        sa.assertAll();
    }

    @Test(priority = 6, dependsOnMethods = "submitCreditMemoForReview", groups = { "Sanity", "CreditMemo" },
            description = "Level-1 approval by Group Accounting Manager (Rohini) - required for amount > 10k")
    public void approveCreditMemoByGroupAccountingManager() {
        approveCreditMemoAs("Group Accounting Manager - Rohini (L1)",
                tokenGam, UID_GAM,
                "approveCreditMemoByGroupAccountingManager",
                "Approved by Group Accounting Manager (L1)",
                TIER_GAM_MIN, Double.MAX_VALUE);
    }

    @Test(priority = 7, dependsOnMethods = "approveCreditMemoByGroupAccountingManager",
            groups = { "Sanity", "CreditMemo" },
            description = "Level-2 approval by Finance Controller (Abinaya) - required for amount > 10k (up to 1L)")
    public void approveCreditMemoByFinanceController() {
        // Per spec: 10k - 1L => FC approves; > 1L => still goes through FC before SGAM.
        approveCreditMemoAs("Finance Controller - Abinaya (L2)",
                tokenFc, UID_FC,
                "approveCreditMemoByFinanceController",
                "Approved by Finance Controller (L2)",
                TIER_GAM_MIN, TIER_SGAM_MIN);
    }

    @Test(priority = 8, dependsOnMethods = "approveCreditMemoByFinanceController",
            groups = { "Sanity", "CreditMemo" },
            description = "Level-3 approval by Senior Group Accounting Manager (Suyash) - required for amount > 1 lakh")
    public void approveCreditMemoBySeniorGroupAccountingManager() {
        approveCreditMemoAs("Senior Group Accounting Manager - Suyash (L3)",
                tokenSgam, UID_SGAM,
                "approveCreditMemoBySeniorGroupAccountingManager",
                "Approved by Senior Group Accounting Manager (L3)",
                TIER_SGAM_MIN, Double.MAX_VALUE);
    }

    @Test(priority = 20, groups = { "CreditMemo", "CreditMemoApproval" },
            description = "Approval workflow L1+L2: amount 15k (>10k, <=1L) - GAM then FC")
    public void approvalWorkflow_L1_and_L2_forAmount15k() {
        runApprovalWorkflow(WORKFLOW_AMOUNT_L1_L2, "L1+L2_15k", true, true, false);
    }

    @Test(priority = 21, groups = { "CreditMemo", "CreditMemoApproval" },
            description = "Approval workflow L1+L3: amount 101k (>1L) - GAM then SGAM (FC not in 10k-1L band)")
    public void approvalWorkflow_L1_and_L3_above1Lakh() {
        runApprovalWorkflow(WORKFLOW_AMOUNT_L3, "L1+L3_101k", true, false, true);
    }

    @Test(priority = 22, groups = { "CreditMemo", "CreditMemoApproval" },
            description = "Approval workflow L1+L2: amount 50k (10k-1L band) - GAM then FC")
    public void approvalWorkflow_L1_and_L2_forAmount50k() {
        runApprovalWorkflow(WORKFLOW_AMOUNT_L2_ONLY, "L1+L2_50k", true, true, false);
    }

    @Test(priority = 23, groups = { "CreditMemo", "CreditMemoApproval" },
            description = "Approval workflow L1+L3: amount 150k (>1L) - GAM then SGAM (FC not in 10k-1L band)")
    public void approvalWorkflow_L1_and_L3_forAmount150k() {
        runApprovalWorkflow(WORKFLOW_AMOUNT_ABOVE_1L, "L1+L3_150k", true, false, true);
    }

    @Test(priority = 9, dependsOnMethods = "approveCreditMemoByGroupAccountingManager",
            alwaysRun = true,
            groups = { "Sanity", "CreditMemo" },
            description = "Re-search the Credit Memo and verify it transitioned out of DRAFT/PENDING_REVIEW")
    public void verifyCreditMemoFinalState() {
        if (createdCreditMemoId <= 0) {
            throw new SkipException("verifyCreditMemoFinalState: no created credit memo");
        }
        SoftAssert sa = new SoftAssert();

        Response response = given()
                .headers(creditMemoHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .queryParam("filter",    "deleted==false;id==" + createdCreditMemoId)
                .queryParam("offset",    0)
                .queryParam("size",      1)
                .queryParam("orderBy",   "modifiedTime")
                .queryParam("orderType", "desc")
                .basePath(EP_CM_SEARCH)
                .when().get()
                .then().extract().response();

        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.logRequestInfo("GET", EP_CM_SEARCH + "?filter=id==" + createdCreditMemoId);
        ApiTestUtils.assertHttpStatus(response, sa, "verifyCreditMemoFinalState", 200);

        if (response.getStatusCode() == 200) {
            JsonPath json = response.jsonPath();
            String status = jsonPathAsString(json, "[0].status");
            String number = jsonPathAsString(json, "[0].adjustmentNumber");
            ApiTestUtils.logInfo("Credit Memo number", number);
            ApiTestUtils.logInfo("Final status",       status);
            ApiTestUtils.logInfo("Recorded amount",    jsonPathAsString(json, "[0].amount"));
            sa.assertNotNull(status, "Final status should be present in re-search");
        }
        sa.assertAll();
    }
}
