package com.generated.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "sd_rescue.db";
    private static final int DATABASE_VERSION = 11; // Upgraded for category index and backfill

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
    public static final String COL_RAW_NAME = "raw_name";
    public static final String COL_UNREADABLE = "unreadable";
    public static final String COL_UNREADABLE_REASON = "unreadable_reason";
    public static final String COL_FIRST_CLUSTER = "first_cluster";
    public static final String COL_RESTORE_STATUS = "restore_status"; // 0=pending, 1=done, 2=failed
    public static final String COL_CATEGORY = "category";

    private static final String TABLE_META = "metadata";
    private static final String COL_META_KEY = "key";
    private static final String COL_META_VAL = "value";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        setWriteAheadLoggingEnabled(true);
        try {
            Cursor c = db.rawQuery("PRAGMA busy_timeout = 15000", null);
            if (c != null) {
                c.moveToFirst();
                c.close();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NODES + " (" +
                COL_ID + " INTEGER PRIMARY KEY, " +
                COL_PARENT_ID + " INTEGER, " +
                COL_NAME + " TEXT, " +
                COL_PATH + " TEXT, " +
                COL_IS_DIR + " INTEGER, " +
                COL_SIZE + " INTEGER, " +
                COL_OFFSET + " INTEGER, " +
                COL_MODIFIED + " INTEGER, " +
                COL_TYPE + " TEXT, " +
                COL_SELECTED + " INTEGER DEFAULT 0, " +
                COL_RAW_NAME + " BLOB, " +
                COL_UNREADABLE + " INTEGER DEFAULT 0, " +
                COL_UNREADABLE_REASON + " TEXT, " +
                COL_FIRST_CLUSTER + " INTEGER DEFAULT 0, " +
                COL_RESTORE_STATUS + " INTEGER DEFAULT 0, " +
                COL_CATEGORY + " TEXT)";
        db.execSQL(createTable);

        String createMeta = "CREATE TABLE " + TABLE_META + " (" +
                COL_META_KEY + " TEXT PRIMARY KEY, " +
                COL_META_VAL + " TEXT)";
        db.execSQL(createMeta);

        db.execSQL("INSERT OR REPLACE INTO " + TABLE_META + "(" + COL_META_KEY + ", " + COL_META_VAL + ") VALUES('db_version', '11')");

        db.execSQL("CREATE INDEX idx_parent ON " + TABLE_NODES + "(" + COL_PARENT_ID + ")");
        db.execSQL("CREATE INDEX idx_path ON " + TABLE_NODES + "(" + COL_PATH + ")");
        db.execSQL("CREATE INDEX idx_name ON " + TABLE_NODES + "(" + COL_NAME + ")");
        db.execSQL("CREATE INDEX idx_cluster ON " + TABLE_NODES + "(" + COL_FIRST_CLUSTER + ")");
        db.execSQL("CREATE INDEX idx_restore ON " + TABLE_NODES + "(" + COL_RESTORE_STATUS + ")");
        db.execSQL("CREATE INDEX idx_category ON " + TABLE_NODES + "(" + COL_CATEGORY + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 11) {
            try {
                if (oldVersion < 9) {
                    db.execSQL("ALTER TABLE " + TABLE_NODES + " ADD COLUMN " + COL_RESTORE_STATUS + " INTEGER DEFAULT 0");
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_restore ON " + TABLE_NODES + "(" + COL_RESTORE_STATUS + ")");
                }
                if (oldVersion < 10) {
                    db.execSQL("ALTER TABLE " + TABLE_NODES + " ADD COLUMN " + COL_CATEGORY + " TEXT");
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_category ON " + TABLE_NODES + "(" + COL_CATEGORY + ")");

                // Fill category with one UPDATE for saved scans from before this change
                db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_CATEGORY + " = CASE " +
                        "WHEN " + COL_IS_DIR + " = 1 THEN NULL " +
                        "WHEN " + COL_NAME + " LIKE '%.jpg' OR " + COL_NAME + " LIKE '%.jpeg' OR " + COL_NAME + " LIKE '%.png' OR " + COL_NAME + " LIKE '%.gif' OR " + COL_NAME + " LIKE '%.webp' OR " + COL_NAME + " LIKE '%.heic' OR " + COL_NAME + " LIKE '%.bmp' OR " + COL_NAME + " LIKE '%.dng' THEN 'PHOTOS' " +
                        "WHEN " + COL_NAME + " LIKE '%.mp4' OR " + COL_NAME + " LIKE '%.mov' OR " + COL_NAME + " LIKE '%.mkv' OR " + COL_NAME + " LIKE '%.avi' OR " + COL_NAME + " LIKE '%.3gp' OR " + COL_NAME + " LIKE '%.webm' OR " + COL_NAME + " LIKE '%.m4v' OR " + COL_NAME + " LIKE '%.mpg' OR " + COL_NAME + " LIKE '%.mpeg' OR " + COL_NAME + " LIKE '%.mts' THEN 'VIDEOS' " +
                        "WHEN " + COL_NAME + " LIKE '%.mp3' OR " + COL_NAME + " LIKE '%.m4a' OR " + COL_NAME + " LIKE '%.aac' OR " + COL_NAME + " LIKE '%.wav' OR " + COL_NAME + " LIKE '%.flac' OR " + COL_NAME + " LIKE '%.ogg' OR " + COL_NAME + " LIKE '%.opus' OR " + COL_NAME + " LIKE '%.wma' OR " + COL_NAME + " LIKE '%.amr' THEN 'MUSIC' " +
                        "WHEN " + COL_NAME + " LIKE '%.pdf' OR " + COL_NAME + " LIKE '%.doc' OR " + COL_NAME + " LIKE '%.docx' OR " + COL_NAME + " LIKE '%.xls' OR " + COL_NAME + " LIKE '%.xlsx' OR " + COL_NAME + " LIKE '%.ppt' OR " + COL_NAME + " LIKE '%.pptx' OR " + COL_NAME + " LIKE '%.txt' OR " + COL_NAME + " LIKE '%.odt' OR " + COL_NAME + " LIKE '%.rtf' OR " + COL_NAME + " LIKE '%.csv' THEN 'DOCUMENTS' " +
                        "WHEN " + COL_NAME + " LIKE '%.zip' OR " + COL_NAME + " LIKE '%.rar' OR " + COL_NAME + " LIKE '%.7z' OR " + COL_NAME + " LIKE '%.apk' THEN 'ARCHIVES_APPS' " +
                        "ELSE 'OTHER' END WHERE " + COL_CATEGORY + " IS NULL");

                db.execSQL("INSERT OR REPLACE INTO " + TABLE_META + "(" + COL_META_KEY + ", " + COL_META_VAL + ") VALUES('db_version', '11')");
                return;
            } catch (Exception ignored) {}
        }
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NODES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_META);
        onCreate(db);
    }

    public synchronized boolean isOlderVersionDb() {
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT " + COL_META_VAL + " FROM " + TABLE_META + " WHERE " + COL_META_KEY + "=?", new String[]{"db_version"});
            if (c != null) {
                if (c.moveToFirst()) {
                    String ver = c.getString(0);
                    c.close();
                    if ("11".equals(ver)) {
                        return false;
                    }
                } else {
                    c.close();
                }
            }
            Cursor checkNodes = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES, null);
            if (checkNodes != null) {
                int count = 0;
                if (checkNodes.moveToFirst()) {
                    count = checkNodes.getInt(0);
                }
                checkNodes.close();
                if (count > 0) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public synchronized void clearDatabase() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("DELETE FROM " + TABLE_NODES);
            db.execSQL("INSERT OR REPLACE INTO " + TABLE_META + "(" + COL_META_KEY + ", " + COL_META_VAL + ") VALUES('db_version', '11')");
        } catch (Exception ignored) {}
    }

    public synchronized void insertEntriesInBatch(List<RecoveredEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (RecoveredEntry entry : entries) {
                ContentValues cv = new ContentValues();
                cv.put(COL_ID, entry.getId());
                cv.put(COL_PARENT_ID, entry.getParentId());
                cv.put(COL_NAME, entry.getName());
                cv.put(COL_PATH, entry.getPath());
                cv.put(COL_IS_DIR, entry.isDirectory() ? 1 : 0);
                cv.put(COL_SIZE, entry.getLength());
                cv.put(COL_OFFSET, entry.getOffset());
                cv.put(COL_MODIFIED, entry.getModifiedTime());
                cv.put(COL_TYPE, FileType.safeName(entry.getType()));
                cv.put(COL_SELECTED, entry.isSelected() ? 1 : 0);
                cv.put(COL_RAW_NAME, entry.getRawNameBytes());
                cv.put(COL_UNREADABLE, entry.isUnreadable() ? 1 : 0);
                cv.put(COL_UNREADABLE_REASON, entry.getUnreadableReason());
                cv.put(COL_FIRST_CLUSTER, entry.getFirstCluster());
                cv.put(COL_RESTORE_STATUS, entry.getRestoreStatus());
                cv.put(COL_CATEGORY, entry.getCategory());
                db.insertWithOnConflict(TABLE_NODES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            DiagnosticReporter.logError(e, "Database batch insert failed");
        } finally {
            try { db.endTransaction(); } catch (Exception ignored) {}
        }
    }

    public synchronized void updateRestoreStatus(long nodeId, int status) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_RESTORE_STATUS + "=? WHERE " + COL_ID + "=?", new Object[]{status, nodeId});
        } catch (Exception ignored) {}
    }

    public synchronized boolean hasUnfinishedRestore() {
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=0 AND " + COL_SELECTED + "=1 AND " + COL_RESTORE_STATUS + "!=1", null);
            if (c != null) {
                int count = 0;
                if (c.moveToFirst()) {
                    count = c.getInt(0);
                }
                c.close();
                if (count > 0) {
                    Cursor totalC = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=0 AND " + COL_SELECTED + "=1", null);
                    if (totalC != null) {
                        int total = 0;
                        if (totalC.moveToFirst()) {
                            total = totalC.getInt(0);
                        }
                        totalC.close();
                        return total > 0;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public synchronized int[] getRestoreQueueStats() {
        int done = 0;
        int total = 0;
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cDone = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=0 AND " + COL_SELECTED + "=1 AND " + COL_RESTORE_STATUS + "=1", null);
            if (cDone != null) {
                if (cDone.moveToFirst()) done = cDone.getInt(0);
                cDone.close();
            }
            Cursor cTotal = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=0 AND " + COL_SELECTED + "=1", null);
            if (cTotal != null) {
                if (cTotal.moveToFirst()) total = cTotal.getInt(0);
                cTotal.close();
            }
        } catch (Exception ignored) {}
        return new int[]{done, total};
    }

    public synchronized void clearRestoreQueue() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_RESTORE_STATUS + "=0");
        } catch (Exception ignored) {}
    }

    public synchronized long getRootId() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_ID + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + "=? AND " + COL_NAME + "=?", new String[]{"/", ""});
        if (c != null) {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                c.close();
                return id;
            }
            c.close();
        }
        return 1L;
    }

    public synchronized List<RecoveredEntry> getChildrenOfDirectory(long parentId) {
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + " FROM " + TABLE_NODES + " WHERE " + COL_PARENT_ID + "=? ORDER BY " + COL_IS_DIR + " DESC, " + COL_NAME + " ASC LIMIT 1000", new String[]{String.valueOf(parentId)});
        
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                long pId = c.getLong(1);
                String name = c.getString(2);
                String path = c.getString(3);
                long offset = c.getLong(4);
                long size = c.getLong(5);
                String typeStr = c.getString(6);
                boolean isDir = c.getInt(7) == 1;
                long modified = c.getLong(8);
                int selectedState = c.getInt(9);
                byte[] rawName = c.getBlob(10);
                boolean isUnreadable = c.getInt(11) == 1;
                String unreadableReason = c.getString(12);
                long firstCluster = c.getLong(13);
                int restoreStatus = c.getInt(14);
                String category = c.getString(15);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);
                entry.setUnreadableReason(unreadableReason);
                entry.setRestoreStatus(restoreStatus);
                entry.setCategory(category);

                if (isDir) {
                    long[] stats = getRecursiveStatsSql(db, id);
                    entry.setCachedRecursiveSize(stats[0]);
                    entry.setCachedRecursiveFileCount((int) stats[1]);
                }

                list.add(entry);
            }
            c.close();
        }
        return list;
    }

    private long[] getRecursiveStatsSql(SQLiteDatabase db, long folderId) {
        long totalSize = 0;
        int fileCount = 0;
        String query = "WITH RECURSIVE sub(id, size, is_dir) AS (" +
                "  SELECT id, size, is_dir FROM " + TABLE_NODES + " WHERE parent_id = ?" +
                "  UNION ALL" +
                "  SELECT n.id, n.size, n.is_dir FROM " + TABLE_NODES + " n JOIN sub s ON n.parent_id = s.id" +
                ") SELECT SUM(size), COUNT(*) FROM sub WHERE is_dir = 0";
        
        Cursor c = null;
        try {
            c = db.rawQuery(query, new String[]{String.valueOf(folderId)});
            if (c != null && c.moveToFirst()) {
                totalSize = c.getLong(0);
                fileCount = c.getInt(1);
            }
        } catch (Exception ignored) {
            try {
                if (c != null) c.close();
            } catch (Exception ignored2) {}
        } finally {
            if (c != null) c.close();
        }
        return new long[]{totalSize, fileCount};
    }

    public synchronized int getChildrenCountForNode(long nodeId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_PARENT_ID + "=?", new String[]{String.valueOf(nodeId)});
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) {
                count = c.getInt(0);
            }
            c.close();
        }
        return count;
    }

    public synchronized int getChildrenFoldersCountForNode(long nodeId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_PARENT_ID + "=? AND " + COL_IS_DIR + "=1", new String[]{String.valueOf(nodeId)});
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) {
                count = c.getInt(0);
            }
            c.close();
        }
        return count;
    }

    public synchronized int getChildrenFilesCountForNode(long nodeId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_PARENT_ID + "=? AND " + COL_IS_DIR + "=0", new String[]{String.valueOf(nodeId)});
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) {
                count = c.getInt(0);
            }
            c.close();
        }
        return count;
    }

    public synchronized int getOrphansCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " c WHERE c." + COL_PARENT_ID + " != 0 AND c." + COL_PARENT_ID + " NOT IN (SELECT " + COL_ID + " FROM " + TABLE_NODES + ")", null);
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) {
                count = c.getInt(0);
            }
            c.close();
        }
        return count;
    }

    public synchronized List<RecoveredEntry> searchNodes(String query) {
        List<RecoveredEntry> list = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return list;
        SQLiteDatabase db = getReadableDatabase();
        String pattern = "%" + query.trim() + "%";
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + " FROM " + TABLE_NODES + " WHERE " + COL_NAME + " LIKE ? ORDER BY " + COL_IS_DIR + " DESC, " + COL_NAME + " ASC LIMIT 100", new String[]{pattern});
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                long pId = c.getLong(1);
                String name = c.getString(2);
                String path = c.getString(3);
                long offset = c.getLong(4);
                long size = c.getLong(5);
                String typeStr = c.getString(6);
                boolean isDir = c.getInt(7) == 1;
                long modified = c.getLong(8);
                int selectedState = c.getInt(9);
                byte[] rawName = c.getBlob(10);
                boolean isUnreadable = c.getInt(11) == 1;
                String unreadableReason = c.getString(12);
                long firstCluster = c.getLong(13);
                int restoreStatus = c.getInt(14);
                String category = c.getString(15);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);
                entry.setUnreadableReason(unreadableReason);
                entry.setRestoreStatus(restoreStatus);
                entry.setCategory(category);
                list.add(entry);
            }
            c.close();
        }
        return list;
    }

    public synchronized void setNodeAndSubtreeSelected(RecoveredEntry entry, int selectedState) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String fullPath = entry.getPath().equals("/") ? "/" + entry.getName() : entry.getPath() + "/" + entry.getName();
            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_SELECTED + "=? WHERE " + COL_PATH + "=? AND " + COL_NAME + "=?", new Object[]{selectedState, entry.getPath(), entry.getName()});

            if (entry.isDirectory()) {
                db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_SELECTED + "=? WHERE " + COL_PATH + " LIKE ?", new Object[]{selectedState, fullPath + "%"});
            }

            updateAncestorStates(db, entry.getPath());
            db.setTransactionSuccessful();
        } catch (Exception ignored) {} finally {
            try { db.endTransaction(); } catch (Exception ignored) {}
        }
    }

    private void updateAncestorStates(SQLiteDatabase db, String path) {
        if (path == null || path.isEmpty() || path.equals("/")) return;
        String[] parts = path.split("/");
        String currentPath = "";
        for (String part : parts) {
            if (part.isEmpty()) continue;
            String parentPath = currentPath.isEmpty() ? "/" : currentPath;
            currentPath = currentPath.equals("/") ? "/" + part : currentPath + "/" + part;

            Cursor c = db.rawQuery("SELECT DISTINCT " + COL_SELECTED + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + "=?", new String[]{currentPath});
            boolean hasSelected = false;
            boolean hasUnselected = false;
            boolean hasPartial = false;
            int count = 0;
            if (c != null) {
                while (c.moveToNext()) {
                    int st = c.getInt(0);
                    if (st == 1) hasSelected = true;
                    else if (st == 0) hasUnselected = true;
                    else if (st == 2) hasPartial = true;
                    count++;
                }
                c.close();
            }

            int newState = 0;
            if (count > 0) {
                if (hasPartial || (hasSelected && hasUnselected)) {
                    newState = 2;
                } else if (hasSelected && !hasUnselected) {
                    newState = 1;
                }
            }

            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_SELECTED + "=? WHERE " + COL_PATH + "=? AND " + COL_NAME + "=?", new Object[]{newState, parentPath, part});
        }
    }

    public synchronized void setAllSelected(boolean selected) {
        SQLiteDatabase db = getWritableDatabase();
        int st = selected ? 1 : 0;
        try {
            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_SELECTED + "=?", new Object[]{st});
        } catch (Exception ignored) {}
    }

    public synchronized long[] getSelectedStats() {
        SQLiteDatabase db = getReadableDatabase();
        long filesCount = 0;
        long totalBytes = 0;
        Cursor c = db.rawQuery("SELECT SUM(" + COL_SIZE + "), COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=0 AND " + COL_SELECTED + "=1", null);
        if (c != null) {
            if (c.moveToFirst()) {
                totalBytes = c.getLong(0);
                filesCount = c.getLong(1);
            }
            c.close();
        }
        return new long[]{filesCount, totalBytes};
    }

    public synchronized Map<String, long[]> getCategoryStats() {
        Map<String, long[]> map = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_CATEGORY + ", COUNT(*), SUM(" + COL_SIZE + ") FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=0 GROUP BY " + COL_CATEGORY, null);
        if (c != null) {
            while (c.moveToNext()) {
                String cat = c.getString(0);
                long count = c.getLong(1);
                long size = c.getLong(2);
                if (cat != null) {
                    map.put(cat, new long[]{count, size});
                }
            }
            c.close();
        }
        return map;
    }

    public synchronized List<RecoveredEntry> getAllEntriesForSaveAll() {
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + " FROM " + TABLE_NODES + " ORDER BY " + COL_IS_DIR + " DESC", null);
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                long pId = c.getLong(1);
                String name = c.getString(2);
                String path = c.getString(3);
                long offset = c.getLong(4);
                long size = c.getLong(5);
                String typeStr = c.getString(6);
                boolean isDir = c.getInt(7) == 1;
                long modified = c.getLong(8);
                int selectedState = c.getInt(9);
                byte[] rawName = c.getBlob(10);
                boolean isUnreadable = c.getInt(11) == 1;
                String unreadableReason = c.getString(12);
                long firstCluster = c.getLong(13);
                int restoreStatus = c.getInt(14);
                String category = c.getString(15);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);
                entry.setUnreadableReason(unreadableReason);
                entry.setRestoreStatus(restoreStatus);
                entry.setCategory(category);
                list.add(entry);
            }
            c.close();
        }
        return list;
    }

    public synchronized List<RecoveredEntry> getOrderedFoldersForRestore(boolean saveAll) {
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String selectionClause = saveAll ? (COL_IS_DIR + "=1") : (COL_IS_DIR + "=1 AND " + COL_SELECTED + "!=0");
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + " FROM " + TABLE_NODES + " WHERE " + selectionClause + " ORDER BY LENGTH(" + COL_PATH + ") ASC, " + COL_PARENT_ID + " ASC", null);
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                long pId = c.getLong(1);
                String name = c.getString(2);
                String path = c.getString(3);
                long offset = c.getLong(4);
                long size = c.getLong(5);
                String typeStr = c.getString(6);
                boolean isDir = c.getInt(7) == 1;
                long modified = c.getLong(8);
                int selectedState = c.getInt(9);
                byte[] rawName = c.getBlob(10);
                boolean isUnreadable = c.getInt(11) == 1;
                String unreadableReason = c.getString(12);
                long firstCluster = c.getLong(13);
                int restoreStatus = c.getInt(14);
                String category = c.getString(15);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);
                entry.setUnreadableReason(unreadableReason);
                entry.setRestoreStatus(restoreStatus);
                entry.setCategory(category);
                list.add(entry);
            }
            c.close();
        }
        return list;
    }

    public synchronized List<RecoveredEntry> getFilesForRestore(boolean saveAll) {
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String selectionClause = saveAll ? (COL_IS_DIR + "=0") : (COL_IS_DIR + "=0 AND " + COL_SELECTED + "=1");
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + " FROM " + TABLE_NODES + " WHERE " + selectionClause + " ORDER BY " + COL_PATH + " ASC", null);
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                long pId = c.getLong(1);
                String name = c.getString(2);
                String path = c.getString(3);
                long offset = c.getLong(4);
                long size = c.getLong(5);
                String typeStr = c.getString(6);
                boolean isDir = c.getInt(7) == 1;
                long modified = c.getLong(8);
                int selectedState = c.getInt(9);
                byte[] rawName = c.getBlob(10);
                boolean isUnreadable = c.getInt(11) == 1;
                String unreadableReason = c.getString(12);
                long firstCluster = c.getLong(13);
                int restoreStatus = c.getInt(14);
                String category = c.getString(15);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);
                entry.setUnreadableReason(unreadableReason);
                entry.setRestoreStatus(restoreStatus);
                entry.setCategory(category);
                list.add(entry);
            }
            c.close();
        }
        return list;
    }

    public synchronized long getParentId(String path, String name) {
        SQLiteDatabase db = getReadableDatabase();
        if (path == null || path.isEmpty()) path = "/";
        Cursor c = db.rawQuery("SELECT " + COL_PARENT_ID + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + "=? AND " + COL_NAME + "=?", new String[]{path, name});
        if (c != null) {
            if (c.moveToFirst()) {
                long pid = c.getLong(0);
                c.close();
                return pid;
            }
            c.close();
        }
        return -1;
    }

    public synchronized long getNodeId(String path, String name) {
        SQLiteDatabase db = getReadableDatabase();
        if (path == null || path.isEmpty()) path = "/";
        Cursor c = db.rawQuery("SELECT " + COL_ID + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + "=? AND " + COL_NAME + "=?", new String[]{path, name});
        if (c != null) {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                c.close();
                return id;
            }
            c.close();
        }
        return -1;
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
