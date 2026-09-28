package dev.salyvn.slBossSpawner.reward;

import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the exact online player name used by other plugins' commands. */
public final class RewardPlayerName {
    // A command argument must stay a single token. Preserve Floodgate's default
    // '.' prefix, but reject wildcard and command punctuation without rewriting it.
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern CMI_SAVED_ITEMS = Pattern.compile(
            "(?i)^(\\s*cmi\\s+saveditems\\s+get\\b.*?\\s+-t:)(\\S+)(.*)$");
    private static final Pattern CMI_MONEY = Pattern.compile(
            "(?i)^(\\s*cmi\\s+money\\s+give\\s+)(\\S+)(.*)$");
    private static final Pattern MYTHIC_DROP_TABLE = Pattern.compile(
            "(?i)^(\\s*mm\\s+items\\s+droptable\\s+\\S+\\s+\\d+\\s+)(\\S+)(.*)$");

    private RewardPlayerName() {}

    /** Returns null if the server name cannot safely be used as a command argument. */
    public static String resolve(Player player) {
        if (player == null) return null;
        String name = player.getName();
        return name != null && SAFE_NAME.matcher(name).matches() ? name : null;
    }

    /** Resolve new templates and repair known CMI/Mythic commands queued by old releases. */
    public static String resolvePendingCommand(String command, String exactName) {
        if (command.contains("{player}")) return command.replace("{player}", exactName);

        // Older releases expanded {player} before queuing, stripping every
        // character except ASCII letters, digits, and underscore. Only rewrite
        // a recognized player argument when it matches that exact old value.
        String legacyName = exactName.replaceAll("[^A-Za-z0-9_]", "");
        if (legacyName.equals(exactName) || legacyName.isEmpty()) return command;
        for (Pattern pattern : new Pattern[]{CMI_SAVED_ITEMS, CMI_MONEY, MYTHIC_DROP_TABLE}) {
            Matcher match = pattern.matcher(command);
            if (match.matches() && legacyName.equals(match.group(2))) {
                return match.group(1) + exactName + match.group(3);
            }
        }
        return command;
    }
}
