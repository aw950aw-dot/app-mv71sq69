package com.generated.app;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class DiagnosticReporter {
    private static final List<String> logs = new ArrayList<>();
    private static final List<String> errorStackTraces = new ArrayList<>();
    private static final List<FileInfoRecord> firstFilesList = new ArrayList<>();

    private static String partitionInfo = "טרם אותרה";
    private static String chosenStartLba = "0";
    private static String backupBootSectorUsed = "לא נעשה שימוש במקטע איתחול מגובי";
    private static String fsTypeDetected = "לא ידוע";
    private static String bootSectorFields = "-";
    private static String firstDataSector = "-";
    private static String rootDirLba = "-";
    private static String rootEntriesCount = "0";
    private static int filesFoundCount = 0;
    private static int foldersFoundCount = 0;
    private static int errorCount = 0;
    private static String skippedEntriesLog = "אין פריטים שדולגו";
    private static String lunUsed = "0";
    private static String rawSectorDumpText = "טרם נסרקו סקטורים גולמיים";
    private static final List<String> rawDirEntriesDump = new ArrayList<>();
    private static final List<String> signatureSectorsDump = new ArrayList<>();

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
        errorStackTraces.clear();
        firstFilesList.clear();
        partitionInfo = "טרם אותרה";
        chosenStartLba = "0";
        backupBootSectorUsed = "לא נעשה שימוש במקטע איתחול מגובי";
        fsTypeDetected = "לא ידוע";
        bootSectorFields = "-";
        firstDataSector = "-";
        rootDirLba = "-";
        rootEntriesCount = "0";
        filesFoundCount = 0;
        foldersFoundCount = 0;
        errorCount = 0;
        skippedEntriesLog = "אין פריטים שדולגו";
        lunUsed = "0";
        rawSectorDumpText = "טרם נסרקו סקטורים גולמיים";
        rawDirEntriesDump.clear();
        signatureSectorsDump.clear();
    }

    public static synchronized void log(String msg) {
        logs.add("[" + System.currentTimeMillis() + "] " + msg);
    }

    public static synchronized void logError(Throwable t, String contextMsg) {
        errorCount++;
        logs.add("[" + System.currentTimeMillis() + "] ERROR: " + contextMsg + " - " + (t != null ? t.getMessage() : "unknown"));
        if (errorStackTraces.size() < 20 && t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            errorStackTraces.add(sw.toString());
        }
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

    public static synchronized List<String> getLogs() {
        return new ArrayList<>(logs);
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

    public static synchronized void setRootEntriesCount(String count) {
        rootEntriesCount = count;
    }

    public static synchronized void setSkippedEntriesLog(String skipped) {
        skippedEntriesLog = skipped;
    }

    public static synchronized void setLunUsed(String lun) {
        lunUsed = lun;
    }

    public static synchronized void setRawSectorDumpText(String text) {
        rawSectorDumpText = text;
    }

    public static synchronized void addRawDirEntryDump(String dump) {
        rawDirEntriesDump.add(dump);
    }

    public static synchronized void addSignatureSectorDump(String dump) {
        signatureSectorsDump.add(dump);
    }

    public static synchronized String generateFullReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== דוח אבחון סריקה מקיף (SD Rescue) ===\n\n");
        sb.append("1. LUN בשימוש: ").append(lunUsed).append("\n");
        sb.append("2. מחיצות (MBR/GPT): \n").append(partitionInfo).append("\n\n");
        sb.append("3. LBA התחלתי נבחר למערכת הקבצים: ").append(chosenStartLba).append("\n");
        sb.append("4. מידע על מקטע איתחול מגובי (Backup Boot Sector): ").append(backupBootSectorUsed).append("\n");
        sb.append("5. סוג מערכת קבצים זוהה: ").append(fsTypeDetected).append("\n\n");
        sb.append("6. שדות מקטע איתחול (Boot Sector):\n").append(bootSectorFields).append("\n\n");
        sb.append("7. סקטור נתונים ראשון מחושב: ").append(firstDataSector).append("\n");
        sb.append("8. LBA של תיקיית השורש (Root Dir): ").append(rootDirLba).append("\n");
        sb.append("9. מספר רשומות תיקיית שורש שנקראו: ").append(rootEntriesCount).append("\n");
        sb.append("10. סך קבצים שנמצאו: ").append(filesFoundCount).append("\n");
        sb.append("11. סך תיקיות שנמצאו: ").append(foldersFoundCount).append("\n");
        sb.append("12. סך שגיאות שבודדו והופקו: ").append(errorCount).append("\n\n");
        
        sb.append("13. 30 שמות קבצים ותיקיות ראשונים שנמצאו עם גודלם:\n");
        if (firstFilesList.isEmpty()) {
            sb.append("(טרם נוספו קבצים לרשימה)\n");
        } else {
            int idx = 1;
            for (FileInfoRecord rec : firstFilesList) {
                sb.append("  ").append(idx++).append(". ").append(rec.name).append(" (").append(rec.size).append(" bytes)\n");
            }
        }
        sb.append("\n");

        sb.append("14. 20 סטאק-טרייסים ראשונים של שגיאות שבודדו:\n");
        if (errorStackTraces.isEmpty()) {
            sb.append("(אין שגיאות לתצוגה)\n");
        } else {
            for (int i = 0; i < errorStackTraces.size(); i++) {
                sb.append("--- שגיאה ").append(i + 1).append(" ---\n").append(errorStackTraces.get(i)).append("\n");
            }
        }
        sb.append("\n");

        sb.append("15. פריטים שדולגו וסיבת דילוג:\n").append(skippedEntriesLog).append("\n\n");
        sb.append("16. יומן אירועים כללי:\n");
        for (String l : logs) {
            sb.append(l).append("\n");
        }

        String result = sb.toString();
        if (result.length() > 100 * 1024) {
            result = result.substring(0, 100 * 1024) + "\n\n[הדוח קוצר עקב חריגה מגבול 100KB]";
        }
        return result;
    }
}
