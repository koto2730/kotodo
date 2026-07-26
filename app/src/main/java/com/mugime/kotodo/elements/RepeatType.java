package com.mugime.kotodo.elements;

/**
 * Repeat cycle unit. Persisted by name, so renaming a constant needs a migration.
 */
public enum RepeatType {
    /** No repetition. Also used whenever {@code Todo.repeat} is false. */
    NONE,
    /** 日次 - every {@code repeatInterval} days. */
    DAILY,
    /** 週次 - every {@code repeatInterval} weeks, on the days selected in {@code weekRule}. */
    WEEKLY,
    /** 月次 - every {@code repeatInterval} months, on the days selected in {@code monthRule}. */
    MONTHLY,
    /** 年次 - every {@code repeatInterval} years, in the months selected in {@code yearRule}. */
    YEARLY;

    public static RepeatType parse(String raw) {
        if (raw == null) {
            return NONE;
        }
        String value = raw.trim();
        for (RepeatType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return NONE;
    }
}
