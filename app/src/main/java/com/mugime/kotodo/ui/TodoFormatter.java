package com.mugime.kotodo.ui;

import android.content.Context;

import androidx.annotation.NonNull;

import com.mugime.kotodo.R;
import com.mugime.kotodo.elements.Priority;
import com.mugime.kotodo.elements.Todo;
import com.mugime.kotodo.utils.DateUtils;
import com.mugime.kotodo.utils.RepeatRule;

import java.time.DayOfWeek;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link Todo} into the short human-readable strings the list and edit
 * screens show. Weekday and month names come from {@code java.time}, so they
 * follow the device locale without needing their own string resources.
 */
public final class TodoFormatter {

    private static final String SEPARATOR = " · ";

    private TodoFormatter() {
    }

    @NonNull
    public static String priorityLabel(@NonNull Context context, @NonNull Priority priority) {
        String[] labels = context.getResources().getStringArray(R.array.priority_labels);
        int index = priority.ordinal();
        return index < labels.length ? labels[index] : priority.name();
    }

    /** The compact second line of a list row: group, dates, cycle and reminder. */
    @NonNull
    public static String metaLine(@NonNull Context context, @NonNull Todo todo) {
        List<String> parts = new ArrayList<>(5);

        if (todo.groupName != null && !todo.groupName.trim().isEmpty()) {
            parts.add(todo.groupName.trim());
        }
        if (todo.startDate != null) {
            parts.add(context.getString(R.string.meta_start, DateUtils.formatShort(todo.startDate)));
        }
        if (todo.dueDate != null) {
            parts.add(context.getString(R.string.meta_due, DateUtils.formatShort(todo.dueDate)));
        }
        String repeat = describeRepeat(context, todo);
        if (!repeat.isEmpty()) {
            parts.add(repeat);
        }
        if (todo.notify) {
            parts.add(context.getString(R.string.meta_notify, DateUtils.formatTime(todo.notifyMinuteOfDay)));
        }
        if (todo.completed && todo.completedDate != null) {
            parts.add(context.getString(R.string.meta_completed, DateUtils.formatShort(todo.completedDate)));
        }
        return String.join(SEPARATOR, parts);
    }

    /**
     * Describes the repeat cycle, e.g. "毎週 (月・水)" or "Every 2 months (1, 15)".
     * Returns an empty string when the todo does not repeat.
     */
    @NonNull
    public static String describeRepeat(@NonNull Context context, @NonNull Todo todo) {
        if (!RepeatRule.isActive(todo)) {
            return "";
        }
        int interval = Math.max(1, todo.repeatInterval);
        boolean single = interval == 1;
        String base;
        String detail = "";

        switch (todo.repeatType) {
            case DAILY:
                base = single
                        ? context.getString(R.string.repeat_every_day)
                        : context.getString(R.string.repeat_every_n_days, interval);
                break;
            case WEEKLY:
                base = single
                        ? context.getString(R.string.repeat_every_week)
                        : context.getString(R.string.repeat_every_n_weeks, interval);
                detail = weekDaysLabel(todo.weekRule);
                break;
            case MONTHLY:
                base = single
                        ? context.getString(R.string.repeat_every_month)
                        : context.getString(R.string.repeat_every_n_months, interval);
                detail = daysOfMonthLabel(context, todo.monthRule);
                break;
            case YEARLY:
                base = single
                        ? context.getString(R.string.repeat_every_year)
                        : context.getString(R.string.repeat_every_n_years, interval);
                detail = joinMonthAndDay(context,
                        monthsLabel(todo.yearRule), daysOfMonthLabel(context, todo.monthRule));
                break;
            default:
                return "";
        }

        if (detail.isEmpty()) {
            return base;
        }
        return base + context.getString(R.string.repeat_detail_suffix, detail);
    }

    /** Localised short weekday names for the selected bits, e.g. "月・水". */
    @NonNull
    public static String weekDaysLabel(int weekRule) {
        List<String> names = new ArrayList<>(7);
        for (DayOfWeek day : RepeatRule.weekDays(weekRule)) {
            names.add(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()));
        }
        return String.join("・", names);
    }

    /** Localised short month names for the selected bits, e.g. "1月・7月". */
    @NonNull
    public static String monthsLabel(int yearRule) {
        List<String> names = new ArrayList<>(12);
        for (int month : RepeatRule.months(yearRule)) {
            names.add(Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault()));
        }
        return String.join("・", names);
    }

    /** Days of the month for the selected bits, e.g. "1日・15日". */
    @NonNull
    public static String daysOfMonthLabel(@NonNull Context context, int monthRule) {
        List<String> names = new ArrayList<>(31);
        for (int day : RepeatRule.monthDays(monthRule)) {
            names.add(context.getString(R.string.day_of_month, day));
        }
        return String.join("・", names);
    }

    /**
     * Joins the month and day parts of a yearly rule. The separator is a resource
     * because English wants a space ("Jul 15") and Japanese does not ("7月15日").
     */
    private static String joinMonthAndDay(Context context, String months, String days) {
        if (months.isEmpty()) {
            return days;
        }
        if (days.isEmpty()) {
            return months;
        }
        return context.getString(R.string.repeat_month_day_join, months, days);
    }
}
