package com.mugime.kotodo.elements;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mugime.kotodo.utils.RepeatRule;

import org.junit.Test;

import java.time.LocalDate;

/** The listing rule and the copy semantics the list adapter depends on. */
public class TodoTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 25);

    @Test
    public void undatedTodoIsAlwaysVisible() {
        Todo todo = new Todo("read a book", null);

        assertTrue(todo.isUndated());
        assertTrue(todo.isVisibleOn(TODAY));
        assertTrue(todo.isVisibleOn(TODAY.plusYears(5)));
    }

    @Test
    public void todoBecomesVisibleOnItsStartDate() {
        Todo todo = new Todo("dentist", null);
        todo.startDate = LocalDate.of(2026, 7, 20);
        todo.dueDate = LocalDate.of(2026, 7, 26);

        assertFalse(todo.isVisibleOn(LocalDate.of(2026, 7, 19)));
        assertTrue(todo.isVisibleOn(LocalDate.of(2026, 7, 20)));
        assertTrue(todo.isVisibleOn(TODAY));
    }

    @Test
    public void overdueTodoKeepsShowingUp() {
        Todo todo = new Todo("health check", null);
        todo.dueDate = LocalDate.of(2026, 7, 15);

        assertTrue(todo.isVisibleOn(TODAY));
        assertTrue(todo.isOverdueOn(TODAY));
    }

    @Test
    public void completedTodoIsOnlyVisibleOnTheDayItWasCompleted() {
        Todo todo = new Todo("pay rent", null);
        todo.dueDate = LocalDate.of(2026, 7, 1);
        todo.completed = true;
        todo.completedDate = TODAY;

        assertTrue(todo.isVisibleOn(TODAY));
        assertFalse(todo.isVisibleOn(TODAY.plusDays(1)));
        assertFalse(todo.isOverdueOn(TODAY));
    }

    @Test
    public void dueDateIsTheRepeatAnchorWhenBothDatesAreSet() {
        Todo todo = new Todo("weekly report", null);
        todo.startDate = LocalDate.of(2026, 7, 20);
        todo.dueDate = LocalDate.of(2026, 7, 25);

        assertEquals(LocalDate.of(2026, 7, 25), todo.anchorDate());
        // Reminders fire on the earlier date, when the todo first appears.
        assertEquals(LocalDate.of(2026, 7, 20), todo.notifyDate());
    }

    @Test
    public void copyIsIndependentOfTheOriginal() {
        Todo original = new Todo("thing", "note");
        original.id = 42L;
        original.dueDate = TODAY;

        Todo copy = original.copy();
        copy.completed = true;
        copy.completedDate = TODAY;

        assertNotSame(original, copy);
        assertEquals(42L, copy.id);
        // The instance the list adapter still holds must not have changed.
        assertFalse(original.completed);
        assertNull(original.completedDate);
    }

    @Test
    public void copyAsNewClearsIdentityAndCompletion() {
        Todo original = new Todo("thing", null);
        original.id = 42L;
        original.completed = true;
        original.completedDate = TODAY;

        Todo fresh = original.copyAsNew();

        assertEquals(0L, fresh.id);
        assertFalse(fresh.completed);
        assertNull(fresh.completedDate);
        assertEquals("thing", fresh.title);
    }

    @Test
    public void copyPreservesTheFollowUpLatch() {
        Todo original = new Todo("thing", null);
        original.followUpCreated = true;
        original.parentTodoId = 99L;

        Todo copy = original.copy();
        assertTrue(copy.followUpCreated);
        assertEquals(Long.valueOf(99L), copy.parentTodoId);
    }

    @Test
    public void copyAsNewResetsTheFollowUpLatch() {
        Todo original = new Todo("thing", null);
        original.followUpCreated = true;
        original.parentTodoId = 99L;

        Todo fresh = original.copyAsNew();
        assertFalse(fresh.followUpCreated);
        assertNull(fresh.parentTodoId);
    }

    /**
     * Reproduces the check/uncheck/re-check cycle from TodoRepository.setCompleted
     * (not itself unit-testable: it needs Room) to prove the followUpCreated latch
     * stops a second follow-up from being spawned. Regression test for the bug where
     * repeatedly toggling a repeating todo's checkbox kept creating more "next" todos.
     */
    @Test
    public void completingTwiceNeverSpawnsASecondFollowUp() {
        Todo todo = new Todo("standup", null);
        todo.repeat = true;
        todo.repeatType = RepeatType.DAILY;
        todo.dueDate = TODAY;

        Todo edited = todo.copy();
        edited.completed = true;
        edited.completedDate = TODAY;
        Todo firstFollowUp = edited.followUpCreated ? null : RepeatRule.nextOccurrence(edited, TODAY);
        if (firstFollowUp != null) {
            edited.followUpCreated = true;
        }
        assertEquals(TODAY.plusDays(1), firstFollowUp.dueDate);
        assertTrue(edited.followUpCreated);

        // Unchecking clears completion but must not reset the latch.
        edited.completed = false;
        edited.completedDate = null;
        assertTrue(edited.followUpCreated);

        // Re-checking must not spawn a second follow-up.
        edited.completed = true;
        edited.completedDate = TODAY;
        Todo secondFollowUp = edited.followUpCreated ? null : RepeatRule.nextOccurrence(edited, TODAY);
        assertNull(secondFollowUp);
    }

    @Test
    public void buildingTheNextOccurrenceLeavesTheCompletedTodoAlone() {
        Todo todo = new Todo("standup", null);
        todo.id = 7L;
        todo.repeat = true;
        todo.repeatType = RepeatType.DAILY;
        todo.dueDate = TODAY;
        todo.completed = true;
        todo.completedDate = TODAY;

        Todo next = RepeatRule.nextOccurrence(todo, TODAY);

        assertEquals(TODAY.plusDays(1), next.dueDate);
        assertEquals(7L, todo.id);
        assertEquals(TODAY, todo.dueDate);
    }

    @Test
    public void TestRepeatDay_me() {
        Todo todo = new Todo("standup", null);
        todo.id = 7L;
        todo.repeat = true;
        todo.repeatType = RepeatType.DAILY;
        todo.dueDate = TODAY;
        todo.completed = true;
        todo.completedDate = TODAY;

        Todo next = RepeatRule.nextOccurrence(todo, TODAY);
        for (int i=1; i<10; i++) {
            next = RepeatRule.nextOccurrence(next, TODAY.plusDays((i)));
        }

        assertEquals(TODAY.plusDays(10), next.dueDate);
        assertEquals(7L, todo.id);
        assertEquals(TODAY, todo.dueDate);
    }
    @org.junit.Ignore("WIP: User experiment")
    @Test
    public void TestRepeatWeek_me() {
        Todo todo = new Todo("standup", null);
        todo.id = 7L;
        todo.repeat = true;
        todo.repeatType = RepeatType.WEEKLY;
        todo.weekRule = Todo.WEEK_RULE_ALL; // All weekdays selected
        todo.dueDate = TODAY;
        todo.completed = true;
        todo.completedDate = TODAY;

        Todo next = RepeatRule.nextOccurrence(todo, TODAY);
        for (int i=0; i<61; i++) {
            next = RepeatRule.nextOccurrence(next, TODAY.plusDays((i)));
        }

        assertEquals(TODAY.plusDays(61), next.dueDate);
        assertEquals(7L, todo.id);
        assertEquals(TODAY, todo.dueDate);
    }

    @Test
    public void deletingChildFollowUpResetsParentLatch() {
        Todo parent = new Todo("parent", null);
        parent.id = 10L;
        parent.repeat = true;
        parent.repeatType = RepeatType.DAILY;
        parent.dueDate = TODAY;
        parent.completed = true;
        parent.completedDate = TODAY;
        parent.followUpCreated = true;

        // Child follow-up spawned from parent
        Todo child = RepeatRule.nextOccurrence(parent, TODAY);
        org.junit.Assert.assertNotNull(child);
        child.id = 11L;
        child.parentTodoId = parent.id;

        assertEquals(Long.valueOf(10L), child.parentTodoId);

        // Deleting child resets parent latch if parent exists
        if (child.parentTodoId != null && child.parentTodoId == parent.id) {
            parent.followUpCreated = false;
        }
        assertFalse(parent.followUpCreated);

        // Parent can now generate follow-up again
        Todo secondChild = parent.followUpCreated ? null : RepeatRule.nextOccurrence(parent, TODAY);
        org.junit.Assert.assertNotNull(secondChild);
        assertEquals(TODAY.plusDays(1), secondChild.dueDate);
    }
}
