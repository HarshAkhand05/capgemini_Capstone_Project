package com.notes.mcp;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * McpServer — MCP Implementation Layer (Section 3.4)
 *
 * This is the MCP HOST that:
 * 1. Receives plain English prompt via HTTP
 * 2. Sends prompt to LLM (Gemini) — LLM SIDE
 * 3. LLM returns steps to execute
 * 4. Steps are executed via ToolRegistry — TOOL SIDE
 *
 * Real MCP flow:
 *   HTTP Request → LLM decides steps → Tools execute
 */
public class McpServer {

    private static final Logger log =
            LoggerFactory.getLogger(McpServer.class);

    public static void main(String[] args) throws Exception {

        // ── Register all available tools FIRST ──
        // This is the TOOL SIDE of MCP
        registerTools();

        // ── Start HTTP server ──
        HttpServer server = HttpServer.create(
                new InetSocketAddress(8085), 0);

        server.createContext("/execute", exchange -> {

            String query =
                    exchange.getRequestURI().getQuery();

            // Parse prompt from query parameter
            String prompt = parsePrompt(query);

            log.info("[MCP-Server] Received request");
            log.info("[MCP-Server] Prompt: {}", prompt);

            StringBuilder result = new StringBuilder();
            result.append("MCP Execution Report\n");
            result.append("====================\n");
            result.append("Prompt: ").append(prompt).append("\n\n");

            try {
                // ── STEP 1: LLM SIDE ──
                // Send prompt to Gemini, get back steps
                log.info("[MCP-Server] Sending to LLM (Gemini)...");

                GeneratedScript script =
                        AgenticAiClient.generateScript(prompt);

                log.info("[MCP-Server] LLM returned steps: {}",
                        script.getSteps());

                result.append("LLM Steps: ")
                        .append(script.getSteps())
                        .append("\n\n");

                // ── STEP 2: TOOL SIDE ──
                // Execute each step via McpToolExecutor → ToolRegistry
                log.info("[MCP-Server] Executing via Tool Registry...");

                for (String step : script.getSteps()) {

                    log.info("[MCP-Server] Executing tool: {}", step);

                    McpRequest request = new McpRequest();
                    request.setAction(step);
                    request.setValue("");

                    // Goes through ToolRegistry — proper MCP tool execution
                    McpResponse response =
                            McpToolExecutor.execute(request);

                    log.info("[MCP-Server] Tool {} result: {} — {}",
                            step,
                            response.isSuccess() ? "SUCCESS" : "FAILED",
                            response.getMessage());

                    result.append("Step: ").append(step)
                            .append(" → ")
                            .append(response.isSuccess()
                                    ? "✅ SUCCESS" : "❌ FAILED")
                            .append(" — ")
                            .append(response.getMessage())
                            .append("\n");
                }

                result.append("\n✅ MCP Execution Completed");

            } catch (Exception e) {
                log.error("[MCP-Server] Execution failed: {}",
                        e.getMessage());
                result.append("❌ ERROR: ").append(e.getMessage());
            }

            // Send response
            byte[] responseBytes =
                    result.toString().getBytes();

            exchange.sendResponseHeaders(
                    200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        server.start();

        log.info("[MCP-Server] ✅ Started at http://localhost:8085");
        System.out.println("\n[MCP] Server running at http://localhost:8085/execute");
        System.out.println("  → Login:       http://localhost:8085/execute?action=login");
        System.out.println("  → Create note: http://localhost:8085/execute?action=createNote");
        System.out.println("  → Delete note: http://localhost:8085/execute?action=delete");
    }

    // =========================================================
    // TOOL SIDE — Register all tools into ToolRegistry
    // This is what MCP exposes as "available tools"
    // =========================================================

    private static void registerTools() {

        log.info("[MCP-Tools] Registering tools...");

        // LOGIN tool
        ToolRegistry.registerTool("LOGIN", () -> {
            log.info("[MCP-Tool] Executing LOGIN");
            ScriptExecutor.executeLoginOnly();
        });

        // OPEN_ADD_NOTE tool
        ToolRegistry.registerTool("OPEN_ADD_NOTE", () -> {
            log.info("[MCP-Tool] Executing OPEN_ADD_NOTE");
            ScriptExecutor.executeOpenAddNote();
        });

        // CREATE_NOTE tool
        ToolRegistry.registerTool("CREATE_NOTE", () -> {
            log.info("[MCP-Tool] Executing CREATE_NOTE");
            ScriptExecutor.executeCreateNote();
        });

        // DELETE_NOTE tool
        ToolRegistry.registerTool("DELETE_NOTE", () -> {
            log.info("[MCP-Tool] Executing DELETE_NOTE");
            ScriptExecutor.executeDeleteNote();
        });

        log.info("[MCP-Tools] {} tools registered: LOGIN, OPEN_ADD_NOTE, CREATE_NOTE, DELETE_NOTE",
                4);
    }

    // =========================================================
    // Parse prompt from HTTP query string
    // =========================================================

    private static String parsePrompt(String query) {

        if (query == null) return "create note";

        if (query.contains("action=login"))       return "login";
        if (query.contains("action=createNote"))  return "create note";
        if (query.contains("action=delete"))      return "delete note";

        // Support custom prompt: ?prompt=create+a+work+note
        if (query.contains("prompt=")) {
            return query.replace("prompt=", "")
                    .replace("+", " ")
                    .trim();
        }

        return "create note";
    }
}