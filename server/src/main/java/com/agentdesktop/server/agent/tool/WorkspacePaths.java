package com.agentdesktop.server.agent.tool;

import java.nio.file.Path;
import java.util.List;

public final class WorkspacePaths {

    private WorkspacePaths() {
    }

    public static Path resolve(ToolContext context, String rawPath) {
        if (!context.hasWorkspace()) {
            throw new IllegalArgumentException("Workspace root is not set. Open a folder in the IDE first.");
        }
        Path workspace = context.workspacePath().toAbsolutePath().normalize();
        Path target = Path.of(rawPath == null ? "" : rawPath.trim());
        if (!target.isAbsolute()) {
            target = workspace.resolve(target);
        }
        target = target.toAbsolutePath().normalize();
        if (!target.startsWith(workspace)) {
            if (isUnderAttachmentReadRoot(target, context.attachmentReadRootsOrEmpty())) {
                return target;
            }
            throw new IllegalArgumentException("Path escapes workspace: " + rawPath);
        }
        return target;
    }

    /** CC additionalWorkingDirectories parity — AD attachment store under ~/.agent-desktop/projects/.../attachments/ */
    static boolean isUnderAttachmentReadRoot(Path target, List<String> roots) {
        for (String root : roots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            try {
                Path base = Path.of(root).toAbsolutePath().normalize();
                if (target.equals(base) || target.startsWith(base)) {
                    return true;
                }
            } catch (Exception ignored) {
                // skip malformed roots
            }
        }
        return false;
    }
}
