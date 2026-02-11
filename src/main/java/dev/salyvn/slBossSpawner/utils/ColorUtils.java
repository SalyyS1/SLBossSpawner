package dev.salyvn.slBossSpawner.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String colorize(String message) {
        if (message == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 4 * 8);

        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, "§x§" + group.charAt(0) + "§" + group.charAt(1) + "§" +
                    group.charAt(2) + "§" + group.charAt(3) + "§" + group.charAt(4) + "§" + group.charAt(5));
        }

        matcher.appendTail(buffer);
        return buffer.toString().replace('&', '§');
    }

    public static Component toComponent(String message) {
        return LegacyComponentSerializer.legacySection().deserialize(colorize(message));
    }
}
