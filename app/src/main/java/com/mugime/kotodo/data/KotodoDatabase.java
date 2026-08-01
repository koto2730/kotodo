package com.mugime.kotodo.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.mugime.kotodo.elements.Todo;

/**
 * The on-device SQLite database. There is no server and no sync: this file under
 * {@code /data/data/com.mugime.kotodo/databases/} is the single source of truth.
 */
@Database(entities = {Todo.class}, version = 2, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class KotodoDatabase extends RoomDatabase {

    private static final String DB_NAME = "kotodo.db";

    private static volatile KotodoDatabase instance;

    /** Adds {@link Todo#followUpCreated}, the repeat-follow-up idempotency latch. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE todos ADD COLUMN followUpCreated INTEGER NOT NULL DEFAULT 0");
        }
    };

    public abstract TodoDao todoDao();

    public static KotodoDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (KotodoDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    KotodoDatabase.class,
                                    DB_NAME)
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
