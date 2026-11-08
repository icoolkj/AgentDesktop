package com.agentdesktop.server.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动完成后向 stdout 输出 AGENT_READY，供 Electron 主进程解析（与 Node 版协议一致）。
 */
@Component
public class AgentReadyNotifier implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${agent.ws-path:/ws}")
    private String wsPath;

    private final Environment environment;

    public AgentReadyNotifier(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String port = environment.getProperty("local.server.port", "8080");
        String wsUrl = "ws://127.0.0.1:" + port + wsPath;
        String payload = String.format(
                "{\"type\":\"agent-ready\",\"port\":%s,\"wsUrl\":\"%s\"}",
                port, wsUrl);
        System.out.println("AGENT_READY " + payload);
        System.out.flush();
    }
}
