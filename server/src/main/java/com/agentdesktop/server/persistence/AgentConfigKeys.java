package com.agentdesktop.server.persistence;

/**
 * Well-known {@code agent_config.config_key} values and Redis cache prefixes.
 */
public final class AgentConfigKeys {

    public static final String FEATURES = "features";
    public static final String HOOKS_GLOBAL = "hooks_global";

    public static final String REDIS_CONFIG_PREFIX = "agent:config:";
    public static final String REDIS_MCP_ALL = "agent:config:mcp:all";

    private AgentConfigKeys() {
    }

    public static String redisKey(String configKey) {
        return REDIS_CONFIG_PREFIX + configKey;
    }
}
