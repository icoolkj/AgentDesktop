package com.agentdesktop.server.agent.llm.openai;

import com.agentdesktop.server.agent.llm.LlmContentPart;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiRequestMapperMultimodalTest {

    private static final LlmRuntimeConfig CPA_CONFIG = new LlmRuntimeConfig(
            true,
            "openai-compatible",
            "http://127.0.0.1:8317/v1",
            "test-key",
            "claude-sonnet-4-20250514",
            0.2,
            8192,
            120,
            true,
            true
    );

    private static final LlmRuntimeConfig OPENAI_CONFIG = new LlmRuntimeConfig(
            true,
            "openai-compatible",
            "https://api.openai.com/v1",
            "test-key",
            "gpt-4o",
            0.2,
            8192,
            120,
            true,
            true
    );

    private static JsonNode toApiMessage(LlmMessage message, LlmRuntimeConfig config) throws Exception {
        Method method = OpenAiRequestMapper.class.getDeclaredMethod(
                "toApiMessage", ObjectMapper.class, LlmMessage.class, LlmRuntimeConfig.class);
        method.setAccessible(true);
        ObjectMapper mapper = new ObjectMapper();
        return (JsonNode) method.invoke(null, mapper, message, config);
    }

    @Test
    void cpaImageUsesAnthropicBlock() throws Exception {
        LlmMessage message = LlmMessage.userMultimodal(
                "describe this",
                List.of(
                        LlmContentPart.text("describe this"),
                        LlmContentPart.image("image/png", "abc")
                ));

        JsonNode node = toApiMessage(message, CPA_CONFIG);
        assertEquals("image", node.path("content").get(1).path("type").asText());
        assertEquals("base64", node.path("content").get(1).path("source").path("type").asText());
        assertEquals("image/png", node.path("content").get(1).path("source").path("media_type").asText());
        assertEquals("abc", node.path("content").get(1).path("source").path("data").asText());
    }

    @Test
    void openAiImageUsesImageUrlBlock() throws Exception {
        LlmMessage message = LlmMessage.userMultimodal(
                "describe this",
                List.of(
                        LlmContentPart.text("describe this"),
                        LlmContentPart.image("image/png", "abc")
                ));

        JsonNode node = toApiMessage(message, OPENAI_CONFIG);
        assertEquals("image_url", node.path("content").get(1).path("type").asText());
        assertEquals("data:image/png;base64,abc", node.path("content").get(1).path("image_url").path("url").asText());
    }

    @Test
    void pdfDocumentUsesAnthropicDocumentBlock() throws Exception {
        LlmMessage message = LlmMessage.userMultimodal(
                "read pdf",
                List.of(
                        LlmContentPart.text("read pdf"),
                        LlmContentPart.document("application/pdf", "JVBERi0")
                ));

        JsonNode node = toApiMessage(message, CPA_CONFIG);
        assertEquals("document", node.path("content").get(1).path("type").asText());
        assertEquals("base64", node.path("content").get(1).path("source").path("type").asText());
        assertEquals("application/pdf", node.path("content").get(1).path("source").path("media_type").asText());
    }

    @Test
    void plainUserMessageUsesStringContent() throws Exception {
        JsonNode node = toApiMessage(LlmMessage.user("hello"), CPA_CONFIG);
        assertEquals("hello", node.path("content").asText());
    }

    @Test
    void useAnthropicBlocksForCpaPort() {
        assertTrue(OpenAiRequestMapper.useAnthropicContentBlocks(CPA_CONFIG));
    }
}
