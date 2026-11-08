package com.agentdesktop.server.agent.llm;

public class LlmException extends RuntimeException {

    private final int statusCode;

    public LlmException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public LlmException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
