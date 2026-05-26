package com.notes.mcp;

public class McpResponse {

    private boolean success;
    private String message;

    public McpResponse(
            boolean success,
            String message) {

        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}