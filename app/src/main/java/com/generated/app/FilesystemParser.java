package com.generated.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FilesystemParser {

    private static String recoveryMethodUsed = "טרם אותרה מערכת קבצים";

    public static String getRecoveryMethodUsed() {
        return recoveryMethodUsed;
    }

    public static interface ParserCallback {
        void onBatchReady(List<RecoveredEntry> batch);
        void onProgress(String currentFolder);
    }

    public static List<RecoveredEntry> parseFilesystem(ByteSource source, ParserCallback callback) throws IOException {
        long totalSize = source.getSize();
        if (totalSize < 512) {
            DiagnosticReporter.log("Total size too small (<512 bytes): " + totalSize);
            return new ArrayList<>();
        }

        DiagnosticReporter.log("Starting strict filesystem parsing. Total media size: " + totalSize);
        generateAndStoreRawSectorDumps(source, totalSize);

        byte[] sector0 = new byte[512];
        if (source.readAt(0, sector0, 512) < 512) {
            DiagnosticReporter.log("Failed to read sector 0.");
            return new ArrayList<>();
        }

        List<Long> candidatePartitionOffsets = new ArrayList<>();
        StringBuilder partitionInfoBuilder = new StringBuilder();

        boolean hasValidMbrSig = (sector0[510] == (byte) 0x55 && sector0[511] == (byte) 0xAA);
        if (hasValidMbrSig) {
            partitionInfoBuilder.append("זוהה MBR עם חתימה 0x55AA בסקטור 0.\n");
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

                if (pType != 0 && pType != 0xEE && startSector > 0 && pSectors > 0) {
                    long off = startSector * 512;
                    if (off < totalSize && !candidatePartitionOffsets.contains(off)) {
                        candidatePartitionOffsets.add(off);
                    }
                }
            }
        }

        scanBootSectorsFirst256MB(source, totalSize, candidatePartitionOffsets, partitionInfoBuilder);
        DiagnosticReporter.setPartitionInfo(partitionInfoBuilder.toString());

        List<RecoveredEntry> bestEntries = new ArrayList<>();
        String bestMethod = "טרם אותרה מערכת קבצים";
        int maxFilesFound = 0;
        StringBuilder skippedLogBuilder = new StringBuilder();

        for (long partOff : candidatePartitionOffsets) {
            DiagnosticReporter.setChosenStartLba(String.valueOf(partOff / 512));
            List<RecoveredEntry> candidateEntries = new ArrayList<>();

            try {
                byte[] bootSector = new byte[512];
                if (source.readAt(partOff, bootSector, 512) == 512) {
                    if (isValidFat32(bootSector)) {
                        candidateEntries = parseFat32(source, partOff, bootSector, skippedLogBuilder, callback);
                        if (!candidateEntries.isEmpty()) {
                            if (candidateEntries.size() > maxFilesFound) {
                                maxFilesFound = candidateEntries.size();
                                bestEntries = candidateEntries;
                                bestMethod = "מערכת קבצים FAT32 (LBA " + (partOff / 512) + ")";
                            }
                            continue;
                        }
                    }
                }

                long backupFat32Off = partOff + 6 * 512;
                if (backupFat32Off + 512 <= totalSize && source.readAt(backupFat32Off, bootSector, 512) == 512 && isValidFat32(bootSector)) {
                    DiagnosticReporter.setBackupBootSectorUsed("זוהה מקטע איתחול מגובי ב-LBA " + (backupFat32Off / 512));
                    candidateEntries = parseFat32(source, partOff, bootSector, skippedLogBuilder, callback);
                    if (!candidateEntries.isEmpty()) {
                        if (candidateEntries.size() > maxFilesFound) {
                            maxFilesFound = candidateEntries.size();
                            bestEntries = candidateEntries;
                            bestMethod = "מערכת קבצים FAT32 עם מקטע איתחול מגובי (LBA " + (partOff / 512) + ")";
                        }
                        continue;
                    }
                }

                if (source.readAt(partOff, bootSector, 512) == 512 && isValidExFat(bootSector)) {
                    candidateEntries = parseExFat(source, partOff, bootSector, skippedLogBuilder, callback);
                    if (!candidateEntries.isEmpty()) {
                        if (candidateEntries.size() > maxFilesFound) {
                            maxFilesFound = candidateEntries.size();
                            bestEntries = candidateEntries;
                            bestMethod = "מערכת קבצים exFAT (LBA " + (partOff / 512) + ")";
                        }
                        continue;
                    }
                }

            } catch (Exception e) {
                DiagnosticReporter.logError(e, "Error parsing candidate partition offset " + partOff);
            }
        }

        if (!bestEntries.isEmpty()) {
            recoveryMethodUsed = bestMethod;
            DiagnosticReporter.setFsTypeDetected(bestMethod);
            return bestEntries;
        }

        DiagnosticReporter.log("כל שלבי סריקת מערכת הקבצים החזירו 0 פריטים תקפים.");
        DiagnosticReporter.setSkippedEntriesLog(skippedLogBuilder.toString());
        return new ArrayList<>();
    }

    private static void scanBootSectorsFirst256MB(ByteSource source, long totalSize, List<Long> candidateOffsets, StringBuilder partitionInfo) {
        long limit = Math.min(totalSize, 256L * 1024L * 1024L);
        byte[] readBuffer = new byte[65536];
        long currentOffset = 0;

        while (currentOffset < limit) {
            int toRead = (int) Math.min(readBuffer.length, limit - currentOffset);
            try {
                int read = source.readAt(currentOffset, readBuffer, toRead);
                if (read <= 0) break;

                for (int i = 0; i <= read - 512; i += 512) {
                    long absoluteOffset = currentOffset + i;
                    if (readBuffer[i + 510] != (byte) 0x55 || readBuffer[i + 511] != (byte) 0xAA) {
                        continue;
                    }

                    boolean isExFatHit = "EXFAT   ".equals(new String(readBuffer, i + 3, 8, StandardCharsets.US_ASCII));
                    boolean isFat32Hit = "FAT32   ".equals(new String(readBuffer, i + 82, 8, StandardCharsets.US_ASCII));

                    if (isExFatHit || isFat32Hit) {
                        if (!candidateOffsets.contains(absoluteOffset)) {
                            candidateOffsets.add(absoluteOffset);
                            partitionInfo.append("זוהה מקטע איתחול ב-LBA ").append(absoluteOffset / 512).append("\n");
                        }
                    }
                }
            } catch (Exception ignored) {}
            currentOffset += readBuffer.length;
        }
    }

    private static boolean isValidExFat(byte[] boot) {
        try {
            if (boot[510] != (byte) 0x55 || boot[511] != (byte) 0xAA) return false;
            String oem = new String(boot, 3, 8, StandardCharsets.US_ASCII);
            if (!"EXFAT   ".equals(oem)) return false;
            ByteBuffer b = ByteBuffer.wrap(boot);
            b.order(ByteOrder.LITTLE_ENDIAN);
            int bytesPerSectorShift = b.get(109) & 0xFF;
            long clusterCount = b.getInt(92) & 0xFFFFFFFFL;
            long rootDirCluster = b.getInt(96) & 0xFFFFFFFFL;
            return bytesPerSectorShift >= 9 && bytesPerSectorShift <= 12 && rootDirCluster >= 2 && rootDirCluster <= clusterCount + 1;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isValidFat32(byte[] boot) {
        try {
            if (boot[510] != (byte) 0x55 || boot[511] != (byte) 0xAA) return false;
            ByteBuffer b = ByteBuffer.wrap(boot);
            b.order(ByteOrder.LITTLE_ENDIAN);
            int bytesPerSector = b.getShort(11) & 0xFFFF;
            int sectorsPerCluster = b.get(13) & 0xFF;
            int reserved = b.getShort(14) & 0xFFFF;
            int numFATs = b.get(16) & 0xFF;
            long sectorsPerFAT = b.getInt(36) & 0xFFFFFFFFL;
            long rootCluster = b.getInt(44) & 0xFFFFFFFFL;

            return (bytesPerSector == 512 || bytesPerSector == 1024 || bytesPerSector == 2048 || bytesPerSector == 4096) &&
                    sectorsPerCluster > 0 && reserved > 0 && numFATs > 0 && sectorsPerFAT > 0 && rootCluster >= 2;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<RecoveredEntry> parseFat32(ByteSource source, long partOffset, byte[] boot, StringBuilder skippedLog, ParserCallback callback) throws IOException {
        List<RecoveredEntry> entries = new ArrayList<>();
        List<RecoveredEntry> batch = new ArrayList<>();
        try {
            ByteBuffer b = ByteBuffer.wrap(boot);
            b.order(ByteOrder.LITTLE_ENDIAN);

            int bytesPerSector = b.getShort(11) & 0xFFFF;
            int sectorsPerCluster = b.get(13) & 0xFF;
            int reserved = b.getShort(14) & 0xFFFF;
            int numFATs = b.get(16) & 0xFF;
            long sectorsPerFAT = b.getInt(36) & 0xFFFFFFFFL;
            long rootCluster = b.getInt(44) & 0xFFFF;

            long fatStartOffset = partOffset + (long) reserved * bytesPerSector;
            long clusterRegionStart = partOffset + (long) reserved * bytesPerSector + (long) numFATs * sectorsPerFAT * bytesPerSector;
            long clusterSize = (long) sectorsPerCluster * bytesPerSector;

            DiagnosticReporter.setBootSectorFields("BytesPerSector=" + bytesPerSector + ", SectorsPerCluster=" + sectorsPerCluster + ", ReservedSectors=" + reserved + ", NumFATs=" + numFATs + ", SectorsPerFAT=" + sectorsPerFAT + ", RootCluster=" + rootCluster);
            DiagnosticReporter.setFirstDataSector(String.valueOf(clusterRegionStart / 512));
            DiagnosticReporter.setRootDirLba(String.valueOf((clusterRegionStart + (rootCluster - 2) * clusterSize) / 512));
            DiagnosticReporter.setRootEntriesCount(String.valueOf(sectorsPerCluster * bytesPerSector / 32));

            int fatBytesToRead = (int) Math.min(sectorsPerFAT * bytesPerSector, 8 * 1024 * 1024);
            byte[] fatTable = new byte[fatBytesToRead];
            source.readAt(fatStartOffset, fatTable, fatBytesToRead);

            traverseFat32DirectorySafe(source, clusterRegionStart, clusterSize, rootCluster, fatTable, "/", entries, batch, new HashSet<Long>(), skippedLog, callback);
            if (!batch.isEmpty() && callback != null) {
                callback.onBatchReady(new ArrayList<>(batch));
                batch.clear();
            }
        } catch (Exception e) {
            DiagnosticReporter.logError(e, "FAT32 parsing exception");
        }
        return entries;
    }

    private static void traverseFat32DirectorySafe(ByteSource source, long clusterRegionStart, long clusterSize, long startCluster, byte[] fatTable, String currentPath, List<RecoveredEntry> entries, List<RecoveredEntry> batch, Set<Long> visitedClusters, StringBuilder skippedLog, ParserCallback callback) {
        long currentCluster = startCluster;
        int maxClusters = 5000;
        List<Fat32LfnItem> lfnBuffer = new ArrayList<>();

        if (callback != null) {
            callback.onProgress(currentPath);
        }

        while (currentCluster >= 2 && currentCluster < 0x0FFFFFF8 && maxClusters-- > 0) {
            if (!visitedClusters.add(currentCluster)) break;

            try {
                long clusterOffset = clusterRegionStart + (currentCluster - 2) * clusterSize;
                if (clusterOffset < 0 || clusterOffset + clusterSize > source.getSize()) break;

                byte[] clusterData = new byte[(int) clusterSize];
                int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
                if (read <= 0) break;

                for (int i = 0; i < read - 31; i += 32) {
                    try {
                        byte firstByte = clusterData[i];
                        if (firstByte == 0x00) break;
                        if (firstByte == (byte) 0xE5) {
                            lfnBuffer.clear();
                            continue;
                        }

                        int attr = clusterData[i + 11] & 0xFF;
                        if ((attr & 0x08) != 0) {
                            lfnBuffer.clear();
                            continue;
                        }

                        if ((attr & 0x0F) == 0x0F) {
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

                        long fileDataOffset = clusterRegionStart + (fileCluster - 2) * clusterSize;
                        FileType type = isDir ? FileType.UNKNOWN : FileTypeDetector.detectTypeByNameOrHeader(source, resolvedName, fileDataOffset, fileLength);

                        RecoveredEntry entry = new RecoveredEntry(resolvedName, currentPath, fileDataOffset, fileLength, type, false, isDir, modTime, rawNameBytes);
                        entries.add(entry);
                        batch.add(entry);

                        if (batch.size() >= 1000) {
                            if (callback != null) {
                                callback.onBatchReady(new ArrayList<>(batch));
                            }
                            batch.clear();
                        }

                        DiagnosticReporter.addFirstFile(resolvedName, fileLength);

                        if (isDir && fileCluster >= 2 && fileCluster != startCluster) {
                            String subPath = currentPath.equals("/") ? "/" + resolvedName : currentPath + "/" + resolvedName;
                            traverseFat32DirectorySafe(source, clusterRegionStart, clusterSize, fileCluster, fatTable, subPath, entries, batch, new HashSet<>(visitedClusters), skippedLog, callback);
                        }
                    } catch (Exception entryEx) {
                        DiagnosticReporter.logError(entryEx, "Isolated failure processing directory entry in FAT32");
                    }
                }
            } catch (Exception clusterEx) {
                DiagnosticReporter.logError(clusterEx, "Isolated failure processing cluster in FAT32");
            }

            currentCluster = getNextFat32Cluster(fatTable, currentCluster);
        }
    }

    private static List<RecoveredEntry> parseExFat(ByteSource source, long partOffset, byte[] boot, StringBuilder skippedLog, ParserCallback callback) throws IOException {
        List<RecoveredEntry> entries = new ArrayList<>();
        List<RecoveredEntry> batch = new ArrayList<>();
        try {
            ByteBuffer b = ByteBuffer.wrap(boot);
            b.order(ByteOrder.LITTLE_ENDIAN);

            long fatOffset = b.getInt(80) & 0xFFFFFFFFL;
            long fatLength = b.getInt(84) & 0xFFFFFFFFL;
            long clusterHeapOffset = b.getInt(88) & 0xFFFFFFFFL;
            long clusterCount = b.getInt(92) & 0xFFFFFFFFL;
            long rootDirCluster = b.getInt(96) & 0xFFFFFFFFL;
            int bytesPerSectorShift = b.get(109) & 0xFF;
            int sectorsPerClusterShift = b.get(110) & 0xFF;

            int bytesPerSector = 1 << bytesPerSectorShift;
            long clusterSize = (long) bytesPerSector * (1 << sectorsPerClusterShift);
            long clusterHeapStart = partOffset + clusterHeapOffset * bytesPerSector;

            DiagnosticReporter.setBootSectorFields("exFAT PartitionOffset=" + partOffset + ", FatOffset=" + fatOffset + ", ClusterHeapOffset=" + clusterHeapOffset + ", RootDirCluster=" + rootDirCluster);
            DiagnosticReporter.setFirstDataSector(String.valueOf(clusterHeapStart / 512));

            long fatStartOffset = partOffset + fatOffset * bytesPerSector;
            int fatBytesToRead = (int) Math.min(fatLength * bytesPerSector, 8 * 1024 * 1024);
            byte[] fatTable = new byte[fatBytesToRead];
            source.readAt(fatStartOffset, fatTable, fatBytesToRead);

            traverseExFatDirectorySafe(source, clusterHeapStart, clusterSize, rootDirCluster, fatTable, clusterCount, "/", entries, batch, new HashSet<Long>(), skippedLog, callback);
            if (!batch.isEmpty() && callback != null) {
                callback.onBatchReady(new ArrayList<>(batch));
                batch.clear();
            }
        } catch (Exception e) {
            DiagnosticReporter.logError(e, "exFAT parsing exception");
        }
        return entries;
    }

    private static void traverseExFatDirectorySafe(ByteSource source, long clusterHeapStart, long clusterSize, long startCluster, byte[] fatTable, long clusterCount, String currentPath, List<RecoveredEntry> entries, List<RecoveredEntry> batch, Set<Long> visitedClusters, StringBuilder skippedLog, ParserCallback callback) {
        long currentCluster = startCluster;
        int maxClusters = 5000;

        if (callback != null) {
            callback.onProgress(currentPath);
        }

        while (currentCluster >= 2 && currentCluster < 0xFFFFFFF8 && maxClusters-- > 0) {
            if (!visitedClusters.add(currentCluster)) break;

            try {
                long clusterOffset = clusterHeapStart + (currentCluster - 2) * clusterSize;
                if (clusterOffset < 0 || clusterOffset + clusterSize > source.getSize()) break;

                byte[] clusterData = new byte[(int) clusterSize];
                int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
                if (read <= 0) break;

                for (int i = 0; i < read - 31; i += 32) {
                    try {
                        int entryType = clusterData[i] & 0xFF;
                        if (entryType == 0x00) break;
                        if ((entryType & 0x80) == 0) continue;
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

                        String name = fileName.length() > 0 ? fileName.toString().trim() : "unnamed";
                        if (name.isEmpty() || name.equals(".") || name.equals("..")) continue;

                        long fileDataOffset = clusterHeapStart + (startingCluster - 2) * clusterSize;
                        FileType type = isDir ? FileType.UNKNOWN : FileTypeDetector.detectTypeByNameOrHeader(source, name, fileDataOffset, dataLength);
                        byte[] rawNameBytes = name.getBytes(StandardCharsets.UTF_16LE);

                        RecoveredEntry entry = new RecoveredEntry(name, currentPath, fileDataOffset, dataLength, type, false, isDir, System.currentTimeMillis(), rawNameBytes);
                        entries.add(entry);
                        batch.add(entry);

                        if (batch.size() >= 1000) {
                            if (callback != null) {
                                callback.onBatchReady(new ArrayList<>(batch));
                            }
                            batch.clear();
                        }

                        DiagnosticReporter.addFirstFile(name, dataLength);

                        if (isDir && startingCluster >= 2) {
                            String subPath = currentPath.equals("/") ? "/" + name : currentPath + "/" + name;
                            traverseExFatDirectorySafe(source, clusterHeapStart, clusterSize, startingCluster, fatTable, clusterCount, subPath, entries, batch, new HashSet<>(visitedClusters), skippedLog, callback);
                        }
                    } catch (Exception ex) {
                        DiagnosticReporter.logError(ex, "Isolated exFAT entry error");
                    }
                }
            } catch (Exception ex) {
                DiagnosticReporter.logError(ex, "Isolated exFAT cluster error");
            }

            currentCluster = getNextExFatCluster(fatTable, currentCluster);
        }
    }

    private static class Fat32LfnItem {
        byte[] data = new byte[32];
        Fat32LfnItem(byte[] buf, int offset) {
            System.arraycopy(buf, offset, data, 0, 32);
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
        return lfn.isEmpty() ? shortName : lfn;
    }

    private static long getNextFat32Cluster(byte[] fatTable, long cluster) {
        int index = (int) (cluster * 4);
        if (index + 4 > fatTable.length) return 0x0FFFFFFF;
        return ByteBuffer.wrap(fatTable, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x0FFFFFFF;
    }

    private static long getNextExFatCluster(byte[] fatTable, long cluster) {
        int index = (int) (cluster * 4);
        if (index + 4 > fatTable.length) return 0xFFFFFFF8L;
        return ByteBuffer.wrap(fatTable, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    }

    private static void generateAndStoreRawSectorDumps(ByteSource source, long totalSize) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LBA 0 (Full 512 Bytes Raw Hex) ===\n");
        try {
            byte[] lba0Buf = new byte[512];
            int read = source.readAt(0, lba0Buf, 512);
            if (read > 0) {
                for (int i = 0; i < read; i += 16) {
                    sb.append(String.format("0x%04X: ", i));
                    for (int j = 0; j < 16; j++) {
                        if (i + j < read) {
                            sb.append(String.format("%02X ", lba0Buf[i + j] & 0xFF));
                        } else {
                            sb.append("   ");
                        }
                    }
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            sb.append("(שגיאה בקריאת LBA 0)\n");
        }
        DiagnosticReporter.setRawSectorDumpText(sb.toString());
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
