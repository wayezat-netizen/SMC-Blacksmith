package com.simmc.blacksmith.util;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {
    }

    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder(text.length() + 32);

        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static String stripColor(String text) {
        if (text == null) {
            return null;
        }
        return ChatColor.stripColor(colorize(text));
    }

    public static String createProgressBar(double progress, int length) {
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;

        int filled = (int) (progress * length);
        int empty = length - filled;

        StringBuilder bar = new StringBuilder();
        bar.append("§a");
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }
        bar.append("§7");
        for (int i = 0; i < empty; i++) {
            bar.append("░");
        }

        return bar.toString();
    }

    public static String formatStars(int stars, int maxStars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxStars; i++) {
            if (i < stars) {
                sb.append("§6★");
            } else {
                sb.append("§7☆");
            }
        }
        return sb.toString();
    }
}