package com.generated.app;

import android.content.Context;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiagnosticReporter {
    private static final List<String> logs = new ArrayList<>();
    private static final Map<String, Integer> errorCountsMap = new HashMap<>();
    private static final Map<String, List<String>> errorExamplesMap = new HashMap<>();
    private static final List<FileInfoRecord> firstFilesList = new ArrayList<>();

    private static String partitionInfo = "טרם אותרה";
    private static String chosenStartLba = "0";
    private static String backupBootSectorUsed = "לא נעשה שימוש במקטע איתחול מגובי";
    private static String fsTypeDetected = "לא ידוע";
    private static String bootSectorFields = "-";
    private static String firstDataSector = "-";
    private static String rootDirLba = "-";
    private static int filesFoundCount = 0;
    private static int foldersFoundCount = 0;
    private static int errorCount = 0;
    private static String lunUsed = "0";

    private static int longNameCount = 0;
    private static int shortNameCount = 0;
    private static int skippedGarbageCount = 0;
    private static int unreadableFoldersCount = 0;

    public static class FileInfoRecord {
        public final String name;
        public final long size;
        public FileInfoRecord(String name, long size) {
            this.name = name;
            this.size = size;
        }
    }

    public static synchronized void clear() {
        logs.clear();
        errorCountsMap.clear();
        errorExamplesMap.clear();
        firstFilesList.clear();
        partitionInfo = "טרם אותרה";
        chosenStartLba = "0";
        backupBootSectorUsed = "לא נעשה שימוש במקטע איתחול מגובי";
        fsTypeDetected = "לא ידוע";
        bootSectorFields = "-";
        firstDataSector = "-";
        rootDirLba = "-";
        filesFoundCount = 0;
        foldersFoundCount = 0;
        errorCount = 0;
        lunUsed = "0";
        longNameCount = 0;
        shortNameCount = 0;
        skippedGarbageCount = 0;
        unreadableFoldersCount = 0;
    }

    public static synchronized void incrementLongName() { longNameCount++; }
    public static synchronized void incrementShortName() { shortNameCount++; }
    public static synchronized void incrementSkippedGarbage() { skippedGarbageCount++; }
    public static synchronized void incrementUnreadableFolder() { unreadableFoldersCount++; }

    public static synchronized void log(String msg) {
        logs.add("[" + System.currentTimeMillis() + "] " + msg);
    }

    public static synchronized void logError(Throwable t, String contextMsg) {
        errorCount++;
        String errorKey = t != null && t.getMessage() != null ? t.getMessage() : (t != null ? t.getClass().getSimpleName() : "unknown error");
        if (contextMsg != null && !contextMsg.isEmpty()) {
            errorKey = contextMsg + ": " + errorKey;
        }

        int currentCount = errorCountsMap.containsKey(errorKey) ? errorCountsMap.get(errorKey) : 0;
        errorCountsMap.put(errorKey, currentCount + 1);

        List<String> examples = errorExamplesMap.get(errorKey);
        if (examples == null) {
            examples = new ArrayList<>();
            errorExamplesMap.put(errorKey, examples);
        }
        if (examples.size() < 20 && t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            examples.add(sw.toString());
        }

        logs.add("[" + System.currentTimeMillis() + "] ERROR: " + errorKey);
    }

    public static synchronized void addFirstFile(String name, long size) {
        if (firstFilesList.size() < 30) {
            firstFilesList.add(new FileInfoRecord(name, size));
        }
    }

    public static synchronized void setFilesFoldersCounts(int files, int folders) {
        filesFoundCount = files;
        foldersFoundCount = folders;
    }

    public static synchronized void setPartitionInfo(String info) {
        partitionInfo = info;
    }

    public static synchronized void setChosenStartLba(String lba) {
        chosenStartLba = lba;
    }

    public static synchronized void setBackupBootSectorUsed(String info) {
        backupBootSectorUsed = info;
    }

    public static synchronized void setFsTypeDetected(String type) {
        fsTypeDetected = type;
    }

    public static synchronized void setBootSectorFields(String fields) {
        bootSectorFields = fields;
    }

    public static synchronized void setFirstDataSector(String sector) {
        firstDataSector = sector;
    }

    public static synchronized void setRootDirLba(String lba) {
        rootDirLba = lba;
    }

    public static synchronized void setLunUsed(String lun) {
        lunUsed = lun;
    }

    public static synchronized String generateFullReport(Context context) {
        DatabaseHelper db = context != null ? DatabaseHelper.getInstance(context) : null;
        StringBuilder sb = new StringBuilder();
        sb.append("=== דוח אבחון סריקה מקיף (SD Rescue) ===\n\n");
        sb.append("1. LUN בשימוש: ").append(lunUsed).append("\n");
        sb.append("2. מחיצות (MBR/GPT): \n").append(partitionInfo).append("\n\n");
        sb.append("3. LBA התחלתי נבחר למערכת הקבצים: ").append(chosenStartLba).append("\n");
        sb.append("4. מידע על מקטע איתחול מגובי: ").append(backupBootSectorUsed).append("\n");
        sb.append("5. סוג מערכת קבצים זוהה: ").append(fsTypeDetected).append("\n\n");
        sb.append("6. שדות מקטע איתחול (Boot Sector):\n").append(bootSectorFields).append("\n\n");
        sb.append("7. סקטור נתונים ראשון מחושב: ").append(firstDataSector).append("\n");
        sb.append("8. LBA של תיקיית השורש (Root Dir): ").append(rootDirLba).append("\n");
        sb.append("9. סך קבצים שנמצאו: ").append(filesFoundCount).append("\n");
        sb.append("10. סך תיקיות שנמצאו: ").append(foldersFoundCount).append("\n");
        sb.append("11. קבצים עם שם ארוך (LFN): ").append(longNameCount).append("\n");
        sb.append("12. קבצים עם שם קצר (8.3): ").append(shortNameCount).append("\n");
        sb.append("13. פריטי זבל שדולגו: ").append(skippedGarbageCount).append("\n");
        sb.append("14. תיקיות לא קריאות: ").append(unreadableFoldersCount).append("\n");
        sb.append("15. סך שגיאות שבודדו והופקו: ").append(errorCount).append("\n\n");
        
        if (db != null) {
            long rootId = db.getRootId();
            int rootChildrenCount = db.getChildrenCountForNode(rootId);
            int totalNodesCount = db.getTotalFilesCount() + db.getTotalFoldersCount();
            int orphansCount = db.getOrphansCount();

            sb.append("--- סטטיסטיקת שורש (Root DB Stats) ---\n");
            sb.append("• Root Node ID: ").append(rootId).append("\n");
            sb.append("• Root Parent ID: 0\n");
            sb.append("• Root Children Count: ").append(rootChildrenCount).append("\n");
            sb.append("nodes: ").append(totalNodesCount).append(", orphans: ").append(orphansCount).append("\n\n");

            sb.append("--- פילוח לפי קטגוריות (Category Group By Stats) ---\n");
            Map<String, long[]> catStats = db.getCategoryStats();
            if (catStats.isEmpty()) {
                sb.append("(אין נתונים קטגוריאליים)\n\n");
            } else {
                for (Map.Entry<String, long[]> entry : catStats.entrySet()) {
                    String catName = entry.getKey();
                    long[] val = entry.getValue();
                    long count = val[0];
                    long size = val[1];
                    sb.append("• ").append(catName).append(": ").append(count).append(" קבצים, ").append(size).append(" בייטס\n");
                }
                sb.append("\n");
            }
        }

        sb.append("16. 30 שמות קבצים ותיקיות ראשונים שנמצאו עם גודלם:\n");
        if (firstFilesList.isEmpty()) {
            sb.append("(טרם נוספו קבצים לרשימה)\n");
        } else {
            int idx = 1;
            for (FileInfoRecord rec : firstFilesList) {
                sb.append("  ").append(idx++).append(". ").append(rec.name).append(" (").append(rec.size).append(" bytes)\n");
            }
        }
        sb.append("\n");

        sb.append("17. שגיאות מקובצות לפי סיבה ומופעים:\n");
        if (errorCountsMap.isEmpty()) {
            sb.append("(אין שגיאות לתצוגה)\n");
        } else {
            int errIdx = 1;
            for (Map.Entry<String, Integer> entry : errorCountsMap.entrySet()) {
                String cause = entry.getKey();
                int count = entry.getValue();
                sb.append("--- שגיאה ").append(errIdx++).append(" (מופעים: ").append(count).append(") ---\n");
                sb.append("סיבה: ").append(cause).append("\n\n");
            }
        }

        String result = sb.toString();
        if (result.length() > 100 * 1024) {
            result = result.substring(0, 100 * 1024) + "\n\n[הדוח קוצר עקב חריגה מגבול 100KB]";
        }
        return result;
    }
}
