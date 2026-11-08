package com.agentdesktop.server.agent.extension;

/** CC-aligned skill injection wrappers (user role, not API system). */
public final class SkillMessages {

    private SkillMessages() {
    }

    public static String wrapSystemReminder(String content) {
        return "<system-reminder>\n" + content + "\n</system-reminder>";
    }

    public static String skillListingMessage(String listingBody) {
        return wrapSystemReminder(
                "The following skills are available for use with the Skill tool:\n\n" + listingBody);
    }
}
