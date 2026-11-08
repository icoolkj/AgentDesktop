package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.llm.LlmContentPart;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmRole;
import com.agentdesktop.server.agent.tool.builtin.SkillTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops oldest tool blocks when over budget — Claude Code microcompact / autocompact first stage.
 * Skill invocation blocks are preserved (CC invokedSkills parity).
 */
public final class AgentContextCompactor {

    private AgentContextCompactor() {
    }

    public static List<LlmMessage> compact(List<LlmMessage> messages, int maxEstimatedChars) {
        List<LlmMessage> working = new ArrayList<>(messages);
        while (estimateChars(working) > maxEstimatedChars) {
            int dropAt = findOldestRemovableToolBlockStart(working);
            if (dropAt < 0) {
                break;
            }
            removeToolBlock(working, dropAt);
        }
        return working;
    }

    public static boolean isProtectedSkillMessage(LlmMessage message) {
        if (message == null) {
            return false;
        }
        if (message.role() == LlmRole.USER && message.content() != null) {
            String content = message.content();
            if (content.contains("<system-reminder>")
                    && (content.contains("Previously invoked skill")
                    || content.contains("Launching skill")
                    || content.contains("Base directory for this skill"))) {
                return true;
            }
        }
        if (message.role() == LlmRole.TOOL && SkillTool.TOOL_NAME.equals(message.toolName())) {
            return true;
        }
        return false;
    }

    public static int estimateChars(List<LlmMessage> messages) {
        int total = 0;
        for (LlmMessage message : messages) {
            if (!message.contentParts().isEmpty()) {
                for (LlmContentPart part : message.contentParts()) {
                    if ("text".equals(part.type()) && part.text() != null) {
                        total += part.text().length();
                    } else if ("image_url".equals(part.type()) || "image".equals(part.type())) {
                        total += 2_000;
                    } else if ("document".equals(part.type())) {
                        if (part.documentBase64() != null) {
                            total += Math.min(part.documentBase64().length(), 50_000);
                        } else {
                            total += 10_000;
                        }
                    }
                }
            } else if (message.content() != null) {
                total += message.content().length();
            }
            total += message.toolCalls().size() * 128;
        }
        return total;
    }

    private static int findOldestRemovableToolBlockStart(List<LlmMessage> messages) {
        for (int i = 0; i < messages.size(); i++) {
            LlmMessage message = messages.get(i);
            if (message.role() == LlmRole.ASSISTANT && !message.toolCalls().isEmpty()) {
                if (isProtectedToolBlock(messages, i)) {
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    private static boolean isProtectedToolBlock(List<LlmMessage> messages, int assistantIndex) {
        LlmMessage assistant = messages.get(assistantIndex);
        if (assistant.toolCalls().stream().anyMatch(call -> SkillTool.TOOL_NAME.equals(call.name()))) {
            return true;
        }
        int end = assistantIndex + 1;
        while (end < messages.size() && messages.get(end).role() == LlmRole.TOOL) {
            end++;
        }
        if (end < messages.size() && isProtectedSkillMessage(messages.get(end))) {
            return true;
        }
        return false;
    }

    private static void removeToolBlock(List<LlmMessage> messages, int assistantIndex) {
        int end = assistantIndex + 1;
        while (end < messages.size() && messages.get(end).role() == LlmRole.TOOL) {
            end++;
        }
        if (end < messages.size() && messages.get(end).role() == LlmRole.USER) {
            String content = messages.get(end).content();
            if (content != null && content.contains("<system-reminder>") && content.contains("skill")) {
                end++;
            }
        }
        messages.subList(assistantIndex, end).clear();
    }
}
