package com.mugime.kotodo.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.mugime.kotodo.elements.Todo;

import java.util.List;

/**
 * Data access for the {@code todos} table.
 *
 * <p>Only the coarse "which rows belong on this screen" decision happens in SQL.
 * The user's filter and sort choices are applied in {@code TodoListViewModel} on
 * the loaded list, which keeps the queries static and the combinations open.</p>
 */
@Dao
public interface TodoDao {

    @Insert
    long insert(Todo todo);

    @Insert
    List<Long> insertAll(List<Todo> todos);

    @Update
    void update(Todo todo);

    @Delete
    void delete(Todo todo);

    /** Bulk delete used by the "clear completed" action; overloaded on parameter type. */
    @Delete
    void delete(List<Todo> todos);

    @Query("DELETE FROM todos")
    void deleteAll();

    @Query("SELECT * FROM todos WHERE id = :id")
    Todo findById(long id);

    @Query("SELECT * FROM todos WHERE id = :id")
    LiveData<Todo> observeById(long id);

    /**
     * The main screen query: everything that is due on {@code day} according to the
     * spec (開始予定日 &lt;= 当日 or 完了予定日 &lt;= 当日, plus undated items), and
     * anything completed on that same day so the strike-through stays visible.
     */
    @Query("SELECT * FROM todos WHERE "
            + "(completed = 0 AND ((startDate IS NULL AND dueDate IS NULL) "
            + "                    OR startDate <= :day OR dueDate <= :day)) "
            + "OR (completed = 1 AND completedDate = :day)")
    LiveData<List<Todo>> observeForDay(long day);

    @Query("SELECT * FROM todos")
    LiveData<List<Todo>> observeAll();

    @Query("SELECT * FROM todos WHERE completed = 1")
    LiveData<List<Todo>> observeCompleted();

    /** Snapshot read for CSV export; must not run on the main thread. */
    @Query("SELECT * FROM todos")
    List<Todo> loadAll();

    /** Candidates for reminder scheduling. */
    @Query("SELECT * FROM todos WHERE notify = 1 AND completed = 0 "
            + "AND (startDate IS NOT NULL OR dueDate IS NOT NULL)")
    List<Todo> loadNotifiable();

    /** Distinct group names, used to populate the filter chips and the edit autocomplete. */
    @Query("SELECT DISTINCT groupName FROM todos "
            + "WHERE groupName IS NOT NULL AND TRIM(groupName) != '' ORDER BY groupName COLLATE NOCASE")
    LiveData<List<String>> observeGroups();
}
