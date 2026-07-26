package com.mugime.kotodo.ui.edit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.mugime.kotodo.R;
import com.mugime.kotodo.databinding.FragmentTodoEditBinding;
import com.mugime.kotodo.elements.Priority;
import com.mugime.kotodo.elements.RepeatType;
import com.mugime.kotodo.elements.Todo;
import com.mugime.kotodo.ui.TodoFormatter;
import com.mugime.kotodo.utils.DateUtils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Create / edit screen covering every field of a {@link Todo}.
 *
 * <p>Edits are collected from the widgets into the working todo only when the user
 * saves, except for the date pickers, which write through immediately so the
 * repeat preview and the button labels stay in step.</p>
 */
public class TodoEditFragment extends Fragment {

    public static final String ARG_TODO_ID = "todoId";

    private FragmentTodoEditBinding binding;
    private TodoEditViewModel viewModel;
    private Todo working;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted && binding != null) {
                    binding.switchNotify.setChecked(false);
                    Snackbar.make(binding.getRoot(), R.string.notification_permission_denied,
                            Snackbar.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTodoEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TodoEditViewModel.class);

        buildPriorityChips();
        buildRepeatTypeChips();
        buildWeekChips();
        buildMonthDayChips();
        buildMonthChips();
        wireControls();
        setUpMenu();

        Bundle args = getArguments();
        long id = args == null ? 0L : args.getLong(ARG_TODO_ID, 0L);
        viewModel.load(id);
        viewModel.getTodo().observe(getViewLifecycleOwner(), this::bind);
        viewModel.getGroups().observe(getViewLifecycleOwner(), this::bindGroupSuggestions);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ------------------------------------------------------------------ setup

    private void wireControls() {
        // Every handler below runs against `working`, which only exists once the
        // ViewModel has delivered the todo, so each one bails out until then.
        binding.buttonStartDate.setOnClickListener(v -> withWorking(todo ->
                pickDate(todo.startDate, R.string.field_start_date, picked -> {
                    todo.startDate = picked;
                    refreshDates();
                })));
        binding.buttonClearStart.setOnClickListener(v -> withWorking(todo -> {
            todo.startDate = null;
            refreshDates();
        }));

        binding.buttonDueDate.setOnClickListener(v -> withWorking(todo ->
                pickDate(todo.dueDate, R.string.field_due_date, picked -> {
                    todo.dueDate = picked;
                    refreshDates();
                })));
        binding.buttonClearDue.setOnClickListener(v -> withWorking(todo -> {
            todo.dueDate = null;
            refreshDates();
        }));

        binding.buttonRepeatEnd.setOnClickListener(v -> withWorking(todo ->
                pickDate(todo.repeatEndDate, R.string.field_repeat_end, picked -> {
                    todo.repeatEndDate = picked;
                    refreshDates();
                })));
        binding.buttonClearRepeatEnd.setOnClickListener(v -> withWorking(todo -> {
            todo.repeatEndDate = null;
            refreshDates();
        }));

        binding.buttonNotifyTime.setOnClickListener(v -> withWorking(todo -> pickNotifyTime()));

        binding.switchNotify.setOnCheckedChangeListener((button, checked) -> {
            binding.buttonNotifyTime.setEnabled(checked);
            if (checked) {
                ensureNotificationPermission();
            }
        });

        binding.switchRepeat.setOnCheckedChangeListener((button, checked) -> {
            binding.repeatSection.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked && selectedRepeatType() == RepeatType.NONE) {
                selectRepeatType(RepeatType.DAILY);
            }
            refreshRepeatSections();
        });

        // Every control that feeds the cycle description refreshes the preview, so the
        // "毎週 (月・水)" line under the pickers always matches what is selected.
        binding.chipsRepeatType.setOnCheckedStateChangeListener((group, ids) -> refreshRepeatSections());
        binding.chipsWeekRule.setOnCheckedStateChangeListener((group, ids) -> refreshRepeatSections());
        binding.chipsMonthRule.setOnCheckedStateChangeListener((group, ids) -> refreshRepeatSections());
        binding.chipsYearRule.setOnCheckedStateChangeListener((group, ids) -> refreshRepeatSections());
        binding.inputRepeatInterval.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshRepeatSections();
            }
        });

        binding.switchCompleted.setOnCheckedChangeListener((button, checked) -> refreshCompletion(checked));
    }

    private void setUpMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.todo_edit, menu);
                MenuItem delete = menu.findItem(R.id.action_delete);
                if (delete != null) {
                    delete.setVisible(working != null && working.id != 0);
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_save) {
                    save();
                    return true;
                }
                if (id == R.id.action_delete) {
                    confirmDelete();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    // ------------------------------------------------------------------ chips

    private void buildPriorityChips() {
        binding.chipsPriority.removeAllViews();
        for (Priority priority : Priority.values()) {
            Chip chip = newChip(binding.chipsPriority,
                    TodoFormatter.priorityLabel(requireContext(), priority));
            chip.setTag(priority);
            binding.chipsPriority.addView(chip);
        }
    }

    private void buildRepeatTypeChips() {
        String[] labels = getResources().getStringArray(R.array.repeat_type_labels);
        RepeatType[] types = {RepeatType.DAILY, RepeatType.WEEKLY, RepeatType.MONTHLY, RepeatType.YEARLY};
        binding.chipsRepeatType.removeAllViews();
        for (int i = 0; i < types.length; i++) {
            Chip chip = newChip(binding.chipsRepeatType, labels[i]);
            chip.setTag(types[i]);
            binding.chipsRepeatType.addView(chip);
        }
    }

    private void buildWeekChips() {
        binding.chipsWeekRule.removeAllViews();
        for (int i = 0; i < 7; i++) {
            String label = DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault());
            Chip chip = newChip(binding.chipsWeekRule, label);
            chip.setTag(i);
            binding.chipsWeekRule.addView(chip);
        }
    }

    private void buildMonthDayChips() {
        binding.chipsMonthRule.removeAllViews();
        for (int day = 1; day <= 31; day++) {
            Chip chip = newChip(binding.chipsMonthRule, Integer.toString(day));
            chip.setTag(day - 1);
            binding.chipsMonthRule.addView(chip);
        }
    }

    private void buildMonthChips() {
        binding.chipsYearRule.removeAllViews();
        for (int month = 1; month <= 12; month++) {
            String label = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault());
            Chip chip = newChip(binding.chipsYearRule, label);
            chip.setTag(month - 1);
            binding.chipsYearRule.addView(chip);
        }
    }

    private Chip newChip(ChipGroup parent, String label) {
        Chip chip = (Chip) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.view_filter_chip, parent, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        return chip;
    }

    // ------------------------------------------------------------------- bind

    private void bind(@Nullable Todo todo) {
        if (todo == null || binding == null) {
            return;
        }
        working = todo;
        requireActivity().invalidateOptionsMenu();

        binding.inputTitle.setText(todo.title);
        binding.inputDescription.setText(todo.description);
        binding.inputGroup.setText(todo.groupName, false);

        checkByTag(binding.chipsPriority, todo.priority);
        applyMask(binding.chipsWeekRule, todo.weekRule);
        applyMask(binding.chipsMonthRule, todo.monthRule);
        applyMask(binding.chipsYearRule, todo.yearRule);

        binding.switchRepeat.setChecked(todo.repeat);
        binding.repeatSection.setVisibility(todo.repeat ? View.VISIBLE : View.GONE);
        selectRepeatType(todo.repeatType == RepeatType.NONE ? RepeatType.DAILY : todo.repeatType);
        binding.inputRepeatInterval.setText(String.valueOf(Math.max(1, todo.repeatInterval)));

        binding.switchNotify.setChecked(todo.notify);
        binding.buttonNotifyTime.setEnabled(todo.notify);

        binding.switchCompleted.setChecked(todo.completed);

        refreshDates();
        refreshRepeatSections();
        refreshCompletion(todo.completed);
    }

    private void bindGroupSuggestions(@Nullable List<String> groups) {
        if (binding == null || groups == null) {
            return;
        }
        binding.inputGroup.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, groups));
    }

    private void refreshDates() {
        if (binding == null || working == null) {
            return;
        }
        binding.buttonStartDate.setText(dateLabel(working.startDate));
        binding.buttonDueDate.setText(dateLabel(working.dueDate));
        binding.buttonRepeatEnd.setText(dateLabel(working.repeatEndDate));
        binding.buttonNotifyTime.setText(DateUtils.formatTime(working.notifyMinuteOfDay));
    }

    private String dateLabel(@Nullable LocalDate date) {
        return date == null ? getString(R.string.date_not_set) : DateUtils.formatDisplay(date);
    }

    /** Shows only the rule pickers that apply to the selected cycle. */
    private void refreshRepeatSections() {
        if (binding == null) {
            return;
        }
        RepeatType type = selectedRepeatType();
        boolean repeating = binding.switchRepeat.isChecked();

        binding.weekRuleSection.setVisibility(
                repeating && type == RepeatType.WEEKLY ? View.VISIBLE : View.GONE);
        binding.monthRuleSection.setVisibility(
                repeating && (type == RepeatType.MONTHLY || type == RepeatType.YEARLY)
                        ? View.VISIBLE : View.GONE);
        binding.yearRuleSection.setVisibility(
                repeating && type == RepeatType.YEARLY ? View.VISIBLE : View.GONE);

        binding.labelIntervalUnit.setText(intervalUnitLabel(type));
        binding.repeatPreview.setText(previewText());
    }

    private String intervalUnitLabel(RepeatType type) {
        String[] units = getResources().getStringArray(R.array.repeat_interval_units);
        switch (type) {
            case WEEKLY:
                return units[1];
            case MONTHLY:
                return units[2];
            case YEARLY:
                return units[3];
            case DAILY:
            default:
                return units[0];
        }
    }

    /** Live description of the cycle as currently configured, shown under the pickers. */
    private String previewText() {
        Todo preview = new Todo();
        preview.repeat = binding.switchRepeat.isChecked();
        preview.repeatType = selectedRepeatType();
        preview.repeatInterval = readInterval();
        preview.weekRule = readMask(binding.chipsWeekRule);
        preview.monthRule = readMask(binding.chipsMonthRule);
        preview.yearRule = readMask(binding.chipsYearRule);
        return TodoFormatter.describeRepeat(requireContext(), preview);
    }

    private void refreshCompletion(boolean completed) {
        if (binding == null || working == null) {
            return;
        }
        LocalDate date = completed
                ? (working.completedDate != null ? working.completedDate : DateUtils.today())
                : null;
        binding.textCompletedDate.setText(completed
                ? getString(R.string.field_completed_on, DateUtils.formatDisplay(date))
                : getString(R.string.field_not_completed));
    }

    // ---------------------------------------------------------------- pickers

    private interface DatePicked {
        void onPicked(LocalDate date);
    }

    private void pickDate(@Nullable LocalDate initial, int titleRes, DatePicked callback) {
        LocalDate start = initial == null ? DateUtils.today() : initial;
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(titleRes)
                .setSelection(start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .build();
        picker.addOnPositiveButtonClickListener(selection ->
                callback.onPicked(Instant.ofEpochMilli(selection).atZone(ZoneId.of("UTC")).toLocalDate()));
        picker.show(getChildFragmentManager(), "date_picker");
    }

    private void pickNotifyTime() {
        int current = working == null ? Todo.DEFAULT_NOTIFY_MINUTE : working.notifyMinuteOfDay;
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(requireContext())
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(current / 60)
                .setMinute(current % 60)
                .setTitleText(R.string.field_notify_time)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            working.notifyMinuteOfDay = picker.getHour() * 60 + picker.getMinute();
            refreshDates();
        });
        picker.show(getChildFragmentManager(), "time_picker");
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    // ------------------------------------------------------------------ save

    private void save() {
        if (working == null || binding == null) {
            return;
        }
        String title = text(binding.inputTitle);
        if (TextUtils.isEmpty(title)) {
            binding.layoutTitle.setError(getString(R.string.error_title_required));
            binding.inputTitle.requestFocus();
            return;
        }
        binding.layoutTitle.setError(null);

        working.title = title;
        working.description = emptyToNull(text(binding.inputDescription));
        working.groupName = emptyToNull(text(binding.inputGroup));
        working.priority = selectedPriority();

        working.repeat = binding.switchRepeat.isChecked();
        working.repeatType = working.repeat ? selectedRepeatType() : RepeatType.NONE;
        working.repeatInterval = readInterval();
        working.weekRule = readMask(binding.chipsWeekRule);
        working.monthRule = readMask(binding.chipsMonthRule);
        working.yearRule = readMask(binding.chipsYearRule);
        if (!working.repeat) {
            working.repeatEndDate = null;
        }

        working.notify = binding.switchNotify.isChecked();

        boolean completed = binding.switchCompleted.isChecked();
        if (completed && !working.completed) {
            working.completedDate = DateUtils.today();
        } else if (!completed) {
            working.completedDate = null;
        }
        working.completed = completed;

        viewModel.save(working, this::navigateBack);
    }

    private void confirmDelete() {
        if (working == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, working.title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        viewModel.delete(working, this::navigateBack))
                .show();
    }

    private void navigateBack() {
        if (isAdded()) {
            NavHostFragment.findNavController(this).navigateUp();
        }
    }

    // --------------------------------------------------------------- helpers

    private interface WorkingAction {
        void run(@NonNull Todo todo);
    }

    private void withWorking(WorkingAction action) {
        if (working != null && binding != null) {
            action.run(working);
        }
    }

    private Priority selectedPriority() {
        for (int i = 0; i < binding.chipsPriority.getChildCount(); i++) {
            View child = binding.chipsPriority.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                return (Priority) child.getTag();
            }
        }
        return Priority.DEFAULT;
    }

    private RepeatType selectedRepeatType() {
        for (int i = 0; i < binding.chipsRepeatType.getChildCount(); i++) {
            View child = binding.chipsRepeatType.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                return (RepeatType) child.getTag();
            }
        }
        return RepeatType.NONE;
    }

    private void selectRepeatType(RepeatType type) {
        checkByTag(binding.chipsRepeatType, type);
    }

    private void checkByTag(ChipGroup group, Object tag) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(tag.equals(child.getTag()));
            }
        }
    }

    /** Reads a bit mask back out of a multi-select chip group. */
    private int readMask(ChipGroup group) {
        int mask = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                mask |= 1 << (Integer) child.getTag();
            }
        }
        return mask;
    }

    private void applyMask(ChipGroup group, int mask) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked((mask & (1 << (Integer) child.getTag())) != 0);
            }
        }
    }

    private int readInterval() {
        try {
            return Math.max(1, Integer.parseInt(text(binding.inputRepeatInterval)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String text(android.widget.TextView view) {
        CharSequence value = view.getText();
        return value == null ? "" : value.toString().trim();
    }

    @Nullable
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
