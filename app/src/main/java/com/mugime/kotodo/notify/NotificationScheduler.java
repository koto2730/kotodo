package com.mugime.kotodo.notify;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;

import com.mugime.kotodo.R;
import com.mugime.kotodo.data.KotodoDatabase;
import com.mugime.kotodo.elements.Todo;
import com.mugime.kotodo.utils.DateUtils;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Arms one {@link AlarmManager} alarm per todo that has 通知有り set.
 *
 * <p>Reminders fire at the todo's notify time on the day it first appears in the
 * list (the earlier of 開始予定日 and 完了予定日). Alarms are re-armed from scratch
 * after every database write and after boot, so the schedule is always derived
 * from the current data rather than patched incrementally.</p>
 *
 * <p>Everything is local: no push service, no network.</p>
 */
public final class NotificationScheduler {

    public static final String CHANNEL_ID = "kotodo_reminders";
    public static final String EXTRA_TODO_ID = "com.mugime.kotodo.EXTRA_TODO_ID";

    private static final String PREFS = "kotodo_alarms";
    private static final String KEY_SCHEDULED_IDS = "scheduled_ids";

    /** Alarms further out than this are re-armed on a later app launch instead. */
    private static final int HORIZON_DAYS = 120;

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();

    private NotificationScheduler() {
    }

    /** Creates the notification channel. Safe to call repeatedly. */
    public static void createChannel(@NonNull Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    /**
     * Cancels every previously armed alarm and re-arms the current set.
     * Runs asynchronously, so it is safe to call from any thread.
     */
    public static void rescheduleAll(@NonNull Context context, @NonNull LocalDate today) {
        Context appContext = context.getApplicationContext();
        worker.execute(() -> rescheduleNow(appContext, today));
    }

    private static void rescheduleNow(Context context, LocalDate today) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        for (String id : prefs.getStringSet(KEY_SCHEDULED_IDS, new HashSet<>())) {
            try {
                // FLAG_NO_CREATE returns null when the alarm is already gone.
                PendingIntent existing = alarmIntent(context, Long.parseLong(id), PendingIntent.FLAG_NO_CREATE);
                if (existing != null) {
                    alarmManager.cancel(existing);
                    existing.cancel();
                }
            } catch (NumberFormatException ignored) {
                // stale preference value, nothing to cancel
            }
        }

        List<Todo> candidates = KotodoDatabase.getInstance(context).todoDao().loadNotifiable();
        long now = System.currentTimeMillis();
        long horizon = DateUtils.toEpochMillis(today.plusDays(HORIZON_DAYS), 0);
        Set<String> scheduled = new HashSet<>();

        for (Todo todo : candidates) {
            LocalDate day = todo.notifyDate();
            if (day == null) {
                continue;
            }
            long triggerAt = DateUtils.toEpochMillis(day, todo.notifyMinuteOfDay);
            // Past reminders are not replayed: showing yesterday's 09:00 alert now
            // would be noise, and the todo is already visible in today's list.
            if (triggerAt <= now || triggerAt > horizon) {
                continue;
            }
            schedule(alarmManager, context, todo.id, triggerAt);
            scheduled.add(Long.toString(todo.id));
        }

        prefs.edit().putStringSet(KEY_SCHEDULED_IDS, scheduled).apply();
    }

    private static void schedule(AlarmManager alarmManager, Context context, long todoId, long triggerAt) {
        PendingIntent intent = alarmIntent(context, todoId, PendingIntent.FLAG_UPDATE_CURRENT);
        if (intent == null) {
            return;
        }
        try {
            if (canScheduleExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent);
            } else {
                // Without the exact-alarm permission the reminder may slip by a few
                // minutes, which is fine for a todo nudge.
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, intent);
            }
        } catch (SecurityException e) {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, intent);
        }
    }

    private static boolean canScheduleExact(AlarmManager alarmManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return alarmManager.canScheduleExactAlarms();
    }

    private static PendingIntent alarmIntent(Context context, long todoId, int flags) {
        Intent intent = new Intent(context, TodoAlarmReceiver.class)
                .putExtra(EXTRA_TODO_ID, todoId);
        return PendingIntent.getBroadcast(
                context,
                (int) todoId,
                intent,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }
}
