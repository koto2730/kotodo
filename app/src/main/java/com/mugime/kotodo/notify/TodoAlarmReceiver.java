package com.mugime.kotodo.notify;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.mugime.kotodo.MainActivity;
import com.mugime.kotodo.R;
import com.mugime.kotodo.data.KotodoDatabase;
import com.mugime.kotodo.elements.Todo;
import com.mugime.kotodo.utils.DateUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fires when a todo's reminder time arrives and posts the local notification.
 */
public class TodoAlarmReceiver extends BroadcastReceiver {

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        long todoId = intent.getLongExtra(NotificationScheduler.EXTRA_TODO_ID, -1L);
        if (todoId < 0) {
            return;
        }
        Context appContext = context.getApplicationContext();
        PendingResult pendingResult = goAsync();
        worker.execute(() -> {
            try {
                Todo todo = KotodoDatabase.getInstance(appContext).todoDao().findById(todoId);
                // The todo may have been completed or deleted since the alarm was set.
                if (todo != null && !todo.completed) {
                    notify(appContext, todo);
                }
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void notify(Context context, Todo todo) {
        // POST_NOTIFICATIONS only exists from API 33; below that, checkSelfPermission
        // would report an unknown permission as denied.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, (int) todo.id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(todo.title)
                .setContentText(summaryOf(context, todo))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        if (todo.description != null && !todo.description.trim().isEmpty()) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(todo.description));
        }

        try {
            NotificationManagerCompat.from(context).notify((int) todo.id, builder.build());
        } catch (SecurityException ignored) {
            // Permission revoked between the check above and the post.
        }
    }

    private String summaryOf(Context context, Todo todo) {
        if (todo.dueDate != null) {
            return context.getString(R.string.notification_due_on, DateUtils.formatDisplay(todo.dueDate));
        }
        if (todo.startDate != null) {
            return context.getString(R.string.notification_starts_on, DateUtils.formatDisplay(todo.startDate));
        }
        return todo.description == null ? "" : todo.description;
    }
}
