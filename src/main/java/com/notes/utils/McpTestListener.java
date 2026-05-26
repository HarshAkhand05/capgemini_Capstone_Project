package com.notes.utils;

import com.notes.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IAnnotationTransformer;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * McpTestListener — TestNG Listener (Section 3.3 + 3.4)
 *
 * Does two things:
 *
 * 1. IAnnotationTransformer: Automatically attaches RetryUtils to EVERY @Test
 *    so you don't need to add retryAnalyzer = RetryUtils.class manually.
 *
 * 2. ITestListener: Logs test start/pass/fail with MCP context.
 *    On failure, it triggers MCP to generate a script suggestion.
 *
 * Register in testng-all.xml (and other suite XMLs):
 *   <listeners>
 *     <listener class-name="com.notes.utils.McpTestListener"/>
 *   </listeners>
 */
public class McpTestListener implements IAnnotationTransformer, ITestListener {

    private static final Logger log = LoggerFactory.getLogger(McpTestListener.class);

    // ── IAnnotationTransformer ───────────────────────────────────────────────

    /**
     * Automatically attaches RetryUtils to every @Test in the suite.
     * This means no test class needs to manually declare retryAnalyzer.
     */
    @Override
    public void transform(ITestAnnotation annotation,
                          Class testClass,
                          Constructor testConstructor,
                          Method testMethod) {
        if (annotation.getRetryAnalyzerClass() == null) {
            annotation.setRetryAnalyzer(RetryUtils.class);
            log.debug("[McpListener] RetryUtils attached to: {}",
                    testMethod != null ? testMethod.getName() : "unknown");
        }
    }

    // ── ITestListener ────────────────────────────────────────────────────────

    @Override
    public void onTestStart(ITestResult result) {
        log.info("[McpListener] ▶ TEST START: {} | Thread: {}",
                result.getMethod().getMethodName(),
                Thread.currentThread().getId());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        log.info("[McpListener] ✔ TEST PASSED: {}",
                result.getMethod().getMethodName());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        Throwable cause = result.getThrowable();

        log.error("[McpListener] ✖ TEST FAILED: {} | Reason: {}",
                testName,
                cause != null ? cause.getMessage() : "unknown");

        // MCP Enhancement: Ask Claude to suggest fix for this test failure
        triggerMcpFailureAnalysis(testName, cause);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        log.warn("[McpListener] ⚠ TEST SKIPPED: {}",
                result.getMethod().getMethodName());
    }

    // ── MCP failure analysis ─────────────────────────────────────────────────

    /**
     * When a test fails, ask Claude (via MCP) what might have gone wrong
     * and attach the suggestion to the Allure report.
     */
    private void triggerMcpFailureAnalysis(String testName, Throwable cause) {
        try {
            String mcpEnabled = ConfigReader.getInstance()
                    .get("mcp.enabled", "false");

            if (!"true".equalsIgnoreCase(mcpEnabled)) return;

            String errorSummary = cause != null
                    ? cause.getClass().getSimpleName() + ": " + cause.getMessage()
                    : "Unknown failure";

            String scenario = "Test '" + testName + "' failed with: " + errorSummary
                    + ". Suggest what might have gone wrong and how to fix it "
                    + "in a Selenium Java POM framework testing the Notes web app.";

            String suggestion = McpSeleniumClient.generateTestScript(scenario);

            if (suggestion != null && !suggestion.isBlank()) {
                io.qameta.allure.Allure.addAttachment(
                        "🤖 MCP Failure Analysis — " + testName,
                        "text/plain",
                        "Test: " + testName + "\n"
                                + "Error: " + errorSummary + "\n\n"
                                + "Claude Suggestion:\n" + suggestion
                );
                log.info("[McpListener] MCP failure analysis attached to Allure report");
            }

        } catch (Exception e) {
            log.debug("[McpListener] MCP analysis skipped: {}", e.getMessage());
        }
    }
}