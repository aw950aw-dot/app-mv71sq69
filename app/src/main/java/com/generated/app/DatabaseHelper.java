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
    private static final int DATABASE_VERSION = 14;

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

    public static final String TABLE_JOB_STATE = "job_state";
    public static final String COL_JOB_TYPE = "job_type"; // "scan" or "restore"
    public static final String COL_JOB_PHASE = "phase"; // "RUNNING", "COMPLETED", "INTERRUPTED", "CANCELLED", "FAILED", "DISCARDED"
    public static final String COL_JOB_STARTED_AT = "started_at";
    public static final String COL_JOB_HEARTBEAT = "last_heartbeat";
    public static final String COL_JOB_FOLDERS = "folders";
    public static final String COL_JOB_FILES = "files";
    public static final String COL_JOB_BYTES = "bytes";

    public static final String TABLE_SCAN_SUMMARY = "scan_summary";
    public static final String COL_SUMMARY_ID = "id";
    public static final String COL_SUMMARY_LUN = "lun_used";
    public static final String COL_SUMMARY_PARTITION_INFO = "partition_info";
    public static final String COL_SUMMARY_START_LBA = "start_lba";
    public static final String COL_SUMMARY_BACKUP_BOOT = "backup_boot";
    public static final String COL_SUMMARY_FS_TYPE = "fs_type";
    public static final String COL_SUMMARY_BOOT_FIELDS = "boot_fields";
    public static final String COL_SUMMARY_FIRST_DATA = "first_data";
    public static final String COL_SUMMARY_ROOT_LBA = "root_lba";
    public static final String COL_SUMMARY_FILES_COUNT = "files_count";
    public static final String COL_SUMMARY_FOLDERS_COUNT = "folders_count";
    public static final String COL_SUMMARY_LONG_NAMES = "long_names";
    public static final String COL_SUMMARY_SHORT_NAMES = "short_names";
    public static final String COL_SUMMARY_SKIPPED_GARBAGE = "skipped_garbage";
    public static final String COL_SUMMARY_UNREADABLE_FOLDERS = "unreadable_folders";
    public static final String COL_SUMMARY_ERROR_COUNT = "error_count";
    public static final String COL_SUMMARY_FULL_REPORT = "full_report";

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

        String createJobState = "CREATE TABLE " + TABLE_JOB_STATE + " (" +
                COL_JOB_TYPE + " TEXT PRIMARY KEY, " +
                COL_JOB_PHASE + " TEXT, " +
                COL_JOB_STARTED_AT + " INTEGER, " +
                COL_JOB_HEARTBEAT + " INTEGER, " +
                COL_JOB_FOLDERS + " INTEGER, " +
                COL_JOB_FILES + " INTEGER, " +
                COL_JOB_BYTES + " INTEGER)";
        db.execSQL(createJobState);

        String createSummary = "CREATE TABLE " + TABLE_SCAN_SUMMARY + " (" +
                COL_SUMMARY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SUMMARY_LUN + " TEXT, " +
                COL_SUMMARY_PARTITION_INFO + " TEXT, " +
                COL_SUMMARY_START_LBA + " TEXT, " +
                COL_SUMMARY_BACKUP_BOOT + " TEXT, " +
                COL_SUMMARY_FS_TYPE + " TEXT, " +
                COL_SUMMARY_BOOT_FIELDS + " TEXT, " +
                COL_SUMMARY_FIRST_DATA + " TEXT, " +
                COL_SUMMARY_ROOT_LBA + " TEXT, " +
                COL_SUMMARY_FILES_COUNT + " INTEGER, " +
                COL_SUMMARY_FOLDERS_COUNT + " INTEGER, " +
                COL_SUMMARY_LONG_NAMES + " INTEGER, " +
                COL_SUMMARY_SHORT_NAMES + " INTEGER, " +
                COL_SUMMARY_SKIPPED_GARBAGE + " INTEGER, " +
                COL_SUMMARY_UNREADABLE_FOLDERS + " INTEGER, " +
                COL_SUMMARY_ERROR_COUNT + " INTEGER, " +
                COL_SUMMARY_FULL_REPORT + " TEXT)";
        db.execSQL(createSummary);

        db.execSQL("INSERT OR REPLACE INTO " + TABLE_META + "(" + COL_META_KEY + ", " + COL_META_VAL + ") VALUES('db_version', '14')");

        db.execSQL("CREATE INDEX idx_nodes_parent_dir_name ON " + TABLE_NODES + "(" + COL_PARENT_ID + ", " + COL_IS_DIR + ", " + COL_NAME + ")");
        db.execSQL("CREATE INDEX idx_nodes_category_name ON " + TABLE_NODES + "(" + COL_CATEGORY + ", " + COL_NAME + ")");
        db.execSQL("CREATE INDEX idx_nodes_selected ON " + TABLE_NODES + "(" + COL_SELECTED + ")");
        db.execSQL("CREATE INDEX idx_nodes_path ON " + TABLE_NODES + "(" + COL_PATH + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 14) {
            try {
                String createSummary = "CREATE TABLE IF NOT EXISTS " + TABLE_SCAN_SUMMARY + " (" +
                        COL_SUMMARY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_SUMMARY_LUN + " TEXT, " +
                        COL_SUMMARY_PARTITION_INFO + " TEXT, " +
                        COL_SUMMARY_START_LBA + " TEXT, " +
                        COL_SUMMARY_BACKUP_BOOT + " TEXT, " +
                        COL_SUMMARY_FS_TYPE + " TEXT, " +
                        COL_SUMMARY_BOOT_FIELDS + " TEXT, " +
                        COL_SUMMARY_FIRST_DATA + " TEXT, " +
                        COL_SUMMARY_ROOT_LBA + " TEXT, " +
                        COL_SUMMARY_FILES_COUNT + " INTEGER, " +
                        COL_SUMMARY_FOLDERS_COUNT + " INTEGER, " +
                        COL_SUMMARY_LONG_NAMES + " INTEGER, " +
                        COL_SUMMARY_SHORT_NAMES + " INTEGER, " +
                        COL_SUMMARY_SKIPPED_GARBAGE + " INTEGER, " +
                        COL_SUMMARY_UNREADABLE_FOLDERS + " INTEGER, " +
                        COL_SUMMARY_ERROR_COUNT + " INTEGER, " +
                        COL_SUMMARY_FULL_REPORT + " TEXT)";
                db.execSQL(createSummary);
                db.execSQL("INSERT OR REPLACE INTO " + TABLE_META + "(" + COL_META_KEY + ", " + COL_META_VAL + ") VALUES('db_version', '14')");
                return;
            } catch (Exception ignored) {}
        }
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NODES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_META);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_JOB_STATE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCAN_SUMMARY);
        onCreate(db);
    }

    private RecoveredEntry cursorToEntry(Cursor c) {
        long id = c.getLong(0);
        long pId = c.getLong(1);
        String name = c.getString(2);
        String path = c.getString(3);
        long offsetVal = c.getLong(4);
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
        long totalSize = c.getInt(17);

        FileType type = FileType.UNKNOWN;
        try {
            type = FileType.valueOf(typeStr);
        } catch (Exception ignored) {}

        RecoveredEntry entry = new RecoveredEntry(id, pId, name, path, offsetVal, size, type, false, isDir, modified, rawName, firstCluster);
        entry.setSelectedState(selectedState);
        entry.setUnreadable(isUnreadable);
        entry.setUnreadableReason(unreadableReason);
        entry.setRestoreStatus(restoreStatus);
        entry.setCategory(category);
        entry.setCachedRecursiveSize(totalSize);
        entry.setCachedRecursiveFileCount(fileCount);
        return entry;
    }

    public synchronized List<RecoveredEntry> getEntriesByCategory(String category, long minSizeBytes) {
        long start = System.currentTimeMillis();
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " WHERE " + COL_CATEGORY + "=? AND " + COL_IS_DIR + "=0 AND " + COL_SIZE + ">=? ORDER BY " + COL_NAME + " COLLATE NOCASE", new String[]{category, String.valueOf(minSizeBytes)});
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToEntry(c));
            }
            c.close();
        }
        PerformanceLogger.record("getEntriesByCategory", System.currentTimeMillis() - start);
        return list;
    }

    public synchronized void saveScanSummary(String lun, String partitionInfo, String startLba, String backupBoot,
                                             String fsType, String bootFields, String firstData, String rootLba,
                                             int filesCount, int foldersCount, int longNames, int shortNames,
                                             int skippedGarbage, int unreadableFolders, int errorCount, String fullReport) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SUMMARY_LUN, lun);
        cv.put(COL_SUMMARY_PARTITION_INFO, partitionInfo);
        cv.put(COL_SUMMARY_START_LBA, startLba);
        cv.put(COL_SUMMARY_BACKUP_BOOT, backupBoot);
        cv.put(COL_SUMMARY_FS_TYPE, fsType);
        cv.put(COL_SUMMARY_BOOT_FIELDS, bootFields);
        cv.put(COL_SUMMARY_FIRST_DATA, firstData);
        cv.put(COL_SUMMARY_ROOT_LBA, rootLba);
        cv.put(COL_SUMMARY_FILES_COUNT, filesCount);
        cv.put(COL_SUMMARY_FOLDERS_COUNT, foldersCount);
        cv.put(COL_SUMMARY_LONG_NAMES, longNames);
        cv.put(COL_SUMMARY_SHORT_NAMES, shortNames);
        cv.put(COL_SUMMARY_SKIPPED_GARBAGE, skippedGarbage);
        cv.put(COL_SUMMARY_UNREADABLE_FOLDERS, unreadableFolders);
        cv.put(COL_SUMMARY_ERROR_COUNT, errorCount);
        cv.put(COL_SUMMARY_FULL_REPORT, fullReport);

        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM " + TABLE_SCAN_SUMMARY);
            db.insert(TABLE_SCAN_SUMMARY, null, cv);
            db.setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            try { db.endTransaction(); } catch (Exception ignored) {}
        }
    }

    public synchronized String getSavedScanReport() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_SUMMARY_FULL_REPORT + " FROM " + TABLE_SCAN_SUMMARY + " ORDER BY " + COL_SUMMARY_ID + " DESC LIMIT 1", null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    String report = c.getString(0);
                    c.close();
                    return report;
                }
            } catch (Exception ignored) {}
            finally {
                c.close();
            }
        }
        return null;
    }

    public synchronized void updateJobState(String jobType, String phase, long startedAt, long heartbeat, int folders, int files, long bytes) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_JOB_TYPE, jobType);
        cv.put(COL_JOB_PHASE, phase);
        cv.put(COL_JOB_STARTED_AT, startedAt);
        cv.put(COL_JOB_HEARTBEAT, heartbeat);
        cv.put(COL_JOB_FOLDERS, folders);
        cv.put(COL_JOB_FILES, files);
        cv.put(COL_JOB_BYTES, bytes);
        db.insertWithOnConflict(TABLE_JOB_STATE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void checkAndInterruptStaleJobs() {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        try {
            Cursor c = db.rawQuery("SELECT " + COL_JOB_TYPE + ", " + COL_JOB_PHASE + ", " + COL_JOB_HEARTBEAT + " FROM " + TABLE_JOB_STATE, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String type = c.getString(0);
                    String phase = c.getString(1);
                    long heartbeat = c.getLong(2);
                    if ("RUNNING".equalsIgnoreCase(phase) && (now - heartbeat > 15000)) {
                        db.execSQL("UPDATE " + TABLE_JOB_STATE + " SET " + COL_JOB_PHASE + "='INTERRUPTED' WHERE " + COL_JOB_TYPE + "=?", new Object[]{type});
                    }
                }
                c.close();
            }
        } catch (Exception ignored) {}
    }

    public synchronized String getJobPhase(String jobType) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_JOB_PHASE + " FROM " + TABLE_JOB_STATE + " WHERE " + COL_JOB_TYPE + "=?", new String[]{jobType});
        if (c != null) {
            if (c.moveToFirst()) {
                String phase = c.getString(0);
                c.close();
                return phase;
            }
            c.close();
        }
        return "IDLE";
    }

    public synchronized void clearJobState(String jobType) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.execSQL("DELETE FROM " + TABLE_JOB_STATE + " WHERE " + COL_JOB_TYPE + "=?", new Object[]{jobType});
        } catch (Exception ignored) {}
    }

    public synchronized boolean isOlderVersionDb() {
        long start = System.currentTimeMillis();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT " + COL_META_VAL + " FROM " + TABLE_META + " WHERE " + COL_META_KEY + "=?", new String[]{"db_version"});
            if (c != null) {
                if (c.moveToFirst()) {
                    String ver = c.getString(0);
                    c.close();
                    if ("14".equals(ver)) {
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
        finally {
            PerformanceLogger.record("isOlderVersionDb", System.currentTimeMillis() - start);
        }
        return false;
    }

    public synchronized void clearDatabase() {
        long start = System.currentTimeMillis();
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("DELETE FROM " + TABLE_NODES);
            db.execSQL("DELETE FROM " + TABLE_JOB_STATE);
            db.execSQL("DELETE FROM " + TABLE_SCAN_SUMMARY);
            db.execSQL("INSERT OR REPLACE INTO " + TABLE_META + "(" + COL_META_KEY + ", " + COL_META_VAL + ") VALUES('db_version', '14')");
        } catch (Exception ignored) {}
        finally {
            PerformanceLogger.record("clearDatabase", System.currentTimeMillis() - start);
        }
    }

    public synchronized void insertEntriesInBatch(List<RecoveredEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        long start = System.currentTimeMillis();
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
            PerformanceLogger.record("insertEntriesInBatch", System.currentTimeMillis() - start);
        }
    }

    public synchronized void computeAndStoreFolderTotals() {
        long start = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_FILE_COUNT + "=0, " + COL_TOTAL_SIZE + "=0 WHERE " + COL_IS_DIR + "=1");

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
            PerformanceLogger.record("computeAndStoreFolderTotals", System.currentTimeMillis() - start);
        }
    }

    public synchronized void updateRestoreStatus(long nodeId, int status) {
        long start = System.currentTimeMillis();
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_RESTORE_STATUS + "=? WHERE " + COL_ID + "=?", new Object[]{status, nodeId});
        } catch (Exception ignored) {}
        finally {
            PerformanceLogger.record("updateRestoreStatus", System.currentTimeMillis() - start);
        }
    }

    public synchronized boolean hasUnfinishedRestore() {
        long start = System.currentTimeMillis();
        try {
            String phase = getJobPhase("restore");
            if (!"INTERRUPTED".equalsIgnoreCase(phase)) {
                return false;
            }
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
        finally {
            PerformanceLogger.record("hasUnfinishedRestore", System.currentTimeMillis() - start);
        }
        return false;
    }

    public synchronized void discardRestore() {
        long start = System.currentTimeMillis();
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_SELECTED + "=0, " + COL_RESTORE_STATUS + "=0");
            long now = System.currentTimeMillis();
            updateJobState("restore", "DISCARDED", now, now, 0, 0, 0);
        } catch (Exception ignored) {}
        finally {
            PerformanceLogger.record("discardRestore", System.currentTimeMillis() - start);
        }
    }

    public synchronized int[] getRestoreQueueStats() {
        long start = System.currentTimeMillis();
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
        finally {
            PerformanceLogger.record("getRestoreQueueStats", System.currentTimeMillis() - start);
        }
        return new int[]{done, total};
    }

    public synchronized void clearRestoreQueue() {
        long start = System.currentTimeMillis();
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_RESTORE_STATUS + "=0");
        } catch (Exception ignored) {}
        finally {
            PerformanceLogger.record("clearRestoreQueue", System.currentTimeMillis() - start);
        }
    }

    public synchronized long getRootId() {
        long start = System.currentTimeMillis();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_ID + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + "=? AND " + COL_NAME + "=?", new String[]{"/", ""});
        if (c != null) {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                c.close();
                PerformanceLogger.record("getRootId", System.currentTimeMillis() - start);
                return id;
            }
            c.close();
        }
        PerformanceLogger.record("getRootId", System.currentTimeMillis() - start);
        return 1L;
    }

    public synchronized List<RecoveredEntry> getChildrenOfDirectory(long parentId, int limit, int offset) {
        long start = System.currentTimeMillis();
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " WHERE " + COL_PARENT_ID + "=? ORDER BY " + COL_IS_DIR + " DESC, " + COL_NAME + " COLLATE NOCASE LIMIT ? OFFSET ?", new String[]{String.valueOf(parentId), String.valueOf(limit), String.valueOf(offset)});
        
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToEntry(c));
            }
            c.close();
        }
        PerformanceLogger.record("getChildrenOfDirectory", System.currentTimeMillis() - start);
        return list;
    }

    public synchronized int getChildrenCountForNode(long nodeId) {
        long start = System.currentTimeMillis();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_PARENT_ID + "=?", new String[]{String.valueOf(nodeId)});
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) {
                count = c.getInt(0);
            }
            c.close();
        }
        PerformanceLogger.record("getChildrenCountForNode", System.currentTimeMillis() - start);
        return count;
    }

    public synchronized int getOrphansCount() {
        long start = System.currentTimeMillis();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " c WHERE c." + COL_PARENT_ID + " != 0 AND c." + COL_PARENT_ID + " NOT IN (SELECT " + COL_ID + " FROM " + TABLE_NODES + ")", null);
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) {
                count = c.getInt(0);
            }
            c.close();
        }
        PerformanceLogger.record("getOrphansCount", System.currentTimeMillis() - start);
        return count;
    }

    public synchronized List<RecoveredEntry> searchNodes(String query) {
        long start = System.currentTimeMillis();
        List<RecoveredEntry> list = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return list;
        SQLiteDatabase db = getReadableDatabase();
        String pattern = "%" + query.trim() + "%";
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " WHERE " + COL_NAME + " LIKE ? ORDER BY " + COL_IS_DIR + " DESC, " + COL_NAME + " ASC LIMIT 100", new String[]{pattern});
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToEntry(c));
            }
            c.close();
        }
        PerformanceLogger.record("searchNodes", System.currentTimeMillis() - start);
        return list;
    }

    public synchronized void setNodeAndSubtreeSelected(RecoveredEntry entry, int selectedState) {
        long start = System.currentTimeMillis();
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
            PerformanceLogger.record("setNodeAndSubtreeSelected", System.currentTimeMillis() - start);
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
        long start = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        int st = selected ? 1 : 0;
        try {
            db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_SELECTED + "=?", new Object[]{st});
        } catch (Exception ignored) {}
        finally {
            PerformanceLogger.record("setAllSelected", System.currentTimeMillis() - start);
        }
    }

    public synchronized void setCategorySelected(String categoryKey, boolean selected) {
        long start = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        int st = selected ? 1 : 0;
        try {
            if (categoryKey != null) {
                db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_SELECTED + "=? WHERE " + COL_CATEGORY + "=?", new Object[]{st, categoryKey});
            } else {
                db.execSQL("UPDATE " + TABLE_NODES + " SET " + COL_SELECTED + "=?", new Object[]{st});
            }
        } catch (Exception ignored) {}
        finally {
            PerformanceLogger.record("setCategorySelected", System.currentTimeMillis() - start);
        }
    }

    public synchronized long[] getSelectedStats() {
        long start = System.currentTimeMillis();
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
        PerformanceLogger.record("getSelectedStats", System.currentTimeMillis() - start);
        return new long[]{filesCount, totalBytes};
    }

    public synchronized Map<String, long[]> getCategoryStats() {
        long start = System.currentTimeMillis();
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
        PerformanceLogger.record("getCategoryStats", System.currentTimeMillis() - start);
        return map;
    }

    public synchronized List<RecoveredEntry> getAllEntriesForSaveAll() {
        long start = System.currentTimeMillis();
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " ORDER BY " + COL_IS_DIR + " DESC", null);
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToEntry(c));
            }
            c.close();
        }
        PerformanceLogger.record("getAllEntriesForSaveAll", System.currentTimeMillis() - start);
        return list;
    }

    public synchronized List<RecoveredEntry> getOrderedFoldersForRestore(boolean saveAll) {
        long start = System.currentTimeMillis();
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String selectionClause = saveAll ? (COL_IS_DIR + "=1") : (COL_IS_DIR + "=1 AND " + COL_SELECTED + "!=0");
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " WHERE " + selectionClause + " ORDER BY LENGTH(" + COL_PATH + ") ASC, " + COL_PARENT_ID + " ASC", null);
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToEntry(c));
            }
            c.close();
        }
        PerformanceLogger.record("getOrderedFoldersForRestore", System.currentTimeMillis() - start);
        return list;
    }

    public synchronized List<RecoveredEntry> getFilesForRestore(boolean saveAll) {
        long start = System.currentTimeMillis();
        List<RecoveredEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String selectionClause = saveAll ? (COL_IS_DIR + "=0") : (COL_IS_DIR + "=0 AND " + COL_SELECTED + "=1");
        Cursor c = db.rawQuery("SELECT " + COL_ID + ", " + COL_PARENT_ID + ", " + COL_NAME + ", " + COL_PATH + ", " + COL_OFFSET + ", " + COL_SIZE + ", " + COL_TYPE + ", " + COL_IS_DIR + ", " + COL_MODIFIED + ", " + COL_SELECTED + ", " + COL_RAW_NAME + ", " + COL_UNREADABLE + ", " + COL_UNREADABLE_REASON + ", " + COL_FIRST_CLUSTER + ", " + COL_RESTORE_STATUS + ", " + COL_CATEGORY + ", " + COL_FILE_COUNT + ", " + COL_TOTAL_SIZE + " FROM " + TABLE_NODES + " WHERE " + selectionClause + " ORDER BY " + COL_PATH + " ASC", null);
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToEntry(c));
            }
            c.close();
        }
        PerformanceLogger.record("getFilesForRestore", System.currentTimeMillis() - start);
        return list;
    }

    public synchronized long getParentId(String path, String name) {
        long start = System.currentTimeMillis();
        SQLiteDatabase db = getReadableDatabase();
        if (path == null || path.isEmpty()) path = "/";
        Cursor c = db.rawQuery("SELECT " + COL_PARENT_ID + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + "=? AND " + COL_NAME + "=?", new String[]{path, name});
        if (c != null) {
            if (c.moveToFirst()) {
                long pid = c.getLong(0);
                c.close();
                PerformanceLogger.record("getParentId", System.currentTimeMillis() - start);
                return pid;
            }
            c.close();
        }
        PerformanceLogger.record("getParentId", System.currentTimeMillis() - start);
        return -1;
    }

    public synchronized long getNodeId(String path, String name) {
        long start = System.currentTimeMillis();
        SQLiteDatabase db = getReadableDatabase();
        if (path == null || path.isEmpty()) path = "/";
        Cursor c = db.rawQuery("SELECT " + COL_ID + " FROM " + TABLE_NODES + " WHERE " + COL_PATH + "=? AND " + COL_NAME + "=?", new String[]{path, name});
        if (c != null) {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                c.close();
                PerformanceLogger.record("getNodeId", System.currentTimeMillis() - start);
                return id;
            }
            c.close();
        }
        PerformanceLogger.record("getNodeId", System.currentTimeMillis() - start);
        return -1;
    }

    public synchronized int getTotalFilesCount() {
        long start = System.currentTimeMillis();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=0", null);
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
        }
        PerformanceLogger.record("getTotalFilesCount", System.currentTimeMillis() - start);
        return count;
    }

    public synchronized int getTotalFoldersCount() {
        long start = System.currentTimeMillis();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NODES + " WHERE " + COL_IS_DIR + "=1", null);
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
        }
        PerformanceLogger.record("getTotalFoldersCount", System.currentTimeMillis() - start);
        return count;
    }
}
