package com.notes.mcp;

import com.notes.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * AgenticAiClient — LLM Side of MCP
 *
 * This class connects to Gemini API (LLM side).
 * It sends a plain English prompt to Gemini and
 * gets back a list of steps to execute on the tool side.
 *
 * MCP Architecture:
 *   User Prompt → AgenticAiClient (LLM) → GeneratedScript (steps)
 *                                               ↓
 *                                        ScriptExecutor (Tool)
 *                                               ↓
 *                                        Selenium Actions
 */
public class AgenticAiClient {

    private static final Logger log =
            LoggerFactory.getLogger(AgenticAiClient.class);

    // Gemini API endpoint
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-2.0-flash-lite:generateContent?key=";

    /**
     * Sends prompt to Gemini LLM and gets back execution steps.
     * This is the LLM SIDE of MCP.
     *
     * @param prompt  Plain English instruction
     * @return        GeneratedScript containing steps for tool side
     */
    public static GeneratedScript generateScript(String prompt) {

        log.info("[MCP-LLM] Received prompt: {}", prompt);

        // ── Try Gemini LLM first ──
        String apiKey = ConfigReader.getInstance()
                .get("mcp.api.key", "");

        if (!apiKey.isBlank()) {
            try {
                List<String> steps = callGeminiForSteps(prompt, apiKey);
                if (!steps.isEmpty()) {
                    log.info("[MCP-LLM] Gemini returned steps: {}", steps);
                    return new GeneratedScript(steps);
                }
            } catch (Exception e) {
                log.warn("[MCP-LLM] Gemini call failed: {} — falling back to keyword match",
                        e.getMessage());
            }
        } else {
            log.warn("[MCP-LLM] No API key found — using keyword matching fallback");
        }

        // ── Fallback: keyword matching if Gemini unavailable ──
        return keywordFallback(prompt);
    }

    // ── LLM SIDE — Gemini API Call ────────────────────────────────────────

    /**
     * Calls Gemini API with the prompt.
     * Asks Gemini to return ONLY step names from a fixed set.
     * This ensures tool side can execute them reliably.
     */
    private static List<String> callGeminiForSteps(
            String prompt, String apiKey) throws Exception {

        String systemPrompt = """
                You are an automation script generator for a Notes web application.
                
                Based on the user's instruction, return ONLY the steps needed
                from this exact list (one per line, no explanations):
                
                Available steps:
                  LOGIN
                  OPEN_ADD_NOTE
                  CREATE_NOTE
                  DELETE_NOTE
                
                Rules:
                - Return ONLY step names, one per line
                - Always include LOGIN as first step if any action is needed
                - For creating a note: LOGIN, OPEN_ADD_NOTE, CREATE_NOTE
                - For deleting a note: LOGIN, DELETE_NOTE
                - For just login: LOGIN
                - No extra text, no explanations, no numbering
                
                User instruction: %s
                """.formatted(prompt);

        String requestBody = "{"
                + "\"contents\": [{"
                + "  \"parts\": [{"
                + "    \"text\": " + jsonEscape(systemPrompt)
                + "  }]"
                + "}]"
                + "}";

        String url = GEMINI_URL + apiKey;

        HttpURLConnection conn =
                (HttpURLConnection) new URL(url).openConnection();
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
            try (Scanner sc = new Scanner(
                    conn.getErrorStream(), StandardCharsets.UTF_8)) {
                String err = sc.useDelimiter("\\A").next();
                log.error("[MCP-LLM] Gemini error (HTTP {}): {}", status, err);
            }
            throw new RuntimeException("Gemini API returned HTTP " + status);
        }

        String rawResponse;
        try (Scanner sc = new Scanner(
                conn.getInputStream(), StandardCharsets.UTF_8)) {
            rawResponse = sc.useDelimiter("\\A").next();
        }

        log.debug("[MCP-LLM] Gemini raw response: {}", rawResponse);

        // Extract text from Gemini response
        String geminiText = extractTextFromResponse(rawResponse);
        log.info("[MCP-LLM] Gemini suggested steps text: {}", geminiText);

        // Parse steps from Gemini response
        return parseStepsFromText(geminiText);
    }

    /**
     * Parses Gemini's response text into a list of valid steps.
     * Only accepts steps from the known valid set.
     */
    private static List<String> parseStepsFromText(String text) {

        List<String> validSteps = List.of(
                "LOGIN", "OPEN_ADD_NOTE", "CREATE_NOTE", "DELETE_NOTE"
        );

        List<String> steps = new ArrayList<>();

        for (String line : text.split("\n")) {
            String step = line.trim().toUpperCase()
                    .replaceAll("[^A-Z_]", "");

            if (validSteps.contains(step)) {
                steps.add(step);
                log.info("[MCP-LLM] Parsed valid step: {}", step);
            }
        }

        return steps;
    }

    /**
     * Extracts text content from Gemini JSON response.
     * Response format: candidates[0].content.parts[0].text
     */
    private static String extractTextFromResponse(String jsonResponse) {

        int partsIndex = jsonResponse.indexOf("\"parts\"");
        if (partsIndex == -1) return "";

        int textIndex = jsonResponse.indexOf("\"text\"", partsIndex);
        if (textIndex == -1) return "";

        int colonIndex = jsonResponse.indexOf(":", textIndex);
        int startQuote = jsonResponse.indexOf("\"", colonIndex) + 1;

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

    // ── TOOL SIDE FALLBACK — Keyword Matching ────────────────────────────

    /**
     * Fallback when Gemini API is unavailable.
     * Uses simple keyword matching to determine steps.
     * This is the TOOL SIDE logic.
     */
    private static GeneratedScript keywordFallback(String prompt) {

        log.info("[MCP-Tool] Using keyword fallback for prompt: {}", prompt);

        List<String> steps = new ArrayList<>();
        String lowerPrompt = prompt.toLowerCase();

        if (lowerPrompt.contains("login")) {
            steps.add("LOGIN");
        }

        if (lowerPrompt.contains("create note")) {
            steps.clear();
            steps.add("LOGIN");
            steps.add("OPEN_ADD_NOTE");
            steps.add("CREATE_NOTE");
        }

        if (lowerPrompt.contains("delete note")) {
            steps.clear();
            steps.add("LOGIN");
            steps.add("DELETE_NOTE");
        }

        if (steps.isEmpty()) {
            log.warn("[MCP-Tool] No steps matched prompt: {}", prompt);
        } else {
            log.info("[MCP-Tool] Keyword fallback steps: {}", steps);
        }

        return new GeneratedScript(steps);
    }

    // ── Helper ────────────────────────────────────────────────────────────

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