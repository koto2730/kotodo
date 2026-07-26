package com.mugime.kotodo.ui.list;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.mugime.kotodo.R;
import com.mugime.kotodo.databinding.SheetFilterSortBinding;
import com.mugime.kotodo.elements.Priority;
import com.mugime.kotodo.ui.TodoFormatter;

import java.util.List;

/**
 * Bottom sheet with the list's filter and sort controls.
 *
 * <p>Changes apply immediately to the parent screen's {@link TodoListViewModel},
 * so the list behind the sheet updates as options are tapped.</p>
 */
public class FilterSortSheet extends BottomSheetDialogFragment {

    public static final String TAG = "filter_sort_sheet";

    private SheetFilterSortBinding binding;
    private TodoListViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetFilterSortBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Share the parent list screen's ViewModel so edits land on the right list.
        viewModel = new ViewModelProvider(requireParentFragment()).get(TodoListViewModel.class);

        buildTimeFilterChips();
        buildPriorityChips();
        buildSortChips();

        ListOptions options = viewModel.getOptions();
        binding.switchAscending.setChecked(options.ascending);
        binding.switchHideCompleted.setChecked(options.hideCompleted);

        binding.switchAscending.setOnCheckedChangeListener((button, checked) ->
                update(current -> current.ascending = checked));
        binding.switchHideCompleted.setOnCheckedChangeListener((button, checked) ->
                update(current -> current.hideCompleted = checked));
        binding.buttonReset.setOnClickListener(v -> resetFilters());

        viewModel.getGroups().observe(getViewLifecycleOwner(), this::buildGroupChips);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ------------------------------------------------------------------ chips

    private void buildTimeFilterChips() {
        String[] labels = getResources().getStringArray(R.array.time_filter_labels);
        ListOptions.TimeFilter[] values = ListOptions.TimeFilter.values();
        ListOptions options = viewModel.getOptions();

        binding.chipsTime.removeAllViews();
        for (int i = 0; i < values.length; i++) {
            ListOptions.TimeFilter value = values[i];
            Chip chip = newChip(binding.chipsTime, labels[i], options.timeFilter == value);
            chip.setOnClickListener(v -> {
                update(current -> current.timeFilter = value);
                // Single-select: keep exactly one chip checked.
                setOnlyChecked(binding.chipsTime, chip);
            });
            binding.chipsTime.addView(chip);
        }
    }

    private void buildPriorityChips() {
        ListOptions options = viewModel.getOptions();
        binding.chipsPriority.removeAllViews();
        for (Priority priority : Priority.values()) {
            Chip chip = newChip(binding.chipsPriority,
                    TodoFormatter.priorityLabel(requireContext(), priority),
                    options.priorities.contains(priority));
            chip.setOnCheckedChangeListener((button, checked) -> update(current -> {
                if (checked) {
                    current.priorities.add(priority);
                } else {
                    current.priorities.remove(priority);
                }
            }));
            binding.chipsPriority.addView(chip);
        }
    }

    private void buildGroupChips(@Nullable List<String> groups) {
        if (binding == null) {
            return;
        }
        ListOptions options = viewModel.getOptions();
        binding.chipsGroup.removeAllViews();

        boolean hasGroups = groups != null && !groups.isEmpty();
        binding.labelGroup.setVisibility(hasGroups ? View.VISIBLE : View.GONE);
        binding.chipsGroup.setVisibility(hasGroups ? View.VISIBLE : View.GONE);
        if (!hasGroups) {
            return;
        }

        for (String group : groups) {
            addGroupChip(group, group, options.groups.contains(group));
        }
        addGroupChip(ListOptions.NO_GROUP, getString(R.string.filter_no_group),
                options.groups.contains(ListOptions.NO_GROUP));
    }

    private void addGroupChip(String value, String label, boolean checked) {
        Chip chip = newChip(binding.chipsGroup, label, checked);
        chip.setOnCheckedChangeListener((button, isChecked) -> update(current -> {
            if (isChecked) {
                current.groups.add(value);
            } else {
                current.groups.remove(value);
            }
        }));
        binding.chipsGroup.addView(chip);
    }

    private void buildSortChips() {
        String[] labels = getResources().getStringArray(R.array.sort_labels);
        ListOptions.SortKey[] values = ListOptions.SortKey.values();
        ListOptions options = viewModel.getOptions();

        binding.chipsSort.removeAllViews();
        for (int i = 0; i < values.length; i++) {
            ListOptions.SortKey value = values[i];
            Chip chip = newChip(binding.chipsSort, labels[i], options.sortKey == value);
            chip.setOnClickListener(v -> {
                update(current -> current.sortKey = value);
                setOnlyChecked(binding.chipsSort, chip);
            });
            binding.chipsSort.addView(chip);
        }
    }

    private Chip newChip(ChipGroup parent, String label, boolean checked) {
        Chip chip = (Chip) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.view_filter_chip, parent, false);
        chip.setText(label);
        chip.setChecked(checked);
        return chip;
    }

    private void setOnlyChecked(ChipGroup group, Chip selected) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(child == selected);
            }
        }
    }

    // ----------------------------------------------------------------- state

    private interface Mutation {
        void apply(ListOptions options);
    }

    /** Copy-on-write so the ViewModel always sees a new instance and re-emits. */
    private void update(Mutation mutation) {
        ListOptions updated = new ListOptions(viewModel.getOptions());
        mutation.apply(updated);
        viewModel.setOptions(updated);
    }

    private void resetFilters() {
        ListOptions updated = new ListOptions(viewModel.getOptions());
        updated.clearFilters();
        viewModel.setOptions(updated);

        binding.switchHideCompleted.setChecked(false);
        buildTimeFilterChips();
        buildPriorityChips();
        buildGroupChips(viewModel.getGroups().getValue());
    }
}
