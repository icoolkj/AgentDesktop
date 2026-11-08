package com.agentdesktop.server.agent.extension;

import com.agentdesktop.server.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bash / terminal command sandbox — pattern denylist + workspace guard.
 * Stricter than Claude Code regex baseline: blocks common download-exec and privilege-escalation patterns.
 */
@Component
public class BashSandboxService {

    private static final Pattern[] STRICT_DENY = {
            Pattern.compile("(?i)\\brm\\s+-rf\\s+[/~\\\\]"),
            Pattern.compile("(?i)\\bformat\\s+[a-z]:"),
            Pattern.compile("(?i)\\bdel\\s+/[fq]"),
            Pattern.compile("(?i)\\b(shutdown|reboot|mkfs)\\b"),
            Pattern.compile("(?i)\\bcurl\\s+.*\\|\\s*(ba)?sh"),
            Pattern.compile("(?i)\\bwget\\s+.*\\|\\s*(ba)?sh"),
            Pattern.compile("(?i)\\bsource\\s+.*<(curl|wget)"),
            Pattern.compile("(?i)>{1,2}\\s*/etc/"),
            Pattern.compile("(?i)\\bchmod\\s+[0-7]*777\\b"),
            Pattern.compile("(?i)\\bsudo\\b"),
            Pattern.compile("(?i)\\bdoas\\b"),
            Pattern.compile("(?i)\\brunas\\b"),
            Pattern.compile("(?i)\\bdd\\s+if="),
            Pattern.compile("(?i)\\bnc\\s+.*\\s-e\\s"),
            Pattern.compile("(?i)\\bnetcat\\b"),
            Pattern.compile("(?i)\\bcmd\\.exe\\s+/c"),
            Pattern.compile("(?i)\\bpowershell\\b"),
            Pattern.compile("(?i)\\bpwsh\\b"),
            Pattern.compile("(?i)\\binvoke-expression\\b"),
            Pattern.compile("(?i)\\biex\\s"),
            Pattern.compile("(?i)\\beval\\s+"),
            Pattern.compile("(?i)\\bpython\\s+-c\\s+.*\\|\\s*(ba)?sh"),
            Pattern.compile("(?i)\\bnode\\s+-e\\s+.*\\|\\s*(ba)?sh"),
            Pattern.compile("(?i)\\bbash\\s+-c\\s+.*\\|\\s*(ba)?sh"),
            Pattern.compile("(?i)\\bsh\\s+-c\\s+.*\\|\\s*(ba)?sh"),
            Pattern.compile("(?i)\\bcurl\\s+[^|]+\\s+-o\\s+.*\\|\\s*(ba)?sh"),
    };

    private static final Pattern CD_PARENT = Pattern.compile("(?i)\\bcd\\s+\\.\\.");
    private static final Pattern WINDOWS_ABS = Pattern.compile("(?i)([A-Za-z]:\\\\[^\\s\"'|;&<>]+)");
    private static final Pattern UNIX_ABS = Pattern.compile("(?<!\\w)(/(?:[^\\s\"'|;&<>]+)+)");

    public BashValidation validate(String command, AgentFeatureSettings features, String workspaceRoot) {
        if (command == null || command.isBlank()) {
            return BashValidation.allow();
        }
        if (features == null || !features.bashSandboxEnabled()) {
            return BashValidation.allow();
        }
        String mode = features.bashSandboxMode() == null ? "strict" : features.bashSandboxMode().trim().toLowerCase();
        if ("off".equals(mode)) {
            return BashValidation.allow();
        }

        for (Pattern pattern : STRICT_DENY) {
            if (pattern.matcher(command).find()) {
                return applyMode(BashValidation.deny("Sandbox blocked dangerous command pattern"), mode);
            }
        }

        if (workspaceRoot != null && !workspaceRoot.isBlank()) {
            BashValidation workspaceCheck = validateWorkspaceScope(command, workspaceRoot);
            if (!workspaceCheck.allowed()) {
                return applyMode(workspaceCheck, mode);
            }
        }

        return BashValidation.allow();
    }

    private static BashValidation validateWorkspaceScope(String command, String workspaceRoot) {
        Path root;
        try {
            root = Path.of(workspaceRoot).toAbsolutePath().normalize();
        } catch (Exception e) {
            return BashValidation.allow();
        }

        if (CD_PARENT.matcher(command).find()) {
            return BashValidation.deny("Sandbox blocked cd outside workspace");
        }

        Matcher winMatcher = WINDOWS_ABS.matcher(command);
        while (winMatcher.find()) {
            BashValidation check = pathInsideWorkspace(winMatcher.group(1), root);
            if (!check.allowed()) {
                return check;
            }
        }

        Matcher unixMatcher = UNIX_ABS.matcher(command);
        while (unixMatcher.find()) {
            BashValidation check = pathInsideWorkspace(unixMatcher.group(1), root);
            if (!check.allowed()) {
                return check;
            }
        }

        return BashValidation.allow();
    }

    private static BashValidation pathInsideWorkspace(String rawPath, Path workspaceRoot) {
        if (rawPath == null || rawPath.isBlank()) {
            return BashValidation.allow();
        }
        try {
            Path candidate = Path.of(rawPath.trim()).toAbsolutePath().normalize();
            if (!candidate.startsWith(workspaceRoot)) {
                return BashValidation.deny("Sandbox blocked path outside workspace: " + candidate);
            }
        } catch (Exception ignored) {
            // ignore unparseable tokens
        }
        return BashValidation.allow();
    }

    private static BashValidation applyMode(BashValidation validation, String mode) {
        if (validation.allowed() || !"warn".equals(mode)) {
            return validation;
        }
        return BashValidation.warn(validation.message());
    }

    public record BashValidation(boolean allowed, boolean warning, String message) {
        public static BashValidation allow() {
            return new BashValidation(true, false, null);
        }

        public static BashValidation deny(String message) {
            return new BashValidation(false, false, message);
        }

        public static BashValidation warn(String message) {
            return new BashValidation(true, true, message);
        }

        public ToolResult toToolPreface() {
            if (message == null || message.isBlank()) {
                return null;
            }
            if (!allowed) {
                return ToolResult.failure(message);
            }
            if (warning) {
                return ToolResult.success("[sandbox warning] " + message);
            }
            return null;
        }
    }
}
