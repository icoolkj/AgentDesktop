package com.agentdesktop.server.agent.llm;

import java.util.List;

public record LlmChatRequest(
        List<LlmMessage> messages,
        String model,
        List<LlmToolDefinition> tools,
        String toolChoice,
        Double temperature,
        Integer maxTokens,
        boolean stream
) {
    public LlmChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<LlmMessage> messages = List.of();
        private String model;
        private List<LlmToolDefinition> tools = List.of();
        private String toolChoice;
        private Double temperature;
        private Integer maxTokens;
        private boolean stream;

        public Builder messages(List<LlmMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder tools(List<LlmToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        /** OpenAI {@code tool_choice}: {@code auto}, {@code none}, or {@code required}. */
        public Builder toolChoice(String toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        public LlmChatRequest build() {
            return new LlmChatRequest(messages, model, tools, toolChoice, temperature, maxTokens, stream);
        }
    }
}
