package com.agentdesktop.server.agent.llm;

import com.agentdesktop.server.agent.attachment.PdfLimits;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;

/**
 * Shared helpers for multimodal payloads. Model capability is not inferred here —
 * CPA gateway Payload 规则 and upstream providers decide what each model accepts.
 */
public final class LlmMediaSupport {

    private LlmMediaSupport() {
    }

    public static String effectiveModel(String requestModel, LlmRuntimeConfig config) {
        if (requestModel != null && !requestModel.isBlank()) {
            return requestModel.trim();
        }
        return config.model() == null ? "" : config.model();
    }

    public static int maxInlinePdfTextChars() {
        return PdfLimits.PDF_TARGET_RAW_SIZE;
    }
}
