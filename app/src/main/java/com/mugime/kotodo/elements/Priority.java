package com.mugime.kotodo.elements;

import java.util.Locale;

/**
 * Todo priority. The declaration order is also the sort order: the first
 * constant is the most urgent one, so {@link Enum#ordinal()} can be used
 * directly as an ascending sort key.
 *
 * <p>The ordinal is what gets persisted, so never reorder or remove constants
 * without writing a database migration.</p>
 */
public enum Priority {
    HighestPriority,
    HighPriority,
    MiddlePriority,
    LowPriority,
    LowestPriority;

    public static final Priority DEFAULT = MiddlePriority;

    /** Null-safe, out-of-range-safe lookup used by the database and the CSV importer. */
    public static Priority fromOrdinal(int ordinal) {
        Priority[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return DEFAULT;
        }
        return values[ordinal];
    }

    /**
     * Parses the CSV representation. Accepts the enum name ("HighPriority"),
     * the short name ("high") and the raw ordinal ("1").
     */
    public static Priority parse(String raw) {
        if (raw == null) {
            return DEFAULT;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return DEFAULT;
        }
        for (Priority priority : values()) {
            if (priority.name().equalsIgnoreCase(value) || priority.shortName().equalsIgnoreCase(value)) {
                return priority;
            }
        }
        try {
            return fromOrdinal(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return DEFAULT;
        }
    }

    /** Stable, human friendly token used in exported CSV files. */
    public String shortName() {
        // Locale.ROOT, not the device locale: this token goes into files that other
        // devices read back, and a Turkish locale would lower-case "I" to "ı".
        return name().replace("Priority", "").toLowerCase(Locale.ROOT);
    }
}
