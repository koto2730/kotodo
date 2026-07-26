package com.mugime.kotodo.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.mugime.kotodo.elements.Todo;
import com.mugime.kotodo.notify.NotificationScheduler;
import com.mugime.kotodo.utils.DateUtils;
import com.mugime.kotodo.utils.RepeatRule;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The single entry point the UI uses to touch the database.
 *
 * <p>Reads are exposed as {@link LiveData} (Room does the threading); writes run on
 * a single background thread, which also serialises them. Every write re-arms the
 * pending reminders, so notifications can never drift away from the data.</p>
 */
public class TodoRepository {

    /** Result callback, always delivered on the main thread. */
    public interface Callback<T> {
        void onResult(T value);
    }

    private static volatile TodoRepository instance;

    private final Context appContext;
    private final TodoDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    private TodoRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.dao = KotodoDatabase.getInstance(this.appContext).todoDao();
    }

    public static TodoRepository get(@NonNull Context context) {
        if (instance == null) {
            synchronized (TodoRepository.class) {
                if (instance == null) {
                    instance = new TodoRepository(context);
                }
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ reads

    public LiveData<List<Todo>> observeForDay(@NonNull LocalDate day) {
        return dao.observeForDay(day.toEpochDay());
    }

    public LiveData<List<Todo>> observeAll() {
        return dao.observeAll();
    }

    public LiveData<List<Todo>> observeCompleted() {
        return dao.observeCompleted();
    }

    public LiveData<List<String>> observeGroups() {
        return dao.observeGroups();
    }

    public LiveData<Todo> observeById(long id) {
        return dao.observeById(id);
    }

    /** One-shot read used by the edit screen; delivers {@code null} for a missing id. */
    public void loadById(long id, @NonNull Callback<Todo> callback) {
        io.execute(() -> {
            Todo todo = dao.findById(id);
            mainThread.post(() -> callback.onResult(todo));
        });
    }

    public void loadAll(@NonNull Callback<List<Todo>> callback) {
        io.execute(() -> {
            List<Todo> all = dao.loadAll();
            mainThread.post(() -> callback.onResult(all));
        });
    }

    // ----------------------------------------------------------------- writes

    public void insert(@NonNull Todo todo, @Nullable Callback<Long> callback) {
        io.execute(() -> {
            long id = dao.insert(todo);
            afterWrite();
            if (callback != null) {
                mainThread.post(() -> callback.onResult(id));
            }
        });
    }

    public void update(@NonNull Todo todo, @Nullable Runnable callback) {
        io.execute(() -> {
            dao.update(todo);
            afterWrite();
            postIfNeeded(callback);
        });
    }

    public void delete(@NonNull Todo todo, @Nullable Runnable callback) {
        io.execute(() -> {
            dao.delete(todo);
            afterWrite();
            postIfNeeded(callback);
        });
    }

    /** Bulk delete backing the completed screen's "clear all" action. */
    public void deleteAll(@NonNull List<Todo> todos, @Nullable Runnable callback) {
        io.execute(() -> {
            dao.delete(todos);
            afterWrite();
            postIfNeeded(callback);
        });
    }

    /** Re-inserts previously deleted todos, keeping their identity so undo is a true restore. */
    public void restoreAll(@NonNull List<Todo> todos) {
        io.execute(() -> {
            dao.insertAll(todos);
            afterWrite();
        });
    }

    /**
     * Toggles 完了フラグ.
     *
     * <p>Completing sets 完了日 to the given day and, when the item repeats, inserts
     * the next occurrence in the same transaction-ish step. Un-completing only clears
     * the two completion fields: an already generated follow-up is left alone, since
     * deleting a todo the user may have started editing would be worse than a
     * duplicate they can remove.</p>
     *
     * @param callback receives the newly created follow-up todo, or {@code null}.
     */
    public void setCompleted(@NonNull Todo todo, boolean completed, @NonNull LocalDate day,
                             @Nullable Callback<Todo> callback) {
        io.execute(() -> {
            Todo followUp = null;
            // Update a copy: the caller's instance is the one the list adapter is
            // still holding, and mutating it would hide the change from DiffUtil.
            Todo edited = todo.copy();
            edited.completed = completed;
            edited.completedDate = completed ? day : null;
            dao.update(edited);

            if (completed) {
                Todo next = RepeatRule.nextOccurrence(edited, day);
                if (next != null) {
                    long id = dao.insert(next);
                    next.id = id;
                    followUp = next;
                }
            }
            afterWrite();

            Todo result = followUp;
            if (callback != null) {
                mainThread.post(() -> callback.onResult(result));
            }
        });
    }

    /**
     * Bulk insert used by the CSV importer.
     *
     * @param replaceExisting when true the table is cleared first, so the file becomes
     *                        the complete new state instead of being merged in.
     * @param callback        receives the number of rows written.
     */
    public void importTodos(@NonNull List<Todo> todos, boolean replaceExisting,
                            @Nullable Callback<Integer> callback) {
        io.execute(() -> {
            if (replaceExisting) {
                dao.deleteAll();
            }
            dao.insertAll(todos);
            afterWrite();
            if (callback != null) {
                mainThread.post(() -> callback.onResult(todos.size()));
            }
        });
    }

    /** Runs {@code work} on the repository's background thread. */
    public void runInBackground(@NonNull Runnable work) {
        io.execute(work);
    }

    private void afterWrite() {
        NotificationScheduler.rescheduleAll(appContext, DateUtils.today());
    }

    private void postIfNeeded(@Nullable Runnable callback) {
        if (callback != null) {
            mainThread.post(callback);
        }
    }
}
