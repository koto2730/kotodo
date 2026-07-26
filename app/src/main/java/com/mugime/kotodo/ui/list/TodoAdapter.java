package com.mugime.kotodo.ui.list;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.mugime.kotodo.R;
import com.mugime.kotodo.databinding.ItemTodoBinding;
import com.mugime.kotodo.elements.Todo;
import com.mugime.kotodo.ui.TodoFormatter;
import com.mugime.kotodo.utils.DateUtils;

import java.time.LocalDate;
import java.util.Objects;

/** Renders the todo list. Completed rows are dimmed and struck through. */
public class TodoAdapter extends ListAdapter<Todo, TodoAdapter.TodoViewHolder> {

    public interface Listener {
        /** The checkbox on the left was tapped. */
        void onToggleCompleted(@NonNull Todo todo, boolean completed);

        /** The row was tapped: open it for editing. */
        void onOpen(@NonNull Todo todo);

        /** The row was long-pressed: offer to delete it. */
        void onLongPress(@NonNull Todo todo);
    }

    private final Listener listener;
    private LocalDate referenceDate = DateUtils.today();

    public TodoAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    /** The date overdue highlighting is measured against. */
    public void setReferenceDate(@NonNull LocalDate date) {
        if (!date.equals(referenceDate)) {
            referenceDate = date;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @NonNull
    @Override
    public TodoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTodoBinding binding = ItemTodoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TodoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TodoViewHolder holder, int position) {
        holder.bind(getItem(position), referenceDate, listener);
    }

    static class TodoViewHolder extends RecyclerView.ViewHolder {

        private final ItemTodoBinding binding;

        TodoViewHolder(ItemTodoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Todo todo, LocalDate referenceDate, Listener listener) {
            Context context = binding.getRoot().getContext();

            binding.todoTitle.setText(todo.title);
            applyStrikeThrough(todo.completed);

            String meta = TodoFormatter.metaLine(context, todo);
            binding.todoMeta.setText(meta);
            binding.todoMeta.setVisibility(meta.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

            String description = todo.description == null ? "" : todo.description.trim();
            binding.todoDescription.setText(description);
            binding.todoDescription.setVisibility(
                    description.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

            int[] colors = context.getResources().getIntArray(R.array.priority_colors);
            binding.priorityStripe.setBackgroundColor(colors[todo.priority.ordinal()]);

            boolean overdue = todo.isOverdueOn(referenceDate);
            binding.todoMeta.setTextColor(ContextCompat.getColor(
                    context, overdue ? R.color.overdue : R.color.meta_text));

            binding.getRoot().setAlpha(todo.completed ? 0.5f : 1.0f);

            // Detach the listener before setChecked so recycling cannot fire a toggle.
            binding.todoCheck.setOnCheckedChangeListener(null);
            binding.todoCheck.setChecked(todo.completed);
            binding.todoCheck.setOnCheckedChangeListener(
                    (button, checked) -> listener.onToggleCompleted(todo, checked));

            binding.getRoot().setOnClickListener(view -> listener.onOpen(todo));
            binding.getRoot().setOnLongClickListener(view -> {
                listener.onLongPress(todo);
                return true;
            });
        }

        private void applyStrikeThrough(boolean completed) {
            int flags = binding.todoTitle.getPaintFlags();
            if (completed) {
                flags |= Paint.STRIKE_THRU_TEXT_FLAG;
            } else {
                flags &= ~Paint.STRIKE_THRU_TEXT_FLAG;
            }
            binding.todoTitle.setPaintFlags(flags);
        }
    }

    private static final DiffUtil.ItemCallback<Todo> DIFF = new DiffUtil.ItemCallback<Todo>() {
        @Override
        public boolean areItemsTheSame(@NonNull Todo oldItem, @NonNull Todo newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Todo oldItem, @NonNull Todo newItem) {
            // Room hands out fresh instances on every emission, so compare the
            // fields the row actually renders.
            return oldItem.completed == newItem.completed
                    && oldItem.notify == newItem.notify
                    && oldItem.repeat == newItem.repeat
                    && oldItem.repeatInterval == newItem.repeatInterval
                    && oldItem.weekRule == newItem.weekRule
                    && oldItem.monthRule == newItem.monthRule
                    && oldItem.yearRule == newItem.yearRule
                    && oldItem.notifyMinuteOfDay == newItem.notifyMinuteOfDay
                    && Objects.equals(oldItem.priority, newItem.priority)
                    && Objects.equals(oldItem.repeatType, newItem.repeatType)
                    && Objects.equals(oldItem.title, newItem.title)
                    && Objects.equals(oldItem.description, newItem.description)
                    && Objects.equals(oldItem.groupName, newItem.groupName)
                    && Objects.equals(oldItem.startDate, newItem.startDate)
                    && Objects.equals(oldItem.dueDate, newItem.dueDate)
                    && Objects.equals(oldItem.completedDate, newItem.completedDate);
        }
    };
}
