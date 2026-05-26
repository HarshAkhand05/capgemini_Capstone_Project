package com.notes.mcp;

import java.util.HashMap;
import java.util.Map;

public class ToolRegistry {

    private static final Map<String, Runnable> tools =
            new HashMap<>();

    public static void registerTool(
            String name,
            Runnable action) {

        tools.put(name, action);
    }

    public static Runnable getTool(
            String name) {

        return tools.get(name);
    }
}