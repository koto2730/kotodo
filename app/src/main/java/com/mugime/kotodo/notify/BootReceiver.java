package com.mugime.kotodo.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mugime.kotodo.utils.DateUtils;

/**
 * Alarms do not survive a reboot, an app update or a clock change, so the whole
 * reminder schedule is rebuilt whenever one of those happens.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        NotificationScheduler.createChannel(context);
        NotificationScheduler.rescheduleAll(context, DateUtils.today());
    }
}
