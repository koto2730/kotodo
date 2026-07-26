package com.mugime.kotodo.data;

import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import com.mugime.kotodo.elements.Priority;
import com.mugime.kotodo.elements.RepeatType;

import java.time.LocalDate;

/**
 * Room type converters.
 *
 * <p>Dates are stored as epoch days rather than formatted strings: they stay
 * comparable with {@code &lt;=} in SQL, they are timezone free, and they survive
 * a device moving between locales.</p>
 */
public final class Converters {

    private Converters() {
    }

    @TypeConverter
    @Nullable
    public static Long fromLocalDate(@Nullable LocalDate date) {
        return date == null ? null : date.toEpochDay();
    }

    @TypeConverter
    @Nullable
    public static LocalDate toLocalDate(@Nullable Long epochDay) {
        return epochDay == null ? null : LocalDate.ofEpochDay(epochDay);
    }

    @TypeConverter
    public static int fromPriority(@Nullable Priority priority) {
        return priority == null ? Priority.DEFAULT.ordinal() : priority.ordinal();
    }

    @TypeConverter
    public static Priority toPriority(int ordinal) {
        return Priority.fromOrdinal(ordinal);
    }

    @TypeConverter
    public static String fromRepeatType(@Nullable RepeatType type) {
        return type == null ? RepeatType.NONE.name() : type.name();
    }

    @TypeConverter
    public static RepeatType toRepeatType(@Nullable String name) {
        return RepeatType.parse(name);
    }
}
