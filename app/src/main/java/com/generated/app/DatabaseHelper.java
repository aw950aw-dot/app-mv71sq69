package com.generated.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "sd_rescue.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NODES = "nodes";
    public static final String COL_ID = "id";
    public static final String COL_PARENT_ID = "parent_id";
    public static final String COL_NAME = "name";
    public static final String COL_PATH = "path";
    public static final String COL_IS_DIR = "is_dir";
    public static final String COL_SIZE = "size";
    public static final String COL_OFFSET = "file_offset";
    public static final String COL_MODIFIED = "modified";
    public static final String COL_TYPE = "type";
    public static final String COL_SELECTED = "selected";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NODES + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PARENT_ID + " INTEGER, " +
                COL_NAME + " TEXT, " +
                COL_PATH + " TEXT, " +
                COL_IS_DIR + " INTEGER, " +
                COL_SIZE + " INTEGER, " +
                COL_OFFSET + " INTEGER, " +
                COL_MODIFIED + " INTEGER, " +
                COL_TYPE + " TEXT, " +
                COL_SELECTED + " INTEGER DEFAULT 1)";
        db.execSQL(createTable);
        db.execSQL("CREATE INDEX idx_parent ON " + TABLE_NODES + "(" + COL_PARENT_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NODES);
        onCreate(db);
    }

    public synchronized void clearDatabase() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_NODES);
    }

    public synchronized void insertEntriesInBatch(List<RecoveredEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (RecoveredEntry entry : entries) {
                ContentValues cv = new ContentValues();
                cv.put(COL_PARENT_ID, getOrCreateParentId(db, entry.getPath()));
                cv.put(COL_NAME, entry.getName());
                cv.put(COL_PATH, entry.getPath());
                cv.put(COL_IS_DIR, entry.isDirectory() ? 1 : 0);
                cv.put(COL_SIZE, entry.getLength());
                cv.put(COL_OFFSET, entry.getOffset());
                cv.put(COL_MODIFIED, entry.getModifiedTime());
                cv.put(COL_TYPE, FileType.safeName(entry.getType()));
                cv.put(COL_SELECTED, entry.isSelected() ? 1 : 0);
                db.insert(TABLE_NODES, null, cv);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            DiagnosticReporter.logError(e, "Database batch insert failed");
        } finally {
            db.endTransaction();
        }
    }

    private long getOrCreateParentId(SQLiteDatabase db, String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            // Check if root directory exists
            Cursor c = db.rawQuery("SELECT " + COL_ID + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + "=? AND " + COL_NAME + "=?", new String[]{"/", ""});
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                c.close();
                return id;
            }
            c.close();
            ContentValues cv = new ContentValues();
            cv.put(COL_PARENT_ID, 0);
            cv.put(COL_NAME, "");
            cv.put(COL_PATH, "/");
            cv.put(COL_IS_DIR, 1);
            cv.put(COL_SIZE, 0);
            cv.put(COL_OFFSET, 0);
            cv.put(COL_MODIFIED, System.currentTimeMillis());
            cv.put(COL_TYPE, FileType.UNKNOWN.getName());
            cv.put(COL_SELECTED, 1);
            return db.insert(TABLE_NODES, null, cv);
        }

        // Ensure parent hierarchy
        String[] parts = path.split("/");
        long currentParentId = 0;
        String currentPathSoFar = "";

        for (String part : parts) {
            if (part.isEmpty()) continue;
            currentPathSoFar = currentPathSoFar.equals("/") ? "/" + part : currentPathSoFar + "/" + part;
            
            Cursor c = db.rawQuery("SELECT " + COL_ID + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + "=? AND " + COL_NAME + "=?", new String[]{currentPathSoFar, part});
            if (c.moveToFirst()) {
                currentParentId = c.getLong(0);
                c.close();
            } else {
                c.close();
                ContentValues cv = new ContentValues();
                cv.put(COL_PARENT_ID, currentParentId);
                cv.put(COL_NAME, part);
                cv.put(COL_PATH, currentPathSoFar);
                cv.put(COL_IS_DIR, 1);
                cv.put(COL_SIZE, 0);
                cv.put(COL_OFFSET, 0);
                cv.put(COL_MODIFIED, System.currentTimeMillis());
                cv.put(COL_TYPE, FileType.UNKNOWN.getName());
                cv.put(COL_SELECTED, 1);
                currentParentId = db.insert(TABLE_NODES, null, cv);
            }
        }
        return currentParentId;
    }

    public synchronized List<RecoveredEntry> getChildrenOfDirectory(String dirPath) {
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        
        // Normalize dirPath
        if (dirPath == null || dirPath.isEmpty()) dirPath = "/";
        if (!dirPath.startsWith("/")) dirPath = "/" + dirPath;

        Cursor c = db.rawQuery("SELECT " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + "=? ORDER BY " + COL_IS_DIR + " DESC, " + COL_NAME + " ASC", new String[]{dirPath});
        
        if (c != null) {
            while (c.moveToNext()) {
                String name = c.getString(0);
                String path = c.getString(1);
                long offset = c.getLong(2);
                long size = c.getLong(3);
                String typeStr = c.getString(4);
                boolean isDir = c.getInt(5) == 1;
                long modified = c.getLong(6);
                boolean selected = c.getInt(7) == 1;

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(name, path, offset, size, type, false, isDir, modified, name.getBytes());
                entry.setSelected(selected);
                list.add(entry);
            }
            c.close();
        }
        return list;
    }

    public synchronized int getTotalFilesCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=0", null);
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
        }
        return count;
    }

    public synchronized int getTotalFoldersCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=1", null);
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
        }
        return count;
    }
}
