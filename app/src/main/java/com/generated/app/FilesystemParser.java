package com.generated.app;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FilesystemParser {

    private static final String TAG = "FilesystemParser";
    private static String recoveryMethodUsed = "סריקת מערכת קבצים רגילה";

    public static String getRecoveryMethodUsed() {
        return recoveryMethodUsed;
    }

    public static List<RecoveredEntry> parseFilesystem(ByteSource source) throws IOException {
        long totalSize = source.getSize();
        if (totalSize < 512) {
            DiagnosticReporter.log("Total size too small (<512 bytes): " + totalSize);
            return new ArrayList<>();
        }

        DiagnosticReporter.log("Starting filesystem parsing. Total media size: " + totalSize);

        byte[] sector0 = new byte[512];
        if (source.readAt(0, sector0, 512) < 512) {
            DiagnosticReporter.log("Failed to read sector 0 (MBR/GPT).");
            return new ArrayList<>();
        }

        List<Long> candidatePartitionOffsets = new ArrayList<>();
        StringBuilder partitionInfoBuilder = new StringBuilder();

        // 1. Check MBR Partition Table
        boolean hasValidMbr = (sector0[510] == (byte) 0x55 && sector0[511] == (byte) 0xAA);
        if (hasValidMbr) {
            partitionInfoBuilder.append("זוהה MBR תקין בסקטור 0.\n");
            for (int p = 0; p < 4; p++) {
                int pOffset = 446 + p * 16;
                int pType = sector0[pOffset + 4] & 0xFF;
                ByteBuffer bb = ByteBuffer.wrap(sector0, pOffset + 8, 4);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                long startSector = bb.getInt() & 0xFFFFFFFFL;
                ByteBuffer sizeBb = ByteBuffer.wrap(sector0, pOffset + 12, 4);
                sizeBb.order(ByteOrder.LITTLE_ENDIAN);
                long pSectors = sizeBb.getInt() & 0xFFFFFFFFL;

                partitionInfoBuilder.append(" - מחיצה ").append(p + 1).append(": סוג=0x").append(Integer.toHexString(pType))
                        .append(", Start LBA=").append(startSector).append(", Sectors=").append(pSectors).append("\n");

                if (pType != 0 && pType != 0xEE) {
                    long off = startSector * 512;
                    if (off > 0 && off < totalSize && !candidatePartitionOffsets.contains(off)) {
                        candidatePartitionOffsets.add(off);
                    }
                }
            }
        } else {
            partitionInfoBuilder.append("לא נמצא MBR תקף (חתימה 0x55AA חסרה או זוהה GPT).\n");
        }

        // 2. Check GPT Backup / Protective MBR / GPT Header at LBA 1
        byte[] lba1 = new byte[512];
        if (source.readAt(512, lba1, 512) == 512) {
            String gptSig = new String(lba1, 0, 8, StandardCharsets.US_ASCII);
            if ("EFI PART".equals(gptSig)) {
                partitionInfoBuilder.append("זוהה כותרת GPT (EFI PART) בסקטור 1.\n");
                ByteBuffer gptBb = ByteBuffer.wrap(lba1);
                gptBb.order(ByteOrder.LITTLE_ENDIAN);
                long partitionEntriesLba = gptBb.getLong(72);
                long firstUsableLba = gptBb.getLong(40);
                partitionInfoBuilder.append(" - GPT First Usable LBA=").append(firstUsableLba).append("\n");
                long gptOff = firstUsableLba * 512;
                if (gptOff > 0 && gptOff < totalSize && !candidatePartitionOffsets.contains(gptOff)) {
                    candidatePartitionOffsets.add(gptOff);
                }
            }
        }

        // Always add offset 0 (raw card / unpartitioned / Superfloppy) as fallback candidate
        candidatePartitionOffsets.add(0L);

        DiagnosticReporter.setPartitionInfo(partitionInfoBuilder.toString());

        // Sequential Step-by-Step Fallback Strategy as requested:
        // Try Primary -> Backup Boot Sector -> Second FAT -> exFAT -> Specific LBA Scan (0, 2048, 8192, 32768, 65536)

        List<RecoveredEntry> entries = new ArrayList<>();
        int skippedCount = 0;
        StringBuilder skippedLogBuilder = new StringBuilder();

        for (long partOff : candidatePartitionOffsets) {
            DiagnosticReporter.log("בודק אופסט מחיצה: " + partOff + " (LBA " + (partOff / 512) + ")");
            DiagnosticReporter.setChosenStartLba(String.valueOf(partOff / 512));

            byte[] bootSector = new byte[512];
            if (source.readAt(partOff, bootSector, 512) == 512) {
                // A) Primary FAT32
                if (isFat32(bootSector)) {
                    recoveryMethodUsed = "מערכת קבצים FAT32 ראשית";
                    DiagnosticReporter.setFsTypeDetected("FAT32 Primary");
                    entries = parseFat32(source, partOff, bootSector, false, skippedLogBuilder);
                    if (!entries.isEmpty()) {
                        DiagnosticReporter.log("הצלחה ב-FAT32 ראשי באופסט " + partOff + ", נמצאו " + entries.size() + " פריטים.");
                        return entries;
                    }
                }
                // B) Primary exFAT
                if (isExFat(bootSector)) {
                    recoveryMethodUsed = "מערכת קבצים exFAT ראשית";
                    DiagnosticReporter.setFsTypeDetected("exFAT Primary");
                    entries = parseExFat(source, partOff, bootSector, false, skippedLogBuilder);
                    if (!entries.isEmpty()) {
                        DiagnosticReporter.log("הצלחה ב-exFAT ראשי באופסט " + partOff + ", נמצאו " + entries.size() + " פריטים.");
                        return entries;
                    }
                }

                // C) Backup Boot Sector FAT32 (typically sector 6)
                long backupOff1 = partOff + 6 * 512;
                if (backupOff1 + 512 <= totalSize && source.readAt(backupOff1, bootSector, 512) == 512 && isFat32(bootSector)) {
                    recoveryMethodUsed = "שחזור מקטע איתחול מגובי (Backup Boot Sector FAT32)";
                    DiagnosticReporter.setFsTypeDetected("FAT32 Backup Boot Sector");
                    entries = parseFat32(source, backupOff1, bootSector, false, skippedLogBuilder);
                    if (!entries.isEmpty()) {
                        DiagnosticReporter.log("הצלחה ב-Backup Boot Sector FAT32, נמצאו " + entries.size() + " פריטים.");
                        return entries;
                    }
                }

                // D) Second FAT table check
                recoveryMethodUsed = "טבלת FAT שנייה מגובה (Second FAT)";
                DiagnosticReporter.setFsTypeDetected("FAT32 Second FAT Table");
                entries = parseFat32(source, partOff, bootSector, true, skippedLogBuilder);
                if (!entries.isEmpty()) {
                    DiagnosticReporter.log("הצלחה בשימוש בטבלת FAT שנייה מגובה, נמצאו " + entries.size() + " פריטים.");
                    return entries;
                }

                // E) exFAT Backup sector (typically sector 12)
                long backupOffEx = partOff + 12 * 512;
                if (backupOffEx + 512 <= totalSize && source.readAt(backupOffEx, bootSector, 512) == 512 && isExFat(bootSector)) {
                    recoveryMethodUsed = "שחזור מקטע איתחול מגובי exFAT";
                    DiagnosticReporter.setFsTypeDetected("exFAT Backup Boot Sector");
                    entries = parseExFat(source, backupOffEx, bootSector, true, skippedLogBuilder);
                    if (!entries.isEmpty()) {
                        DiagnosticReporter.log("הצלחה ב-Backup Boot Sector exFAT, נמצאו " + entries.size() + " פריטים.");
                        return entries;
                    }
                }
            }
        }

        // F) Scan specific LBAs: 0, 2048, 8192, 32768, 65536
        long[] specificLbas = {0, 2048, 8192, 32768, 65536};
        for (long lba : specificLbas) {
            long off = lba * 512;
            if (off + 512 <= totalSize) {
                DiagnosticReporter.log("סריקת LBA ספציפי: " + lba);
                DiagnosticReporter.setChosenStartLba(String.valueOf(lba));
                byte[] bs = new byte[512];
                if (source.readAt(off, bs, 512) == 512) {
                    if (isFat32(bs)) {
                        recoveryMethodUsed = "איתור מקטע איתחול FAT32 ב-LBA " + lba;
                        DiagnosticReporter.setFsTypeDetected("FAT32 at LBA " + lba);
                        entries = parseFat32(source, off, bs, false, skippedLogBuilder);
                        if (!entries.isEmpty()) return entries;
                    } else if (isExFat(bs)) {
                        recoveryMethodUsed = "איתור מקטע איתחול exFAT ב-LBA " + lba;
                        DiagnosticReporter.setFsTypeDetected("exFAT at LBA " + lba);
                        entries = parseExFat(source, off, bs, false, skippedLogBuilder);
                        if (!entries.isEmpty()) return entries;
                    }
                }
            }
        }

        DiagnosticReporter.log("כל שלבי סריקת מערכת הקבצים החזירו 0 פריטים תקפים. מעבר למודל חיתוך (Carving).");
        DiagnosticReporter.setFilesFoldersFound("0 (מערכת קבצים ריקה/פגומה)");
        DiagnosticReporter.setSkippedEntriesLog(skippedLogBuilder.toString());
        return new ArrayList<>();
    }

    private static boolean isFat32(byte[] boot) {
        try {
            int bytesPerSector = ByteBuffer.wrap(boot, 11, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            if (bytesPerSector != 512 && bytesPerSector != 1024 && bytesPerSector != 2048 && bytesPerSector != 4096) {
                return false;
            }
            int sectorsPerCluster = boot[13] & 0xFF;
            if (sectorsPerCluster == 0 || (sectorsPerCluster & (sectorsPerCluster - 1)) != 0) {
                return false;
            }
            int fatSize16 = ByteBuffer.wrap(boot, 22, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            long totalSectors32 = ByteBuffer.wrap(boot, 32, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
            String fsType = new String(boot, 82, 8, StandardCharsets.US_ASCII);
            boolean hasFat32Str = fsType.contains("FAT32");
            return fatSize16 == 0 && totalSectors32 > 0 && (hasFat32Str || boot[66] == 0x29 || boot[66] == 0x28);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isExFat(byte[] boot) {
        try {
            String oem = new String(boot, 3, 8, StandardCharsets.US_ASCII);
            return "EXFAT   ".equals(oem);
        } catch (Exception e) {
            return false;
        }
    }

    public static List<RecoveredEntry> buildTree(List<RecoveredEntry> flatEntries) {
        RecoveredEntry root = new RecoveredEntry("", "/", 0, 0, null, false, true, 0, new byte[0]);
        Map<String, RecoveredEntry> dirMap = new HashMap<>();
        dirMap.put("/", root);

        for (RecoveredEntry entry : flatEntries) {
            String parentPath = entry.getPath();
            if (parentPath == null || parentPath.isEmpty()) parentPath = "/";
            if (!parentPath.startsWith("/")) parentPath = "/" + parentPath;

            RecoveredEntry parentDir = dirMap.get(parentPath);
            if (parentDir == null) {
                parentDir = ensureDirectoryPath(root, dirMap, parentPath);
            }
            parentDir.addChild(entry);
            if (entry.isDirectory()) {
                String fullDirPath = parentPath.equals("/") ? "/" + entry.getName() : parentPath + "/" + entry.getName();
                dirMap.put(fullDirPath, entry);
            }
        }
        root.updateSelectionStateFromChildren();
        List<RecoveredEntry> result = new ArrayList<>();
        result.add(root);
        return result;
    }

    private static RecoveredEntry ensureDirectoryPath(RecoveredEntry root, Map<String, RecoveredEntry> dirMap, String path) {
        String[] parts = path.split("/");
        RecoveredEntry current = root;
        StringBuilder currentPath = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            currentPath.append("/").append(part);
            String pStr = currentPath.toString();
            RecoveredEntry existing = dirMap.get(pStr);
            if (existing == null) {
                existing = new RecoveredEntry(part, current.getPath(), 0, 0, null, false, true, System.currentTimeMillis(), part.getBytes(StandardCharsets.UTF_8));
                current.addChild(existing);
                dirMap.put(pStr, existing);
            }
            current = existing;
        }
        return current;
    }

    private static List<RecoveredEntry> parseFat32(ByteSource source, long partOffset, byte[] boot, boolean useSecondFatCopy, StringBuilder skippedLog) throws IOException {
        List<RecoveredEntry> entries = new ArrayList<>();
        try {
            ByteBuffer b = ByteBuffer.wrap(boot);
            b.order(ByteOrder.LITTLE_ENDIAN);

            int bytesPerSector = b.getShort(11) & 0xFFFF;
            if (bytesPerSector <= 0 || bytesPerSector > 4096) bytesPerSector = 512;

            int sectorsPerCluster = b.get(13) & 0xFF;
            if (sectorsPerCluster <= 0) sectorsPerCluster = 8;

            int reservedSectorCount = b.getShort(14) & 0xFFFF;
            if (reservedSectorCount <= 0) reservedSectorCount = 32;

            int fatCount = b.get(16) & 0xFF;
            if (fatCount <= 0) fatCount = 2;

            long fatSize32 = b.getInt(36) & 0xFFFFFFFFL;
            if (fatSize32 <= 0) fatSize32 = 4096;

            long rootCluster = b.getInt(44) & 0xFFFFFFFFL;
            if (rootCluster < 2) rootCluster = 2;

            String bsFields = "BytesPerSector=" + bytesPerSector + ", SectorsPerCluster=" + sectorsPerCluster +
                    ", ReservedSectors=" + reservedSectorCount + ", FatCount=" + fatCount +
                    ", SectorsPerFAT=" + fatSize32 + ", RootCluster=" + rootCluster;
            DiagnosticReporter.setBootSectorFields(bsFields);

            long fatStartOffset = partOffset + (long) reservedSectorCount * bytesPerSector;
            if (useSecondFatCopy && fatCount > 1) {
                fatStartOffset += fatSize32 * bytesPerSector;
                recoveryMethodUsed += " (משתמש בטבלת FAT שנייה מגובה)";
                DiagnosticReporter.log("משתמש בטבלת FAT שנייה באופסט " + fatStartOffset);
            }

            long clusterRegionStart = partOffset + (long) reservedSectorCount * bytesPerSector + (long) fatCount * fatSize32 * bytesPerSector;
            long clusterSize = (long) sectorsPerCluster * bytesPerSector;

            DiagnosticReporter.setFirstDataSector(String.valueOf(clusterRegionStart / 512));
            long rootDirLbaVal = (clusterRegionStart + (rootCluster - 2) * clusterSize) / 512;
            DiagnosticReporter.setRootDirLba(String.valueOf(rootDirLbaVal));

            int fatBytesToRead = (int) Math.min(fatSize32 * bytesPerSector, 4 * 1024 * 1024);
            byte[] fatTable = new byte[fatBytesToRead];
            source.readAt(fatStartOffset, fatTable, fatBytesToRead);

            traverseFat32Directory(source, clusterRegionStart, clusterSize, rootCluster, fatTable, "/", entries, new HashSet<Long>(), skippedLog);

            DiagnosticReporter.setRootEntriesCount(String.valueOf(entries.size()));
            DiagnosticReporter.setFilesFoldersFound(String.valueOf(entries.size()));
        } catch (Exception e) {
            DiagnosticReporter.log("שגיאה בפענוח FAT32: " + e.getMessage());
        }
        return entries;
    }

    private static void traverseFat32Directory(ByteSource source, long clusterRegionStart, long clusterSize, long startCluster, byte[] fatTable, String currentPath, List<RecoveredEntry> entries, Set<Long> visitedClusters, StringBuilder skippedLog) throws IOException {
        long currentCluster = startCluster;
        int maxClusters = 2000;
        int dumpCounter = 0;

        List<Fat32LfnItem> lfnBuffer = new ArrayList<>();

        while (currentCluster >= 2 && currentCluster < 0x0FFFFFF8 && maxClusters-- > 0) {
            if (!visitedClusters.add(currentCluster)) break;

            long clusterOffset = clusterRegionStart + (currentCluster - 2) * clusterSize;
            if (clusterOffset < 0 || clusterOffset + clusterSize > source.getSize()) break;

            byte[] clusterData = new byte[(int) clusterSize];
            int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
            if (read <= 0) break;

            for (int i = 0; i < read - 31; i += 32) {
                if (dumpCounter < 32) {
                    StringBuilder hex = new StringBuilder();
                    for (int h = 0; h < 32; h++) {
                        hex.append(String.format("%02X ", clusterData[i + h]));
                    }
                    String interp = "Offset: 0x" + Long.toHexString(clusterOffset + i) + " | Hex: " + hex.toString();
                    DiagnosticReporter.addRawDirEntryDump(interp);
                    dumpCounter++;
                }

                byte firstByte = clusterData[i];
                if (firstByte == 0x00) break;

                if (firstByte == (byte) 0xE5) {
                    skippedLog.append("קובץ מחוק/דולג (0xE5) באופסט 0x").append(Long.toHexString(clusterOffset + i)).append("\n");
                    lfnBuffer.clear();
                    continue;
                }

                int attr = clusterData[i + 11] & 0xFF;
                if ((attr & 0x08) != 0) { // Volume ID
                    skippedLog.append("תווית נפח (Volume ID) דולגה.\n");
                    lfnBuffer.clear();
                    continue;
                }

                if ((attr & 0x0F) == 0x0F) { // LFN entry
                    lfnBuffer.add(new Fat32LfnItem(clusterData, i));
                    continue;
                }

                boolean isDir = (attr & 0x10) != 0;
                String shortName = CodepageDecoder.decode83(clusterData, i, 11);
                String resolvedName = assembleAndValidateFat32Lfn(lfnBuffer, shortName);
                lfnBuffer.clear();

                if (resolvedName.equals(".") || resolvedName.equals("..") || resolvedName.isEmpty()) {
                    continue;
                }

                int clusterHigh = ByteBuffer.wrap(clusterData, i + 20, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                int clusterLow = ByteBuffer.wrap(clusterData, i + 26, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                long fileCluster = ((long) clusterHigh << 16) | clusterLow;
                long fileLength = ByteBuffer.wrap(clusterData, i + 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;

                int timeField = ByteBuffer.wrap(clusterData, i + 22, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                int dateField = ByteBuffer.wrap(clusterData, i + 24, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                long modTime = decodeDosDateTime(dateField, timeField);

                byte[] rawNameBytes = new byte[11];
                System.arraycopy(clusterData, i, rawNameBytes, 0, 11);

                if (fileCluster >= 2 || isDir) {
                    long fileDataOffset = clusterRegionStart + (fileCluster - 2) * clusterSize;
                    FileType type = isDir ? null : FileTypeDetector.detectTypeByNameOrHeader(source, resolvedName, fileDataOffset, fileLength);
                    String fullPath = currentPath;

                    entries.add(new RecoveredEntry(resolvedName, fullPath, fileDataOffset, fileLength, type, false, isDir, modTime, rawNameBytes));

                    if (isDir && fileCluster >= 2 && fileCluster != startCluster) {
                        String subPath = currentPath.equals("/") ? "/" + resolvedName : currentPath + "/" + resolvedName;
                        traverseFat32Directory(source, clusterRegionStart, clusterSize, fileCluster, fatTable, subPath, entries, new HashSet<>(visitedClusters), skippedLog);
                    }
                }
            }

            currentCluster = getNextFat32Cluster(fatTable, currentCluster);
        }
    }

    private static class Fat32LfnItem {
        int sequenceNumber;
        byte[] data = new byte[32];
        Fat32LfnItem(byte[] buf, int offset) {
            System.arraycopy(buf, offset, data, 0, 32);
            this.sequenceNumber = data[0] & 0xFF;
        }
    }

    private static String assembleAndValidateFat32Lfn(List<Fat32LfnItem> lfnList, String shortName) {
        if (lfnList == null || lfnList.isEmpty()) return shortName;
        StringBuilder sb = new StringBuilder();
        for (int i = lfnList.size() - 1; i >= 0; i--) {
            byte[] buf = lfnList.get(i).data;
            int[] indices = {1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30};
            for (int idx : indices) {
                char c = (char) ByteBuffer.wrap(buf, idx, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                if (c == 0 || c == 0xFFFF) break;
                sb.append(c);
            }
        }
        String lfn = sb.toString().trim();
        if (lfn.isEmpty()) return shortName;
        return lfn;
    }

    private static long getNextFat32Cluster(byte[] fatTable, long cluster) {
        int index = (int) (cluster * 4);
        if (index + 4 > fatTable.length) return 0x0FFFFFFF;
        return ByteBuffer.wrap(fatTable, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x0FFFFFFF;
    }

    private static List<RecoveredEntry> parseExFat(ByteSource source, long partOffset, byte[] boot, boolean isBackup, StringBuilder skippedLog) throws IOException {
        List<RecoveredEntry> entries = new ArrayList<>();
        try {
            ByteBuffer b = ByteBuffer.wrap(boot);
            b.order(ByteOrder.LITTLE_ENDIAN);

            int bytesPerSectorShift = b.get(109) & 0xFF;
            if (bytesPerSectorShift < 9 || bytesPerSectorShift > 12) bytesPerSectorShift = 9;

            int sectorsPerClusterShift = b.get(110) & 0xFF;
            if (sectorsPerClusterShift > 24) sectorsPerClusterShift = 3;

            long clusterHeapOffset = b.getInt(72) & 0xFFFFFFFFL;
            long firstClusterOfRootDirectory = b.getInt(96) & 0xFFFFFFFFL;

            int bytesPerSector = 1 << bytesPerSectorShift;
            long clusterSize = (long) bytesPerSector * (1 << sectorsPerClusterShift);
            long clusterHeapStart = partOffset + clusterHeapOffset * bytesPerSector;

            if (isBackup) {
                recoveryMethodUsed += " (מקטע מגובי exFAT)";
            }

            DiagnosticReporter.setBootSectorFields("exFAT: BytesPerSectorShift=" + bytesPerSectorShift + ", ClusterHeapOffset=" + clusterHeapOffset);
            DiagnosticReporter.setFirstDataSector(String.valueOf(clusterHeapStart / 512));

            traverseExFatDirectory(source, clusterHeapStart, clusterSize, firstClusterOfRootDirectory, "/", entries, skippedLog);
            DiagnosticReporter.setRootEntriesCount(String.valueOf(entries.size()));
            DiagnosticReporter.setFilesFoldersFound(String.valueOf(entries.size()));
        } catch (Exception e) {
            DiagnosticReporter.log("שגיאה בפענוח exFAT: " + e.getMessage());
        }
        return entries;
    }

    private static void traverseExFatDirectory(ByteSource source, long clusterHeapStart, long clusterSize, long startCluster, String currentPath, List<RecoveredEntry> entries, StringBuilder skippedLog) throws IOException {
        long currentCluster = startCluster;
        int maxClusters = 500;
        int dumpCounter = 0;

        while (currentCluster >= 2 && maxClusters-- > 0) {
            long clusterOffset = clusterHeapStart + (currentCluster - 2) * clusterSize;
            if (clusterOffset < 0 || clusterOffset + clusterSize > source.getSize()) break;

            byte[] clusterData = new byte[(int) clusterSize];
            int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
            if (read <= 0) break;

            for (int i = 0; i < read - 31; i += 32) {
                if (dumpCounter < 32) {
                    StringBuilder hex = new StringBuilder();
                    for (int h = 0; h < 32; h++) {
                        hex.append(String.format("%02X ", clusterData[i + h]));
                    }
                    DiagnosticReporter.addRawDirEntryDump("exFAT Offset: 0x" + Long.toHexString(clusterOffset + i) + " | Hex: " + hex.toString());
                    dumpCounter++;
                }

                int entryType = clusterData[i] & 0xFF;
                if (entryType == 0x00) break;
                if ((entryType & 0x80) == 0) {
                    skippedLog.append("exFAT entry דולג (סוג 0x").append(Integer.toHexString(entryType)).append(")\n");
                    continue;
                }
                if (entryType != 0x85) continue;

                if (i + 64 > read) break;
                int secondaryCount = clusterData[i + 1] & 0xFF;
                int attr = ByteBuffer.wrap(clusterData, i + 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                boolean isDir = (attr & 0x10) != 0;

                long startingCluster = 0;
                long dataLength = 0;
                StringBuilder fileName = new StringBuilder();

                for (int s = 1; s <= secondaryCount && (i + s * 32 + 31) < read; s++) {
                    int secType = clusterData[i + s * 32] & 0xFF;
                    if (secType == 0xC0) {
                        startingCluster = ByteBuffer.wrap(clusterData, i + s * 32 + 20, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
                        dataLength = ByteBuffer.wrap(clusterData, i + s * 32 + 24, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                    } else if (secType == 0xC1) {
                        for (int n = 0; n < 15 && (i + s * 32 + 4 + n * 2 + 2) <= read; n++) {
                            char c = (char) ByteBuffer.wrap(clusterData, i + s * 32 + 4 + n * 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                            if (c == 0) break;
                            fileName.append(c);
                        }
                    }
                }

                String name = fileName.length() > 0 ? fileName.toString() : "unnamed";
                long fileDataOffset = clusterHeapStart + (startingCluster - 2) * clusterSize;
                FileType type = isDir ? null : FileTypeDetector.detectTypeByNameOrHeader(source, name, fileDataOffset, dataLength);
                byte[] rawNameBytes = name.getBytes(StandardCharsets.UTF_16LE);

                entries.add(new RecoveredEntry(name, currentPath, fileDataOffset, dataLength, type, false, isDir, System.currentTimeMillis(), rawNameBytes));
            }
            break;
        }
    }

    private static long decodeDosDateTime(int date, int time) {
        int year = ((date >> 9) & 0x7F) + 1980;
        int month = ((date >> 5) & 0x0F) - 1;
        int day = date & 0x1F;
        int hour = (time >> 11) & 0x1F;
        int minute = (time >> 5) & 0x3F;
        int second = (time & 0x1F) * 2;

        Calendar cal = Calendar.getInstance();
        try {
            cal.set(year, Math.max(0, Math.min(11, month)), Math.max(1, Math.min(31, day)),
                    Math.max(0, Math.min(23, hour)), Math.max(0, Math.min(59, minute)), Math.max(0, Math.min(59, second)));
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
