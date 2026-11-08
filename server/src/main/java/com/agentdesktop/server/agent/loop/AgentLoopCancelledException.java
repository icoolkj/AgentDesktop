package com.agentdesktop.server.agent.loop;

/** Thrown when the user stops generation mid-run. */
public final class AgentLoopCancelledException extends RuntimeException {

    public AgentLoopCancelledException() {
        super("Generation cancelled");
    }
}
