package com.mugime.kotodo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

/** Unit tests for {@link DateUtils} date/time parsing, formatting, and calculation helpers. */
public class DateUtilsTest {

    @Test
    public void todayMatchesSystemTimeZoneDate() {
        LocalDate before = LocalDate.now(ZoneId.systemDefault());
        LocalDate result = DateUtils.today();
        LocalDate after = LocalDate.now(ZoneId.systemDefault());

        assertTrue(result.equals(before) || result.equals(after));
    }

    @Test
    public void getTodayFormatsWithProvidedPattern() {
        String formatted = DateUtils.getToday("yyyy-MM-dd");
        assertEquals(DateUtils.toIso(DateUtils.today()), formatted);
    }

    @Test
    public void toIsoFormatsDateAndReturnsEmptyForNull() {
        assertEquals("", DateUtils.toIso(null));
        assertEquals("2026-07-25", DateUtils.toIso(LocalDate.of(2026, 7, 25)));
        assertEquals("2028-02-29", DateUtils.toIso(LocalDate.of(2028, 2, 29)));
    }

    @Test
    public void parseDateAcceptsAllSupportedFormatsAndTrimsWhitespace() {
        LocalDate expected = LocalDate.of(2026, 7, 5);
        assertEquals(expected, DateUtils.parseDate("2026-07-05"));
        assertEquals(expected, DateUtils.parseDate("  2026-07-05  "));
        assertEquals(expected, DateUtils.parseDate("2026/7/5"));
        assertEquals(expected, DateUtils.parseDate("2026/07/05"));
        assertEquals(expected, DateUtils.parseDate("2026.7.5"));
        assertEquals(expected, DateUtils.parseDate("2026.07.05"));
        assertEquals(expected, DateUtils.parseDate("20260705"));
    }

    @Test
    public void parseDateHandlesLeapYearAndRejectsInvalidDates() {
        assertEquals(LocalDate.of(2028, 2, 29), DateUtils.parseDate("2028-02-29"));
        assertNull(DateUtils.parseDate(null));
        assertNull(DateUtils.parseDate(""));
        assertNull(DateUtils.parseDate("   "));
        assertNull(DateUtils.parseDate("not-a-date"));
        assertNull(DateUtils.parseDate("2026-13-01"));
    }

    @Test
    public void formatDisplayAndFormatShortHandleNullAndYearComparison() {
        assertEquals("", DateUtils.formatDisplay(null));
        assertFalse(DateUtils.formatDisplay(LocalDate.of(2026, 7, 25)).isEmpty());

        assertEquals("", DateUtils.formatShort(null));
        LocalDate currentYearDate = LocalDate.of(DateUtils.today().getYear(), 7, 25);
        assertEquals("7/25", DateUtils.formatShort(currentYearDate));

        LocalDate otherYearDate = LocalDate.of(DateUtils.today().getYear() + 1, 7, 25);
        assertEquals((DateUtils.today().getYear() + 1) + "/7/25", DateUtils.formatShort(otherYearDate));
    }

    @Test
    public void timeOfDayAndFormatTimeClampOutOfRangeMinutes() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertEquals(LocalTime.of(0, 0), DateUtils.timeOfDay(-50));
            assertEquals(LocalTime.of(9, 30), DateUtils.timeOfDay(9 * 60 + 30));
            assertEquals(LocalTime.of(23, 59), DateUtils.timeOfDay(24 * 60 + 100));

            assertEquals("00:00", DateUtils.formatTime(-1));
            assertEquals("09:05", DateUtils.formatTime(9 * 60 + 5));
            assertEquals("23:59", DateUtils.formatTime(9999));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void parseMinuteOfDaySupportsColonFourDigitAndRawMinutes() {
        assertEquals(540, DateUtils.parseMinuteOfDay(null, 540));
        assertEquals(540, DateUtils.parseMinuteOfDay("   ", 540));
        assertEquals(540, DateUtils.parseMinuteOfDay("invalid", 540));

        assertEquals(9 * 60 + 30, DateUtils.parseMinuteOfDay("9:30", 0));
        assertEquals(9 * 60 + 30, DateUtils.parseMinuteOfDay("09:30", 0));
        assertEquals(8 * 60 + 15, DateUtils.parseMinuteOfDay("0815", 0));
        assertEquals(600, DateUtils.parseMinuteOfDay("600", 0));

        assertEquals(0, DateUtils.parseMinuteOfDay("-10", 540));
        assertEquals(24 * 60 - 1, DateUtils.parseMinuteOfDay("99:99", 540));
    }

    @Test
    public void toEpochMillisMatchesSystemZoneInstant() {
        LocalDate date = LocalDate.of(2026, 7, 25);
        int minuteOfDay = 9 * 60 + 30;
        long millis = DateUtils.toEpochMillis(date, minuteOfDay);

        LocalDate roundTripDate = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalTime roundTripTime = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime();

        assertNotNull(roundTripDate);
        assertEquals(date, roundTripDate);
        assertEquals(LocalTime.of(9, 30), roundTripTime);
    }
}
