package net.kronig.gridlock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public final class Text {

    public static final String PREFIX = "<dark_gray>[<gradient:#ff3b3b:#ff9e3b>GridLock</gradient>]</dark_gray> ";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static Component prefixed(String miniMessage) {
        return MM.deserialize(PREFIX + miniMessage);
    }

    /** Component for item names/lore: no default italics. */
    public static Component item(String miniMessage) {
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String escape(String raw) {
        return MM.escapeTags(raw);
    }

    /** Formats seconds as HH:MM:SS (or D:HH:MM:SS above one day). */
    public static String time(long totalSeconds) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (days > 0) {
            return String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds);
        }
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Parses a duration: "1:30:00", "45:00", "90m", "2h", "1h30m", "300s" or plain seconds.
     *
     * @return seconds, or -1 if the input is not a duration
     */
    public static long parseDuration(String input) {
        String in = input.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            if (in.contains(":")) {
                long total = 0;
                for (String part : in.split(":")) {
                    total = total * 60 + Long.parseLong(part);
                }
                return total;
            }
            if (in.matches("\\d+")) {
                return Long.parseLong(in);
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)([dhms])").matcher(in);
            long total = 0;
            int consumed = 0;
            while (matcher.find()) {
                long value = Long.parseLong(matcher.group(1));
                total += switch (matcher.group(2)) {
                    case "d" -> value * 86400;
                    case "h" -> value * 3600;
                    case "m" -> value * 60;
                    default -> value;
                };
                consumed += matcher.group().length();
            }
            return consumed == in.length() && consumed > 0 ? total : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Word-wraps plain text into lines of roughly {@code width} characters. */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    public static String progressBar(double progress, int length, String doneColor, String todoColor) {
        int done = (int) Math.round(Math.max(0, Math.min(1, progress)) * length);
        return "<" + doneColor + ">" + "▰".repeat(done) + "</" + doneColor + "><" + todoColor + ">"
                + "▱".repeat(length - done) + "</" + todoColor + ">";
    }
}
