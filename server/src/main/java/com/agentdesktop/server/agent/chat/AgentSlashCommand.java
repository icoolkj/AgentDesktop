package com.agentdesktop.server.agent.chat;

import com.agentdesktop.server.agent.extension.ExtensionBundle;
import com.agentdesktop.server.agent.extension.SkillContentLoader;
import com.agentdesktop.server.agent.extension.SessionSkillState;
import com.agentdesktop.server.agent.extension.SlashCommandRegistry;
import com.agentdesktop.server.agent.extension.SlashCommandResult;

/**
 * Slash commands handled before {@code queryLoop} — Claude Code REPL parity.
 * Built-ins are fixed; custom commands load from extension directories at runtime.
 */
public sealed interface AgentSlashCommand permits AgentSlashCommand.Compact, AgentSlashCommand.Context, AgentSlashCommand.None {

    record Compact(String instructions) implements AgentSlashCommand {
    }

    record Context() implements AgentSlashCommand {
    }

    record None() implements AgentSlashCommand {
        static final None INSTANCE = new None();
    }

    static AgentSlashCommand parse(String message) {
        SlashCommandResult result = new SlashCommandRegistry(new SkillContentLoader(null), new SessionSkillState())
                .resolve(message, ExtensionBundle.empty());
        if (result instanceof SlashCommandResult.Compact compact) {
            return new Compact(compact.instructions());
        }
        if (result instanceof SlashCommandResult.Context) {
            return new Context();
        }
        return None.INSTANCE;
    }
}
