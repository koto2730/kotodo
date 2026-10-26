package com.mugime.kotodo.ui.list;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.mugime.kotodo.MainActivity;
import com.mugime.kotodo.R;
import com.mugime.kotodo.data.TodoRepository;
import com.mugime.kotodo.databinding.SheetQuickAddBinding;
import com.mugime.kotodo.elements.Priority;
import com.mugime.kotodo.elements.Todo;
import com.mugime.kotodo.ui.TodoFormatter;
import com.mugime.kotodo.utils.DateUtils;

import java.time.LocalDate;

/**
 * The add button's fast path: a title, when it is due and how important it is,
 * and nothing else. "Details" hands what was typed over to the full editor.
 */
public class QuickAddSheet extends BottomSheetDialogFragment {

    /** The due-date chips carry an offset in days from today as their tag; this means "no date". */
    private static final int NO_DATE = -1;

    private SheetQuickAddBinding binding;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        // Open fully expanded with the keyboard up, so a todo is one tap and some typing away.
        dialog.setOnShowListener(d -> dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED));
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetQuickAddBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        addChip(binding.chipsQuickWhen, getString(R.string.shortcut_today), 0, true);
        addChip(binding.chipsQuickWhen, getString(R.string.shortcut_tomorrow), 1, false);
        addChip(binding.chipsQuickWhen, getString(R.string.shortcut_no_date), NO_DATE, false);

        for (Priority priority : Priority.values()) {
            addChip(binding.chipsQuickPriority,
                    TodoFormatter.priorityLabel(requireContext(), priority),
                    priority, priority == Priority.DEFAULT);
        }

        binding.buttonQuickAdd.setOnClickListener(v -> submit());
        binding.buttonQuickDetails.setOnClickListener(v -> openDetails());
        binding.inputQuickTitle.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        binding.inputQuickTitle.requestFocus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void addChip(ChipGroup group, String label, Object tag, boolean checked) {
        Chip chip = (Chip) LayoutInflater.from(requireContext())
                .inflate(R.layout.view_filter_chip, group, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(tag);
        chip.setChecked(checked);
        group.addView(chip);
    }

    @Nullable
    private LocalDate selectedDue() {
        Object tag = selectedTag(binding.chipsQuickWhen);
        if (!(tag instanceof Integer) || (Integer) tag == NO_DATE) {
            return null;
        }
        return DateUtils.today().plusDays((Integer) tag);
    }

    private Priority selectedPriority() {
        Object tag = selectedTag(binding.chipsQuickPriority);
        return tag instanceof Priority ? (Priority) tag : Priority.DEFAULT;
    }

    @Nullable
    private static Object selectedTag(ChipGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                return child.getTag();
            }
        }
        return null;
    }

    private String title() {
        CharSequence text = binding.inputQuickTitle.getText();
        return text == null ? "" : text.toString().trim();
    }

    private void submit() {
        String title = title();
        if (TextUtils.isEmpty(title)) {
            binding.layoutQuickTitle.setError(getString(R.string.error_title_required));
            return;
        }
        Todo todo = new Todo();
        todo.title = title;
        todo.dueDate = selectedDue();
        todo.priority = selectedPriority();
        TodoRepository.get(requireContext()).insert(todo, null);
        Toast.makeText(requireContext().getApplicationContext(),
                R.string.quick_add_added, Toast.LENGTH_SHORT).show();
        dismiss();
    }

    private void openDetails() {
        MainActivity activity = (MainActivity) requireActivity();
        activity.openEditor(title(), selectedDue(), selectedPriority());
        dismiss();
    }
}
