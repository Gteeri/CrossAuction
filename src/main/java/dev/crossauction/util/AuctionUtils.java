package dev.crossauction.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

public final class AuctionUtils {

    private AuctionUtils() {}

    public static BigDecimal parsePrice(String raw) {
        if (raw == null) return null;
        try {
            BigDecimal value = new BigDecimal(raw.trim().replace(",", "."));
            if (value.compareTo(BigDecimal.ZERO) <= 0) return null;
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatTimeLeft(Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative()) return "expired";
        long days = remaining.toDays();
        long hours = remaining.toHoursPart();
        long minutes = remaining.toMinutesPart();
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
