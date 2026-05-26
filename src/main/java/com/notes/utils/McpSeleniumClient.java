package com.notes.utils;

import com.notes.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * McpSeleniumClient — MCP Implementation Layer (Section 3.4)
 *
 * Uses Google Gemini API as the MCP (Model Context Protocol) backend.
 * When a Selenium locator breaks, this client sends the broken locator + page HTML
 * to Gemini and asks it to suggest a working alternative.
 *
 * Configuration (config.properties):
 *   mcp.api.key=AIzaSyXXXXXXXXXXXXXXXXXX   ← your Gemini API key from aistudio.google.com
 *   mcp.enabled=true
 *
 * Gemini is prompted to reply in a strict format:
 *   css::selector        → By.cssSelector(...)
 *   xpath:://path        → By.xpath(...)
 *   id::element-id       → By.id(...)
 */
public class McpSeleniumClient {

    private static final Logger log = LoggerFactory.getLogger(McpSeleniumClient.class);

    // Gemini API endpoint — using gemini-2.0-flash (free tier)
    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-2.0-flash-lite:generateContent?key=";

    private McpSeleniumClient() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Ask Gemini to suggest a working Selenium locator.
     *
     * @param brokenLocator  The locator string that failed (e.g., "By.id: note-submit")
     * @param pageHtml       The current page source (trimmed to 8000 chars for token efficiency)
     * @return               A locator string in format "css::...", "xpath::...", or "id::..."
     *                       Returns null if MCP is disabled or request fails.
     */
    public static String suggestLocator(String brokenLocator, String pageHtml) {

        if (!isMcpEnabled()) return null;

        String apiKey = getApiKey();
        if (apiKey == null) return null;

        // Trim page HTML to avoid token overflow
        String trimmedHtml = pageHtml != null && pageHtml.length() > 8000
                ? pageHtml.substring(0, 8000) + "\n...[HTML TRIMMED]"
                : pageHtml;

        String prompt = buildLocatorPrompt(brokenLocator, trimmedHtml);

        log.info("[MCP-Gemini] Calling Gemini API to suggest locator for: {}", brokenLocator);

        try {
            String response = callGeminiApi(apiKey, prompt);
            String locator = extractFirstLine(response);
            log.info("[MCP-Gemini] Gemini suggested locator: {}", locator);
            return locator;
        } catch (Exception e) {
            log.error("[MCP-Gemini] Gemini API call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Ask Gemini to generate a Selenium test script / failure fix suggestion.
     * Called by McpTestListener when a test fails.
     *
     * @param scenario  Plain English description of the test scenario or failure
     * @return          Java code snippet or fix suggestion as a String
     */
    public static String generateTestScript(String scenario) {

        if (!isMcpEnabled()) return "// MCP disabled";

        String apiKey = getApiKey();
        if (apiKey == null) return "// No MCP API key configured";

        String prompt = """
                You are a Selenium Java test automation expert.
                Generate a clean Java code snippet for this test scenario:

                Scenario: %s

                Framework details:
                - Uses POM pattern with BasePage
                - WaitUtils for explicit waits
                - RestAssured for API calls
                - TestNG annotations

                Return ONLY the Java method body code. No explanations, no class wrapper.
                """.formatted(scenario);

        try {
            String response = callGeminiApi(apiKey, prompt);
            log.info("[MCP-Gemini] Script generated for scenario: {}", scenario);
            return response;
        } catch (Exception e) {
            log.error("[MCP-Gemini] Script generation failed: {}", e.getMessage());
            return "// MCP script generation failed: " + e.getMessage();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the locator suggestion prompt for Gemini.
     */
    private static String buildLocatorPrompt(String brokenLocator, String pageHtml) {
        return """
                You are a Selenium locator expert.

                A Selenium locator has FAILED and needs to be fixed:
                  Failed locator: %s

                Here is the current page HTML (partial):
                %s

                Your task: Suggest ONE working Selenium locator to find this element.

                Rules:
                - Reply with EXACTLY ONE line in one of these formats:
                    css::your-css-selector
                    xpath:://your-xpath
                    id::element-id
                - No explanations. No extra text. Just the locator line.
                - Prefer data-testid attributes if visible in the HTML.
                - Prefer CSS over XPath when possible.
                """.formatted(brokenLocator, pageHtml);
    }

    /**
     * Makes a POST request to the Gemini generateContent endpoint.
     *
     * Gemini request format:
     * {
     *   "contents": [{ "parts": [{ "text": "..." }] }]
     * }
     *
     * Gemini response format:
     * {
     *   "candidates": [{
     *     "content": {
     *       "parts": [{ "text": "..." }]
     *     }
     *   }]
     * }
     */
    private static String callGeminiApi(String apiKey, String userMessage) throws Exception {

        String url = GEMINI_API_URL + apiKey;

        // Build Gemini request JSON
        String requestBody = "{"
                + "\"contents\": [{"
                + "  \"parts\": [{"
                + "    \"text\": " + jsonEscape(userMessage)
                + "  }]"
                + "}]"
                + "}";

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();

        if (status != 200) {
            // Read error body for better logging
            try (Scanner sc = new Scanner(conn.getErrorStream(), StandardCharsets.UTF_8)) {
                String errorBody = sc.useDelimiter("\\A").next();
                log.error("[MCP-Gemini] API error (HTTP {}): {}", status, errorBody);
            }
            throw new RuntimeException("Gemini API returned HTTP " + status);
        }

        String rawResponse;
        try (Scanner sc = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
            rawResponse = sc.useDelimiter("\\A").next();
        }

        log.debug("[MCP-Gemini] Raw response: {}", rawResponse);

        return extractTextFromGeminiResponse(rawResponse);
    }

    /**
     * Extracts the text content from Gemini's JSON response.
     *
     * Gemini response structure:
     *   {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}
     *
     * We locate the "text" field inside "parts" to get the answer.
     */
    private static String extractTextFromGeminiResponse(String jsonResponse) {

        // Find "parts" array then the "text" field inside it
        int partsIndex = jsonResponse.indexOf("\"parts\"");
        if (partsIndex == -1) {
            log.warn("[MCP-Gemini] Could not find 'parts' in response");
            return "";
        }

        int textIndex = jsonResponse.indexOf("\"text\"", partsIndex);
        if (textIndex == -1) {
            log.warn("[MCP-Gemini] Could not find 'text' in response");
            return "";
        }

        // Skip past  "text":  and the opening quote
        int colonIndex = jsonResponse.indexOf(":", textIndex);
        int startQuote = jsonResponse.indexOf("\"", colonIndex) + 1;

        // Find closing quote (handle escaped quotes inside the value)
        int endQuote = startQuote;
        while (endQuote < jsonResponse.length()) {
            char c = jsonResponse.charAt(endQuote);
            if (c == '"' && jsonResponse.charAt(endQuote - 1) != '\\') break;
            endQuote++;
        }

        if (startQuote >= endQuote) return "";

        return jsonResponse.substring(startQuote, endQuote)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim();
    }

    /**
     * Returns the first non-blank line from the extracted text.
     * Used for locator suggestions (should be a single line).
     */
    private static String extractFirstLine(String text) {
        if (text == null || text.isBlank()) return "";
        for (String line : text.split("\n")) {
            if (!line.isBlank()) return line.trim();
        }
        return text.trim();
    }

    /**
     * Checks if MCP is enabled in config.properties.
     */
    private static boolean isMcpEnabled() {
        String enabled = ConfigReader.getInstance().get("mcp.enabled", "false");
        if (!"true".equalsIgnoreCase(enabled)) {
            log.info("[MCP-Gemini] MCP is disabled (mcp.enabled=false). Skipping.");
            return false;
        }
        return true;
    }

    /**
     * Reads and validates the API key from config.properties.
     */
    private static String getApiKey() {
        String apiKey = ConfigReader.getInstance().get("mcp.api.key", "");
        if (apiKey.isBlank()) {
            log.warn("[MCP-Gemini] No API key found (mcp.api.key). Skipping MCP call.");
            return null;
        }
        return apiKey;
    }

    /**
     * Minimal JSON string escaping — wraps text in quotes and escapes specials.
     */
    private static String jsonEscape(String text) {
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

}