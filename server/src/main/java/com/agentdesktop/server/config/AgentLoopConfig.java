package com.agentdesktop.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentLoopProperties.class)
public class AgentLoopConfig {
}
