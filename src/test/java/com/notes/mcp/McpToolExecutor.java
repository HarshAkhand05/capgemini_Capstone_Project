package com.notes.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpToolExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(
                    McpToolExecutor.class
            );

    public static McpResponse execute(
            McpRequest request) {

        Runnable tool =
                ToolRegistry.getTool(
                        request.getAction()
                );

        if (tool == null) {

            return new McpResponse(
                    false,
                    "Tool not found"
            );
        }

        try {

            tool.run();

            return new McpResponse(
                    true,
                    "Tool executed successfully"
            );

        } catch (Exception e) {

            log.error(
                    "MCP execution failed",
                    e
            );

            return new McpResponse(
                    false,
                    e.getMessage()
            );
        }
    }
}