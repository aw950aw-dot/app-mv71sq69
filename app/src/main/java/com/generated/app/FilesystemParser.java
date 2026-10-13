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
        if (totalSize < 512) return new ArrayList<>();

        byte[] sector0 = new byte[512];
        if (source.readAt(0, sector0, 512) < 512) {
            return new ArrayList<>();
        }

        long partitionOffset = 0;
        boolean hasValidMbr = (sector0[510] == (byte) 0x55 && sector0[511] == (byte) 0xAA);
        if (hasValidMbr) {
            int p1Offset = 446;
            int partitionType = sector0[p1Offset + 4] & 0xFF;
            if (partitionType != 0 && partitionType != 0xEE) {
                ByteBuffer bb = ByteBuffer.wrap(sector0, p1Offset + 8, 4);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                long startSector = bb.getInt() & 0xFFFFFFFFL;
                if (startSector > 0 && startSector * 512 < totalSize) {
                    partitionOffset = startSector * 512;
                }
            }
        }

        byte[] bootSector = new byte[512];
        if (source.readAt(partitionOffset, bootSector, 512) == 512) {
            if (isFat32(bootSector)) {
                recoveryMethodUsed = "מערכת קבצים FAT32 ראשית";
                List<RecoveredEntry> entries = parseFat32(source, partitionOffset, bootSector, false);
                if (!entries.isEmpty()) return entries;
            } else if (isExFat(bootSector)) {
                recoveryMethodUsed = "מערכת קבצים exFAT ראשית";
                List<RecoveredEntry> entries = parseExFat(source, partitionOffset, bootSector, false);
                if (!entries.isEmpty()) return entries;
            }
        }

        if (partitionOffset != 0 && source.readAt(0, bootSector, 512) == 512) {
            if (isFat32(bootSector)) {
                recoveryMethodUsed = "מערכת קבצים FAT32 באופסט 0";
                List<RecoveredEntry> entries = parseFat32(source, 0, bootSector, false);
                if (!entries.isEmpty()) return entries;
            } else if (isExFat(bootSector)) {
                recoveryMethodUsed = "מערכת קבצים exFAT באופסט 0";
                List<RecoveredEntry> entries = parseExFat(source, 0, bootSector, false);
                if (!entries.isEmpty()) return entries;
            }
        }

        long[] fat32BackupOffsets = { partitionOffset + 6 * 512, 6 * 512, partitionOffset + 3072, 3072 };
        for (long off : fat32BackupOffsets) {
            if (off >= 0 && off + 512 <= totalSize) {
                if (source.readAt(off, bootSector, 512) == 512 && isFat32(bootSector)) {
                    recoveryMethodUsed = "שחזור מקטע איתחול מגובי (Backup Boot Sector FAT32)";
                    List<RecoveredEntry> entries = parseFat32(source, off, bootSector, true);
                    if (!entries.isEmpty()) return entries;
                }
            }
        }

        long[] exFatBackupOffsets = { partitionOffset + 12 * 512, 12 * 512, partitionOffset + 6144, 6144 };
        for (long off : exFatBackupOffsets) {
            if (off >= 0 && off + 512 <= totalSize) {
                if (source.readAt(off, bootSector, 512) == 512 && isExFat(bootSector)) {
                    recoveryMethodUsed = "שחזור מקטע איתחול מגובי (Backup Boot Sector exFAT)";
                    List<RecoveredEntry> entries = parseExFat(source, off, bootSector, true);
                    if (!entries.isEmpty()) return entries;
                }
            }
        }

        long scanStep = 64 * 512;
        for (long pos = 0; pos < totalSize; pos += scanStep) {
            if (source.readAt(pos, bootSector, 512) == 512) {
                if (isFat32(bootSector)) {
                    recoveryMethodUsed = "איתור מקטע איתחול FAT32 בסריקת סקטורים";
                    List<RecoveredEntry> entries = parseFat32(source, pos, bootSector, true);
                    if (!entries.isEmpty()) return entries;
                } else if (isExFat(bootSector)) {
                    recoveryMethodUsed = "איתור מקטע איתחול exFAT בסריקת סקטורים";
                    List<RecoveredEntry> entries = parseExFat(source, pos, bootSector, true);
                    if (!entries.isEmpty()) return entries;
                }
            }
        }

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

    private static List<RecoveredEntry> parseFat32(ByteSource source, long partOffset, byte[] boot, boolean useSecondFatCopy) throws IOException {
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

            long fatStartOffset = partOffset + (long) reservedSectorCount * bytesPerSector;
            if (useSecondFatCopy && fatCount > 1) {
                fatStartOffset += fatSize32 * bytesPerSector;
                recoveryMethodUsed += " (משתמש בטבלת FAT שנייה מגובה)";
            }

            long clusterRegionStart = partOffset + (long) reservedSectorCount * bytesPerSector + (long) fatCount * fatSize32 * bytesPerSector;
            long clusterSize = (long) sectorsPerCluster * bytesPerSector;

            int fatBytesToRead = (int) Math.min(fatSize32 * bytesPerSector, 4 * 1024 * 1024);
            byte[] fatTable = new byte[fatBytesToRead];
            source.readAt(fatStartOffset, fatTable, fatBytesToRead);

            traverseFat32Directory(source, clusterRegionStart, clusterSize, rootCluster, fatTable, "/", entries, new HashSet<Long>());
        } catch (Exception e) {
            Log.d(TAG, "Error parsing FAT32: " + e.getMessage());
        }
        return entries;
    }

    private static void traverseFat32Directory(ByteSource source, long clusterRegionStart, long clusterSize, long startCluster, byte[] fatTable, String currentPath, List<RecoveredEntry> entries, Set<Long> visitedClusters) throws IOException {
        long currentCluster = startCluster;
        int maxClusters = 2000;

        List<Fat32LfnItem> lfnBuffer = new ArrayList<>();

        while (currentCluster >= 2 && currentCluster < 0x0FFFFFF8 && maxClusters-- > 0) {
            if (!visitedClusters.add(currentCluster)) break;

            long clusterOffset = clusterRegionStart + (currentCluster - 2) * clusterSize;
            if (clusterOffset < 0 || clusterOffset + clusterSize > source.getSize()) break;

            byte[] clusterData = new byte[(int) clusterSize];
            int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
            if (read <= 0) break;

            for (int i = 0; i < read - 31; i += 32) {
                byte firstByte = clusterData[i];
                if (firstByte == 0x00) break;

                if (firstByte == (byte) 0xE5) {
                    lfnBuffer.clear();
                    continue;
                }

                int attr = clusterData[i + 11] & 0xFF;
                if ((attr & 0x08) != 0) { // Volume ID
                    lfnBuffer.clear();
                    continue;
                }

                if ((attr & 0x0F) == 0x0F) { // LFN entry
                    lfnBuffer.add(new Fat32LfnItem(clusterData, i));
                    continue;
                }

                boolean isDir = (attr & 0x10) != 0;
                String shortName = CodepageDecoder.decode83(clusterData, i, 11);
                
                // Assemble LFN with validation
                String resolvedName = assembleAndValidateFat32Lfn(lfnBuffer, shortName);
                lfnBuffer.clear();

                if (resolvedName.equals(".") || resolvedName.equals("..") || resolvedName.isEmpty()) continue;

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
                        traverseFat32Directory(source, clusterRegionStart, clusterSize, fileCluster, fatTable, subPath, entries, new HashSet<>(visitedClusters));
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

        // Sort by sequence number (masked with 0x40 for last entry)
        // Reconstruct UTF-16LE characters from 13 chars per entry across the 3 fields
        StringBuilder sb = new StringBuilder();
        // Simple robust assembler
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

        // Checksum validation against short name
        byte checksum = computeFat32Checksum(shortName);
        boolean checksumValid = true;
        for (Fat32LfnItem item : lfnList) {
            byte itemChk = item.data[13];
            if (itemChk != checksum) {
                checksumValid = false;
                break;
            }
        }

        if (!checksumValid) {
            return shortName; // fallback to short name if checksum mismatch (orphaned or damaged LFN)
        }

        return lfn;
    }

    private static byte computeFat32Checksum(String shortName) {
        byte sum = 0;
        // Pad short name to 11 chars
        String padded = shortName;
        if (!padded.contains(".")) {
            padded = padded + "           ";
        }
        if (padded.length() > 11) padded = padded.substring(0, 11);
        while (padded.length() < 11) padded = padded + " ";
        
        for (int i = 0; i < 11; i++) {
            sum = (byte) (((sum & 1) << 7) + (sum >> 1) + (padded.charAt(i) & 0xFF));
        }
        return sum;
    }

    private static long getNextFat32Cluster(byte[] fatTable, long cluster) {
        int index = (int) (cluster * 4);
        if (index + 4 > fatTable.length) return 0x0FFFFFFF;
        return ByteBuffer.wrap(fatTable, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x0FFFFFFF;
    }

    private static List<RecoveredEntry> parseExFat(ByteSource source, long partOffset, byte[] boot, boolean isBackup) throws IOException {
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

            traverseExFatDirectory(source, clusterHeapStart, clusterSize, firstClusterOfRootDirectory, "/", entries);
        } catch (Exception e) {
            Log.d(TAG, "Error parsing exFAT: " + e.getMessage());
        }
        return entries;
    }

    private static void traverseExFatDirectory(ByteSource source, long clusterHeapStart, long clusterSize, long startCluster, String currentPath, List<RecoveredEntry> entries) throws IOException {
        long currentCluster = startCluster;
        int maxClusters = 500;

        while (currentCluster >= 2 && maxClusters-- > 0) {
            long clusterOffset = clusterHeapStart + (currentCluster - 2) * clusterSize;
            if (clusterOffset < 0 || clusterOffset + clusterSize > source.getSize()) break;

            byte[] clusterData = new byte[(int) clusterSize];
            int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
            if (read <= 0) break;

            for (int i = 0; i < read - 31; i += 32) {
                int entryType = clusterData[i] & 0xFF;
                if (entryType == 0x00) break;
                if ((entryType & 0x80) == 0) continue;
                if (entryType != 0x85) continue; // File directory entry

                if (i + 64 > read) break;
                int secondaryCount = clusterData[i + 1] & 0xFF;
                int attr = ByteBuffer.wrap(clusterData, i + 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                boolean isDir = (attr & 0x10) != 0;

                long startingCluster = 0;
                long dataLength = 0;
                int nameLength = 0;
                StringBuilder fileName = new StringBuilder();

                // Read stream extension and file name entries following the 0x85 entry
                for (int s = 1; s <= secondaryCount && (i + s * 32 + 31) < read; s++) {
                    int secType = clusterData[i + s * 32] & 0xFF;
                    if (secType == 0xC0) { // Stream extension
                        startingCluster = ByteBuffer.wrap(clusterData, i + s * 32 + 20, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
                        dataLength = ByteBuffer.wrap(clusterData, i + s * 32 + 24, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                    } else if (secType == 0xC1) { // File name
                        nameLength = clusterData[i + s * 32 + 3] & 0xFF;
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
