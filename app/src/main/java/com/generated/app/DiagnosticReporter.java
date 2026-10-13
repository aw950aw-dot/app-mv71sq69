package com.generated.app;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticReporter {
    private static final List<String> logs = new ArrayList<>();
    private static String partitionInfo = "טרם אותרה";
    private static String chosenStartLba = "0";
    private static String fsTypeDetected = "לא ידוע";
    private static String bootSectorFields = "-";
    private static String firstDataSector = "-";
    private static String rootDirLba = "-";
    private static String rootEntriesCount = "0";
    private static String filesFoldersFound = "0";
    private static String skippedEntriesLog = "אין פריטים שדולגו";
    private static String lunUsed = "0";
    private static String rawSectorDumpText = "טרם נסרקו סקטורים גולמיים";
    private static final List<String> rawDirEntriesDump = new ArrayList<>();

    public static synchronized void clear() {
        logs.clear();
        partitionInfo = "טרם אותרה";
        chosenStartLba = "0";
        fsTypeDetected = "לא ידוע";
        bootSectorFields = "-";
        firstDataSector = "-";
        rootDirLba = "-";
        rootEntriesCount = "0";
        filesFoldersFound = "0";
        skippedEntriesLog = "אין פריטים שדולגו";
        lunUsed = "0";
        rawSectorDumpText = "טרם נסרקו סקטורים גולמיים";
        rawDirEntriesDump.clear();
    }

    public static synchronized void log(String msg) {
        logs.add("[" + System.currentTimeMillis() + "] " + msg);
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

    public static synchronized void setFilesFoldersFound(String count) {
        filesFoldersFound = count;
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

    public static synchronized String generateFullReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== דוח אבחון סריקה מקיף (SD Rescue) ===\n\n");
        sb.append("1. LUN בשימוש: ").append(lunUsed).append("\n");
        sb.append("2. מחיצות (MBR/GPT): \n").append(partitionInfo).append("\n\n");
        sb.append("3. LBA התחלתי נבחר למערכת הקבצים: ").append(chosenStartLba).append("\n");
        sb.append("4. סוג מערכת קבצים זוהה: ").append(fsTypeDetected).append("\n\n");
        sb.append("5. שדות מקטע איתחול (Boot Sector):\n").append(bootSectorFields).append("\n\n");
        sb.append("6. סקטור נתונים ראשון מחושב: ").append(firstDataSector).append("\n");
        sb.append("7. LBA של תיקיית השורש (Root Dir): ").append(rootDirLba).append("\n");
        sb.append("8. מספר רשומות תיקיית שורש שנקראו: ").append(rootEntriesCount).append("\n");
        sb.append("9. סך קבצים ותיקיות שנמצאו: ").append(filesFoldersFound).append("\n\n");
        sb.append("10. דאמפ סקטורים גולמיים (LBA 0, 1, 6, 2048, 8192, 32768, 65536, 1048576):\n").append(rawSectorDumpText).append("\n\n");
        sb.append("11. פריטים שדולגו וסיבת דילוג:\n").append(skippedEntriesLog).append("\n\n");
        sb.append("12. דאמפ 32 רשומות שורש ראשונות (Hex + פיענוח):\n");
        if (rawDirEntriesDump.isEmpty()) {
            sb.append("(אין דאמפ זמין)\n");
        } else {
            for (String d : rawDirEntriesDump) {
                sb.append(d).append("\n");
            }
        }
        sb.append("\n13. יומן אירועים כללי:\n");
        for (String l : logs) {
            sb.append(l).append("\n");
        }
        return sb.toString();
    }
}
