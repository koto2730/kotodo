package com.mugime.kotodo.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Local-date helpers. Everything here reads the <em>device</em> clock and the
 * <em>device</em> time zone, which is what the spec asks for: the list is built
 * from the local date of the device the app runs on.
 */
public final class DateUtils {

    /** The canonical on-disk / CSV date format. */
    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Formats accepted on import, tried in order. */
    private static final DateTimeFormatter[] LENIENT_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy.M.d", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT),
    };

    private DateUtils() {
    }

    /** Today according to the device's own time zone. */
    @NonNull
    public static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    /** Today, rendered with an arbitrary {@link DateTimeFormatter} pattern. */
    @NonNull
    public static String getToday(@NonNull String format) {
        return today().format(DateTimeFormatter.ofPattern(format, Locale.getDefault()));
    }

    @NonNull
    public static String toIso(@Nullable LocalDate date) {
        return date == null ? "" : date.format(ISO);
    }

    /**
     * Parses a date from CSV or user input. Returns {@code null} for blank or
     * unparseable input rather than throwing, because import should skip a bad
     * cell instead of failing the whole file.
     */
    @Nullable
    public static LocalDate parseDate(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : LENIENT_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try the next pattern
            }
        }
        return null;
    }

    /** Locale-aware medium date, e.g. "2026/07/25" in ja-JP and "Jul 25, 2026" in en-US. */
    @NonNull
    public static String formatDisplay(@Nullable LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault()));
    }

    /** Short date without the year, used for the compact meta line in list rows. */
    @NonNull
    public static String formatShort(@Nullable LocalDate date) {
        if (date == null) {
            return "";
        }
        if (date.getYear() == today().getYear()) {
            return date.format(DateTimeFormatter.ofPattern("M/d", Locale.getDefault()));
        }
        return date.format(DateTimeFormatter.ofPattern("yyyy/M/d", Locale.getDefault()));
    }

    @NonNull
    public static LocalTime timeOfDay(int minuteOfDay) {
        int clamped = Math.max(0, Math.min(24 * 60 - 1, minuteOfDay));
        return LocalTime.of(clamped / 60, clamped % 60);
    }

    @NonNull
    public static String formatTime(int minuteOfDay) {
        LocalTime time = timeOfDay(minuteOfDay);
        return String.format(Locale.getDefault(), "%02d:%02d", time.getHour(), time.getMinute());
    }

    /** Parses "9:00" / "09:00" / "0900" into a minute of day, or the fallback. */
    public static int parseMinuteOfDay(@Nullable String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            if (value.contains(":")) {
                String[] parts = value.split(":", 2);
                int hour = Integer.parseInt(parts[0].trim());
                int minute = Integer.parseInt(parts[1].trim());
                return clampMinuteOfDay(hour * 60 + minute);
            }
            if (value.length() == 4) {
                int hour = Integer.parseInt(value.substring(0, 2));
                int minute = Integer.parseInt(value.substring(2));
                return clampMinuteOfDay(hour * 60 + minute);
            }
            return clampMinuteOfDay(Integer.parseInt(value));
        } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
            return fallback;
        }
    }

    public static int clampMinuteOfDay(int minuteOfDay) {
        return Math.max(0, Math.min(24 * 60 - 1, minuteOfDay));
    }

    /** Epoch millis of {@code date} at {@code minuteOfDay} in the device time zone. */
    public static long toEpochMillis(@NonNull LocalDate date, int minuteOfDay) {
        return date.atTime(timeOfDay(minuteOfDay))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }
}
