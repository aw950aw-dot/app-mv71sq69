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
    private static final int DATABASE_VERSION = 12;

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
    public static final String COL_RESTORE_STATUS = "restore_status";
    public static final String COL_CATEGORY = "category";
    public static final String COL_FILE_COUNT = "file_count";
    public static final String COL_TOTAL_SIZE = "total_size";

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
                COL_CATEGORY + " TEXT, " +
                COL_FILE_COUNT + " INTEGER DEFAULT 0, " +
                COL_TOTAL_SIZE + " INTEGER DEFAULT 0)";
        db.execSQL(createTable);

        String createMeta = "CREATE TABLE " + TABLE_META + " (" +
                COL_META_KEY + " TEXT PRIMARY KEY, " +
                COL_META_VAL + " TEXT)";
        db.execSQL(createMeta);

        db.execSQL("INSERT OR REPLACE INTO " + TABLE_META + "(" + COL_META_KEY + ", " + COL_META_VAL + ") VALUES('db_version', '12')");

        // Requested Indexes: 1) nodes(parent_id, is_dir, name), nodes(category, name), nodes(selected)
        db.execSQL("CREATE INDEX idx_nodes_parent_dir_name ON " + TABLE_NODES + "(" + COL_PARENT_ID + ", " + COL_IS_DIR + ", " + COL_NAME + ")");
        db.execSQL("CREATE INDEX idx_nodes_category_name ON " + TABLE_NODES + "(" + COL_CATEGORY + ", " + COL_NAME + ")");
        db.execSQL("CREATE INDEX idx_nodes_selected ON " + TABLE_NODES + "(" + COL_SELECTED + ")");
        db.execSQL("CREATE INDEX idx_nodes_path ON " + TABLE_NODES + "(" + COL_PATH + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 12) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NODES + " ADD COLUMN " + COL_FILE_COUNT + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_NODES + " ADD COLUMN " + COL_TOTAL_SIZE + " INTEGER DEFAULT 0");

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_nodes_parent_dir_name ON " + TABLE_NODES + "(" + COL_PARENT_ID + ", " + COL_IS_DIR + ", " + COL_NAME + ")");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_nodes_category_name ON " + TABLE_NODES + "(" + COL_CATEGORY + ", " + COL_NAME + ")");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_nodes_selected ON " + TABLE_NODES + "(" + COL_SELECTED + ")");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_nodes_path ON " + TABLE_NODES + "(" + COL_PATH + ")");

                db.execSQL("INSERT OR REPLACE INTO " + TABLE_META + "(" + COL_META_KEY + ", " + COL_META_VAL + ") VALUES('db_version', '12')");
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
                    if ("12".equals(ver)) {
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
            db.execSQL("INSERT OR REPLACE INTO " + TABLE_META + "(" + COL_META_KEY + ", " + COL_META_VAL + ") VALUES('db_version', '12')");
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
                cv.put(COL_FILE_COUNT, entry.getCachedRecursiveFileCount());
                cv.put(COL_TOTAL_SIZE, entry.getCachedRecursiveSize());
                db.insertWithOnConflict(TABLE_NODES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            DiagnosticReporter.logError(e, "Database batch insert failed");
        } finally {
            try { db.endTransaction(); } catch (Exception ignored) {}
        }
    }

    // Requested feature: Compute folder totals once from deepest level up and store in columns file_count and total_size
    public synchronized void computeAndStoreFolderTotals() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // First reset all folders file_count and total_size to 0
            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_FILE_COUNT + "=0, " + COL_TOTAL_SIZE + "=0 WHERE " + COL_IS_DIR + "=1");

            // For each file (is_dir=0), add its size (1 file, size) directly to its immediate parent,
            // then recursively propagate up the tree or iterate by depth level.
            // Since we want robust and lightning fast performance, we fetch all nodes into memory (id, parent_id, is_dir, size)
            // and compute bottom-up in RAM, then batch update.
            Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_IS_DIR + ", " + COL_SIZE + " FROM " + TABLE_NODES, null);
            if (c != null) {
                Map<Long, Long> parentMap = new HashMap<>();
                Map<Long, Boolean> isDirMap = new HashMap<>();
                Map<Long, Long> fileSizeMap = new HashMap<>();
                List<Long> allNodeIds = new ArrayList<>();
                List<Long> folderIds = new ArrayList<>();

                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    long pId = c.getLong(1);
                    boolean isDir = c.getInt(2) == 1;
                    long size = c.getLong(3);

                    allNodeIds.add(id);
                    parentMap.put(id, pId);
                    isDirMap.put(id, isDir);
                    if (!isDir) {
                        fileSizeMap.put(id, size);
                    } else {
                        folderIds.add(id);
                    }
                }
                c.close();

                Map<Long, Integer> folderFileCount = new HashMap<>();
                Map<Long, Long> folderTotalSize = new HashMap<>();

                // Helper to get depth or process bottom-up by sorting folders descending by path length or id
                // Bottom-up aggregation: files contribute 1 count and their size to their parent, grandparent, etc.
                for (Map.Entry<Long, Long> entry : fileSizeMap.entrySet()) {
                    long fileId = entry.getKey();
                    long fSize = entry.getValue();

                    long currParent = parentMap.get(fileId);
                    while (currParent != 0 && parentMap.containsKey(currParent)) {
                        folderFileCount.put(currParent, folderFileCount.getOrDefault(currParent, 0) + 1);
                        folderTotalSize.put(currParent, folderTotalSize.getOrDefault(currParent, 0L) + fSize);
                        currParent = parentMap.get(currParent);
                    }
                }

                // Batch update folder stats
                for (long fId : folderIds) {
                    int count = folderFileCount.getOrDefault(fId, 0);
                    long size = folderTotalSize.getOrDefault(fId, 0L);
                    db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_FILE_COUNT + "=?, " + COL_TOTAL_SIZE + "=? WHERE " + COL_ID + "=?", new Object[]{count, size, fId});
                }
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            DiagnosticReporter.logError(e, "Failed to compute and store folder totals");
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
        
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " WHERE " + COL_PARENT_ID + "=? ORDER BY " + COL_IS_DIR + " DESC, " + COL_NAME + " ASC LIMIT 1000", new String[]{String.valueOf(parentId)});
        
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
                int fileCount = c.getInt(16);
                long totalSize = c.getLong(17);

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

                // Read directly from stored columns - no recursive query!
                entry.setCachedRecursiveSize(totalSize);
                entry.setCachedRecursiveFileCount(fileCount);

                list.add(entry);
            }
            c.close();
        }
        return list;
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
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " WHERE " + COL_NAME + " LIKE ? ORDER BY " + COL_IS_DIR + " DESC, " + COL_NAME + " ASC LIMIT 100", new String[]{pattern});
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
                int fileCount = c.getInt(16);
                long totalSize = c.getLong(17);

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
                entry.setCachedRecursiveSize(totalSize);
                entry.setCachedRecursiveFileCount(fileCount);
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

    public synchronized void setCategorySelected(String categoryKey, boolean selected) {
        SQLiteDatabase db = getWritableDatabase();
        int st = selected ? 1 : 0;
        try {
            if (categoryKey != null) {
                db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_SELECTED + "=? WHERE " + COL_CATEGORY + "=?", new Object[]{st, categoryKey});
            } else {
                db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_SELECTED + "=?", new Object[]{st});
            }
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
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " ORDER BY " + COL_IS_DIR + " DESC", null);
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
                int fileCount = c.getInt(16);
                long totalSize = c.getLong(17);

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
                entry.setCachedRecursiveSize(totalSize);
                entry.setCachedRecursiveFileCount(fileCount);
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
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " WHERE " + selectionClause + " ORDER BY LENGTH(" + COL_PATH + ") ASC, " + COL_PARENT_ID + " ASC", null);
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
                int fileCount = c.getInt(16);
                long totalSize = c.getLong(17);

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
                entry.setCachedRecursiveSize(totalSize);
                entry.setCachedRecursiveFileCount(fileCount);
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
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " WHERE " + selectionClause + " ORDER BY " + COL_PATH + " ASC", null);
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
                int fileCount = c.getInt(16);
                long totalSize = c.getLong(17);

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
                entry.setCachedRecursiveSize(totalSize);
                entry.setCachedRecursiveFileCount(fileCount);
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
