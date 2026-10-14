package com.generated.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "sd_rescue.db";
    private static final int DATABASE_VERSION = 6;

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
    public static final String COL_FIRST_CLUSTER = "first_cluster";

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
                COL_FIRST_CLUSTER + " INTEGER DEFAULT 0)";
        db.execSQL(createTable);
        db.execSQL("CREATE INDEX idx_parent ON " + TABLE_NODES + "(" + COL_PARENT_ID + ")");
        db.execSQL("CREATE INDEX idx_path ON " + TABLE_NODES + "(" + COL_PATH + ")");
        db.execSQL("CREATE INDEX idx_name ON " + TABLE_NODES + "(" + COL_NAME + ")");
        db.execSQL("CREATE INDEX idx_cluster ON " + TABLE_NODES + "(" + COL_FIRST_CLUSTER + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NODES);
        onCreate(db);
    }

    public synchronized void clearDatabase() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("DELETE FROM " + TABLE_NODES);
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
                cv.put(COL_FIRST_CLUSTER, entry.getFirstCluster());
                db.insertWithOnConflict(TABLE_NODES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            DiagnosticReporter.logError(e, "Database batch insert failed");
        } finally {
            try { db.endTransaction(); } catch (Exception ignored) {}
        }
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
        
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_FIRST_CLUSTER + " FROM " + TABLE_NODES + " WHERE " + COL_PARENT_ID + "=? ORDER BY " + COL_IS_DIR + " DESC, " + COL_NAME + " ASC LIMIT 500", new String[]{String.valueOf(parentId)});
        
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
                long firstCluster = c.getLong(12);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);

                if (isDir) {
                    String subPath = path.equals("/") ? "/" + name : path + "/" + name;
                    long[] stats = getRecursiveStatsRecursiveSql(db, subPath);
                    entry.setCachedRecursiveSize(stats[0]);
                    entry.setCachedRecursiveFileCount((int) stats[1]);
                }

                list.add(entry);
            }
            c.close();
        }
        return list;
    }

    private long[] getRecursiveStatsRecursiveSql(SQLiteDatabase db, String dirPath) {
        long totalSize = 0;
        int fileCount = 0;
        String query = "WITH RECURSIVE sub(id, size, is_dir, path, name) AS (" +
                "  SELECT id, size, is_dir, path, name FROM " + TABLE_NODES + " WHERE path = ? OR (path = '/' AND name = ?)" +
                "  UNION ALL" +
                "  SELECT n.id, n.size, n.is_dir, n.path, n.name FROM " + TABLE_NODES + " n JOIN sub s ON n.parent_id = s.id" +
                ") SELECT SUM(size), COUNT(*) FROM sub WHERE is_dir = 0";
        
        String directName = dirPath.contains("/") ? dirPath.substring(dirPath.lastIndexOf('/') + 1) : dirPath;
        String parentPath = dirPath.contains("/") ? dirPath.substring(0, dirPath.lastIndexOf('/')) : "/";
        if (parentPath.isEmpty()) parentPath = "/";

        Cursor c = null;
        try {
            c = db.rawQuery(query, new String[]{dirPath, directName});
            if (c != null && c.moveToFirst()) {
                totalSize = c.getLong(0);
                fileCount = c.getInt(1);
            }
        } catch (Exception ignored) {
            try {
                if (c != null) c.close();
                Cursor fallback = db.rawQuery("SELECT " + COL_SIZE + ", " + COL_IS_DIR + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + " LIKE ?", new String[]{dirPath + "%"});
                if (fallback != null) {
                    while (fallback.moveToNext()) {
                        if (fallback.getInt(1) == 0) {
                            totalSize += fallback.getLong(0);
                            fileCount++;
                        }
                    }
                    fallback.close();
                }
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

    public synchronized List<RecoveredEntry> searchNodes(String query) {
        List<RecoveredEntry> list = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return list;
        SQLiteDatabase db = getReadableDatabase();
        String pattern = "%" + query.trim() + "%";
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_FIRST_CLUSTER + " FROM " + TABLE_NODES + " WHERE " + COL_NAME + " LIKE ? ORDER BY " + COL_IS_DIR + " DESC, " + COL_NAME + " ASC LIMIT 100", new String[]{pattern});
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
                long firstCluster = c.getLong(12);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);
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

    public synchronized List<RecoveredEntry> getAllSelectedFiles() {
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_FIRST_CLUSTER + " FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=0 AND " + COL_SELECTED + "=1", null);
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
                long firstCluster = c.getLong(12);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);
                list.add(entry);
            }
            c.close();
        }
        return list;
    }

    public synchronized List<RecoveredEntry> getAllEntriesForSaveAll() {
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_FIRST_CLUSTER + " FROM " + TABLE_NODES + " ORDER BY " + COL_IS_DIR + " DESC", null);
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
                long firstCluster = c.getLong(12);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);
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
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_FIRST_CLUSTER + " FROM " + TABLE_NODES + " WHERE " + selectionClause + " ORDER BY LENGTH(" + COL_PATH + ") ASC, " + COL_PARENT_ID + " ASC", null);
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
                long firstCluster = c.getLong(12);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);
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
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_FIRST_CLUSTER + " FROM " + TABLE_NODES + " WHERE " + selectionClause + " ORDER BY " + COL_PATH + " ASC", null);
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
                long firstCluster = c.getLong(12);

                FileType type = FileType.UNKNOWN;
                try {
                    type = FileType.valueOf(typeStr);
                } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offset, size, type, false, isDir, modified, rawName, firstCluster);
                entry.setSelectedState(selectedState);
                entry.setUnreadable(isUnreadable);
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
