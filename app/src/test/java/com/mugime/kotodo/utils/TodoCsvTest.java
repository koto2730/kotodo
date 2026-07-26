package com.mugime.kotodo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mugime.kotodo.elements.Priority;
import com.mugime.kotodo.elements.RepeatType;
import com.mugime.kotodo.elements.Todo;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** CSV parsing, the checkbox prefix rule and the export/import round trip. */
public class TodoCsvTest {

    @Test
    public void parsesQuotedFieldsAndEmbeddedNewlines() {
        List<List<String>> rows = Csv.parse("a,\"b,c\",\"line1\nline2\"\r\nd,e,f\n");

        assertEquals(2, rows.size());
        assertEquals(Arrays.asList("a", "b,c", "line1\nline2"), rows.get(0));
        assertEquals(Arrays.asList("d", "e", "f"), rows.get(1));
    }

    @Test
    public void parsesDoubledQuotes() {
        List<List<String>> rows = Csv.parse("\"say \"\"hi\"\"\"");
        assertEquals(Collections.singletonList("say \"hi\""), rows.get(0));
    }

    @Test
    public void stripsMarkdownCheckboxFromTitle() {
        TodoCsv.ImportResult result = TodoCsv.parse("title\n- [ ] buy milk\n-[ ] call mum\n[ ] pay rent\n");

        assertEquals(3, result.todos.size());
        assertEquals("buy milk", result.todos.get(0).title);
        assertEquals("call mum", result.todos.get(1).title);
        assertEquals("pay rent", result.todos.get(2).title);
        assertFalse(result.todos.get(0).completed);
    }

    @Test
    public void checkedCheckboxMarksTheTodoComplete() {
        TodoCsv.ImportResult result = TodoCsv.parse("title\n- [x] done thing\n- [ ] open thing\n");

        assertTrue(result.todos.get(0).completed);
        assertFalse(result.todos.get(1).completed);
    }

    @Test
    public void explicitCompletedColumnBeatsTheCheckbox() {
        TodoCsv.ImportResult result = TodoCsv.parse("title,completed\n\"- [x] thing\",false\n");

        assertFalse(result.todos.get(0).completed);
    }

    @Test
    public void readsAFileWithNoHeaderAsOneTitlePerLine() {
        TodoCsv.ImportResult result = TodoCsv.parse("- [ ] first\n- [ ] second\n");

        assertFalse(result.hadHeader);
        assertEquals(2, result.todos.size());
        assertEquals("first", result.todos.get(0).title);
    }

    @Test
    public void matchesHeaderAliasesAndAnyColumnOrder() {
        TodoCsv.ImportResult result = TodoCsv.parse(
                "期限,タイトル,優先度\n2026-07-25,ゴミ出し,high\n");

        assertTrue(result.hadHeader);
        Todo todo = result.todos.get(0);
        assertEquals("ゴミ出し", todo.title);
        assertEquals(Priority.HighPriority, todo.priority);
        assertEquals(LocalDate.of(2026, 7, 25), todo.dueDate);
    }

    @Test
    public void skipsRowsWithoutATitle() {
        TodoCsv.ImportResult result = TodoCsv.parse("title,description\n,orphan note\nreal,ok\n");

        assertEquals(1, result.todos.size());
        assertEquals(1, result.skippedRows);
    }

    @Test
    public void repeatTypeAloneImpliesRepeating() {
        TodoCsv.ImportResult result = TodoCsv.parse("title,repeat_type,week_rule\nstandup,weekly,MON|WED\n");

        Todo todo = result.todos.get(0);
        assertTrue(todo.repeat);
        assertEquals(RepeatType.WEEKLY, todo.repeatType);
        assertEquals(Arrays.asList(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY),
                RepeatRule.weekDays(todo.weekRule));
    }

    @Test
    public void parsesYearlyMonthAndDayRules() {
        TodoCsv.ImportResult result = TodoCsv.parse(
                "title,repeat_type,month_rule,year_rule\n健康診断,yearly,15,7\n");

        Todo todo = result.todos.get(0);
        assertEquals(RepeatType.YEARLY, todo.repeatType);
        assertEquals(Collections.singletonList(15), RepeatRule.monthDays(todo.monthRule));
        assertEquals(Collections.singletonList(7), RepeatRule.months(todo.yearRule));
    }

    @Test
    public void parsesMultipleRuleValuesWithVariousSeparators() {
        TodoCsv.ImportResult result = TodoCsv.parse(
                "title,repeat_type,month_rule\nrent,monthly,1|15\n");

        assertEquals(Arrays.asList(1, 15), RepeatRule.monthDays(result.todos.get(0).monthRule));
    }

    @Test
    public void exportThenImportPreservesEveryField() {
        Todo original = new Todo("週次レポート", "先週分をまとめる");
        original.priority = Priority.HighestPriority;
        original.groupName = "仕事";
        original.startDate = LocalDate.of(2026, 7, 20);
        original.dueDate = LocalDate.of(2026, 7, 25);
        original.repeat = true;
        original.repeatType = RepeatType.WEEKLY;
        original.repeatInterval = 2;
        original.weekRule = RepeatRule.withBit(0, 4, true); // Friday
        original.repeatEndDate = LocalDate.of(2026, 12, 31);
        original.notify = true;
        original.notifyMinuteOfDay = 8 * 60 + 30;

        TodoCsv.ImportResult result = TodoCsv.parse(
                TodoCsv.export(Collections.singletonList(original)));

        assertEquals(1, result.todos.size());
        Todo restored = result.todos.get(0);
        assertEquals(original.title, restored.title);
        assertEquals(original.description, restored.description);
        assertEquals(original.priority, restored.priority);
        assertEquals(original.groupName, restored.groupName);
        assertEquals(original.startDate, restored.startDate);
        assertEquals(original.dueDate, restored.dueDate);
        assertEquals(original.repeat, restored.repeat);
        assertEquals(original.repeatType, restored.repeatType);
        assertEquals(original.repeatInterval, restored.repeatInterval);
        assertEquals(original.weekRule, restored.weekRule);
        assertEquals(original.repeatEndDate, restored.repeatEndDate);
        assertEquals(original.notify, restored.notify);
        assertEquals(original.notifyMinuteOfDay, restored.notifyMinuteOfDay);
        assertFalse(restored.completed);
        assertNull(restored.completedDate);
    }

    @Test
    public void exportQuotesFieldsContainingCommasAndQuotes() {
        Todo todo = new Todo("a,b", "he said \"hi\"");

        String csv = TodoCsv.export(Collections.singletonList(todo));
        TodoCsv.ImportResult result = TodoCsv.parse(csv);

        assertEquals("a,b", result.todos.get(0).title);
        assertEquals("he said \"hi\"", result.todos.get(0).description);
    }

    @Test
    public void ignoresAByteOrderMark() {
        TodoCsv.ImportResult result = TodoCsv.parse("﻿title\nwith bom\n");

        assertTrue(result.hadHeader);
        assertEquals("with bom", result.todos.get(0).title);
    }
}
