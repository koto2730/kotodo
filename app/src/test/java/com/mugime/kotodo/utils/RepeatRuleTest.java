package com.mugime.kotodo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mugime.kotodo.elements.RepeatType;
import com.mugime.kotodo.elements.Todo;

import org.junit.Test;

import java.time.LocalDate;

/** Date arithmetic for the repeat rules. Runs on the JVM, no device needed. */
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

    @Test
    public void dailyAddsTheInterval() {
        Todo todo = repeating(RepeatType.DAILY, 3);
        assertEquals(LocalDate.of(2026, 7, 28),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 25)));
    }

    @Test
    public void weeklyWithoutRuleKeepsTheSameWeekday() {
        Todo todo = repeating(RepeatType.WEEKLY, 1);
        // 2026-07-25 is a Saturday.
        assertEquals(LocalDate.of(2026, 8, 1),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 25)));
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
    public void monthlyWithSeveralDaysReturnsTheNextOne() {
        Todo todo = repeating(RepeatType.MONTHLY, 1);
        todo.monthRule = dayMask(1, 15);
        assertEquals(LocalDate.of(2026, 7, 15),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 1)));
        assertEquals(LocalDate.of(2026, 8, 1),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 15)));
    }

    @Test
    public void yearlyUsesTheSelectedMonths() {
        Todo todo = repeating(RepeatType.YEARLY, 1);
        todo.yearRule = RepeatRule.withBit(RepeatRule.withBit(0, 0, true), 6, true); // Jan, Jul
        todo.monthRule = dayMask(10);
        assertEquals(LocalDate.of(2027, 1, 10),
                RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 10)));
    }

    @Test
    public void nonRepeatingTodoHasNoNextDate() {
        Todo todo = new Todo("test", null);
        assertNull(RepeatRule.nextDateAfter(todo, LocalDate.of(2026, 7, 25)));
    }

    // ------------------------------------------------------- next occurrence

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
        assertTrue(!next.completed);
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
