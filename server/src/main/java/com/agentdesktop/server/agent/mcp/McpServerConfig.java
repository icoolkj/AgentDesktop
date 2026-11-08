package com.agentdesktop.server.agent.mcp;

import java.util.List;
import java.util.Map;

public record McpServerConfig(
        String transport,
        String command,
        List<String> args,
        Map<String, String> env,
        String cwd,
        String url,
        Map<String, String> headers,
        boolean enabled) {

    public McpServerConfig {
        transport = transport == null || transport.isBlank() ? "stdio" : transport;
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /** Backward-compatible constructor without transport/url/headers/enabled. */
    public McpServerConfig(String command, List<String> args, Map<String, String> env, String cwd) {
        this("stdio", command, args, env, cwd, null, Map.of(), true);
    }

    public McpServerConfig(String command, List<String> args, Map<String, String> env, String cwd, boolean enabled) {
        this("stdio", command, args, env, cwd, null, Map.of(), enabled);
    }

    public boolean isRemoteTransport() {
        return "http".equalsIgnoreCase(transport) || "sse".equalsIgnoreCase(transport);
    }
}
