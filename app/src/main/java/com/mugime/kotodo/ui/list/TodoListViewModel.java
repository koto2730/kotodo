package com.mugime.kotodo.ui.list;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.mugime.kotodo.data.TodoRepository;
import com.mugime.kotodo.elements.Todo;
import com.mugime.kotodo.utils.DateUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds one list screen's state: which base query to run, which date it is
 * anchored to, and the user's filter/sort choices.
 *
 * <p>The database query stays coarse (see {@code TodoDao}); filtering and sorting
 * run here over the loaded list so any combination of options works without a
 * combinatorial explosion of SQL.</p>
 */
public class TodoListViewModel extends AndroidViewModel {

    /** The (mode, date) pair that decides which DAO query is observed. */
    private static class Query {
        final ListMode mode;
        final LocalDate date;

        Query(ListMode mode, LocalDate date) {
            this.mode = mode;
            this.date = date;
        }
    }

    private final TodoRepository repository;

    private final MutableLiveData<Query> query = new MutableLiveData<>();
    private final MutableLiveData<ListOptions> options = new MutableLiveData<>(new ListOptions());
    private final MutableLiveData<LocalDate> referenceDate = new MutableLiveData<>(DateUtils.today());
    private final MediatorLiveData<List<Todo>> visibleTodos = new MediatorLiveData<>();

    private final LiveData<List<Todo>> source;
    private ListMode mode = ListMode.TODAY;
    private List<Todo> lastLoaded = Collections.emptyList();

    public TodoListViewModel(@NonNull Application application) {
        super(application);
        repository = TodoRepository.get(application);

        source = Transformations.switchMap(query, current -> {
            switch (current.mode) {
                case ALL:
                    return repository.observeAll();
                case COMPLETED:
                    return repository.observeCompleted();
                case TODAY:
                default:
                    return repository.observeForDay(current.date);
            }
        });

        visibleTodos.addSource(source, todos -> {
            lastLoaded = todos == null ? Collections.emptyList() : todos;
            recompute();
        });
        visibleTodos.addSource(options, ignored -> recompute());
    }

    /** Called once by the fragment, from the navigation argument. */
    public void setMode(@NonNull ListMode mode) {
        if (query.getValue() != null && this.mode == mode) {
            return;
        }
        this.mode = mode;
        query.setValue(new Query(mode, currentDate()));
    }

    public ListMode getMode() {
        return mode;
    }

    /** Moves the 当日 screen to another date so past and future days can be reviewed. */
    public void setReferenceDate(@NonNull LocalDate date) {
        if (date.equals(currentDate())) {
            return;
        }
        referenceDate.setValue(date);
        query.setValue(new Query(mode, date));
        recompute();
    }

    public void shiftReferenceDate(long days) {
        setReferenceDate(currentDate().plusDays(days));
    }

    public void resetToToday() {
        setReferenceDate(DateUtils.today());
    }

    @NonNull
    public LocalDate currentDate() {
        LocalDate date = referenceDate.getValue();
        return date == null ? DateUtils.today() : date;
    }

    public LiveData<LocalDate> getReferenceDate() {
        return referenceDate;
    }

    public LiveData<List<Todo>> getVisibleTodos() {
        return visibleTodos;
    }

    public LiveData<List<String>> getGroups() {
        return repository.observeGroups();
    }

    @NonNull
    public ListOptions getOptions() {
        ListOptions current = options.getValue();
        return current == null ? new ListOptions() : current;
    }

    public void setOptions(@NonNull ListOptions newOptions) {
        options.setValue(newOptions);
    }

    // ------------------------------------------------------------ mutations

    /**
     * Ticks an item off. On completion the repository also creates the next
     * occurrence for repeating todos; {@code onFollowUp} receives it so the UI can
     * tell the user what was scheduled.
     */
    public void setCompleted(@NonNull Todo todo, boolean completed,
                             @Nullable TodoRepository.Callback<Todo> onFollowUp) {
        // Deliberately DateUtils.today(), not currentDate(): completing an item is a
        // real-world action that happens now, regardless of which day the Today
        // screen's date bar happens to be browsing. Using the browsed date here would
        // record the wrong 完了日 and, for repeating todos, compute the next occurrence
        // from the wrong reference date.
        repository.setCompleted(todo, completed, DateUtils.today(), onFollowUp);
    }

    public void delete(@NonNull Todo todo, @Nullable Runnable onDone) {
        repository.delete(todo, onDone);
    }

    /** Re-inserts a deleted todo, keeping its identity so undo is a true restore. */
    public void restore(@NonNull Todo todo) {
        repository.insert(todo, null);
    }

    /** Deletes every todo currently passed in, e.g. all completed items on screen. */
    public void deleteAll(@NonNull List<Todo> todos, @Nullable Runnable onDone) {
        repository.deleteAll(todos, onDone);
    }

    /** Undo counterpart to {@link #deleteAll}. */
    public void restoreAll(@NonNull List<Todo> todos) {
        repository.restoreAll(todos);
    }

    public TodoRepository getRepository() {
        return repository;
    }

    // ------------------------------------------------------------- internals

    private void recompute() {
        ListOptions current = getOptions();
        LocalDate date = currentDate();
        List<Todo> result = new ArrayList<>(lastLoaded.size());
        for (Todo todo : lastLoaded) {
            if (current.matches(todo, date)) {
                result.add(todo);
            }
        }
        Collections.sort(result, current.comparator());
        visibleTodos.setValue(result);
    }
}
