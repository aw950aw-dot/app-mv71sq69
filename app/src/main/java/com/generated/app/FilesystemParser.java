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

        for (long partOff : candidatePartitionOffsets) {
            DiagnosticReporter.setChosenStartLba(String.valueOf(partOff / 512));
            List<RecoveredEntry> candidateEntries = new ArrayList<>();

            try {
                byte[] bootSector = new byte[512];
                if (source.readAt(partOff, bootSector, 512) == 512) {
                    if (isValidFat32(bootSector)) {
                        candidateEntries = parseFat32(source, partOff, bootSector, totalSize, callback);
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
                    candidateEntries = parseFat32(source, partOff, bootSector, totalSize, callback);
                    if (!candidateEntries.isEmpty()) {
                        if (candidateEntries.size() > maxFilesFound) {
                            maxFilesFound = candidateEntries.size();
                            bestEntries = candidateEntries;
                            bestMethod = "מערכת קבצים FAT32 עם מקטע איתחול מגובי (LBA " + (partOff / 512) + ")";
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
                    boolean isFat32Hit = "FAT32   ".equals(new String(readBuffer, i + 82, 8, StandardCharsets.US_ASCII));
                    if (isFat32Hit) {
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
            long totalClusters = (b.getInt(32) & 0xFFFFFFFFL); // total sectors / sectorsPerCluster roughly

            long fatStartOffset = partOffset + (long) reserved * bytesPerSector;
            long clusterRegionStart = partOffset + (long) reserved * bytesPerSector + (long) numFATs * sectorsPerFAT * bytesPerSector;
            long clusterSize = (long) sectorsPerCluster * bytesPerSector;

            DiagnosticReporter.setBootSectorFields("BytesPerSector=" + bytesPerSector + ", SectorsPerCluster=" + sectorsPerCluster + ", RootCluster=" + rootCluster);
            DiagnosticReporter.setFirstDataSector(String.valueOf(clusterRegionStart / 512));
            DiagnosticReporter.setRootDirLba(String.valueOf((clusterRegionStart + (rootCluster - 2) * clusterSize) / 512));

            int fatBytesToRead = (int) Math.min(sectorsPerFAT * bytesPerSector, 8 * 1024 * 1024);
            byte[] fatTable = new byte[fatBytesToRead];
            source.readAt(fatStartOffset, fatTable, fatBytesToRead);

            traverseFat32DirectorySafe(source, clusterRegionStart, clusterSize, rootCluster, fatTable, totalSize, "/", entries, batch, new HashSet<Long>(), callback);
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
        // Rule 4: Garbage filter
        // - attribute bits 0x40 or 0x80 set
        if ((attrBits & 0x40) != 0 || (attrBits & 0x80) != 0) return true;

        // - has no long name and its short name contains a character below 0x20, or one of " * + , / : ; < = > ? [ \ ] | or 0x7F
        if (!item.hadLfn) {
            String name = item.name;
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c < 0x20 || c == 0x7F || c == '*' || c == '+' || c == ',' || c == '/' || c == ':' || c == ';' || c == '<' || c == '=' || c == '>' || c == '?' || c == '[' || c == '\\' || c == ']' || c == '|' || c == '\"') {
                    return true;
                }
            }
        }

        // - firstCluster != 0 and (firstCluster < 2 or firstCluster > totalSize)
        if (item.firstCluster != 0 && (item.firstCluster < 2 || item.firstCluster > 0x0FFFFFF5L)) {
            return true;
        }

        // - file size larger than the card
        if (item.size > totalSize) {
            return true;
        }

        return false;
    }

    private static void traverseFat32DirectorySafe(ByteSource source, long clusterRegionStart, long clusterSize, long startCluster, byte[] fatTable, long totalSize, String currentPath, List<RecoveredEntry> entries, List<RecoveredEntry> batch, Set<Long> visitedClusters, ParserCallback callback) {
        long currentCluster = startCluster;
        int maxClusters = 5000;

        if (callback != null) {
            callback.onProgress(currentPath);
        }

        // Rule 1: Create a NEW parser for each directory.
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

                    // Collect raw LFN entries for Hex detail popup
                    List<byte[]> rawLfnBuffer = new ArrayList<>();
                    // Capture LFNs preceding this entry
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

                    // Rule 5: Folder check
                    boolean isUnreadableFolder = false;
                    if (item.isDir && item.firstCluster >= 2) {
                        long subClusterOffset = clusterRegionStart + (item.firstCluster - 2) * clusterSize;
                        if (subClusterOffset >= 0 && subClusterOffset + 32 <= source.getSize()) {
                            byte[] dotEntryBuf = new byte[32];
                            if (source.readAt(subClusterOffset, dotEntryBuf, 32) == 32) {
                                String dotName = new String(dotEntryBuf, 0, 11, StandardCharsets.US_ASCII).trim();
                                int dotAttr = dotEntryBuf[11] & 0xFF;
                                // Rule 6: First cluster verification
                                long dotCluster = ((long) ByteBuffer.wrap(dotEntryBuf, 20, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() << 16) |
                                        (ByteBuffer.wrap(dotEntryBuf, 26, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF);

                                if (!dotName.equals(".") || (dotAttr & 0x10) == 0 || dotCluster != item.firstCluster) {
                                    isUnreadableFolder = true;
                                    DiagnosticReporter.incrementUnreadableFolder();
                                }
                            } else {
                                isUnreadableFolder = true;
                                DiagnosticReporter.incrementUnreadableFolder();
                            }
                        } else {
                            isUnreadableFolder = true;
                            DiagnosticReporter.incrementUnreadableFolder();
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

                    // Rule 3: Store returned name in DB as is (Java String)
                    RecoveredEntry entry = new RecoveredEntry(item.name, currentPath, fileDataOffset, item.size, type, false, item.isDir, modTime, flattenedRaw);
                    entry.setUnreadable(isUnreadableFolder);

                    entries.add(entry);
                    batch.add(entry);

                    if (batch.size() >= 1000) {
                        if (callback != null) {
                            callback.onBatchReady(new ArrayList<>(batch));
                        }
                        batch.clear();
                    }

                    DiagnosticReporter.addFirstFile(item.name, item.size);

                    if (item.isDir && item.firstCluster >= 2 && item.firstCluster != startCluster && !isUnreadableFolder) {
                        String subPath = currentPath.equals("/") ? "/" + item.name : currentPath + "/" + item.name;
                        traverseFat32DirectorySafe(source, clusterRegionStart, clusterSize, item.firstCluster, fatTable, totalSize, subPath, entries, batch, new HashSet<>(visitedClusters), callback);
                    }
                }
            } catch (Exception clusterEx) {
                DiagnosticReporter.logError(clusterEx, "Isolated failure processing cluster in FAT32");
            }

            currentCluster = getNextFat32Cluster(fatTable, currentCluster);
        }
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
