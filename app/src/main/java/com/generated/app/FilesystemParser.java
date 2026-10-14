package com.generated.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class FilesystemParser {

    private static String recoveryMethodUsed = "טרם אותרה מערכת קבצים";

    public static String getRecoveryMethodUsed() {
        return recoveryMethodUsed;
    }

    public interface ParserCallback {
        void onBatchReady(List<RecoveredEntry> batch);
        void onProgress(String currentFolder);
    }

    private static Charset getShortCharset() {
        try {
            String cp = CodepageDecoder.getCodepage();
            if (cp != null && !cp.isEmpty()) {
                return Charset.forName(cp);
            }
        } catch (Exception ignored) {}
        try {
            return Charset.forName("IBM862");
        } catch (Exception ignored) {}
        try {
            return Charset.forName("windows-1255");
        } catch (Exception ignored) {}
        return StandardCharsets.ISO_8859_1;
    }

    public static List<RecoveredEntry> parseFilesystem(ByteSource source, ParserCallback callback) throws IOException {
        long totalSize = source.getSize();
        if (totalSize < 512) {
            return new ArrayList<>();
        }

        byte[] sector0 = new byte[512];
        if (source.readAt(0, sector0, 512) < 512) {
            return new ArrayList<>();
        }

        long primaryPartitionStartLba = 0;
        boolean mbrFound = false;
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

                if (pType != 0 && pType != 0xEE && startSector > 0 && pSectors > 0) {
                    primaryPartitionStartLba = startSector;
                    mbrFound = true;
                    partitionInfoBuilder.append("נמצאה מחיצה ב-LBA ").append(startSector).append(" (סוג 0x").append(Integer.toHexString(pType)).append(")\n");
                    break;
                }
            }
        }

        if (!mbrFound) {
            primaryPartitionStartLba = 0;
            partitionInfoBuilder.append("לא נמצאה טבלת מחיצות MBR תקפה, ברירת מחדל LBA 0.\n");
        }

        DiagnosticReporter.setPartitionInfo(partitionInfoBuilder.toString());

        long volumeStartOffset = primaryPartitionStartLba * 512;
        DiagnosticReporter.setChosenStartLba(String.valueOf(primaryPartitionStartLba));

        List<RecoveredEntry> bestEntries = new ArrayList<>();
        String bestMethod = "טרם אותרה מערכת קבצים";

        try {
            byte[] bootSector = new byte[512];
            boolean bootReadOk = false;

            if (volumeStartOffset < totalSize && source.readAt(volumeStartOffset, bootSector, 512) == 512 && isValidFat32(bootSector)) {
                bootReadOk = true;
                DiagnosticReporter.setBackupBootSectorUsed("נעשה שימוש במקטע איתחול ראשי ב-LBA " + primaryPartitionStartLba);
            } else {
                long backupOff = volumeStartOffset + 6 * 512;
                if (backupOff < totalSize && source.readAt(backupOff, bootSector, 512) == 512 && isValidFat32(bootSector)) {
                    bootReadOk = true;
                    DiagnosticReporter.setBackupBootSectorUsed("זוהה מקטע איתחול מגובי ב-LBA " + (backupOff / 512) + " (BPB נקרא ממנו, אך ה-Volume Start נשאר LBA " + primaryPartitionStartLba + ")");
                }
            }

            if (bootReadOk) {
                bestEntries = parseFat32(source, volumeStartOffset, bootSector, totalSize, callback);
                if (!bestEntries.isEmpty()) {
                    bestMethod = "מערכת קבצים FAT32 (Volume Start LBA " + primaryPartitionStartLba + ")";
                }
            }
        } catch (Exception e) {
            DiagnosticReporter.logError(e, "Error parsing volume at start LBA " + primaryPartitionStartLba);
        }

        if (!bestEntries.isEmpty()) {
            recoveryMethodUsed = bestMethod;
            DiagnosticReporter.setFsTypeDetected(bestMethod);
            return bestEntries;
        }

        return new ArrayList<>();
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
            long rootCluster = b.getInt(44) & 0xFFFF;

            return (bytesPerSector == 512 || bytesPerSector == 1024 || bytesPerSector == 2048 || bytesPerSector == 4096) &&
                    sectorsPerCluster > 0 && reserved > 0 && numFATs > 0 && sectorsPerFAT > 0 && rootCluster >= 2;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<RecoveredEntry> parseFat32(ByteSource source, long partOffset, byte[] boot, long totalSize, ParserCallback callback) throws IOException {
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
            long clusterRegionStart = partOffset + ((long) reserved + (long) numFATs * sectorsPerFAT) * bytesPerSector;
            long clusterSize = (long) sectorsPerCluster * bytesPerSector;

            long volumeStartLba = partOffset / 512;
            long firstDataSectorCalculated = volumeStartLba + reserved + numFATs * sectorsPerFAT;
            long cluster2Offset = clusterRegionStart;

            DiagnosticReporter.setBootSectorFields("BytesPerSector=" + bytesPerSector + ", SectorsPerCluster=" + sectorsPerCluster + ", RootCluster=" + rootCluster + ", Reserved=" + reserved + ", NumFATs=" + numFATs + ", SectorsPerFAT=" + sectorsPerFAT);
            DiagnosticReporter.setFirstDataSector(String.valueOf(firstDataSectorCalculated));
            DiagnosticReporter.setRootDirLba(String.valueOf((clusterRegionStart + (rootCluster - 2) * clusterSize) / 512));

            // Self-Check verification per requirement 3
            boolean selfCheckCluster2Ok = (cluster2Offset == clusterRegionStart);
            StringBuilder selfCheckLog = new StringBuilder();
            selfCheckLog.append("=== בדיקה עצמית של מערכת הקבצים (Self-Check) ===\n");
            selfCheckLog.append("• Volume Start LBA: ").append(volumeStartLba).append("\n");
            selfCheckLog.append("• Calculated First Data Sector: ").append(firstDataSectorCalculated).append("\n");
            selfCheckLog.append("• Cluster 2 offset matches Region Start: ").append(selfCheckCluster2Ok ? "OK (" + cluster2Offset + ")" : "FAIL").append("\n");
            DiagnosticReporter.log(selfCheckLog.toString());

            int fatBytesToRead = (int) Math.min(sectorsPerFAT * bytesPerSector, 16 * 1024 * 1024);
            byte[] fatTable = new byte[fatBytesToRead];
            source.readAt(fatStartOffset, fatTable, fatBytesToRead);

            AtomicLong nextId = new AtomicLong(1);

            long rootId = nextId.getAndIncrement();
            RecoveredEntry rootEntry = new RecoveredEntry(rootId, 0, "", "/", 0, 0, FileType.UNKNOWN, false, true, System.currentTimeMillis(), new byte[0], rootCluster);
            entries.add(rootEntry);
            batch.add(rootEntry);

            class WorkItem {
                final long cluster;
                final long parentNodeId;
                final String path;
                WorkItem(long cluster, long parentNodeId, String path) {
                    this.cluster = cluster;
                    this.parentNodeId = parentNodeId;
                    this.path = path;
                }
            }

            List<WorkItem> queue = new ArrayList<>();
            queue.add(new WorkItem(rootCluster, rootId, "/"));
            Set<Long> visitedClusters = new HashSet<>();

            while (!queue.isEmpty()) {
                WorkItem work = queue.remove(0);
                long currentCluster = work.cluster;
                long parentNodeId = work.parentNodeId;
                String currentPath = work.path;

                if (callback != null) {
                    callback.onProgress(currentPath);
                }

                int maxClusters = 5000;
                Fat32EntryParser entryParser = new Fat32EntryParser(getShortCharset());

                while (currentCluster >= 2 && currentCluster < 0x0FFFFFF8 && maxClusters-- > 0 && !entryParser.isEnded()) {
                    if (!visitedClusters.add(currentCluster)) break;

                    try {
                        long clusterOffset = clusterRegionStart + (currentCluster - 2) * clusterSize;
                        if (clusterOffset < 0 || clusterOffset + clusterSize > source.getSize()) break;

                        byte[] clusterData = new byte[(int) clusterSize];
                        int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
                        if (read <= 0) break;

                        for (int i = 0; i < read - 31; i += 32) {
                            if (entryParser.isEnded()) break;

                            List<byte[]> rawLfnBuffer = new ArrayList<>();
                            int peekOff = i;
                            while (peekOff >= 32) {
                                int attrCheck = clusterData[peekOff - 32 + 11] & 0xFF;
                                if ((attrCheck & 0x0F) == 0x0F) {
                                    byte[] lfnEntry = new byte[32];
                                    System.arraycopy(clusterData, peekOff - 32, lfnEntry, 0, 32);
                                    rawLfnBuffer.add(0, lfnEntry);
                                    peekOff -= 32;
                                } else {
                                    break;
                                }
                            }

                            int attrBits = clusterData[i + 11] & 0xFF;
                            Fat32EntryParser.Item item = entryParser.feed(clusterData, i);
                            if (item == null) {
                                continue;
                            }

                            if (item.hadLfn) {
                                DiagnosticReporter.incrementLongName();
                            } else {
                                DiagnosticReporter.incrementShortName();
                            }

                            if (isGarbageEntry(item, (byte) attrBits, totalSize)) {
                                DiagnosticReporter.incrementSkippedGarbage();
                                continue;
                            }

                            if (item.name.equals(".") || item.name.equals("..")) {
                                continue;
                            }

                            boolean isUnreadableFolder = false;
                            String unreadableReason = null;
                            if (item.isDir && item.firstCluster >= 2) {
                                long subClusterOffset = clusterRegionStart + (item.firstCluster - 2) * clusterSize;
                                if (subClusterOffset >= 0 && subClusterOffset + 32 <= source.getSize()) {
                                    byte[] dotEntryBuf = new byte[32];
                                    if (source.readAt(subClusterOffset, dotEntryBuf, 32) == 32) {
                                        String dotName = new String(dotEntryBuf, 0, 11, StandardCharsets.US_ASCII).trim();
                                        int dotAttr = dotEntryBuf[11] & 0xFF;
                                        long dotCluster = ((long) ByteBuffer.wrap(dotEntryBuf, 20, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() << 16) |
                                                (ByteBuffer.wrap(dotEntryBuf, 26, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF);

                                        StringBuilder first11Hex = new StringBuilder();
                                        for (int bIdx = 0; bIdx < 11 && bIdx < dotEntryBuf.length; bIdx++) {
                                            first11Hex.append(String.format("%02X ", dotEntryBuf[bIdx]));
                                        }

                                        if (!dotName.equals(".") || (dotAttr & 0x10) == 0 || dotCluster != item.firstCluster) {
                                            // Requirement 4: Flag folder as unreadable ONLY when check fails AND folder has no valid entries in DB
                                            // We perform preliminary check here; final unreadable flag is validated after scanning valid entries
                                            isUnreadableFolder = true;
                                            unreadableReason = String.format(Locale.getDefault(),
                                                    "Failed '.' check: expected dotName='.', dotAttr=0x10, dotCluster=%d. Actual dotName='%s', dotAttr=0x%X, dotCluster=%d. First 11 bytes: [%s]",
                                                    item.firstCluster, dotName, dotAttr, dotCluster, first11Hex.toString().trim());
                                        }
                                    } else {
                                        isUnreadableFolder = true;
                                        unreadableReason = "Failed to read first 32 bytes of subClusterOffset 0x" + Long.toHexString(subClusterOffset);
                                    }
                                } else {
                                    isUnreadableFolder = true;
                                    unreadableReason = "SubClusterOffset out of bounds: 0x" + Long.toHexString(subClusterOffset);
                                }
                            }

                            long fileDataOffset = clusterRegionStart + (item.firstCluster - 2) * clusterSize;
                            FileType type = item.isDir ? FileType.UNKNOWN : FileTypeDetector.detectTypeByNameOrHeader(source, item.name, fileDataOffset, item.size);

                            byte[] rawShortBytes = new byte[32];
                            System.arraycopy(clusterData, i, rawShortBytes, 0, 32);

                            List<byte[]> combinedRaw = new ArrayList<>(rawLfnBuffer);
                            combinedRaw.add(rawShortBytes);
                            byte[] flattenedRaw = flattenRawEntries(combinedRaw);

                            long modTime = decodeDosDateTime(item.dosDate, item.dosTime);

                            long assignedId = nextId.getAndIncrement();
                            RecoveredEntry entry = new RecoveredEntry(assignedId, parentNodeId, item.name, currentPath, fileDataOffset, item.size, type, false, item.isDir, modTime, flattenedRaw, item.firstCluster);
                            entry.setUnreadable(isUnreadableFolder);
                            entry.setUnreadableReason(unreadableReason);

                            entries.add(entry);
                            batch.add(entry);

                            if (batch.size() >= 1000) {
                                if (callback != null) {
                                    callback.onBatchReady(new ArrayList<>(batch));
                                }
                                batch.clear();
                            }

                            DiagnosticReporter.addFirstFile(item.name, item.size);

                            if (item.isDir && item.firstCluster >= 2 && item.firstCluster != currentCluster) {
                                String subPath = currentPath.equals("/") ? "/" + item.name : currentPath + "/" + item.name;
                                queue.add(new WorkItem(item.firstCluster, assignedId, subPath));
                            }
                        }
                    } catch (Exception clusterEx) {
                        DiagnosticReporter.logError(clusterEx, "Isolated failure processing cluster in FAT32");
                    }

                    currentCluster = getNextFat32Cluster(fatTable, currentCluster);
                }
            }

            // Requirement 4: Post-process directory unreadable flag: flag as unreadable only when check fails AND folder has no valid entries
            for (RecoveredEntry entry : entries) {
                if (entry.isDirectory() && entry.isUnreadable()) {
                    boolean hasValidChildren = false;
                    for (RecoveredEntry child : entries) {
                        if (child.getParentId() == entry.getId()) {
                            hasValidChildren = true;
                            break;
                        }
                    }
                    if (hasValidChildren) {
                        entry.setUnreadable(false);
                        entry.setUnreadableReason(null);
                    } else {
                        DiagnosticReporter.incrementUnreadableFolder();
                    }
                }
            }

            if (!batch.isEmpty() && callback != null) {
                callback.onBatchReady(new ArrayList<>(batch));
                batch.clear();
            }
        } catch (Exception e) {
            DiagnosticReporter.logError(e, "FAT32 parsing exception");
        }
        return entries;
    }

    private static boolean isGarbageEntry(Fat32EntryParser.Item item, byte attrBits, long totalSize) {
        if ((attrBits & 0x40) != 0 || (attrBits & 0x80) != 0) return true;

        if (!item.hadLfn) {
            String name = item.name;
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c < 0x20 || c == 0x7F || c == '*' || c == '+' || c == ',' || c == '/' || c == ':' || c == ';' || c == '<' || c == '=' || c == '>' || c == '?' || c == '[' || c == '\\' || c == ']' || c == '|' || c == '\"') {
                    return true;
                }
            }
        }

        if (item.firstCluster != 0 && (item.firstCluster < 2 || item.firstCluster > 0x0FFFFFF5L)) {
            return true;
        }

        if (item.size > totalSize) {
            return true;
        }

        return false;
    }

    private static byte[] flattenRawEntries(List<byte[]> list) {
        int totalLen = 0;
        for (byte[] b : list) totalLen += b.length;
        byte[] result = new byte[totalLen];
        int destPos = 0;
        for (byte[] b : list) {
            System.arraycopy(b, 0, result, destPos, b.length);
            destPos += b.length;
        }
        return result;
    }

    private static long getNextFat32Cluster(byte[] fatTable, long cluster) {
        int index = (int) (cluster * 4);
        if (index + 4 > fatTable.length) return 0x0FFFFFFF;
        return ByteBuffer.wrap(fatTable, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x0FFFFFFF;
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
