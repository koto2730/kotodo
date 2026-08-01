package com.mugime.kotodo.elements;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single todo item. This is also the Room entity, stored in the {@code todos} table.
 *
 * <p>Fields are public on purpose: Room uses direct field access, which keeps this
 * class free of ~40 accessor methods. Dates are stored as epoch days and nullable
 * date columns really are {@code NULL} in SQLite, so the DAO can compare them
 * directly in SQL.</p>
 */
@Entity(tableName = "todos",
        indices = {@Index("startDate"), @Index("dueDate"), @Index("completed")})
public class Todo {

    /** Bit positions for {@link #weekRule}: Monday is bit 0 ... Sunday is bit 6. */
    public static final int WEEK_RULE_ALL = 0b1111111;
    /** Bit positions for {@link #monthRule}: day 1 is bit 0 ... day 31 is bit 30. */
    public static final int MONTH_RULE_ALL = 0x7FFFFFFF;
    /** Bit positions for {@link #yearRule}: January is bit 0 ... December is bit 11. */
    public static final int YEAR_RULE_ALL = 0b111111111111;

    /** Default reminder time: 09:00 local time. */
    public static final int DEFAULT_NOTIFY_MINUTE = 9 * 60;

    @PrimaryKey(autoGenerate = true)
    public long id = 0L;

    /** タイトル */
    @NonNull
    public String title = "";

    /** 説明 */
    @Nullable
    public String description;

    /** プライオリティ */
    @NonNull
    public Priority priority = Priority.DEFAULT;

    /** グループ. Free-form text; the list screen offers existing values as suggestions. */
    @Nullable
    public String groupName;

    /** 繰り返し有り */
    public boolean repeat = false;

    /** 繰り返しの単位 (日次/週次/月次/年次). */
    @NonNull
    public RepeatType repeatType = RepeatType.NONE;

    /** Every N units, N &gt;= 1. "2" plus {@link RepeatType#WEEKLY} means every other week. */
    public int repeatInterval = 1;

    /** Selected weekdays for {@link RepeatType#WEEKLY}. 0 means "same weekday as the anchor date". */
    public int weekRule = 0;

    /** Selected days of month for {@link RepeatType#MONTHLY} and {@link RepeatType#YEARLY}. */
    public int monthRule = 0;

    /** Selected months for {@link RepeatType#YEARLY}. */
    public int yearRule = 0;

    /** 繰り返し終了日. A generated occurrence after this date is not created. */
    @Nullable
    public LocalDate repeatEndDate;

    /** 完了予定日 */
    @Nullable
    public LocalDate dueDate;

    /** 開始予定日 */
    @Nullable
    public LocalDate startDate;

    /** 通知有り */
    public boolean notify = false;

    /** Minute of day the reminder fires on {@link #notifyDate()}. */
    public int notifyMinuteOfDay = DEFAULT_NOTIFY_MINUTE;

    /** 完了日 */
    @Nullable
    public LocalDate completedDate;

    /** 完了フラグ */
    public boolean completed = false;

    /**
     * Internal bookkeeping, not user-facing: true once this specific todo has ever
     * spawned its repeat follow-up. A one-way latch - it stays true even after
     * un-completing, so repeatedly checking and unchecking the same repeating todo
     * cannot create more than one follow-up. Reset to false on {@link #copyAsNew()}
     * since the new occurrence hasn't spawned its own follow-up yet.
     */
    public boolean followUpCreated = false;

    /** Creation timestamp in epoch millis, used as the tie-breaker when sorting. */
    public long createdAt = System.currentTimeMillis();

    public Todo() {
    }

    @Ignore
    public Todo(@NonNull String title, @Nullable String description) {
        this.title = title;
        this.description = description;
    }

    /**
     * The date a repeat cycle is measured from. The due date wins because that is
     * the date the user actually committed to; the start date is the fallback.
     */
    @Nullable
    public LocalDate anchorDate() {
        return dueDate != null ? dueDate : startDate;
    }

    /**
     * The date the item first shows up in the list, i.e. the earliest of the two
     * scheduled dates. Reminders fire on this date.
     */
    @Nullable
    public LocalDate notifyDate() {
        if (startDate == null) {
            return dueDate;
        }
        if (dueDate == null) {
            return startDate;
        }
        return startDate.isBefore(dueDate) ? startDate : dueDate;
    }

    /** True when neither 開始予定日 nor 完了予定日 is set: an undated todo, always listed. */
    public boolean isUndated() {
        return startDate == null && dueDate == null;
    }

    /**
     * The listing rule from the spec: undated todos always show, otherwise the item
     * shows once 開始予定日 &lt;= day or 完了予定日 &lt;= day. Completed items stay
     * visible on the day they were completed so the strike-through is not lost.
     */
    public boolean isVisibleOn(@NonNull LocalDate day) {
        if (completed) {
            return day.equals(completedDate);
        }
        if (isUndated()) {
            return true;
        }
        return (startDate != null && !startDate.isAfter(day))
                || (dueDate != null && !dueDate.isAfter(day));
    }

    /** True when 完了予定日 has passed without the item being completed. */
    public boolean isOverdueOn(@NonNull LocalDate day) {
        return !completed && dueDate != null && dueDate.isBefore(day);
    }

    /**
     * An exact field-for-field copy, primary key included.
     *
     * <p>Used whenever a todo is about to be modified: the list adapter holds the
     * instances Room handed out, so mutating one in place would make {@code DiffUtil}
     * compare an already-updated object against its replacement, decide nothing
     * changed, and skip the rebind.</p>
     */
    public Todo copy() {
        Todo copy = new Todo();
        copy.id = id;
        copy.title = title;
        copy.description = description;
        copy.priority = priority;
        copy.groupName = groupName;
        copy.repeat = repeat;
        copy.repeatType = repeatType;
        copy.repeatInterval = repeatInterval;
        copy.weekRule = weekRule;
        copy.monthRule = monthRule;
        copy.yearRule = yearRule;
        copy.repeatEndDate = repeatEndDate;
        copy.dueDate = dueDate;
        copy.startDate = startDate;
        copy.notify = notify;
        copy.notifyMinuteOfDay = notifyMinuteOfDay;
        copy.completed = completed;
        copy.completedDate = completedDate;
        copy.followUpCreated = followUpCreated;
        copy.createdAt = createdAt;
        return copy;
    }

    /** A copy that is a brand new, not-yet-completed row: used for repeat follow-ups. */
    public Todo copyAsNew() {
        Todo copy = copy();
        copy.id = 0L;
        copy.completed = false;
        copy.completedDate = null;
        copy.followUpCreated = false;
        copy.createdAt = System.currentTimeMillis();
        return copy;
    }

    @NonNull
    @Override
    public String toString() {
        return title + ":" + (description == null ? "" : description);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Todo)) {
            return false;
        }
        Todo todo = (Todo) other;
        return id != 0 && id == todo.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
