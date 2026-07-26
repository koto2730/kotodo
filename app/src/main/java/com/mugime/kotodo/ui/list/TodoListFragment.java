package com.mugime.kotodo.ui.list;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.mugime.kotodo.R;
import com.mugime.kotodo.databinding.FragmentTodoListBinding;
import com.mugime.kotodo.elements.Todo;
import com.mugime.kotodo.ui.edit.TodoEditFragment;
import com.mugime.kotodo.utils.DateUtils;
import com.mugime.kotodo.utils.TodoCsv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * The main list screen. One instance backs each drawer entry; which set of todos
 * it shows comes from the {@code mode} navigation argument.
 */
public class TodoListFragment extends Fragment implements TodoAdapter.Listener {

    public static final String ARG_MODE = "mode";

    private FragmentTodoListBinding binding;
    private TodoListViewModel viewModel;
    private TodoAdapter adapter;

    private final ActivityResultLauncher<String[]> importPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onCsvPicked);

    private final ActivityResultLauncher<String> exportPicker =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), this::onCsvDestinationPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTodoListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(TodoListViewModel.class);
        Bundle args = getArguments();
        ListMode mode = ListMode.parse(args == null ? null : args.getString(ARG_MODE));
        viewModel.setMode(mode);

        adapter = new TodoAdapter(this);
        binding.todoList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.todoList.setAdapter(adapter);

        setUpDateBar(mode);
        setUpMenu();

        viewModel.getVisibleTodos().observe(getViewLifecycleOwner(), this::showTodos);
        viewModel.getReferenceDate().observe(getViewLifecycleOwner(), date -> {
            binding.dateLabel.setText(DateUtils.formatDisplay(date));
            adapter.setReferenceDate(date);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void showTodos(List<Todo> todos) {
        adapter.submitList(todos);
        boolean empty = todos == null || todos.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.todoList.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyView.setText(viewModel.getOptions().isFiltering()
                ? R.string.empty_filtered
                : R.string.empty_list);
    }

    // -------------------------------------------------------------- date bar

    private void setUpDateBar(ListMode mode) {
        // Only the day-based screen has a reference date to move.
        boolean showDateBar = mode == ListMode.TODAY;
        binding.dateBar.setVisibility(showDateBar ? View.VISIBLE : View.GONE);
        if (!showDateBar) {
            return;
        }
        binding.previousDay.setOnClickListener(v -> viewModel.shiftReferenceDate(-1));
        binding.nextDay.setOnClickListener(v -> viewModel.shiftReferenceDate(1));
        binding.dateLabel.setOnClickListener(v -> showDatePicker());
        binding.dateLabel.setOnLongClickListener(v -> {
            viewModel.resetToToday();
            return true;
        });
    }

    private void showDatePicker() {
        LocalDate current = viewModel.currentDate();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.action_pick_date)
                .setSelection(current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            // MaterialDatePicker hands back a UTC midnight timestamp.
            LocalDate picked = Instant.ofEpochMilli(selection).atZone(ZoneId.of("UTC")).toLocalDate();
            viewModel.setReferenceDate(picked);
        });
        picker.show(getChildFragmentManager(), "date_picker");
    }

    // ------------------------------------------------------------------ menu

    private void setUpMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.todo_list, menu);
                MenuItem pickDate = menu.findItem(R.id.action_pick_date);
                if (pickDate != null) {
                    pickDate.setVisible(viewModel.getMode() == ListMode.TODAY);
                }
                MenuItem clearCompleted = menu.findItem(R.id.action_clear_completed);
                if (clearCompleted != null) {
                    clearCompleted.setVisible(viewModel.getMode() == ListMode.COMPLETED);
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_filter_sort) {
                    new FilterSortSheet().show(getChildFragmentManager(), FilterSortSheet.TAG);
                    return true;
                }
                if (id == R.id.action_pick_date) {
                    showDatePicker();
                    return true;
                }
                if (id == R.id.action_import_csv) {
                    // Some file providers report CSV as text/plain or octet-stream.
                    importPicker.launch(new String[]{"text/csv", "text/comma-separated-values",
                            "text/plain", "application/octet-stream", "*/*"});
                    return true;
                }
                if (id == R.id.action_export_csv) {
                    exportPicker.launch(TodoCsv.suggestedFileName(DateUtils.today()));
                    return true;
                }
                if (id == R.id.action_clear_completed) {
                    confirmClearCompleted();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    // ------------------------------------------------------- adapter callbacks

    @Override
    public void onToggleCompleted(@NonNull Todo todo, boolean completed) {
        viewModel.setCompleted(todo, completed, followUp -> {
            if (followUp != null && binding != null) {
                snackbar(getString(R.string.repeat_next_created,
                        DateUtils.formatDisplay(followUp.anchorDate())));
            }
        });
    }

    @Override
    public void onOpen(@NonNull Todo todo) {
        Bundle args = new Bundle();
        args.putLong(TodoEditFragment.ARG_TODO_ID, todo.id);
        navController().navigate(R.id.nav_edit, args);
    }

    @Override
    public void onLongPress(@NonNull Todo todo) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, todo.title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteWithUndo(todo))
                .show();
    }

    private void deleteWithUndo(Todo todo) {
        viewModel.delete(todo, () -> {
            if (binding == null) {
                return;
            }
            Snackbar bar = Snackbar.make(binding.getRoot(), R.string.todo_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo, v -> viewModel.restore(todo));
            anchorToFab(bar);
            bar.show();
        });
    }

    /**
     * "すべて削除" on the 完了済み screen. Deletes exactly what is on screen right now
     * (respecting any priority/group filter the user has set), not every completed
     * todo in the database, so it matches what the user is looking at.
     */
    private void confirmClearCompleted() {
        List<Todo> current = adapter.getCurrentList();
        if (current.isEmpty()) {
            return;
        }
        List<Todo> toDelete = new ArrayList<>(current);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_clear_completed_title)
                .setMessage(getString(R.string.dialog_clear_completed_message, toDelete.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> clearCompletedWithUndo(toDelete))
                .show();
    }

    private void clearCompletedWithUndo(List<Todo> todos) {
        viewModel.deleteAll(todos, () -> {
            if (binding == null) {
                return;
            }
            String message = getResources().getQuantityString(
                    R.plurals.completed_cleared, todos.size(), todos.size());
            Snackbar bar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo, v -> viewModel.restoreAll(todos));
            anchorToFab(bar);
            bar.show();
        });
    }

    // -------------------------------------------------------------- CSV files

    private void onCsvPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Context context = requireContext().getApplicationContext();
        viewModel.getRepository().runInBackground(() -> {
            String text;
            try {
                text = readText(context, uri);
            } catch (IOException e) {
                postToUi(() -> snackbar(getString(R.string.import_failed)));
                return;
            }
            TodoCsv.ImportResult result = TodoCsv.parse(text);
            postToUi(() -> confirmImport(result));
        });
    }

    private void confirmImport(TodoCsv.ImportResult result) {
        if (binding == null) {
            return;
        }
        if (result.todos.isEmpty()) {
            snackbar(getString(R.string.import_nothing_found));
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_import_title)
                .setMessage(getString(R.string.dialog_import_message, result.todos.size(), result.skippedRows))
                .setNeutralButton(android.R.string.cancel, null)
                .setNegativeButton(R.string.action_import_replace, (dialog, which) -> runImport(result, true))
                .setPositiveButton(R.string.action_import_merge, (dialog, which) -> runImport(result, false))
                .show();
    }

    private void runImport(TodoCsv.ImportResult result, boolean replace) {
        viewModel.getRepository().importTodos(result.todos, replace,
                count -> snackbar(getResources().getQuantityString(
                        R.plurals.import_done, count, count)));
    }

    private void onCsvDestinationPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Context context = requireContext().getApplicationContext();
        viewModel.getRepository().loadAll(todos -> viewModel.getRepository().runInBackground(() -> {
            boolean ok = writeText(context, uri, TodoCsv.export(todos));
            postToUi(() -> snackbar(ok
                    ? getResources().getQuantityString(R.plurals.export_done, todos.size(), todos.size())
                    : getString(R.string.export_failed)));
        }));
    }

    private static String readText(Context context, Uri uri) throws IOException {
        StringBuilder out = new StringBuilder();
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open " + uri);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    out.append(buffer, 0, read);
                }
            }
        }
        return out.toString();
    }

    private static boolean writeText(Context context, Uri uri, String text) {
        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) {
                return false;
            }
            try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                writer.write(text);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ----------------------------------------------------------------- helpers

    private void postToUi(Runnable action) {
        View view = binding == null ? null : binding.getRoot();
        if (view != null) {
            view.post(action);
        }
    }

    private void snackbar(String message) {
        if (binding == null) {
            return;
        }
        Snackbar bar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        anchorToFab(bar);
        bar.show();
    }

    private void anchorToFab(Snackbar bar) {
        View fab = requireActivity().findViewById(R.id.fab);
        if (fab != null && fab.getVisibility() == View.VISIBLE) {
            bar.setAnchorView(fab);
        }
    }

    private NavController navController() {
        return NavHostFragment.findNavController(this);
    }
}
