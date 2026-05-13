package dev.bubblechat.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class TextUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private TextUtil() {}

    public static Component colorize(String text) {
        return LEGACY.deserialize(text);
    }

    public static Component miniMessage(String text) {
        return MINI.deserialize(text);
    }

    public static String applyPlaceholders(Player player, String text, boolean papiEnabled) {
        if (papiEnabled) {
            try {
                text = PlaceholderAPI.setPlaceholders(player, text);
            } catch (Exception ignored) {}
        }
        return text;
    }

    public static String format(String template, String playerName, String message) {
        return template
                .replace("%player_name%", playerName)
                .replace("%message%", message);
    }

    public static List<String> wrapText(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String stripped = word.replaceAll("§.", "").replaceAll("&.", "");
            String currentStripped = current.toString().replaceAll("§.", "").replaceAll("&.", "");

            if (currentStripped.length() + stripped.length() + (current.length() > 0 ? 1 : 0) > maxLength) {
                if (!current.isEmpty()) {
                    lines.add(current.toString().trim());
                    current = new StringBuilder();
                }
                if (stripped.length() > maxLength) {
                    lines.add(word);
                    continue;
                }
            }

            if (!current.isEmpty()) current.append(" ");
            current.append(word);
        }

        if (!current.isEmpty()) {
            lines.add(current.toString().trim());
        }

        return lines;
    }

    public static String filterMessage(String message, List<String> badWords, String replacement) {
        for (String word : badWords) {
            message = message.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), replacement);
        }
        return message;
    }

    public static Component buildMultilineComponent(List<String> lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            result = result.append(colorize(lines.get(i)));
            if (i < lines.size() - 1) {
                result = result.append(Component.newline());
            }
        }
        return result;
    }
}
