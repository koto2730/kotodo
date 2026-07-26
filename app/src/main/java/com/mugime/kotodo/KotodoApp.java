package com.mugime.kotodo;

import android.app.Application;

import com.mugime.kotodo.notify.NotificationScheduler;
import com.mugime.kotodo.utils.DateUtils;

/**
 * Application entry point. Creates the notification channel and re-arms reminders
 * once per process start, which covers the case where the app was killed while
 * alarms were pending.
 */
public class KotodoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationScheduler.createChannel(this);
        NotificationScheduler.rescheduleAll(this, DateUtils.today());
    }
}
