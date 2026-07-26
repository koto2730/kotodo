package com.mugime.kotodo.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mugime.kotodo.elements.Priority;
import com.mugime.kotodo.elements.RepeatType;
import com.mugime.kotodo.elements.Todo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts todos to and from CSV.
 *
 * <p>Export always writes the full header. Import is deliberately forgiving:</p>
 * <ul>
 *   <li>column order is free, and columns may be missing entirely;</li>
 *   <li>headers are matched case-insensitively against English and Japanese
 *       aliases, ignoring spaces, underscores and hyphens;</li>
 *   <li>a file with no recognisable header is read as one title per line;</li>
 *   <li>a Markdown checkbox prefix on the title - {@code "- [ ] "} or
 *       {@code "- [x] "} - is stripped, and {@code [x]} marks the item complete
 *       when the file carries no explicit completion column.</li>
 * </ul>
 */
public final class TodoCsv {

    /** Result of reading a file, so the caller can report what happened. */
    public static class ImportResult {
        public final List<Todo> todos;
        public final int skippedRows;
        public final boolean hadHeader;

        ImportResult(List<Todo> todos, int skippedRows, boolean hadHeader) {
            this.todos = todos;
            this.skippedRows = skippedRows;
            this.hadHeader = hadHeader;
        }
    }

    private static final String COL_TITLE = "title";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_PRIORITY = "priority";
    private static final String COL_GROUP = "group";
    private static final String COL_START = "start_date";
    private static final String COL_DUE = "due_date";
    private static final String COL_REPEAT = "repeat";
    private static final String COL_REPEAT_TYPE = "repeat_type";
    private static final String COL_REPEAT_INTERVAL = "repeat_interval";
    private static final String COL_WEEK_RULE = "week_rule";
    private static final String COL_MONTH_RULE = "month_rule";
    private static final String COL_YEAR_RULE = "year_rule";
    private static final String COL_REPEAT_END = "repeat_end_date";
    private static final String COL_NOTIFY = "notify";
    private static final String COL_NOTIFY_TIME = "notify_time";
    private static final String COL_COMPLETED = "completed";
    private static final String COL_COMPLETED_DATE = "completed_date";

    private static final List<String> HEADER = Arrays.asList(
            COL_TITLE, COL_DESCRIPTION, COL_PRIORITY, COL_GROUP,
            COL_START, COL_DUE,
            COL_REPEAT, COL_REPEAT_TYPE, COL_REPEAT_INTERVAL,
            COL_WEEK_RULE, COL_MONTH_RULE, COL_YEAR_RULE, COL_REPEAT_END,
            COL_NOTIFY, COL_NOTIFY_TIME,
            COL_COMPLETED, COL_COMPLETED_DATE);

    /** Markdown / task-list checkbox at the head of a title, with the state captured. */
    private static final Pattern CHECKBOX_PREFIX =
            Pattern.compile("^\\s*(?:[-*+]\\s*)?\\[\\s*([xX ]?)\\s*]\\s*");

    private static final String[] WEEKDAY_TOKENS = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
    private static final String[] WEEKDAY_JA = {"月", "火", "水", "木", "金", "土", "日"};

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        alias(COL_TITLE, "title", "name", "task", "todo", "subject", "item", "タイトル", "件名", "名前", "やること");
        alias(COL_DESCRIPTION, "description", "desc", "note", "notes", "memo", "detail", "details", "説明", "詳細", "メモ");
        alias(COL_PRIORITY, "priority", "prio", "importance", "優先度", "プライオリティ", "重要度");
        alias(COL_GROUP, "group", "category", "tag", "project", "list", "グループ", "カテゴリ", "分類");
        alias(COL_START, "startdate", "start", "begin", "from", "開始予定日", "開始日", "開始");
        alias(COL_DUE, "duedate", "due", "deadline", "end", "完了予定日", "期限", "締切", "期日", "予定日");
        alias(COL_REPEAT, "repeat", "recurring", "recurrence", "繰り返し", "繰り返し有り", "繰返し");
        alias(COL_REPEAT_TYPE, "repeattype", "cycle", "frequency", "freq", "繰り返し種別", "周期", "サイクル");
        alias(COL_REPEAT_INTERVAL, "repeatinterval", "interval", "every", "間隔");
        alias(COL_WEEK_RULE, "weekrule", "weekdays", "weekday", "daysofweek", "曜日");
        alias(COL_MONTH_RULE, "monthrule", "daysofmonth", "monthdays", "days", "日にち");
        alias(COL_YEAR_RULE, "yearrule", "months", "月");
        alias(COL_REPEAT_END, "repeatenddate", "repeatend", "until", "繰り返し終了日", "終了日");
        alias(COL_NOTIFY, "notify", "notification", "reminder", "alarm", "通知", "通知有り");
        alias(COL_NOTIFY_TIME, "notifytime", "remindertime", "time", "通知時刻", "通知時間");
        alias(COL_COMPLETED, "completed", "complete", "done", "status", "完了", "完了フラグ", "済");
        alias(COL_COMPLETED_DATE, "completeddate", "completedat", "donedate", "完了日");
    }

    private TodoCsv() {
    }

    private static void alias(String column, String... names) {
        for (String name : names) {
            ALIASES.put(name, column);
        }
    }

    // ----------------------------------------------------------------- export

    @NonNull
    public static String export(@NonNull List<Todo> todos) {
        StringBuilder out = new StringBuilder();
        out.append(Csv.formatRow(HEADER)).append('\n');
        for (Todo todo : todos) {
            out.append(Csv.formatRow(toRow(todo))).append('\n');
        }
        return out.toString();
    }

    private static List<String> toRow(Todo todo) {
        List<String> row = new ArrayList<>(HEADER.size());
        row.add(todo.title);
        row.add(todo.description == null ? "" : todo.description);
        row.add(todo.priority.shortName());
        row.add(todo.groupName == null ? "" : todo.groupName);
        row.add(DateUtils.toIso(todo.startDate));
        row.add(DateUtils.toIso(todo.dueDate));
        row.add(todo.repeat ? "true" : "false");
        row.add(todo.repeatType.name().toLowerCase(Locale.ROOT));
        row.add(Integer.toString(Math.max(1, todo.repeatInterval)));
        row.add(formatWeekRule(todo.weekRule));
        row.add(formatNumberRule(RepeatRule.monthDays(todo.monthRule)));
        row.add(formatNumberRule(RepeatRule.months(todo.yearRule)));
        row.add(DateUtils.toIso(todo.repeatEndDate));
        row.add(todo.notify ? "true" : "false");
        row.add(DateUtils.formatTime(todo.notifyMinuteOfDay));
        row.add(todo.completed ? "true" : "false");
        row.add(DateUtils.toIso(todo.completedDate));
        return row;
    }

    private static String formatWeekRule(int weekRule) {
        StringBuilder out = new StringBuilder();
        for (DayOfWeek day : RepeatRule.weekDays(weekRule)) {
            if (out.length() > 0) {
                out.append('|');
            }
            out.append(WEEKDAY_TOKENS[day.getValue() - 1]);
        }
        return out.toString();
    }

    private static String formatNumberRule(List<Integer> values) {
        StringBuilder out = new StringBuilder();
        for (int value : values) {
            if (out.length() > 0) {
                out.append('|');
            }
            out.append(value);
        }
        return out.toString();
    }

    // ----------------------------------------------------------------- import

    @NonNull
    public static ImportResult parse(@Nullable String text) {
        List<List<String>> rows = Csv.parse(text);
        List<Todo> todos = new ArrayList<>();
        int skipped = 0;

        if (rows.isEmpty()) {
            return new ImportResult(todos, 0, false);
        }

        Map<String, Integer> columns = readHeader(rows.get(0));
        boolean hadHeader = columns != null;
        int firstDataRow = hadHeader ? 1 : 0;
        if (!hadHeader) {
            // No header: assume a plain list, one title per line.
            columns = new HashMap<>();
            columns.put(COL_TITLE, 0);
        }

        for (int i = firstDataRow; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (Csv.isBlank(row)) {
                continue;
            }
            Todo todo = toTodo(row, columns);
            if (todo == null) {
                skipped++;
            } else {
                todos.add(todo);
            }
        }
        return new ImportResult(todos, skipped, hadHeader);
    }

    /** Maps canonical column names to indices, or returns null when this is not a header row. */
    @Nullable
    private static Map<String, Integer> readHeader(List<String> row) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < row.size(); i++) {
            String canonical = ALIASES.get(normalizeHeader(row.get(i)));
            if (canonical != null && !columns.containsKey(canonical)) {
                columns.put(canonical, i);
            }
        }
        // A title column is the minimum needed to treat the first row as a header.
        return columns.containsKey(COL_TITLE) ? columns : null;
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-　]", "");
    }

    @Nullable
    private static Todo toTodo(List<String> row, Map<String, Integer> columns) {
        String rawTitle = cell(row, columns, COL_TITLE);
        if (rawTitle == null) {
            return null;
        }

        Matcher checkbox = CHECKBOX_PREFIX.matcher(rawTitle);
        Boolean checkboxState = null;
        if (checkbox.find()) {
            String mark = checkbox.group(1);
            checkboxState = mark != null && mark.trim().equalsIgnoreCase("x");
            rawTitle = rawTitle.substring(checkbox.end());
        }

        String title = rawTitle.trim();
        if (title.isEmpty()) {
            return null;
        }

        Todo todo = new Todo();
        todo.title = title;
        todo.description = emptyToNull(cell(row, columns, COL_DESCRIPTION));
        todo.priority = Priority.parse(cell(row, columns, COL_PRIORITY));
        todo.groupName = emptyToNull(cell(row, columns, COL_GROUP));
        todo.startDate = DateUtils.parseDate(cell(row, columns, COL_START));
        todo.dueDate = DateUtils.parseDate(cell(row, columns, COL_DUE));

        todo.repeatType = RepeatType.parse(cell(row, columns, COL_REPEAT_TYPE));
        Boolean repeatFlag = parseBoolean(cell(row, columns, COL_REPEAT));
        // The type alone is enough to mean "repeating"; the flag can override it.
        todo.repeat = repeatFlag != null ? repeatFlag : todo.repeatType != RepeatType.NONE;
        if (todo.repeat && todo.repeatType == RepeatType.NONE) {
            todo.repeatType = RepeatType.DAILY;
        }
        todo.repeatInterval = parseInt(cell(row, columns, COL_REPEAT_INTERVAL), 1);
        todo.weekRule = parseWeekRule(cell(row, columns, COL_WEEK_RULE));
        todo.monthRule = parseNumberRule(cell(row, columns, COL_MONTH_RULE), 1, 31);
        todo.yearRule = parseNumberRule(cell(row, columns, COL_YEAR_RULE), 1, 12);
        todo.repeatEndDate = DateUtils.parseDate(cell(row, columns, COL_REPEAT_END));

        Boolean notify = parseBoolean(cell(row, columns, COL_NOTIFY));
        todo.notify = notify != null && notify;
        todo.notifyMinuteOfDay = DateUtils.parseMinuteOfDay(
                cell(row, columns, COL_NOTIFY_TIME), Todo.DEFAULT_NOTIFY_MINUTE);

        Boolean completed = parseBoolean(cell(row, columns, COL_COMPLETED));
        if (completed == null) {
            completed = checkboxState;
        }
        todo.completed = completed != null && completed;
        todo.completedDate = DateUtils.parseDate(cell(row, columns, COL_COMPLETED_DATE));
        if (todo.completed && todo.completedDate == null) {
            todo.completedDate = todo.dueDate != null ? todo.dueDate : DateUtils.today();
        }
        if (!todo.completed) {
            todo.completedDate = null;
        }
        return todo;
    }

    @Nullable
    private static String cell(List<String> row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || index >= row.size()) {
            return null;
        }
        return row.get(index);
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    static Boolean parseBoolean(@Nullable String raw) {
        String value = emptyToNull(raw);
        if (value == null) {
            return null;
        }
        switch (value.toLowerCase(Locale.ROOT)) {
            case "true": case "1": case "yes": case "y": case "on":
            case "done": case "x": case "✓": case "✔": case "○": case "◯":
            case "はい": case "有": case "あり": case "完了": case "済":
                return Boolean.TRUE;
            default:
                return Boolean.FALSE;
        }
    }

    static int parseInt(@Nullable String raw, int fallback) {
        String value = emptyToNull(raw);
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Accepts "MON|WED", "mon,wed", "月・水" and "1|3". */
    static int parseWeekRule(@Nullable String raw) {
        String value = emptyToNull(raw);
        if (value == null) {
            return 0;
        }
        int mask = 0;
        for (String token : splitTokens(value)) {
            int index = weekdayIndex(token);
            if (index >= 0) {
                mask = RepeatRule.withBit(mask, index, true);
            }
        }
        return mask;
    }

    private static int weekdayIndex(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        for (int i = 0; i < WEEKDAY_TOKENS.length; i++) {
            if (upper.startsWith(WEEKDAY_TOKENS[i]) || token.startsWith(WEEKDAY_JA[i])) {
                return i;
            }
        }
        try {
            int number = Integer.parseInt(token);
            if (number >= 1 && number <= 7) {
                return number - 1;
            }
        } catch (NumberFormatException ignored) {
            // not a weekday
        }
        return -1;
    }

    /** Accepts "1|15", "1,15" and "1 15" within the given inclusive bounds. */
    static int parseNumberRule(@Nullable String raw, int min, int max) {
        String value = emptyToNull(raw);
        if (value == null) {
            return 0;
        }
        int mask = 0;
        for (String token : splitTokens(value)) {
            try {
                int number = Integer.parseInt(token);
                if (number >= min && number <= max) {
                    mask = RepeatRule.withBit(mask, number - 1, true);
                }
            } catch (NumberFormatException ignored) {
                // skip anything that is not a number
            }
        }
        return mask;
    }

    private static List<String> splitTokens(String value) {
        List<String> tokens = new ArrayList<>();
        for (String token : value.split("[|,;/・\\s]+")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    /** Suggested file name for an export, e.g. {@code kotodo-2026-07-25.csv}. */
    @NonNull
    public static String suggestedFileName(@NonNull LocalDate day) {
        return "kotodo-" + DateUtils.toIso(day) + ".csv";
    }
}
