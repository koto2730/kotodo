package com.mugime.kotodo.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mugime.kotodo.elements.RepeatType;
import com.mugime.kotodo.elements.Todo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Repeat-rule arithmetic: given a todo's cycle settings and an anchor date,
 * work out when the next occurrence falls.
 *
 * <p>Rules are stored as bit masks on {@link Todo}:</p>
 * <ul>
 *   <li>{@code weekRule} - selected weekdays, Monday = bit 0 .. Sunday = bit 6</li>
 *   <li>{@code monthRule} - selected days of month, day 1 = bit 0 .. day 31 = bit 30</li>
 *   <li>{@code yearRule} - selected months, January = bit 0 .. December = bit 11</li>
 * </ul>
 *
 * <p>An empty mask means "same as the anchor date", so a weekly todo anchored on a
 * Wednesday repeats on Wednesdays until the user picks specific weekdays. A day of
 * month that does not exist in the target month is clamped to the last day, so
 * "the 31st, monthly" lands on 28 February.</p>
 *
 * <p>This class is deliberately free of Android dependencies so the date maths can
 * be unit tested on the JVM.</p>
 */
public final class RepeatRule {

    /** How many cycles ahead we are willing to search before giving up. */
    private static final int CYCLE_SEARCH_LIMIT = 4;

    /** Upper bound on the catch-up loop for long-overdue repeating todos. */
    private static final int CATCH_UP_LIMIT = 500;

    private RepeatRule() {
    }

    // ---------------------------------------------------------------- bit masks

    public static boolean hasBit(int mask, int bitIndex) {
        return (mask & (1 << bitIndex)) != 0;
    }

    public static int withBit(int mask, int bitIndex, boolean on) {
        return on ? (mask | (1 << bitIndex)) : (mask & ~(1 << bitIndex));
    }

    /** Weekdays selected in {@code weekRule}, ascending from Monday. */
    @NonNull
    public static List<DayOfWeek> weekDays(int weekRule) {
        List<DayOfWeek> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            if (hasBit(weekRule, i)) {
                days.add(DayOfWeek.of(i + 1));
            }
        }
        return days;
    }

    /** Days of month selected in {@code monthRule}, ascending. */
    @NonNull
    public static List<Integer> monthDays(int monthRule) {
        List<Integer> days = new ArrayList<>(31);
        for (int i = 0; i < 31; i++) {
            if (hasBit(monthRule, i)) {
                days.add(i + 1);
            }
        }
        return days;
    }

    /** Months selected in {@code yearRule}, ascending, 1 = January. */
    @NonNull
    public static List<Integer> months(int yearRule) {
        List<Integer> values = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            if (hasBit(yearRule, i)) {
                values.add(i + 1);
            }
        }
        return values;
    }

    // ------------------------------------------------------------- next date

    public static boolean isActive(@NonNull Todo todo) {
        return todo.repeat && todo.repeatType != RepeatType.NONE;
    }

    /**
     * The next date strictly after {@code from} that satisfies the todo's rule,
     * ignoring 繰り返し終了日. Returns {@code null} when the todo does not repeat
     * or no date could be found within the search window.
     */
    @Nullable
    public static LocalDate nextDateAfter(@NonNull Todo todo, @NonNull LocalDate from) {
        if (!isActive(todo)) {
            return null;
        }
        int interval = Math.max(1, todo.repeatInterval);
        switch (todo.repeatType) {
            case DAILY:
                return from.plusDays(interval);
            case WEEKLY:
                return nextWeekly(todo, from, interval);
            case MONTHLY:
                return nextMonthly(todo, from, interval);
            case YEARLY:
                return nextYearly(todo, from, interval);
            default:
                return null;
        }
    }

    private static LocalDate nextWeekly(Todo todo, LocalDate from, int interval) {
        List<DayOfWeek> days = weekDays(todo.weekRule);
        if (days.isEmpty()) {
            days.add(from.getDayOfWeek());
        }
        LocalDate anchorWeek = weekStart(from);
        // The furthest a valid day can be is the last day of the week `interval`
        // cycles ahead, so scanning that many days always finds it if it exists.
        int horizon = interval * 7 + 7;
        for (int offset = 1; offset <= horizon; offset++) {
            LocalDate candidate = from.plusDays(offset);
            long weeks = ChronoUnit.WEEKS.between(anchorWeek, weekStart(candidate));
            if (weeks % interval == 0 && days.contains(candidate.getDayOfWeek())) {
                return candidate;
            }
        }
        return null;
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate nextMonthly(Todo todo, LocalDate from, int interval) {
        List<Integer> days = monthDays(todo.monthRule);
        if (days.isEmpty()) {
            days.add(from.getDayOfMonth());
        }
        YearMonth base = YearMonth.from(from);
        for (int cycle = 0; cycle <= CYCLE_SEARCH_LIMIT; cycle++) {
            YearMonth month = base.plusMonths((long) cycle * interval);
            LocalDate candidate = firstDayAfter(month, days, from);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static LocalDate nextYearly(Todo todo, LocalDate from, int interval) {
        List<Integer> monthValues = months(todo.yearRule);
        if (monthValues.isEmpty()) {
            monthValues.add(from.getMonthValue());
        }
        List<Integer> days = monthDays(todo.monthRule);
        if (days.isEmpty()) {
            days.add(from.getDayOfMonth());
        }
        for (int cycle = 0; cycle <= CYCLE_SEARCH_LIMIT; cycle++) {
            int year = from.getYear() + cycle * interval;
            // Months and days are both ascending, so the first hit is the earliest one.
            for (int month : monthValues) {
                LocalDate candidate = firstDayAfter(YearMonth.of(year, month), days, from);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Earliest date in {@code month} that is one of {@code days} and later than {@code after}. */
    private static LocalDate firstDayAfter(YearMonth month, List<Integer> days, LocalDate after) {
        int lastDay = month.lengthOfMonth();
        for (int day : days) {
            LocalDate candidate = month.atDay(Math.min(day, lastDay));
            if (candidate.isAfter(after)) {
                return candidate;
            }
        }
        return null;
    }

    // --------------------------------------------------------- next occurrence

    /**
     * Builds the follow-up todo created when a repeating item is completed.
     *
     * <p>The cycle is measured from 完了予定日 (or 開始予定日 when there is no due
     * date), and both scheduled dates are shifted by the same number of days so the
     * lead time between them is preserved. If the item is long overdue the search
     * keeps advancing until it lands after {@code today}, so completing a daily todo
     * that was forgotten for a week schedules tomorrow rather than last Tuesday.</p>
     *
     * @return the todo to insert, or {@code null} when the item does not repeat or
     *         the next occurrence would fall after 繰り返し終了日.
     */
    @Nullable
    public static Todo nextOccurrence(@NonNull Todo todo, @NonNull LocalDate today) {
        if (!isActive(todo)) {
            return null;
        }
        LocalDate anchor = todo.anchorDate();
        boolean undated = anchor == null;
        if (undated) {
            anchor = today;
        }

        LocalDate next = null;
        LocalDate cursor = anchor;
        for (int guard = 0; guard < CATCH_UP_LIMIT; guard++) {
            LocalDate candidate = nextDateAfter(todo, cursor);
            if (candidate == null) {
                return null;
            }
            if (todo.repeatEndDate != null && candidate.isAfter(todo.repeatEndDate)) {
                return null;
            }
            next = candidate;
            if (candidate.isAfter(today)) {
                break;
            }
            cursor = candidate;
        }
        if (next == null) {
            return null;
        }

        Todo follow = todo.copyAsNew();
        long shift = ChronoUnit.DAYS.between(anchor, next);
        if (undated) {
            follow.dueDate = next;
        } else {
            if (todo.dueDate != null) {
                follow.dueDate = todo.dueDate.plusDays(shift);
            }
            if (todo.startDate != null) {
                follow.startDate = todo.startDate.plusDays(shift);
            }
        }
        return follow;
    }
}
