package com.agentdesktop.server.agent.extension;

import java.util.ArrayList;
import java.util.List;

/** Formats skill catalog entries within a character budget (CC skill_listing parity). */
public final class SkillListingFormatter {

    public static final int DEFAULT_CHAR_BUDGET = 8_000;
    public static final int MAX_LISTING_DESC_CHARS = 250;

    private SkillListingFormatter() {
    }

    public static String format(List<SkillEntry> skills, int charBudget) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        List<String> fullLines = new ArrayList<>();
        for (SkillEntry skill : skills) {
            if (!skill.modelInvocable()) {
                continue;
            }
            fullLines.add("- " + skill.name() + ": " + truncateDescription(skill.listingDescription()));
        }
        if (fullLines.isEmpty()) {
            return "";
        }

        int fullTotal = fullLines.stream().mapToInt(String::length).sum() + Math.max(0, fullLines.size() - 1);
        if (fullTotal <= charBudget) {
            return String.join("\n", fullLines);
        }

        List<String> nameOnly = fullLines.stream()
                .map(line -> line.substring(0, line.indexOf(':')))
                .toList();
        int nameTotal = nameOnly.stream().mapToInt(String::length).sum() + Math.max(0, nameOnly.size() - 1);
        if (nameTotal <= charBudget) {
            return String.join("\n", nameOnly);
        }

        StringBuilder out = new StringBuilder();
        for (String line : nameOnly) {
            if (out.length() + line.length() + 1 > charBudget) {
                break;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString();
    }

    private static String truncateDescription(String description) {
        if (description == null) {
            return "";
        }
        if (description.length() <= MAX_LISTING_DESC_CHARS) {
            return description;
        }
        return description.substring(0, MAX_LISTING_DESC_CHARS - 1) + "\u2026";
    }
}
