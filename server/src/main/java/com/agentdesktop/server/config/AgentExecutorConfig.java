package com.agentdesktop.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated workers for agent chat — avoids {@link java.util.concurrent.ForkJoinPool#commonPool()}
 * starvation when LLM/tool calls block with {@code CompletableFuture.join()}.
 * Claude Code runs the query loop on the main async chain; we isolate blocking work here.
 */
@Configuration
public class AgentExecutorConfig {

    public static final String AGENT_EXECUTOR_BEAN = "agentExecutor";

    @Bean(name = AGENT_EXECUTOR_BEAN)
    public ExecutorService agentExecutor() {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "agent-worker-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(8, factory);
    }
}
