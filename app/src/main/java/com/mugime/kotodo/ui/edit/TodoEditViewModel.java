package com.mugime.kotodo.ui.edit;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mugime.kotodo.data.TodoRepository;
import com.mugime.kotodo.elements.Todo;
import com.mugime.kotodo.utils.DateUtils;

import java.util.List;

/**
 * Backs the edit screen. Holds the todo being edited across configuration changes,
 * so a rotation mid-edit does not throw away unsaved input.
 */
public class TodoEditViewModel extends AndroidViewModel {

    private final TodoRepository repository;
    private final MutableLiveData<Todo> todo = new MutableLiveData<>();
    private boolean loadStarted;
    private boolean creating;

    public TodoEditViewModel(@NonNull Application application) {
        super(application);
        repository = TodoRepository.get(application);
    }

    /** Loads the todo once per ViewModel; later calls are ignored. */
    public void load(long id) {
        if (loadStarted) {
            return;
        }
        loadStarted = true;
        if (id <= 0) {
            creating = true;
            todo.setValue(newTodo());
            return;
        }
        repository.loadById(id, loaded -> {
            if (loaded == null) {
                creating = true;
                todo.setValue(newTodo());
            } else {
                todo.setValue(loaded);
            }
        });
    }

    private Todo newTodo() {
        Todo fresh = new Todo();
        // A new todo defaults to being due today, which puts it straight on the
        // main list and gives repeat rules an anchor date to count from.
        fresh.dueDate = DateUtils.today();
        return fresh;
    }

    public LiveData<Todo> getTodo() {
        return todo;
    }

    @Nullable
    public Todo current() {
        return todo.getValue();
    }

    public boolean isCreating() {
        return creating;
    }

    public LiveData<List<String>> getGroups() {
        return repository.observeGroups();
    }

    public void save(@NonNull Todo edited, @Nullable Runnable onDone) {
        if (edited.id == 0) {
            repository.insert(edited, id -> {
                edited.id = id;
                creating = false;
                if (onDone != null) {
                    onDone.run();
                }
            });
        } else {
            repository.update(edited, onDone);
        }
    }

    public void delete(@NonNull Todo edited, @Nullable Runnable onDone) {
        repository.delete(edited, onDone);
    }
}
