package financeSuite;

import static io.restassured.RestAssured.given;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

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
 * AP Non-PO Invoice Workflow end-to-end automation suite.
 * Built from Invoice_Create.har.
 *
 * Flow:
 *   1. Accountant (Vivek) uploads a file attachment.
 *   2. Accountant creates AP Invoice (status: DRAFT).
 *   3. Accountant checks duplicate invoice.
 *   4. Accountant submits AP Invoice (status: PENDING_APPROVAL).
 *   5. L1 Approver - Senior Accountant (Khushbu) approves if amount > 10k.
 *   6. L2 Approver - Financial Controller (Abinaya) approves if amount > 10k (up to 1 lakh).
 *   7. L3 Approver - Senior Group Accounting Manager (Suyash) approves if amount > 1 lakh.
 *   8. Re-search to verify final status (POSTED).
 *
 * DOA Rules (confirmed from Invoice_Createflow.har):
 *   - L1 (khushbu.patel@visionwaves.com   - Senior Accountant, userId=143080)              : amount > 10,000
 *   - L2 (abinaya.saravanan@visionwaves.com - Finance Controller, userId=143084)            : amount > 10,000 (up to 1,00,000)
 *   - L3 (Suyash.tiwari@visionwaves.com  - Senior Group Accounting Manager, userId=143082) : amount > 1,00,000
 */
@Listeners(ExtentListener.class)
public class FinanceInvoiceWorkflowAPI extends BaseTestDemoNetSingularity {

    private static final Logger logger = LogManager.getLogger(FinanceInvoiceWorkflowAPI.class);

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

    // Fallback IDs from HAR.
    private static final int DEFAULT_GL_ACCOUNT_ID   = 360;
    private static final int DEFAULT_COST_CENTER_ID  = 123249;
    private static final int DEFAULT_VENDOR_ID       = 15324;
    private static final int DEFAULT_TAX_ID          = 33;

    private static final double DOA_THRESHOLD = 10_000d;
    private static final double L3_DOA_THRESHOLD = 100_000d;

    // Fallback userIds (confirmed from Invoice_Createflow.har)
    private static final String UID_ACCOUNTANT         = "140468"; // Vivek Parmar   - Finance Accountant
    private static final String UID_SENIOR_ACCOUNTANT  = "143080"; // Khushbu Patel  - Senior Accountant (L1)
    private static final String UID_FC                 = "143084"; // Abinaya Saravanan - Finance Controller (L2)
    private static final String UID_SGAM               = "143082"; // Suyash Tiwari  - Senior Group Accounting Manager (L3)

    private String tokenAccountant;
    private String tokenSeniorAccountant;
    private String tokenFinanceController;
    private String tokenSeniorGroupAccountingManager;

    private int    createdInvoiceId = -1;
    private String createdInvoiceNumber;
    private double invoiceAmount;
    private int    uploadedAttachmentId = -1;
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

        String rawAccountant = TokenHumainOS.getTokenAccountant();
        if (rawAccountant != null) tokenAccountant = "Bearer " + rawAccountant;
        String rawSeniorAccountant = TokenHumainOS.getTokenSeniorAccountant();
        if (rawSeniorAccountant != null) tokenSeniorAccountant = "Bearer " + rawSeniorAccountant;
        String rawFc = TokenHumainOS.getTokenFinanceController();
        if (rawFc != null) tokenFinanceController = "Bearer " + rawFc;
        // L3: Senior Group Accounting Manager (Suyash.tiwari@visionwaves.com, userId=143082)
        String rawSgam = TokenHumainOS.getTokenSeniorGroupAccountingManager();
        if (rawSgam != null) tokenSeniorGroupAccountingManager = "Bearer " + rawSgam;

        invoiceAmount = parseDoubleProp("apAmount", 1000d); // default is 1000 to match the invoice amount and flow in Invoice_Create.har
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


    @Test(priority = 1, groups = { "CRUDSanity" }, description = "Upload PDF attachment for AP Invoice")
    public void uploadAttachment() {
        if (tokenAccountant == null || tokenAccountant.isEmpty()) {
            throw new SkipException("Accountant token not available");
        }

        StringBuilder fileSource = new StringBuilder();
        byte[] pdfBytes = resolveAttachmentFile(fileSource);
        String fileName = "invoice_doc_" + System.currentTimeMillis() + ".pdf";
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
        ApiTestUtils.logInfo("Uploaded attachment UUID", uploadedAttachmentUuid);
        ExtentListener.test.log(Status.PASS, "<b>Attachment uploaded successfully</b>");
    }

    @Test(priority = 2, dependsOnMethods = {"uploadAttachment"}, groups = { "CRUDSanity" }, description = "Create Non-PO AP Invoice as DRAFT")
    public void createApInvoiceDraft() {
        if (tokenAccountant == null || tokenAccountant.isEmpty()) {
            throw new SkipException("Accountant token not available");
        }

        int randNum = 10000 + new java.util.Random().nextInt(90000);
        createdInvoiceNumber = "TEST-2026-" + randNum;
        long now = System.currentTimeMillis();
        long due = now + 86400000L * 7; // 7 days later

        String payload = "{"
                + "\"invoiceNumber\":\"" + createdInvoiceNumber + "\","
                + "\"remarks\":\"Test AP Invoice Workflow\","
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
                .when().post()
                .then().extract().response();

        int status = response.getStatusCode();
        String body = response.getBody().asString();
        logger.info("createApInvoiceDraft status={} body={}", status, body.length() > 400 ? body.substring(0, 400) + "..." : body);

        SoftAssert sa = new SoftAssert();
        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.assertHttpStatus(response, sa, "createApInvoiceDraft", 200, 201);

        JsonPath j = response.jsonPath();
        Integer invId = extractInt(j, "id", "response[0].entityData.id", "response[0].id");

        if (invId == null || invId <= 0) {
            Assert.fail("Failed to retrieve invoice ID from creation response: " + body);
        }

        createdInvoiceId = invId;

        // Perform mapping update
        if (uploadedAttachmentUuid != null) {
            String mappingPayload = "[{\"documentUUID\":[\"" + uploadedAttachmentUuid + "\"],\"resourceId\":" + createdInvoiceId + "}]";
            Response mappingResponse = given()
                    .headers(invoiceHeaders(tokenAccountant, UID_ACCOUNTANT))
                    .body(mappingPayload)
                    .basePath(EP_UPDATE_MAPPINGS)
                    .when().patch();
            logger.info("update-mappings status={}", mappingResponse.getStatusCode());
        }

        ApiTestUtils.logInfo("Created Invoice ID", String.valueOf(createdInvoiceId));
        ApiTestUtils.logInfo("Created Invoice Number", createdInvoiceNumber);
        ExtentListener.test.log(Status.PASS, "<b>AP Invoice created as DRAFT successfully</b>");
        sa.assertAll();
    }

    @Test(priority = 3, dependsOnMethods = {"createApInvoiceDraft"}, groups = { "CRUDSanity" }, description = "Check duplicate invoice API")
    public void checkDuplicateInvoice() {
        if (tokenAccountant == null || tokenAccountant.isEmpty()) {
            throw new SkipException("Accountant token not available");
        }

        String payload = "{"
                + "\"vendorId\":" + DEFAULT_VENDOR_ID + ","
                + "\"invoiceNumber\":\"" + createdInvoiceNumber + "\","
                + "\"invoicedAmount\":" + invoiceAmount + ","
                + "\"invoiceDate\":" + System.currentTimeMillis() + ","
                + "\"excludeId\":" + createdInvoiceId
                + "}";

        Response response = given()
                .headers(invoiceHeaders(tokenAccountant, UID_ACCOUNTANT))
                .body(payload)
                .basePath(EP_CHECK_DUPLICATE)
                .when().post()
                .then().extract().response();

        int status = response.getStatusCode();
        logger.info("checkDuplicateInvoice status={}", status);

        SoftAssert sa = new SoftAssert();
        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.assertHttpStatus(response, sa, "checkDuplicateInvoice", 200);
        ExtentListener.test.log(Status.PASS, "<b>Check duplicate invoice verified</b>");
        sa.assertAll();
    }

    @Test(priority = 4, dependsOnMethods = {"checkDuplicateInvoice"}, groups = { "CRUDSanity" }, description = "Submit AP Invoice for approval")
    public void submitInvoice() {
        if (tokenAccountant == null || tokenAccountant.isEmpty()) {
            throw new SkipException("Accountant token not available");
        }

        long now = System.currentTimeMillis();
        long due = now + 86400000L * 7;

        String payload = "{"
                + "\"invoiceNumber\":\"" + createdInvoiceNumber + "\","
                + "\"remarks\":\"Submitted for approval\","
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
                + "\"id\":" + createdInvoiceId + ","
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
                .basePath(EP_AP_INVOICE + "/" + createdInvoiceId)
                .when().put()
                .then().extract().response();

        int status = response.getStatusCode();
        logger.info("submitInvoice status={}", status);

        SoftAssert sa = new SoftAssert();
        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.assertHttpStatus(response, sa, "submitInvoice", 200, 204);

        // Perform mapping update again to match UI flow
        if (uploadedAttachmentUuid != null) {
            String mappingPayload = "[{\"documentUUID\":[\"" + uploadedAttachmentUuid + "\"],\"resourceId\":" + createdInvoiceId + "}]";
            given()
                .headers(invoiceHeaders(tokenAccountant, UID_ACCOUNTANT))
                .body(mappingPayload)
                .basePath(EP_UPDATE_MAPPINGS)
                .when().patch();
        }

        ExtentListener.test.log(Status.PASS, "<b>AP Invoice submitted for approval successfully</b>");
        sa.assertAll();
    }

    private static class WorkflowTask {
        String processInstanceId;
        String taskDefId;
        String actionId;
    }

    private WorkflowTask pollForWorkflowTask(String authHeadersToken, String fallbackUid, String stageDescription) {
        int maxRetries = 20;
        long retryDelayMs = 3000;
        String filter = "id=ge=0;deleted==false;id==" + createdInvoiceId;

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
                    // Debug: log workflowStage and actions on every attempt
                    String wfStage = json.getString("[0].workflowStage");
                    String invoiceStatus = json.getString("[0].status");
                    List<?> actions = json.getList("[0].actions");
                    logger.info("[Poll {}/{}] stage={} [{}] | userId={} | actions={}",
                            attempt, maxRetries, stageDescription, fallbackUid,
                            (actions != null ? actions.size() : 0) + " action(s)",
                            "workflowStage=" + wfStage + ", status=" + invoiceStatus);
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
                logger.warn("[Poll {}/{}] {} search returned HTTP {}", attempt, maxRetries, stageDescription, response.getStatusCode());
            }

            logger.info("Attempt {}/{} - Waiting for workflow action task for {}...", attempt, maxRetries, stageDescription);
            try {
                Thread.sleep(retryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    @Test(priority = 5, dependsOnMethods = {"submitInvoice"}, groups = { "CRUDSanity" }, description = "Level-1 Approval by Senior Accountant - applies for amount > 10k")
    public void approveInvoiceL1() {
        if (createdInvoiceId <= 0) {
            throw new SkipException("Invoice not successfully created/submitted.");
        }
        if (invoiceAmount <= DOA_THRESHOLD) {
            throw new SkipException("Invoice amount " + invoiceAmount + " <= " + DOA_THRESHOLD + ". L1 approval not required (amount must be > 10,000).");
        }
        if (tokenSeniorAccountant == null || tokenSeniorAccountant.isEmpty()) {
            throw new SkipException("Senior Accountant token not available");
        }

        WorkflowTask task = pollForWorkflowTask(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT, "L1 Senior Accountant");
        if (task == null) {
            Assert.fail("Workflow task not generated for L1 Senior Accountant approval.");
        }

        SoftAssert sa = new SoftAssert();

        // 1. Post approval action
        String postApprovalBody = "{\"rermark\":\"Approved by L1 Senior Accountant\"}"; // with the typo rermark

        Response postResp = given()
                .headers(invoiceHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .queryParam("id", createdInvoiceId)
                .queryParam("status", "APPROVED")
                .body(postApprovalBody)
                .basePath(EP_POST_AP_INVOICE)
                .when().post();

        logger.info("approveInvoiceL1 post-ap-invoice status={}", postResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(postResp, sa, "approveInvoiceL1_postAction", 200, 204);

        // 2. Complete Camunda task
        String patchTaskUrl = "/apim/financial-accounting/1.0/rest/workflow-actions/" + task.actionId + "/task";
        String patchTaskBody = "{\"review\":\"APPROVED\"}";

        Response patchResp = given()
                .headers(invoiceHeaders(tokenSeniorAccountant, UID_SENIOR_ACCOUNTANT))
                .queryParam("processInstanceId", task.processInstanceId)
                .queryParam("taskDefId", task.taskDefId)
                .body(patchTaskBody)
                .basePath(patchTaskUrl)
                .when().patch();

        logger.info("approveInvoiceL1 patchTask status={}", patchResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(patchResp, sa, "approveInvoiceL1_patchTask", 200, 204);

        ExtentListener.test.log(Status.PASS, "<b>L1 Approved by Senior Accountant successfully</b>");
        sa.assertAll();
    }

    @Test(priority = 6, dependsOnMethods = {"approveInvoiceL1"}, alwaysRun = true, groups = { "CRUDSanity" }, description = "Level-2 Approval by Finance Controller - applies for amount 10k to 100k")
    public void approveInvoiceL2() {
        if (createdInvoiceId <= 0) {
            throw new SkipException("Invoice not successfully created/submitted.");
        }
        if (invoiceAmount <= DOA_THRESHOLD) {
            throw new SkipException("Invoice amount " + invoiceAmount + " <= " + DOA_THRESHOLD + ". L2 approval not required (amount must be > 10,000).");
        }
        if (tokenFinanceController == null || tokenFinanceController.isEmpty()) {
            throw new SkipException("Finance Controller token not available");
        }

        WorkflowTask task = pollForWorkflowTask(tokenFinanceController, UID_FC, "L2 Financial Controller");
        if (task == null) {
            Assert.fail("Workflow task not generated for L2 Financial Controller approval.");
        }

        SoftAssert sa = new SoftAssert();

        // 1. Post approval action
        String postApprovalBody = "{\"rermark\":\"Approved by L2 Financial Controller\"}";

        Response postResp = given()
                .headers(invoiceHeaders(tokenFinanceController, UID_FC))
                .queryParam("id", createdInvoiceId)
                .queryParam("status", "APPROVED")
                .body(postApprovalBody)
                .basePath(EP_POST_AP_INVOICE)
                .when().post();

        logger.info("approveInvoiceL2 post-ap-invoice status={}", postResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(postResp, sa, "approveInvoiceL2_postAction", 200, 204);

        // 2. Complete Camunda task
        String patchTaskUrl = "/apim/financial-accounting/1.0/rest/workflow-actions/" + task.actionId + "/task";
        String patchTaskBody = "{\"review\":\"APPROVED\"}";

        Response patchResp = given()
                .headers(invoiceHeaders(tokenFinanceController, UID_FC))
                .queryParam("processInstanceId", task.processInstanceId)
                .queryParam("taskDefId", task.taskDefId)
                .body(patchTaskBody)
                .basePath(patchTaskUrl)
                .when().patch();

        logger.info("approveInvoiceL2 patchTask status={}", patchResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(patchResp, sa, "approveInvoiceL2_patchTask", 200, 204);

        // Wait for Camunda to process L2 and generate L3 task
        logger.info("Waiting 6s after L2 approval to allow Camunda to progress to L3 stage...");
        try { Thread.sleep(6000); } catch (InterruptedException ignored) { }

        ExtentListener.test.log(Status.PASS, "<b>L2 Approved by Financial Controller successfully</b>");
        sa.assertAll();
    }

    @Test(priority = 7, dependsOnMethods = {"approveInvoiceL2"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Level-3 Approval by Senior Group Accounting Manager - applies for amount > 100k")
    public void approveInvoiceL3() {
        if (createdInvoiceId <= 0) {
            throw new SkipException("Invoice not successfully created/submitted.");
        }
        if (invoiceAmount <= L3_DOA_THRESHOLD) {
            throw new SkipException("Invoice amount " + invoiceAmount + " <= " + L3_DOA_THRESHOLD
                    + ". L3 approval not required (amount must be > 1,00,000).");
        }
        if (tokenSeniorGroupAccountingManager == null || tokenSeniorGroupAccountingManager.trim().isEmpty()) {
            throw new SkipException("Senior Group Accounting Manager token not available");
        }

        WorkflowTask task = pollForWorkflowTask(tokenSeniorGroupAccountingManager, UID_SGAM, "L3 Senior Group Accounting Manager");
        if (task == null) {
            Assert.fail("Workflow task not generated for L3 Senior Group Accounting Manager approval."
                    + " Invoice ID=" + createdInvoiceId + ", Amount=" + invoiceAmount);
        }

        SoftAssert sa = new SoftAssert();

        // 1. Post approval action (HAR: {"remark":"approve by the SGAM"})
        String postApprovalBody = "{\"remark\":\"approve by the SGAM\"}";

        Response postResp = given()
                .headers(invoiceHeaders(tokenSeniorGroupAccountingManager, UID_SGAM))
                .queryParam("id", createdInvoiceId)
                .queryParam("status", "APPROVED")
                .body(postApprovalBody)
                .basePath(EP_POST_AP_INVOICE)
                .when().post();

        logger.info("approveInvoiceL3 post-ap-invoice status={}", postResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(postResp, sa, "approveInvoiceL3_postAction", 200, 204);

        // 2. Complete Camunda task
        String patchTaskUrl = "/apim/financial-accounting/1.0/rest/workflow-actions/" + task.actionId + "/task";
        String patchTaskBody = "{\"review\":\"APPROVED\"}";

        Response patchResp = given()
                .headers(invoiceHeaders(tokenSeniorGroupAccountingManager, UID_SGAM))
                .queryParam("processInstanceId", task.processInstanceId)
                .queryParam("taskDefId", task.taskDefId)
                .body(patchTaskBody)
                .basePath(patchTaskUrl)
                .when().patch();

        logger.info("approveInvoiceL3 patchTask status={}", patchResp.getStatusCode());
        ApiTestUtils.assertHttpStatus(patchResp, sa, "approveInvoiceL3_patchTask", 200, 204);

        ExtentListener.test.log(Status.PASS, "<b>L3 Approved by Senior Group Accounting Manager successfully</b>");
        sa.assertAll();
    }

    @Test(priority = 8, dependsOnMethods = {"submitInvoice"}, alwaysRun = true, groups = { "CRUDSanity" },
            description = "Verify final AP Invoice status is POSTED after all required approvals")
    public void verifyFinalStatus() {
        if (createdInvoiceId <= 0) {
            throw new SkipException("No invoice created");
        }

        // Wait a few seconds for database persistence/state updates
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) { }

        Response response = given()
                .headers(invoiceHeaders(tokenAccountant, UID_ACCOUNTANT))
                .basePath(EP_AP_INVOICE + "/" + createdInvoiceId)
                .when().get()
                .then().extract().response();

        int status = response.getStatusCode();
        logger.info("verifyFinalStatus invoice status={}", status);

        SoftAssert sa = new SoftAssert();
        ApiTestUtils.validateResponseTime(response, ApiTestUtils.DEFAULT_RESPONSE_TIME_THRESHOLD_MS);
        ApiTestUtils.assertHttpStatus(response, sa, "verifyFinalStatus", 200);

        if (status == 200) {
            String finalStatus = response.jsonPath().getString("status");
            String wfStage    = response.jsonPath().getString("workflowStage");
            logger.info("Invoice final status={}, workflowStage={}", finalStatus, wfStage);
            ApiTestUtils.logInfo("Invoice Final Status", finalStatus);
            ApiTestUtils.logInfo("Workflow Stage",       wfStage != null ? wfStage : "N/A");

            sa.assertEquals(finalStatus, "POSTED", "Invoice status should be POSTED after all approvals");
            if ("POSTED".equals(finalStatus)) {
                String approvalChain = "L1✅ L2✅"
                        + (invoiceAmount > L3_DOA_THRESHOLD ? " L3✅" : "");
                ExtentListener.test.log(Status.PASS,
                        "<b>✅ AP Invoice workflow completed. Status=POSTED [" + approvalChain + "]</b>");
            }
        }

        sa.assertAll();
    }
}

