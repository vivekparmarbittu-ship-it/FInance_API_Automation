package base;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.TestNG;
import org.testng.reporters.EmailableReporter2;
import org.testng.reporters.FailedReporter;
import org.testng.reporters.XMLReporter;

public class TestRunner {

    private static final Logger logger = LogManager.getLogger(TestRunner.class);

    public static String env;
    public static String baseUri;
    public static String usernamen;
    public static String password;
    public static String tokenPath;

    private static Properties properties;

    public static void main(String[] args) {

        logger.info("========= TestRunner Started =========");

        loadProperties();

        // Environment (Demo or SIT/Humain). Use -Denv=Dev|Demo|SIT or fallback to default
        env = System.getProperty("env", properties.getProperty("default.env", "Demo"));
        logger.info("Selected env = {}", env);

        // Module selection via system property -Dmodule=ModuleName (fallback to AgentMarketplace)
        String module = System.getProperty("module", properties.getProperty("default.module", "financecrudsanity"));
        logger.info("Selected module = {}", module);

        // Load environment config into static fields (so BaseTests can use them)
        loadEnvironmentConfig(env);

        // Configure Allure REST Assured filter globally for automatic API logging
        try {
            io.restassured.RestAssured.filters(new io.qameta.allure.restassured.AllureRestAssured());
            logger.info("Registered Allure REST Assured Filter");
        } catch (Exception e) {
            logger.error("Failed to register Allure REST Assured Filter", e);
        }

        TestNG testng = new TestNG();
        // Register Allure TestNG listener
        testng.addListener(new io.qameta.allure.testng.AllureTestNg());

        // In restricted environments (like IDE sandboxes), TestNG's default JUnitXMLReporter can throw
        // SocketException/Operation not permitted when resolving the local host.
        // Use a minimal set of reporters to still generate standard TestNG reports.
        testng.setUseDefaultListeners(false);
        testng.addListener(new XMLReporter());
        testng.addListener(new EmailableReporter2());
        testng.addListener(new FailedReporter());

        // If user wants to run a single class (convenience for Eclipse)
        String singleClass = System.getProperty("class");
        if (singleClass != null && !singleClass.trim().isEmpty()) {
            logger.info("Running single test class: {}", singleClass);
            try {
                Class<?> cls = Class.forName(singleClass.trim());
                
                // Programmatically configure suite name to match the class name for beautiful Allure reporting
                String className = cls.getSimpleName();
                org.testng.xml.XmlSuite xmlSuite = new org.testng.xml.XmlSuite();
                xmlSuite.setName(className);
                
                org.testng.xml.XmlTest xmlTest = new org.testng.xml.XmlTest(xmlSuite);
                xmlTest.setName(className + "-Test");
                
                List<org.testng.xml.XmlClass> xmlClasses = new ArrayList<>();
                xmlClasses.add(new org.testng.xml.XmlClass(cls));
                xmlTest.setXmlClasses(xmlClasses);
                
                List<org.testng.xml.XmlSuite> suites = new ArrayList<>();
                suites.add(xmlSuite);
                testng.setXmlSuites(suites);
                
                testng.run();
                generateAllureReport(className + ".html");
                return;
            } catch (ClassNotFoundException e) {
                logger.error("Class not found: {}", singleClass, e);
                return;
            }
        }

        // Determine TestNG xml(s) based on module name
        List<String> suiteFiles = chooseTestNgXmlsForModule(module);

        if (suiteFiles == null || suiteFiles.isEmpty()) {
            String defaultSuite = getXmlFilePath("testngSanity.xml");
            logger.warn("No suite files mapped for module '{}', falling back to {}", module, defaultSuite);
            suiteFiles = new ArrayList<>();
            suiteFiles.add(defaultSuite);
        }

        logger.info("Will run TestNG suite files: {}", suiteFiles);

        // Parse and dynamically rename suites to match the executed class names in Allure reports
        List<org.testng.xml.XmlSuite> xmlSuites = new ArrayList<>();
        for (String suiteFile : suiteFiles) {
            try {
                java.util.Collection<org.testng.xml.XmlSuite> parsed = new org.testng.xml.internal.Parser(suiteFile).parse();
                for (org.testng.xml.XmlSuite s : parsed) {
                    List<String> simpleClassNames = new ArrayList<>();
                    for (org.testng.xml.XmlTest t : s.getTests()) {
                        for (org.testng.xml.XmlClass c : t.getXmlClasses()) {
                            String simpleName = c.getName().substring(c.getName().lastIndexOf('.') + 1);
                            if (!simpleClassNames.contains(simpleName)) {
                                simpleClassNames.add(simpleName);
                            }
                        }
                    }
                    if (!simpleClassNames.isEmpty()) {
                        String prettyName = String.join("_", simpleClassNames);
                        s.setName(prettyName);
                        for (org.testng.xml.XmlTest t : s.getTests()) {
                            t.setName(prettyName + "-Test");
                        }
                    }
                    xmlSuites.add(s);
                }
            } catch (Exception e) {
                logger.error("Failed to parse/rename suite file: {}", suiteFile, e);
            }
        }
        testng.setXmlSuites(xmlSuites);

        // Optional groups passed via -Dgroups and -DexcludeGroups
        String includeGroups = System.getProperty("groups");
        String excludeGroups = System.getProperty("excludeGroups");
        if (includeGroups != null && !includeGroups.trim().isEmpty()) {
            testng.setGroups(includeGroups.trim());
            logger.info("Applying include groups: {}", includeGroups);
        }
        if (excludeGroups != null && !excludeGroups.trim().isEmpty()) {
            testng.setExcludedGroups(excludeGroups.trim());
            logger.info("Applying exclude groups: {}", excludeGroups);
        }

        testng.run();
        generateAllureReport(module + ".html");
    }

    private static void generateAllureReport(String reportName) {
        logger.info("Generating Allure HTML report into '{}'...", reportName);
        try {
            ProcessBuilder pb;
            String os = System.getProperty("os.name").toLowerCase();
            String tempDirName = "allure-temp";
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", "allure generate allure-results --clean --single-file -o " + tempDirName);
            } else {
                pb = new ProcessBuilder("allure", "generate", "allure-results", "--clean", "--single-file", "-o", tempDirName);
            }
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                File tempDir = new File(System.getProperty("user.dir") + "/" + tempDirName);
                File indexFile = new File(tempDir, "index.html");
                if (indexFile.exists()) {
                    File targetFile = new File(System.getProperty("user.dir") + "/" + reportName);
                    java.nio.file.Files.copy(indexFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Allure HTML report generated successfully as '{}'.", targetFile.getName());
                    
                    // Cleanup temp dir
                    indexFile.delete();
                    tempDir.delete();
                } else {
                    logger.warn("Could not find generated index.html inside '{}' directory.", tempDirName);
                }
            } else {
                logger.warn("Allure HTML report generation completed with non-zero exit code: {}", exitCode);
            }
        } catch (Exception e) {
            logger.warn("Could not automatically generate Allure HTML report. Make sure 'allure' CLI is installed and on your PATH. Error: {}", e.getMessage());
        }
    }

    private static void loadProperties() {
        try (InputStream input = TestRunner.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("config.properties not found on classpath!");
            }
            properties = new Properties();
            properties.load(input);
            logger.info("Loaded config.properties");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    private static void loadEnvironmentConfig(String env) {
        // Keep same keys you used in config.properties; fallback logic if needed
        if (env.equalsIgnoreCase("Demo")) {
            baseUri = properties.getProperty("base.uriDemoNetSingularity");
            usernamen = properties.getProperty("unDemoNetSingularity");
            password = properties.getProperty("pwdDemoNetSingularity");
            tokenPath = properties.getProperty("token.path.demo");
        }
        else if (env.equalsIgnoreCase("DEV")) {  // Dev
            baseUri = properties.getProperty("base.uriDevNetSingularity");
            usernamen = properties.getProperty("unDevNetSingularity");
            password = properties.getProperty("pwdDevNetSingularity");
            tokenPath = properties.getProperty("token.path.demo");
        }
        else if (env.equalsIgnoreCase("SIT")) {  // HumainOS
            baseUri = properties.getProperty("base.uriHumain");
            usernamen = properties.getProperty("unHumainos");
            password = properties.getProperty("pwdHumainos");
            tokenPath = properties.getProperty("token.path.humain");
        }
        else if (env.equalsIgnoreCase("HuaminDev")) {  // HumainOS Dev
            baseUri = properties.getProperty("base.uriHumainDev");
            usernamen = properties.getProperty("unHumainosDev");
            password = properties.getProperty("pwdHumainosDev");
            tokenPath = properties.getProperty("token.path.humainDev");
        }
        else if (env.equalsIgnoreCase("QA") || env.equalsIgnoreCase("netsingularityqa") || env.equalsIgnoreCase("humainqa")) {  // QA
            baseUri = properties.getProperty("base.uriNetSingularityQA");
            usernamen = properties.getProperty("unNetSingularityQA");
            password = properties.getProperty("pwdNetSingularityQA");
            tokenPath = properties.getProperty("token.path.netSingularityQA");
        }
        else {
            // fallback to demo-ish values
            baseUri = properties.getProperty("base.uriDemoNetSingularity", properties.getProperty("base.uriNetSingularity"));
            usernamen = properties.getProperty("unDemoNetSingularity");
            password = properties.getProperty("pwdDemoNetSingularity");
            tokenPath = properties.getProperty("token.path.demo", "/oauth/token");
        }

        logger.info("Env config loaded: baseUri={}, tokenPath={}, username present?={}",
                baseUri, tokenPath, (usernamen != null && !usernamen.isEmpty()));
    }

    /**
     * Get XML file path - reads from JAR resources if bundled, otherwise from file system.
     * Returns the path to use with TestNG (may be a temporary extracted file).
     */
    private static String getXmlFilePath(String xmlFileName) {
        List<String> resourceCandidates = Arrays.asList(
                "xml/" + xmlFileName,
                "testng/" + xmlFileName,
                xmlFileName
        );

        // Try multiple classpath locations (works for JAR and IDE classpath)
        for (String resourcePath : resourceCandidates) {
            InputStream resourceStream = TestRunner.class.getClassLoader().getResourceAsStream(resourcePath);
            if (resourceStream == null) {
                continue;
            }
            try (InputStream is = resourceStream) {
                Path tempDir = Files.createTempDirectory("testng-suites-");
                tempDir.toFile().deleteOnExit();

                File tempFile = new File(tempDir.toFile(), xmlFileName);
                tempFile.deleteOnExit();

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                logger.info("Extracted XML resource '{}' to temp file: {}", resourcePath, tempFile.getAbsolutePath());
                return tempFile.getAbsolutePath();
            } catch (IOException e) {
                logger.error("Failed to extract XML resource '{}': {}", resourcePath, e.getMessage(), e);
            }
        }

        // Try common file-system locations for local execution and externalized suites
        List<String> fileCandidates = Arrays.asList(
                System.getProperty("user.dir") + File.separator + "xml" + File.separator + xmlFileName,
                System.getProperty("user.dir") + File.separator + "testng" + File.separator + xmlFileName,
                System.getProperty("user.dir") + File.separator + xmlFileName
        );
        for (String filePath : fileCandidates) {
            File suiteFile = new File(filePath);
            if (suiteFile.exists()) {
                logger.info("Using XML file from file system: {}", filePath);
                return filePath;
            }
        }

        String message = "XML file '" + xmlFileName + "' not found. Checked classpath resources "
                + resourceCandidates + " and file paths " + fileCandidates
                + ". Place suite XML under src/main/resources/xml (or testng), or in an xml/testng folder beside the executable.";
        logger.error(message);
        throw new RuntimeException(message);
    }

    /**
     * Map module names to one or multiple TestNG xml files.
     * Returns absolute file paths - reads from JAR resources if bundled, otherwise from file system.
     */
    private static List<String> chooseTestNgXmlsForModule(String moduleName) {
        // normalize module name for matching
        String key = (moduleName == null) ? "" : moduleName.trim().toLowerCase();

        List<String> files = new ArrayList<>();
        String xmlFileName;

        switch (key) {
  
            case "finance":
                xmlFileName = "FinanceAll.xml";
                files.add(getXmlFilePath(xmlFileName));
                break;

            case "financesanity":
                xmlFileName = "FinanceAllSanity.xml";
                files.add(getXmlFilePath(xmlFileName));
                break;

            case "financecrudsanity":
                xmlFileName = "FinanceAllCRUDSanity.xml";
                files.add(getXmlFilePath(xmlFileName));
                break;

            case "financeunauthorized":
                xmlFileName = "FinanceUnauthorized.xml";
                files.add(getXmlFilePath(xmlFileName));
                break;

            case "creditmemo":
            case "financecreditmemo":
                xmlFileName = "FinanceCreditMemo.xml";
                files.add(getXmlFilePath(xmlFileName));
                break;

            case "debitmemo":
            case "financedebitmemo":
                xmlFileName = "FinanceDebitMemo.xml";
                files.add(getXmlFilePath(xmlFileName));
                break;

            case "invoice":
            case "financeinvoice":
                xmlFileName = "FinanceInvoiceWorkflow.xml";
                files.add(getXmlFilePath(xmlFileName));
                break;

            default:
                logger.warn("Unknown module '{}', defaulting to FinanceAll.xml", moduleName);
                xmlFileName = "FinanceAll.xml";
                files.add(getXmlFilePath(xmlFileName));
                break;
        }

        // filter out files that don't exist (optional) to avoid TestNG failing early
        List<String> finalFiles = new ArrayList<>();
        for (String f : files) {
            finalFiles.add(f); // keep as-is; you can uncomment check below if you prefer existence check
            // File ff = new File(f);
            // if (ff.exists()) finalFiles.add(f);
            // else logger.warn("Suite file not found: {}", f);
        }

        return finalFiles;
    }
}
