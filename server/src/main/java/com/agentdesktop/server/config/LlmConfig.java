package com.agentdesktop.server.config;

import com.agentdesktop.server.agent.llm.LlmProvider;
import com.agentdesktop.server.agent.llm.openai.OpenAiCompatibleLlmProvider;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    @ConditionalOnMissingBean(LlmProvider.class)
    public LlmProvider llmProvider(LlmSettingsService llmSettingsService,
                                   com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new OpenAiCompatibleLlmProvider(llmSettingsService, objectMapper);
    }
}
