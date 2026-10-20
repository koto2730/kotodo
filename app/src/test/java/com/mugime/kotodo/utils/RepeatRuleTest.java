package com.mugime.kotodo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mugime.kotodo.elements.RepeatType;
import com.mugime.kotodo.elements.Todo;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

/**
 * Date arithmetic for the repeat rules. Runs on the JVM, no device needed.
 *
 * <p>{@code nextWeekly}, {@code nextMonthly} and {@code nextYearly} are private, so
 * they are exercised the same way production code reaches them: through
 * {@link RepeatRule#nextDateAfter}, with {@code todo.repeatType} selecting the
 * branch. Test names are grouped by which of the four target methods they cover.</p>
 *
 * <p>Reference calendar used throughout (verified by the JVM's proleptic Gregorian
 * calendar, not assumed): 2026-07-25 is a Saturday, so 2026-07-26 is Sunday,
 * 2026-07-27 is Monday, ... 2026-07-31 is Friday, 2026-08-01 is Saturday again.</p>
 */
public class RepeatRuleTest {

    private static Todo repeating(RepeatType type, int interval) {
        Todo todo = new Todo("test", null);
        todo.repeat = true;
        todo.repeatType = type;
        todo.repeatInterval = interval;
        return todo;
    }

    private static int weekMask(int... zeroBasedDays) {
        int mask = 0;
        for (int day : zeroBasedDays) {
            mask = RepeatRule.withBit(mask, day, true);
        }
        return mask;
    }

    private static int dayMask(int... daysOfMonth) {
        int mask = 0;
        for (int day : daysOfMonth) {
            mask = RepeatRule.withBit(mask, day - 1, true);
        }
        return mask;
    }

    private static int monthMask(int... oneBasedMonths) {
        int mask = 0;
        for (int month : oneBasedMonths) {
            mask = RepeatRule.withBit(mask, month - 1, true);
        }
        return mask;
    }

    /**
     * Walks {@code nextDateAfter} forward {@code cycles} times from {@code start} and
     * fails loudly the moment the chain stalls (returns null), goes backwards, or
     * lands on a date that does not actually satisfy the rule. This is the direct
     * regression check for "repeat is configured but the next occurrence silently
     * never shows up" - a single missing week deep in the chain fails the test.
     */
    private static void assertChainNeverStalls(Todo todo, LocalDate start, int cycles,
                                                Predicate<LocalDate> satisfiesRule) {
        LocalDate cursor = start;
        for (int cycle = 1; cycle <= cycles; cycle++) {
            LocalDate next = RepeatRule.nextDateAfter(todo, cursor);
            assertNotNull("cycle " + cycle + ": expected an occurrence after " + cursor, next);
            assertTrue("cycle " + cycle + ": " + next + " is not after " + cursor, next.isAfter(cursor));
            assertTrue("cycle " + cycle + ": " + next + " does not satisfy the repeat rule",
                    satisfiesRule.test(next));
            cursor = next;
        }
    }

    // ============================================================
    // nextDateAfter - dispatch, isActive gating, interval clamping
    // ============================================================

    @Test
    public void dailyAddsTheInterval() {
        Todo todo = repeating(RepeatType.DAILY, 3);
        assertEquals(LocalDate.of(2026, 7, 28),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 25)));
    }

    @Test
    public void nonRepeatingTodoHasNoNextDate() {
        Todo todo = new Todo("test", null);
        assertNull(RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 25)));
    }

    @Test
    public void repeatFlagFalseHasNoNextDateEvenWithATypeSet() {
        // The `repeat` boolean is the master switch; repeatType alone is not enough.
        Todo todo = new Todo("test", null);
        todo.repeat = false;
        todo.repeatType = RepeatType.WEEKLY;
        assertNull(RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 25)));
    }

    @Test
    public void repeatTypeNoneHasNoNextDateEvenWithRepeatTrue() {
        Todo todo = new Todo("test", null);
        todo.repeat = true;
        todo.repeatType = RepeatType.NONE;
        assertNull(RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 25)));
    }

    @Test
    public void zeroOrNegativeIntervalIsTreatedAsOne() {
        Todo zero = repeating(RepeatType.DAILY, 0);
        Todo negative = repeating(RepeatType.DAILY, -3);
        LocalDate from = LocalDate.of(2026, 7, 25);
        assertEquals(from.plusDays(1), RepeatRule.nextDateAfter(zero, from));
        assertEquals(from.plusDays(1), RepeatRule.nextDateAfter(negative, from));
    }

    // ============================================================
    // nextWeekly (via nextDateAfter, repeatType = WEEKLY)
    // ============================================================

    @Test
    public void weeklyWithoutRuleKeepsTheSameWeekday() {
        Todo todo = repeating(RepeatType.WEEKLY, 1);
        // No weekday picked -> defaults to the anchor's own weekday, so this must
        // equal exactly one week later regardless of what day that is.
        LocalDate from = LocalDate.of(2026, 7, 25);
        assertEquals(from.plusWeeks(1), RepeatRule.nextDateAfter(todo, from));
    }

    @Test
    public void weeklyPicksTheNextSelectedWeekday() {
        Todo todo = repeating(RepeatType.WEEKLY, 1);
        todo.weekRule = weekMask(0, 2, 4); // Mon, Wed, Fri
        // Wednesday 2026-07-29 -> Friday 2026-07-31
        assertEquals(LocalDate.of(2026, 7, 31),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 29)));
        // Friday 2026-07-31 -> Monday 2026-08-03
        assertEquals(LocalDate.of(2026, 8, 3),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 31)));
    }

    @Test
    public void weeklyIntervalSkipsWholeWeeks() {
        Todo todo = repeating(RepeatType.WEEKLY, 2);
        todo.weekRule = weekMask(0); // Monday only
        // Monday 2026-07-27 -> two weeks later, not the following Monday.
        assertEquals(LocalDate.of(2026, 8, 10),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 27)));
    }

    @Test
    public void weeklyWhenFromIsExactlyTheSelectedDayMovesToTheNextCycleNotTheSameDay() {
        // Regression case for "set to repeat but the very next check finds nothing":
        // asking for the next occurrence starting ON the selected weekday itself must
        // never return that same day - it has to move a full interval forward.
        Todo todo = repeating(RepeatType.WEEKLY, 1);
        todo.weekRule = weekMask(0); // Monday only
        LocalDate monday = LocalDate.of(2026, 7, 27);
        assertEquals(monday.plusWeeks(1), RepeatRule.nextDateAfter(todo, monday));
    }

    @Test
    public void weeklyAllSevenDaysRepeatsEveryDay() {
        Todo todo = repeating(RepeatType.WEEKLY, 1);
        todo.weekRule = Todo.WEEK_RULE_ALL;
        LocalDate from = LocalDate.of(2026, 7, 25);
        assertEquals(from.plusDays(1), RepeatRule.nextDateAfter(todo, from));
    }

    @Test
    public void weeklyMultipleDaysWithinAnIntervalGreaterThanOneOnlyFireInTheActiveWeek() {
        // interval = 2 with two selected weekdays: both fire in the "active" week,
        // then the whole "off" week is skipped before the cycle repeats. This is the
        // exact shape of setup ("every other week, Mon + Thu") most likely to look
        // like "repeat stopped working" if the off-week skip were not intentional.
        Todo todo = repeating(RepeatType.WEEKLY, 2);
        todo.weekRule = weekMask(0, 3); // Mon, Thu
        LocalDate monday = LocalDate.of(2026, 7, 27); // active week

        LocalDate thursday = RepeatRule.nextDateAfter(todo, monday);
        assertEquals(LocalDate.of(2026, 7, 30), thursday); // same active week

        LocalDate nextMonday = RepeatRule.nextDateAfter(todo, thursday);
        assertEquals(LocalDate.of(2026, 8, 10), nextMonday); // off week skipped entirely

        LocalDate nextThursday = RepeatRule.nextDateAfter(todo, nextMonday);
        assertEquals(LocalDate.of(2026, 8, 13), nextThursday);
    }

    @Test
    public void weeklyCrossesTheMonthBoundary() {
        Todo todo = repeating(RepeatType.WEEKLY, 1);
        todo.weekRule = weekMask(0); // Monday
        // Friday 2026-07-31 -> Monday 2026-08-03, into the next calendar month.
        assertEquals(LocalDate.of(2026, 8, 3),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 31)));
    }

    @Test
    public void weeklyCrossesTheYearBoundary() {
        Todo todo = repeating(RepeatType.WEEKLY, 1);
        // No weekday rule: same-weekday-next-week must hold even across New Year's,
        // where a naive week-numbering implementation could reset unexpectedly.
        LocalDate from = LocalDate.of(2026, 12, 28);
        assertEquals(from.plusWeeks(1), RepeatRule.nextDateAfter(todo, from));
    }

    @Test
    public void weeklyFromASundayMovesToTomorrowWhenMondayIsSelected() {
        // Sunday is the last day of the app's Mon-Sun week; this is the boundary most
        // likely to trip up an off-by-one in the week-start calculation.
        Todo todo = repeating(RepeatType.WEEKLY, 1);
        todo.weekRule = weekMask(0); // Monday
        LocalDate sunday = LocalDate.of(2026, 7, 26);
        assertEquals(sunday.plusDays(1), RepeatRule.nextDateAfter(todo, sunday));
    }

    @Test
    public void weeklyNeverStallsAcrossFiftyTwoConsecutiveCycles() {
        // One full year of weekly occurrences, for several interval/weekday
        // combinations, none of which may ever silently fail to produce a next date.
        int[] intervals = {1, 2, 3};
        int[][] weekRules = {{0}, {0, 3}, {1, 2, 4, 5}};
        for (int interval : intervals) {
            for (int[] rule : weekRules) {
                Todo todo = repeating(RepeatType.WEEKLY, interval);
                todo.weekRule = weekMask(rule);
                List<DayOfWeek> days = RepeatRule.weekDays(todo.weekRule);
                assertChainNeverStalls(todo, LocalDate.of(2026, 1, 5), 52,
                        date -> days.contains(date.getDayOfWeek()));
            }
        }
    }

    // ============================================================
    // nextMonthly (via nextDateAfter, repeatType = MONTHLY) - month-end focus
    // ============================================================

    @Test
    public void monthlyWithoutRuleKeepsTheDayOfMonth() {
        Todo todo = repeating(RepeatType.MONTHLY, 1);
        assertEquals(LocalDate.of(2026, 8, 15),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 15)));
    }

    @Test
    public void monthlyClampsToTheLastDayOfShortMonths() {
        Todo todo = repeating(RepeatType.MONTHLY, 1);
        todo.monthRule = dayMask(31);
        // 2026 is not a leap year, so the 31st becomes 28 February.
        assertEquals(LocalDate.of(2026, 2, 28),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 1, 31)));
    }

    @Test
    public void monthlyClampsToTheTwentyNinthOnALeapYearFebruary() {
        Todo todo = repeating(RepeatType.MONTHLY, 1);
        todo.monthRule = dayMask(29);
        // 2028 is a leap year, so the 29th exists in February and is not clamped.
        assertEquals(LocalDate.of(2028, 2, 29),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2028, 1, 29)));
    }

    @Test
    public void monthlyClampDoesNotStickInTheFollowingMonth() {
        // The classic month-end bug: some implementations "remember" the clamped day
        // (28) instead of re-clamping the true target day (30) fresh each month.
        Todo todo = repeating(RepeatType.MONTHLY, 1);
        todo.monthRule = dayMask(30);

        LocalDate afterJan = RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 1, 30));
        assertEquals(LocalDate.of(2026, 2, 28), afterJan); // clamped (Feb has 28 days)

        LocalDate afterFeb = RepeatRule.nextDateAfter(todo, afterJan);
        assertEquals(LocalDate.of(2026, 3, 30), afterFeb); // back to the true 30th
    }

    @Test
    public void monthlyTwoMonthEndDaysCollidingOnTheSameClampedDateDoNotStallOrDuplicate() {
        // Days 30 and 31 both clamp to the same date in February. The rule must
        // still produce exactly one occurrence there, and must not get stuck
        // treating that date as perpetually "not yet after" itself.
        Todo todo = repeating(RepeatType.MONTHLY, 1);
        todo.monthRule = dayMask(30, 31);

        LocalDate intoFeb = RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 1, 31));
        assertEquals(LocalDate.of(2026, 2, 28), intoFeb);

        LocalDate intoMarch = RepeatRule.nextDateAfter(todo, intoFeb);
        assertEquals(LocalDate.of(2026, 3, 30), intoMarch);
    }

    @Test
    public void monthlyWithSeveralDaysReturnsTheNextOne() {
        Todo todo = repeating(RepeatType.MONTHLY, 1);
        todo.monthRule = dayMask(1, 15);
        assertEquals(LocalDate.of(2026, 7, 15),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 1)));
        assertEquals(LocalDate.of(2026, 8, 1),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 15)));
    }

    @Test
    public void monthlyIntervalSkipsWholeMonths() {
        Todo todo = repeating(RepeatType.MONTHLY, 3);
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2026, 4, 15), RepeatRule.nextDateAfter(todo, from));
    }

    @Test
    public void monthlyLargeIntervalStillFindsACandidate() {
        // Guards against the fixed cycle-search window ever being too small: any
        // interval must be found within a couple of cycles, however large.
        Todo todo = repeating(RepeatType.MONTHLY, 100);
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(from.plusMonths(100), RepeatRule.nextDateAfter(todo, from));
    }

    @Test
    public void monthlyNeverStallsAcrossTwentyFourConsecutiveCycles() {
        // Two years of a month-end rule, so every single cycle exercises clamping.
        Todo todo = repeating(RepeatType.MONTHLY, 1);
        todo.monthRule = dayMask(31);
        assertChainNeverStalls(todo, LocalDate.of(2026, 1, 31), 24,
                date -> date.getDayOfMonth() == Math.min(31, date.lengthOfMonth()));
    }

    // ============================================================
    // nextYearly (via nextDateAfter, repeatType = YEARLY)
    // ============================================================

    @Test
    public void yearlyWithoutRuleKeepsMonthAndDay() {
        Todo todo = repeating(RepeatType.YEARLY, 1);
        LocalDate from = LocalDate.of(2026, 7, 25);
        assertEquals(from.plusYears(1), RepeatRule.nextDateAfter(todo, from));
    }

    @Test
    public void yearlyUsesTheSelectedMonths() {
        Todo todo = repeating(RepeatType.YEARLY, 1);
        todo.yearRule = monthMask(1, 7); // Jan, Jul
        todo.monthRule = dayMask(10);
        assertEquals(LocalDate.of(2027, 1, 10),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 10)));
    }

    @Test
    public void yearlyMultipleMonthsWrapsToNextYearWhenAllHavePassed() {
        Todo todo = repeating(RepeatType.YEARLY, 1);
        todo.yearRule = monthMask(1, 7); // Jan, Jul
        todo.monthRule = dayMask(10);
        // Asking from August, after both Jan 10 and Jul 10 this year have passed.
        assertEquals(LocalDate.of(2027, 1, 10),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 8, 1)));
    }

    @Test
    public void yearlyClampsFeb29InNonLeapYears() {
        // "Every year on 29 Feb" reuses the same day-of-month clamp as monthly, so it
        // fires on 28 Feb in ordinary years rather than skipping to the next leap
        // year - documented behaviour, worth pinning down explicitly.
        Todo todo = repeating(RepeatType.YEARLY, 1);
        todo.yearRule = monthMask(2);
        todo.monthRule = dayMask(29);
        assertEquals(LocalDate.of(2029, 2, 28),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2028, 3, 1)));
    }

    @Test
    public void yearlyIntervalSkipsWholeYears() {
        Todo todo = repeating(RepeatType.YEARLY, 2);
        LocalDate from = LocalDate.of(2026, 7, 25);
        assertEquals(from.plusYears(2), RepeatRule.nextDateAfter(todo, from));
    }

    @Test
    public void yearlyNeverStallsAcrossTenConsecutiveCycles() {
        Todo todo = repeating(RepeatType.YEARLY, 1);
        LocalDate from = LocalDate.of(2026, 7, 25);
        assertChainNeverStalls(todo, from, 10,
                date -> date.getMonthValue() == 7 && date.getDayOfMonth() == 25);
    }

    // --------------------------------------------------------- next occurrence

    @Test
    public void nextOccurrenceShiftsBothScheduledDates() {
        Todo todo = repeating(RepeatType.DAILY, 7);
        todo.startDate = LocalDate.of(2026, 7, 20);
        todo.dueDate = LocalDate.of(2026, 7, 25);

        Todo next = RepeatRule.nextOccurrence(todo, LocalDate.of(2026, 7, 25));

        assertEquals(LocalDate.of(2026, 8, 1), next.dueDate);
        // The five-day lead time is preserved.
        assertEquals(LocalDate.of(2026, 7, 27), next.startDate);
        assertEquals(0L, next.id);
        assertFalse(next.completed);
        assertNull(next.completedDate);
    }

    @Test
    public void nextOccurrenceStopsAtTheRepeatEndDate() {
        Todo todo = repeating(RepeatType.DAILY, 1);
        todo.dueDate = LocalDate.of(2026, 7, 25);
        todo.repeatEndDate = LocalDate.of(2026, 7, 25);

        assertNull(RepeatRule.nextOccurrence(todo, LocalDate.of(2026, 7, 25)));
    }

    @Test
    public void nextOccurrenceCatchesUpPastOverdueDates() {
        Todo todo = repeating(RepeatType.DAILY, 1);
        todo.dueDate = LocalDate.of(2026, 7, 1);

        // Completed 24 days late: the follow-up should land tomorrow, not in the past.
        Todo next = RepeatRule.nextOccurrence(todo, LocalDate.of(2026, 7, 25));

        assertEquals(LocalDate.of(2026, 7, 26), next.dueDate);
    }

    @Test
    public void undatedRepeatingTodoIsAnchoredToToday() {
        Todo todo = repeating(RepeatType.DAILY, 2);

        Todo next = RepeatRule.nextOccurrence(todo, LocalDate.of(2026, 7, 25));

        assertEquals(LocalDate.of(2026, 7, 27), next.dueDate);
        assertNull(next.startDate);
    }
}
