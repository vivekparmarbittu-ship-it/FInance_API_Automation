package financeSuite;

import static io.restassured.RestAssured.given;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.Assert;
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
 * AP Non-PO Invoice Security & Negative Workflow test suite.
 * Uses a multi-invoice design to isolate security checks:
 *   - Invoice A (Vulnerability Check Invoice): Used to test premature post-ap-invoice approvals
 *     which return 200 OK due to backend bug (exposed in reports).
 *   - Invoice B (Clean Workflow Invoice): Kept completely clean of premature approvals.
 *     Verifies strict role/user security boundaries on task patching (rejections are 422/403/500)
 *     and traces the full valid approval path (L1 -> L2 -> L3) to POSTED status.
 */
@Listeners(ExtentListener.class)
public class FinanceInvoiceSecurityAPI extends BaseTestDemoNetSingularity {

    private static final Logger logger = LogManager.getLogger(FinanceInvoiceSecurityAPI.class);

    private static final String EP_AP_INVOICE          = "/apim/financial-accounting/1.0/rest/ap-invoices";
    private static final String EP_CHECK_DUPLICATE     = "/apim/financial-accounting/1.0/rest/ap-invoices/check-duplicate-invoice";
    private static final String EP_POST_AP_INVOICE     = "/apim/financial-accounting/1.0/rest/ap-invoices/post-ap-invoice";
    private static final String EP_AP_INVOICE_SEARCH   = "/apim/financial-accounting/1.0/rest/ap-invoices/search";
    private static final String EP_UPLOAD_FILE         = "/apim/fb/1.0/rest/resource-attachments/upload-file";
    private static final String EP_UPDATE_MAPPINGS     = "/apim/fb/1.0/rest/resource-attachments/update-mappings";

    private static final String ATTACHMENT_RESOURCE = "vendor-debit-note.pdf";

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

    private static final int DEFAULT_GL_ACCOUNT_ID   = 360;
    private static final int DEFAULT_COST_CENTER_ID  = 123249;
    private static final int DEFAULT_VENDOR_ID       = 15324;
    private static final int DEFAULT_TAX_ID          = 33;

    // Fallback userIds
    private static final String UID_ACCOUNTANT         = "140468"; // Vivek Parmar
    private static final String UID_SENIOR_ACCOUNTANT  = "143080"; // Khushbu Patel (L1)
    private static final String UID_FC                 = "143084"; // Abinaya Saravanan (L2)
    private static final String UID_SGAM               = "143082"; // Suyash Tiwari (L3)

    private String tokenAccountant;
    private String tokenSeniorAccountant;
    private String tokenFinanceController;
    private String tokenSeniorGroupAccountingManager;

    private double invoiceAmount;
    private int    uploadedAttachmentId = -1;
    private String uploadedAttachmentUuid;
    private String uploadedAttachmentFileName;

    // Invoices
    private int securityInvoiceId = -1;
    private int happyInvoiceId = -1;

    // Tasks for Happy Invoice B
    private WorkflowTask happyTaskL1;
    private WorkflowTask happyTaskL2;
    private WorkflowTask happyTaskL3;

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

        String rawAccountant = TokenHumainOS.getTokenAccountant();
        if (rawAccountant != null) tokenAccountant = "Bearer " + rawAccountant;
        String rawSeniorAccountant = TokenHumainOS.getTokenSeniorAccountant();
        if (rawSeniorAccountant != null) tokenSeniorAccountant = "Bearer " + rawSeniorAccountant;
        String rawFc = TokenHumainOS.getTokenFinanceController();
        if (rawFc != null) tokenFinanceController = "Bearer " + rawFc;
        String rawSgam = TokenHumainOS.getTokenSeniorGroupAccountingManager();
        if (rawSgam != null) tokenSeniorGroupAccountingManager = "Bearer " + rawSgam;

        // Force a large amount to ensure L1 -> L2 -> L3 is triggered
        invoiceAmount = parseDoubleProp("apAmount", 120000d);
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

    private Map<String, String> invoiceHeaders(String authToken, String fallbackUid) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json, text/plain, */*");
        h.put("Content-Type", "application/json");
        h.put("accept-language", "en-US,en;q=0.9");
        h.put("audience", "apim");
        h.put("client-code", "8892df07-6d62-4ec4-9596-8f48574908ff");
        h.put("customerid", "1");
        h.put("x-module", "CORE");
        if (authToken != null && !authToken.isEmpty()) {
            h.put("Authorization", authToken);
            h.put("userid", userIdForToken(authToken, fallbackUid));
        } else if (fallbackUid != null) {
            h.put("userid", fallbackUid);
        }
        return h;
    }

    private byte[] resolveAttachmentFile(StringBuilder sourceLabel) {
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream(ATTACHMENT_RESOURCE)) {
            if (in != null) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                byte[] data = out.toByteArray();
                sourceLabel.append("classpath: ").append(ATTACHMENT_RESOURCE).append(" (").append(data.length).append(" bytes)");
                return data;
            }
        } catch (java.io.IOException e) {
            logger.warn("Failed reading classpath resource {}: {}", ATTACHMENT_RESOURCE, e.getMessage());
        }
        sourceLabel.append("inline minimal PDF (").append(FALLBACK_PDF_BYTES.length).append(" bytes)");
        return FALLBACK_PDF_BYTES;
    }

    private static String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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

    private int createAndSubmitInvoice(String invNum) {
        long now = System.currentTimeMillis();
        long due = now + 86400000L * 7;
        String payload = "{"
                + "\"invoiceNumber\":\"" + invNum + "\","
                + "\"remarks\":\"Security AP Invoice Workflow\","
                + "\"company\":{\"id\":1},"
                + "\"currency\":{\"id\":84},"
                + "\"invoiceDate\":" + now + ","
                + "\"dueDate\":" + due + ","
                + "\"costCenterMaster\":{\"id\":" + DEFAULT_COST_CENTER_ID + "},"
                + "\"vendorMaster\":{\"id\":" + DEFAULT_VENDOR_ID + "},"
                + "\"apInvoiceLines\":["
                + "  {"
                + "    \"chartOfAccounts\":{\"id\":" + DEFAULT_GL_ACCOUNT_ID + "},"
                + "    \"tax\":{\"id\":" + DEFAULT_TAX_ID + "},"
                + "    \"amount\":" + invoiceAmount + ","
                + "    \"totalAmount\":" + invoiceAmount + ","
                + "    \"lineDescription\":\"GOSI expense - Employer contribution(EXPENSES)-52114002\""
                + "  }"
                + "],"
                + "\"docType\":\"Vendor Standard Invoice\","
                + "\"paidAmount\":0,"
                + "\"status\":\"DRAFT\","
                + "\"totalAmount\":" + invoiceAmount + ","
                + "\"invoicedAmount\":" + invoiceAmount + ","
                + "\"attachmentEntities\":["
                + "  {"
                + "    \"attachmentId\":" + uploadedAttachmentId + ","
                + "    \"entityType\":\"AP_INVOICE\","
                + "    \"fileName\":\"" + escapeJsonString(uploadedAttachmentFileName) + "\""
                + "  }"
                + "],"
                + "\"isPoInvoice\":false,"
                + "\"vendorAdvanceId\":null,"
                + "\"advanceAmountToApply\":0"
                + "}";

        Response response = given()
                .headers(invoiceHeaders(tokenAccountant, UID_ACCOUNTANT))
                .body(payload)
                .basePath(EP_AP_INVOICE)
                .when().post();

        Assert.assertEquals(response.getStatusCode(), 200, "Failed to create draft invoice: " + response.getBody().asString());
        int invId = response.jsonPath().getInt("id");

        if (uploadedAttachmentUuid != null) {
            String mappingPayload = "[{\"documentUUID\":[\"" + uploadedAttachmentUuid + "\"],\"resourceId\":" + invId + "}]";
            given()
                .headers(invoiceHeaders(tokenAccountant, UID_ACCOUNTANT))
                .body(mappingPayload)
                .basePath(EP_UPDATE_MAPPINGS)
                .when().patch();
        }

        String dupPayload = "{"
                + "\"vendorId\":" + DEFAULT_VENDOR_ID + ","
                + "\"invoiceNumber\":\"" + invNum + "\","
                + "\"invoicedAmount\":" + invoiceAmount + ","
                + "\"invoiceDate\":" + System.currentTimeMillis() + ","
                + "\"excludeId\":" + invId
                + "}";
        given()
                .headers(invoiceHeaders(tokenAccountant, UID_ACCOUNTANT))
                .body(dupPayload)
                .basePath(EP_CHECK_DUPLICATE)
                .when().post();

        String submitPayload = "{"
                + "\"invoiceNumber\":\"" + invNum + "\","
                + "\"remarks\":\"Submitted for workflow\","
                + "\"company\":{\"id\":1},"
                + "\"currency\":{\"id\":84},"
                + "\"invoiceDate\":" + now + ","
                + "\"dueDate\":" + due + ","
                + "\"costCenterMaster\":{\"id\":" + DEFAULT_COST_CENTER_ID + "},"
                + "\"vendorMaster\":{\"id\":" + DEFAULT_VENDOR_ID + "},"
                + "\"apInvoiceLines\":["
                + "  {"
                + "    \"chartOfAccounts\":{\"id\":" + DEFAULT_GL_ACCOUNT_ID + "},"
                + "    \"tax\":{\"id\":" + DEFAULT_TAX_ID + "},"
                + "    \"amount\":" + invoiceAmount + ","
                + "    \"totalAmount\":" + invoiceAmount + ","
                + "    \"lineDescription\":\"GOSI expense - Employer contribution(EXPENSES)-52114002\","
                + "    \"id\":null"
                + "  }"
                + "],"
                + "\"docType\":\"Vendor Standard Invoice\","
                + "\"paidAmount\":0,"
                + "\"status\":\"PENDING_APPROVAL\","
                + "\"totalAmount\":" + invoiceAmount + ","
                + "\"invoicedAmount\":" + invoiceAmount + ","
                + "\"id\":" + invId + ","
                + "\"attachmentEntities\":["
                + "  {"
                + "    \"attachmentId\":" + uploadedAttachmentId + ","
                + "    \"entityType\":\"AP_INVOICE\","
                + "    \"fileName\":\"" + escapeJsonString(uploadedAttachmentFileName) + "\""
                + "  }"
                + "],"
                + "\"isPoInvoice\":false,"
                + "\"vendorAdvanceId\":null,"
                + "\"advanceAmountToApply\":0"
                + "}";

        Response submitResp = given()
                .headers(invoiceHeaders(tokenAccountant, UID_ACCOUNTANT))
                .body(submitPayload)
                .basePath(EP_AP_INVOICE + "/" + invId)
                .when().put();

        Assert.assertTrue(submitResp.getStatusCode() == 200 || submitResp.getStatusCode() == 204, "Failed to submit invoice");

        if (uploadedAttachmentUuid != null) {
            String mappingPayload = "[{\"documentUUID\":[\"" + uploadedAttachmentUuid + "\"],\"resourceId\":" + invId + "}]";
            given()
                .headers(invoiceHeaders(tokenAccountant, UID_ACCOUNTANT))
                .body(mappingPayload)
                .basePath(EP_UPDATE_MAPPINGS)
                .when().patch();
        }

        return invId;
    }

    private static class WorkflowTask {
        String processInstanceId;
        String taskDefId;
        String actionId;
    }

    private WorkflowTask pollForWorkflowTask(int targetInvoiceId, String authHeadersToken, String fallbackUid, String stageDescription) {
        int maxRetries = 20;
        long retryDelayMs = 3000;
        String filter = "id=ge=0;deleted==false;id==" + targetInvoiceId;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Response response = given()
                    .headers(invoiceHeaders(authHeadersToken, fallbackUid))
                    .queryParam("filter", filter)
                    .queryParam("offset", 0)
                    .queryParam("size", 10)
                    .queryParam("orderBy", "modifiedTime")
                    .queryParam("orderType", "desc")
                    .basePath(EP_AP_INVOICE_SEARCH)
                    .when().get();

            if (response.getStatusCode() == 200) {
                JsonPath json = response.jsonPath();
                List<?> list = json.getList("$");
                if (list != null && !list.isEmpty()) {
                    String wfStage = json.getString("[0].workflowStage");
                    String invoiceStatus = json.getString("[0].status");
                    List<?> actions = json.getList("[0].actions");
                    logger.info("[Poll {}/{}] invoiceId={} | stage={} [{}] | actions={} | wfStage={}, status={}",
                            attempt, maxRetries, targetInvoiceId, stageDescription, fallbackUid,
                            (actions != null ? actions.size() : 0),
                            wfStage, invoiceStatus);
                    if (actions != null && !actions.isEmpty()) {
                        WorkflowTask t = new WorkflowTask();
                        t.processInstanceId = json.getString("[0].processInstanceId");
                        t.actionId = json.getString("[0].actions[0].id");
                        t.taskDefId = json.getString("[0].actions[0].taskDefinitionId");
                        if (t.taskDefId == null) {
                            t.taskDefId = json.getString("[0].actions[0].taskDefId");
                        }
                        if (t.processInstanceId != null && t.actionId != null && t.taskDefId != null) {
                            logger.info("Workflow task discovered for {} on attempt {}: actionId={}, taskDefId={}",
                                    stageDescription, attempt, t.actionId, t.taskDefId);
                            return t;
                        }
                    }
                }
            } else {
                logger.warn("[Poll {}/{}] invoiceId={} | {} search returned HTTP {}", attempt, maxRetries, targetInvoiceId, stageDescription, response.getStatusCode());
            }

            try {
                Thread.sleep(retryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    private void assertPostApiRejected(int targetInvoiceId, String token, String uid, String stageLabel) {
        SoftAssert sa = new SoftAssert();
        String postApprovalBody = "{\"remark\":\"Unauthorized security validation check by " + stageLabel + "\"}";
        Response postResp = given()
                .headers(invoiceHeaders(token, uid))
                .queryParam("id", targetInvoiceId)
                .queryParam("status", "APPROVED")
                .body(postApprovalBody)
                .basePath(EP_POST_AP_INVOICE)
                .when().post();

        int postStatus = postResp.getStatusCode();
        logger.info("unauthorized post-ap-invoice by {} on invoice {} returned status {}", stageLabel, targetInvoiceId, postStatus);
        
        boolean isError = (postStatus >= 400);
        if (!isError) {
            sa.fail("Security Violation: Premature/unauthorized post-ap-invoice call by " + stageLabel + " was accepted with HTTP 200 instead of error (>=400)");
        } else {
            ExtentListener.test.log(Status.PASS, "post-ap-invoice attempt by " + stageLabel + " was correctly rejected with HTTP " + postStatus);
        }
        sa.assertAll();
    }

    private void assertTaskPatchRejected(int targetInvoiceId, String token, String uid, String stageLabel, WorkflowTask currentTask) {
        SoftAssert sa = new SoftAssert();
        if (currentTask != null && currentTask.actionId != null) {
            String patchTaskUrl = "/apim/financial-accounting/1.0/rest/workflow-actions/" + currentTask.actionId + "/task";
            String patchTaskBody = "{\"review\":\"APPROVED\"}";

            Response patchResp = given()
                    .headers(invoiceHeaders(token, uid))
                    .queryParam("processInstanceId", currentTask.processInstanceId)
                    .queryParam("taskDefId", currentTask.taskDefId)
                    .body(patchTaskBody)
                    .basePath(patchTaskUrl)
                    .when().patch();

            int patchStatus = patchResp.getStatusCode();
            logger.info("unauthorized patch task by {} on invoice {} returned status {}", stageLabel, targetInvoiceId, patchStatus);
            boolean isPatchError = (patchStatus >= 400);
            if (!isPatchError) {
                sa.fail("Security Violation: Unauthorized patch task by " + stageLabel + " on invoice " + targetInvoiceId + " returned HTTP " + patchStatus + " instead of error (>=400)");
            } else {
                ExtentListener.test.log(Status.PASS, "patch-task attempt by " + stageLabel + " on invoice " + targetInvoiceId + " was correctly rejected with HTTP " + patchStatus);
            }
        } else {
            sa.fail("Cannot verify patch task: current task is null for " + stageLabel);
        }
        sa.assertAll();
    }

    // =========================================================================
    // STEP 1: PREPARATION & FILE UPLOAD
    // =========================================================================


    @Test(priority = 1, groups = { "CRUDSanity" }, description = "Upload PDF attachment for AP Invoices")
    public void uploadAttachment() {
        if (tokenAccountant == null || tokenAccountant.isEmpty()) {
            throw new SkipException("Accountant token not available");
        }

        StringBuilder fileSource = new StringBuilder();
        byte[] pdfBytes = resolveAttachmentFile(fileSource);
        String fileName = "invoice_doc_sec_" + System.currentTimeMillis() + ".pdf";
        uploadedAttachmentFileName = fileName;

        logger.info("uploadAttachment fileSource={}", fileSource);
        ApiTestUtils.logInfo("Attachment file source", fileSource.toString());

        String uploadFileWrapper = "{"
                + "\"moduleName\":\"FINANCIAL-ACCOUNTING_APP_NAME\","
                + "\"workGroupNames\":[],"
                + "\"isVersioning\":false,"
                + "\"resourceFileAttach\":{"
                +     "\"keyLabel\":\"attachmentEntities\","
                +     "\"filename\":\"" + escapeJsonString(fileName) + "\","
                +     "\"resourceName\":\"ApInvoice\""
                + "},"
                + "\"parentFolderType\":\"APPLICATION\","
                + "\"parentFolderValue\":\"FINANCIAL-ACCOUNTING_APP_NAME\","
                + "\"childFolderType\":\"ENTITY\","
                + "\"childFolderValue\":\"ApInvoice\","
                + "\"serviceUrlKey\":\"SM_DOCUMENT_HTTP_URL\","
                + "\"isParent\":false,"
                + "\"metaInformation\":\"{}\""
                + "}";

        Map<String, String> uploadHeaders = invoiceHeaders(tokenAccountant, UID_ACCOUNTANT);
        uploadHeaders.remove("Content-Type");

        Response response = given()
                .headers(uploadHeaders)
                .multiPart("filedata", fileName, pdfBytes, "application/pdf")
                .multiPart("uploadFileWrapper", uploadFileWrapper, "application/json")
                .basePath(EP_UPLOAD_FILE)
                .when().post()
                .then().extract().response();

        int status = response.getStatusCode();
        String body = response.getBody().asString();
        logger.info("uploadAttachment status={} body={}", status, body.length() > 400 ? body.substring(0, 400) + "..." : body);

        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.assertHttpStatus(response, new SoftAssert(), "uploadAttachment", 200, 201);

        JsonPath j = response.jsonPath();
        Integer attId  = extractInt(j, "documentId", "[0].documentId", "id", "[0].id", "data.documentId");
        String  attUid = extractStr(j, "documentUUID", "[0].documentUUID", "data.documentUUID", "uuid", "[0].uuid");

        if (attId == null || attId <= 0) {
            throw new SkipException("Failed to extract documentId from upload response: " + body);
        }

        uploadedAttachmentId = attId;
        uploadedAttachmentUuid = attUid;

        ApiTestUtils.logInfo("Uploaded attachment ID", String.valueOf(uploadedAttachmentId));
    }

    // =========================================================================
    // INVOICE A: SECURITY VULNERABILITY CHECK INVOICE
    // =========================================================================

    @Test(priority = 2, dependsOnMethods = {"uploadAttachment"}, groups = { "CRUDSanity" },
            description = "Invoice A: Create and submit the Vulnerability Check Invoice")
    public void createAndSubmitSecurityInvoice() {
        int randNum = 10000 + new java.util.Random().nextInt(90000);
        String invNum = "TSEC-2026-" + randNum;
        securityInvoiceId = createAndSubmitInvoice(invNum);
        logger.info("Security Invoice A created and submitted successfully: id={}", securityInvoiceId);
        ApiTestUtils.logInfo("Security Invoice A ID", String.valueOf(securityInvoiceId));
    }

    @Test(priority = 3, dependsOnMethods = {"createAndSubmitSecurityInvoice"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Security: Verify if premature L2/L3 approval calls on Invoice A are rejected (exposes backend security bug)")
    public void checkPrematureApprovalsOnSecurityInvoice() {
        SoftAssert sa = new SoftAssert();

        // 1. L2 tries to call post-ap-invoice prematurely
        Response l2Resp = given()
                .headers(invoiceHeaders(tokenFinanceController, UID_FC))
                .queryParam("id", securityInvoiceId)
                .queryParam("status", "APPROVED")
                .body("{\"remark\":\"Premature approval L2\"}")
                .basePath(EP_POST_AP_INVOICE)
                .when().post();
        
        logger.info("Premature L2 post-ap-invoice status={}", l2Resp.getStatusCode());
        if (l2Resp.getStatusCode() == 200) {
            logger.warn("⚠️ SECURITY BUG DISCOVERED: L2 approver was allowed to call post-ap-invoice prematurely at L1 stage (HTTP 200)!");
            ExtentListener.test.log(Status.WARNING, "<b>⚠️ SECURITY BUG DISCOVERED:</b> L2 was allowed to call post-ap-invoice prematurely at L1 stage (HTTP 200).");
            sa.fail("Security Violation: Premature L2 approval call returned HTTP 200 instead of error (>=400)");
        } else {
            ExtentListener.test.log(Status.PASS, "Premature L2 approval call was correctly rejected with HTTP " + l2Resp.getStatusCode());
        }

        // 2. L3 tries to call post-ap-invoice prematurely
        Response l3Resp = given()
                .headers(invoiceHeaders(tokenSeniorGroupAccountingManager, UID_SGAM))
                .queryParam("id", securityInvoiceId)
                .queryParam("status", "APPROVED")
                .body("{\"remark\":\"Premature approval L3\"}")
                .basePath(EP_POST_AP_INVOICE)
                .when().post();
        
        logger.info("Premature L3 post-ap-invoice status={}", l3Resp.getStatusCode());
        if (l3Resp.getStatusCode() == 200) {
            logger.warn("⚠️ SECURITY BUG DISCOVERED: L3 approver was allowed to call post-ap-invoice prematurely at L1 stage (HTTP 200)!");
            ExtentListener.test.log(Status.WARNING, "<b>⚠️ SECURITY BUG DISCOVERED:</b> L3 was allowed to call post-ap-invoice prematurely at L1 stage (HTTP 200).");
            sa.fail("Security Violation: Premature L3 approval call returned HTTP 200 instead of error (>=400)");
        } else {
            ExtentListener.test.log(Status.PASS, "Premature L3 approval call was correctly rejected with HTTP " + l3Resp.getStatusCode());
        }

        sa.assertAll();
    }

    // =========================================================================
    // INVOICE B: CLEAN WORKFLOW INVOICE (HAPPY PATH + SECURE TASK PATCHES)
    // =========================================================================

    @Test(priority = 4, dependsOnMethods = {"uploadAttachment"}, groups = { "CRUDSanity" },
            description = "Invoice B: Create and submit the clean Happy Path Invoice")
    public void createAndSubmitHappyInvoice() {
        int randNum = 10000 + new java.util.Random().nextInt(90000);
        String invNum = "TSEC-2026-" + randNum;
        happyInvoiceId = createAndSubmitInvoice(invNum);
        logger.info("Clean Happy Invoice B created and submitted successfully: id={}", happyInvoiceId);
        ApiTestUtils.logInfo("Happy Invoice B ID", String.valueOf(happyInvoiceId));
    }

    // --- STAGE: L1 PENDING (HAPPY INVOICE B) ---

    @Test(priority = 5, dependsOnMethods = {"createAndSubmitHappyInvoice"}, groups = { "CRUDSanity" },
            description = "Happy Invoice B: Verify L1 Workflow Task is generated")
    public void verifyL1TaskGenerated() {
        happyTaskL1 = pollForWorkflowTask(happyInvoiceId, tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT, "L1 Senior Accountant");
        if (happyTaskL1 == null) {
            Assert.fail("Workflow task not generated for L1 Senior Accountant approval.");
        }
        ExtentListener.test.log(Status.PASS, "<b>L1 task found</b> (actionId=" + happyTaskL1.actionId + ", taskDefId=" + happyTaskL1.taskDefId + ")");
    }

    @Test(priority = 6, dependsOnMethods = {"verifyL1TaskGenerated"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Security: Verify L2 Approver cannot patch L1 task")
    public void rejectL2PatchAtL1() {
        assertTaskPatchRejected(happyInvoiceId, tokenFinanceController, UID_FC, "L2 Finance Controller at L1 Task Patch", happyTaskL1);
    }

    @Test(priority = 7, dependsOnMethods = {"verifyL1TaskGenerated"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Security: Verify L3 Approver cannot patch L1 task")
    public void rejectL3PatchAtL1() {
        assertTaskPatchRejected(happyInvoiceId, tokenSeniorGroupAccountingManager, UID_SGAM, "L3 Senior Group Accounting Manager at L1 Task Patch", happyTaskL1);
    }

    @Test(priority = 8, dependsOnMethods = {"verifyL1TaskGenerated"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Security: Verify Creator/Invalid Token cannot patch L1 task / call post-ap-invoice")
    public void rejectWrongUserPatchAtL1() {
        // Creator Vivek
        assertTaskPatchRejected(happyInvoiceId, tokenAccountant, UID_ACCOUNTANT, "Accountant/Creator at L1 Task Patch", happyTaskL1);
        assertPostApiRejected(happyInvoiceId, tokenAccountant, UID_ACCOUNTANT, "Accountant/Creator at L1 Post API");
        // Random fake token
        assertTaskPatchRejected(happyInvoiceId, "Bearer invalid_token_" + System.currentTimeMillis(), "999999", "Random Invalid Token at L1 Task Patch", happyTaskL1);
        assertPostApiRejected(happyInvoiceId, "Bearer invalid_token_" + System.currentTimeMillis(), "999999", "Random Invalid Token at L1 Post API");
    }

    @Test(priority = 9, dependsOnMethods = {"verifyL1TaskGenerated"}, groups = { "CRUDSanity" },
            description = "Workflow: Process valid L1 Approval by Senior Accountant")
    public void approveInvoiceL1() {
        SoftAssert sa = new SoftAssert();

        String postApprovalBody = "{\"rermark\":\"Approved by L1 Senior Accountant (Security Suite)\"}";
        Response postResp = given()
                .headers(invoiceHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .queryParam("id", happyInvoiceId)
                .queryParam("status", "APPROVED")
                .body(postApprovalBody)
                .basePath(EP_POST_AP_INVOICE)
                .when().post();

        logger.info("approveInvoiceL1 post-ap-invoice status={}", postResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(postResp, sa, "approveInvoiceL1_postAction", 200, 204);

        String patchTaskUrl = "/apim/financial-accounting/1.0/rest/workflow-actions/" + happyTaskL1.actionId + "/task";
        Response patchResp = given()
                .headers(invoiceHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .queryParam("processInstanceId", happyTaskL1.processInstanceId)
                .queryParam("taskDefId", happyTaskL1.taskDefId)
                .body("{\"review\":\"APPROVED\"}")
                .basePath(patchTaskUrl)
                .when().patch();

        logger.info("approveInvoiceL1 patchTask status={}", patchResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(patchResp, sa, "approveInvoiceL1_patchTask", 200, 204);

        logger.info("Waiting 6s for Camunda to process L1 and update workflow status...");
        try { Thread.sleep(6000); } catch (InterruptedException ignored) { }

        ExtentListener.test.log(Status.PASS, "<b>L1 Approved successfully</b>");
        sa.assertAll();
    }

    // --- STAGE: L2 PENDING (HAPPY INVOICE B) ---

    @Test(priority = 10, dependsOnMethods = {"approveInvoiceL1"}, groups = { "CRUDSanity" },
            description = "Happy Invoice B: Verify L2 Workflow Task is generated")
    public void verifyL2TaskGenerated() {
        happyTaskL2 = pollForWorkflowTask(happyInvoiceId, tokenFinanceController, UID_FC, "L2 Financial Controller");
        if (happyTaskL2 == null) {
            Assert.fail("Workflow task not generated for L2 Financial Controller approval.");
        }
        ExtentListener.test.log(Status.PASS, "<b>L2 task found</b> (actionId=" + happyTaskL2.actionId + ", taskDefId=" + happyTaskL2.taskDefId + ")");
    }

    @Test(priority = 11, dependsOnMethods = {"verifyL2TaskGenerated"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Security: Verify L1 Approver cannot patch L2 task")
    public void rejectL1PatchAtL2() {
        assertTaskPatchRejected(happyInvoiceId, tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT, "L1 Senior Accountant at L2 Task Patch (Double Approval)", happyTaskL2);
    }

    @Test(priority = 12, dependsOnMethods = {"verifyL2TaskGenerated"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Security: Verify L3 Approver cannot patch L2 task")
    public void rejectL3PatchAtL2() {
        assertTaskPatchRejected(happyInvoiceId, tokenSeniorGroupAccountingManager, UID_SGAM, "L3 Senior Group Accounting Manager at L2 Task Patch", happyTaskL2);
    }

    @Test(priority = 13, dependsOnMethods = {"verifyL2TaskGenerated"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Security: Verify Creator/Invalid Token cannot patch L2 task")
    public void rejectWrongUserPatchAtL2() {
        // Creator
        assertTaskPatchRejected(happyInvoiceId, tokenAccountant, UID_ACCOUNTANT, "Accountant/Creator at L2 Task Patch", happyTaskL2);
        assertPostApiRejected(happyInvoiceId, tokenAccountant, UID_ACCOUNTANT, "Accountant/Creator at L2 Post API");
        // Random fake token
        assertTaskPatchRejected(happyInvoiceId, "Bearer invalid_token_" + System.currentTimeMillis(), "999999", "Random Invalid Token at L2 Task Patch", happyTaskL2);
        assertPostApiRejected(happyInvoiceId, "Bearer invalid_token_" + System.currentTimeMillis(), "999999", "Random Invalid Token at L2 Post API");
    }

    @Test(priority = 14, dependsOnMethods = {"verifyL2TaskGenerated"}, groups = { "CRUDSanity" },
            description = "Workflow: Process valid L2 Approval by Finance Controller")
    public void approveInvoiceL2() {
        SoftAssert sa = new SoftAssert();

        String postApprovalBody = "{\"rermark\":\"Approved by L2 Financial Controller (Security Suite)\"}";
        Response postResp = given()
                .headers(invoiceHeaders(tokenFinanceController, UID_FC))
                .queryParam("id", happyInvoiceId)
                .queryParam("status", "APPROVED")
                .body(postApprovalBody)
                .basePath(EP_POST_AP_INVOICE)
                .when().post();

        logger.info("approveInvoiceL2 post-ap-invoice status={}", postResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(postResp, sa, "approveInvoiceL2_postAction", 200, 204);

        String patchTaskUrl = "/apim/financial-accounting/1.0/rest/workflow-actions/" + happyTaskL2.actionId + "/task";
        Response patchResp = given()
                .headers(invoiceHeaders(tokenFinanceController, UID_FC))
                .queryParam("processInstanceId", happyTaskL2.processInstanceId)
                .queryParam("taskDefId", happyTaskL2.taskDefId)
                .body("{\"review\":\"APPROVED\"}")
                .basePath(patchTaskUrl)
                .when().patch();

        logger.info("approveInvoiceL2 patchTask status={}", patchResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(patchResp, sa, "approveInvoiceL2_patchTask", 200, 204);

        logger.info("Waiting 6s for Camunda to process L2 and update workflow status...");
        try { Thread.sleep(6000); } catch (InterruptedException ignored) { }

        ExtentListener.test.log(Status.PASS, "<b>L2 Approved successfully</b>");
        sa.assertAll();
    }

    // --- STAGE: L3 PENDING (HAPPY INVOICE B) ---

    @Test(priority = 15, dependsOnMethods = {"approveInvoiceL2"}, groups = { "CRUDSanity" },
            description = "Happy Invoice B: Verify L3 Workflow Task is generated")
    public void verifyL3TaskGenerated() {
        happyTaskL3 = pollForWorkflowTask(happyInvoiceId, tokenSeniorGroupAccountingManager, UID_SGAM, "L3 Senior Group Accounting Manager");
        if (happyTaskL3 == null) {
            Assert.fail("Workflow task not generated for L3 Senior Group Accounting Manager approval.");
        }
        ExtentListener.test.log(Status.PASS, "<b>L3 task found</b> (actionId=" + happyTaskL3.actionId + ", taskDefId=" + happyTaskL3.taskDefId + ")");
    }

    @Test(priority = 16, dependsOnMethods = {"verifyL3TaskGenerated"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Security: Verify L1 Approver cannot patch L3 task")
    public void rejectL1PatchAtL3() {
        assertTaskPatchRejected(happyInvoiceId, tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT, "L1 Senior Accountant at L3 Task Patch", happyTaskL3);
    }

    @Test(priority = 17, dependsOnMethods = {"verifyL3TaskGenerated"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Security: Verify L2 Approver cannot patch L3 task")
    public void rejectL2PatchAtL3() {
        assertTaskPatchRejected(happyInvoiceId, tokenFinanceController, UID_FC, "L2 Financial Controller at L3 Task Patch", happyTaskL3);
    }

    @Test(priority = 18, dependsOnMethods = {"verifyL3TaskGenerated"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Security: Verify Creator/Invalid Token cannot patch L3 task")
    public void rejectWrongUserPatchAtL3() {
        // Creator
        assertTaskPatchRejected(happyInvoiceId, tokenAccountant, UID_ACCOUNTANT, "Accountant/Creator at L3 Task Patch", happyTaskL3);
        assertPostApiRejected(happyInvoiceId, tokenAccountant, UID_ACCOUNTANT, "Accountant/Creator at L3 Post API");
        // Random fake token
        assertTaskPatchRejected(happyInvoiceId, "Bearer invalid_token_" + System.currentTimeMillis(), "999999", "Random Invalid Token at L3 Task Patch", happyTaskL3);
        assertPostApiRejected(happyInvoiceId, "Bearer invalid_token_" + System.currentTimeMillis(), "999999", "Random Invalid Token at L3 Post API");
    }

    @Test(priority = 19, dependsOnMethods = {"verifyL3TaskGenerated"}, groups = { "CRUDSanity" },
            description = "Workflow: Process valid L3 Approval by Senior Group Accounting Manager")
    public void approveInvoiceL3() {
        SoftAssert sa = new SoftAssert();

        String postApprovalBody = "{\"remark\":\"approve by the SGAM\"}";
        Response postResp = given()
                .headers(invoiceHeaders(tokenSeniorGroupAccountingManager, UID_SGAM))
                .queryParam("id", happyInvoiceId)
                .queryParam("status", "APPROVED")
                .body(postApprovalBody)
                .basePath(EP_POST_AP_INVOICE)
                .when().post();

        logger.info("approveInvoiceL3 post-ap-invoice status={}", postResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(postResp, sa, "approveInvoiceL3_postAction", 200, 204);

        String patchTaskUrl = "/apim/financial-accounting/1.0/rest/workflow-actions/" + happyTaskL3.actionId + "/task";
        Response patchResp = given()
                .headers(invoiceHeaders(tokenSeniorGroupAccountingManager, UID_SGAM))
                .queryParam("processInstanceId", happyTaskL3.processInstanceId)
                .queryParam("taskDefId", happyTaskL3.taskDefId)
                .body("{\"review\":\"APPROVED\"}")
                .basePath(patchTaskUrl)
                .when().patch();

        logger.info("approveInvoiceL3 patchTask status={}", patchResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(patchResp, sa, "approveInvoiceL3_patchTask", 200, 204);

        ExtentListener.test.log(Status.PASS, "<b>L3 Approved successfully</b>");
        sa.assertAll();
    }

    // --- FINAL VERIFICATION (HAPPY INVOICE B) ---

    @Test(priority = 20, dependsOnMethods = {"approveInvoiceL3"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Verify Happy Invoice B is successfully transition to status POSTED")
    public void verifyHappyInvoicePosted() {
        if (happyInvoiceId <= 0) {
            throw new SkipException("Happy invoice not created");
        }

        try {
            Thread.sleep(4000);
        } catch (InterruptedException ignored) { }

        Response response = given()
                .headers(invoiceHeaders(tokenAccountant, UID_ACCOUNTANT))
                .basePath(EP_AP_INVOICE + "/" + happyInvoiceId)
                .when().get()
                .then().extract().response();

        int status = response.getStatusCode();
        logger.info("verifyHappyInvoicePosted status={}", status);

        SoftAssert sa = new SoftAssert();
        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.assertHttpStatus(response, sa, "verifyHappyInvoicePosted", 200);

        if (status == 200) {
            String finalStatus = response.jsonPath().getString("status");
            String wfStage    = response.jsonPath().getString("workflowStage");
            logger.info("Happy Invoice final status={}, workflowStage={}", finalStatus, wfStage);
            ApiTestUtils.logInfo("Happy Invoice Final Status", finalStatus);
            ApiTestUtils.logInfo("Happy Workflow Stage",       wfStage != null ? wfStage : "N/A");

            sa.assertEquals(finalStatus, "POSTED", "Invoice status should be POSTED after all approvals");
            if ("POSTED".equals(finalStatus)) {
                ExtentListener.test.log(Status.PASS, "<b>✅ Happy Invoice workflow completed cleanly. Status=POSTED</b>");
            }
        }

        sa.assertAll();
    }
}
