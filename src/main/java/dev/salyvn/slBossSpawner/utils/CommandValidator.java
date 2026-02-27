package dev.salyvn.slBossSpawner.utils;

import java.util.regex.Pattern;

/**
 * Validates commands to prevent injection via dangerous shell characters.
 */
public final class CommandValidator {
    private static final Pattern DANGEROUS_CHARS = Pattern.compile("[;&|`$]");

    private CommandValidator() {}

    public static boolean isValid(String command) {
        return command != null && !DANGEROUS_CHARS.matcher(command).find();
    }
}
