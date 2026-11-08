package com.agentdesktop.server.agent.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class HookRegistry {

    private final ObjectMapper objectMapper;

    public HookRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<HookDefinition> loadFromFiles(List<Path> files) {
        List<HookDefinition> hooks = new ArrayList<>();
        for (Path file : files) {
            try {
                JsonNode root = objectMapper.readTree(Files.readString(file));
                JsonNode hooksNode = root.path("hooks");
                if (hooksNode.isMissingNode() || !hooksNode.isObject()) {
                    continue;
                }
                hooks.addAll(parseHooksObject(hooksNode));
            } catch (Exception ignored) {
                // skip malformed hook config
            }
        }
        return List.copyOf(hooks);
    }

    private List<HookDefinition> parseHooksObject(JsonNode hooksNode) {
        List<HookDefinition> result = new ArrayList<>();
        Iterator<String> events = hooksNode.fieldNames();
        while (events.hasNext()) {
            String event = events.next();
            JsonNode rules = hooksNode.get(event);
            if (!rules.isArray()) {
                continue;
            }
            for (JsonNode rule : rules) {
                String matcher = rule.path("matcher").asText(".*");
                List<HookDefinition.HookAction> actions = new ArrayList<>();
                JsonNode hookList = rule.path("hooks");
                if (!hookList.isArray()) {
                    continue;
                }
                for (JsonNode hook : hookList) {
                    String type = hook.path("type").asText("command");
                    if (!"command".equalsIgnoreCase(type)) {
                        continue;
                    }
                    String command = hook.path("command").asText("");
                    if (command.isBlank()) {
                        continue;
                    }
                    int timeout = hook.path("timeout").asInt(30);
                    actions.add(new HookDefinition.HookAction(type, command, timeout));
                }
                if (!actions.isEmpty()) {
                    result.add(new HookDefinition(event, matcher, actions));
                }
            }
        }
        return result;
    }
}
