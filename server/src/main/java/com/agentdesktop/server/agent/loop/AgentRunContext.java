package com.agentdesktop.server.agent.loop;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per WebSocket session run state — supports cooperative cancellation (Cursor / Claude Code stop).
 */
public final class AgentRunContext {

    private final String wsSessionId;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile String modelOverride;
    private volatile String transcriptSessionId;

    public AgentRunContext(String wsSessionId) {
        this.wsSessionId = wsSessionId;
    }

    public void setTranscriptSessionId(String transcriptSessionId) {
        this.transcriptSessionId = transcriptSessionId;
    }

    public String transcriptSessionId() {
        return transcriptSessionId;
    }

    public void setModelOverride(String model) {
        this.modelOverride = model;
    }

    public String modelOverride() {
        return modelOverride;
    }

    public String wsSessionId() {
        return wsSessionId;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void checkNotCancelled() {
        if (cancelled.get()) {
            throw new AgentLoopCancelledException();
        }
    }
}
