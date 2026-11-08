package com.agentdesktop.server.agent.mcp;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class McpClientFactory {

    private final McpStdioClient stdioClient;
    private final McpHttpClient httpClient;

    public McpClientFactory(McpStdioClient stdioClient, McpHttpClient httpClient) {
        this.stdioClient = stdioClient;
        this.httpClient = httpClient;
    }

    public McpClient clientFor(McpServerConfig config) {
        String transport = config.transport() == null ? "stdio" : config.transport().toLowerCase(Locale.ROOT);
        if ("http".equals(transport) || "sse".equals(transport)) {
            return httpClient;
        }
        return stdioClient;
    }

    public void shutdownAll() {
        stdioClient.shutdownAll();
        httpClient.shutdownAll();
    }
}
