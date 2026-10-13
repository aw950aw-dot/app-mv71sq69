package com.generated.app;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

        // 1. Try normal primary boot sector at partitionOffset (or offset 0)
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

        // 2. Corrupt boot sector recovery: Try backup boot sectors and backup FAT
        // FAT32 backup boot sector is typically sector 6; exFAT backup boot sector is sector 12.
        logData("Boot sector corrupt or unrecognized. Trying backup boot sectors and alternate FAT tables...");

        // Try FAT32 backup boot sector (sector 6 = offset 3072 or partitionOffset + 3072)
        long[] fat32BackupOffsets = { partitionOffset + 6 * 512, 6 * 512, partitionOffset + 3072, 3072 };
        for (long off : fat32BackupOffsets) {
            if (off >= 0 && off + 512 <= totalSize) {
                if (source.readAt(off, bootSector, 512) == 512 && isFat32(bootSector)) {
                    recoveryMethodUsed = "שחזור מקטע איתحול מגובי (Backup Boot Sector FAT32)";
                    List<RecoveredEntry> entries = parseFat32(source, off, bootSector, true);
                    if (!entries.isEmpty()) return entries;
                }
            }
        }

        // Try exFAT backup boot sector (sector 12 = offset 6144)
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

        // 3. Scan for boot sector signatures across the device if partition table is missing or corrupt
        logData("Partition table or boot sectors missing. Scanning storage for FAT32/exFAT signatures...");
        long scanStep = 64 * 512; // 32KB steps
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

    private static void logData(String msg) {
        Log.d(TAG, msg);
    }

    private static boolean isFat32(byte[] boot) {
        try {
            int bytesPerSector = ByteBuffer.wrap(boot, 11, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            if (bytesPerSector != 512 && bytesPerSector != 1024 && bytesPerSector != 2048 && bytesPerSector != 4096) {
                return false;
            }
            int sectorsPerCluster = boot[13] & 0xFF;
            if (sectorsPerCluster == 0 || (sectorsPerCluster & (sectorsPerCluster - 1)) != 0) {
                return false; // must be power of 2
            }
            int fatSize16 = ByteBuffer.wrap(boot, 22, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            long totalSectors32 = ByteBuffer.wrap(boot, 32, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
            
            // FAT32 signature check or file system type string
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
                fatStartOffset += fatSize32 * bytesPerSector; // Switch to 2nd FAT copy if first is damaged
                recoveryMethodUsed += " (משתמש בטבלת FAT שנייה מגובה)";
            }

            long clusterRegionStart = partOffset + (long) reservedSectorCount * bytesPerSector + (long) fatCount * fatSize32 * bytesPerSector;
            long clusterSize = (long) sectorsPerCluster * bytesPerSector;

            int fatBytesToRead = (int) Math.min(fatSize32 * bytesPerSector, 4 * 1024 * 1024);
            byte[] fatTable = new byte[fatBytesToRead];
            source.readAt(fatStartOffset, fatTable, fatBytesToRead);

            traverseFat32Directory(source, clusterRegionStart, clusterSize, rootCluster, fatTable, "/", entries, new HashSet<Long>());
        } catch (Exception e) {
            logData("Error parsing FAT32: " + e.getMessage());
        }
        return entries;
    }

    private static void traverseFat32Directory(ByteSource source, long clusterRegionStart, long clusterSize, long startCluster, byte[] fatTable, String currentPath, List<RecoveredEntry> entries, Set<Long> visitedClusters) throws IOException {
        long currentCluster = startCluster;
        int maxClusters = 2000;

        while (currentCluster >= 2 && currentCluster < 0x0FFFFFF8 && maxClusters-- > 0) {
            if (!visitedClusters.add(currentCluster)) break; // prevent infinite loops in damaged chains

            long clusterOffset = clusterRegionStart + (currentCluster - 2) * clusterSize;
            if (clusterOffset < 0 || clusterOffset + clusterSize > source.getSize()) break;

            byte[] clusterData = new byte[(int) clusterSize];
            int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
            if (read <= 0) break;

            String pendingLfn = "";
            for (int i = 0; i < read - 31; i += 32) {
                byte firstByte = clusterData[i];
                if (firstByte == 0x00) break; // end of directory
                
                // CRITICAL UPDATE: Skip deleted files (0xE5). Focus ONLY on existing/healthy files.
                if (firstByte == (byte) 0xE5) {
                    pendingLfn = "";
                    continue;
                }

                int attr = clusterData[i + 11] & 0xFF;
                if ((attr & 0x08) != 0) { // Volume ID
                    pendingLfn = "";
                    continue;
                }

                if ((attr & 0x0F) == 0x0F) { // LFN entry
                    String lfnPart = extractFat32Lfn(clusterData, i);
                    pendingLfn = lfnPart + pendingLfn;
                    continue;
                }

                boolean isDir = (attr & 0x10) != 0;
                String shortName = extractFat32ShortName(clusterData, i);
                String fileName = !pendingLfn.isEmpty() ? pendingLfn : shortName;
                pendingLfn = "";

                if (fileName.equals(".") || fileName.equals("..") || fileName.isEmpty()) continue;

                int clusterHigh = ByteBuffer.wrap(clusterData, i + 20, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                int clusterLow = ByteBuffer.wrap(clusterData, i + 26, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                long fileCluster = ((long) clusterHigh << 16) | clusterLow;
                long fileLength = ByteBuffer.wrap(clusterData, i + 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;

                if (fileCluster >= 2) {
                    long fileDataOffset = clusterRegionStart + (fileCluster - 2) * clusterSize;
                    if (fileDataOffset >= 0 && fileDataOffset < source.getSize()) {
                        FileType type = FileTypeDetector.detectTypeByNameOrHeader(source, fileName, fileDataOffset, fileLength);
                        String fullPath = currentPath.equals("/") ? "/" + fileName : currentPath + "/" + fileName;

                        // Only add existing (not deleted) files
                        entries.add(new RecoveredEntry(fileName, currentPath, fileDataOffset, fileLength, type, false, isDir));

                        if (isDir && fileCluster != startCluster) {
                            traverseFat32Directory(source, clusterRegionStart, clusterSize, fileCluster, fatTable, fullPath, entries, new HashSet<>(visitedClusters));
                        }
                    }
                }
            }

            currentCluster = getNextFat32Cluster(fatTable, currentCluster);
        }
    }

    private static long getNextFat32Cluster(byte[] fatTable, long cluster) {
        int index = (int) (cluster * 4);
        if (index + 4 > fatTable.length) return 0x0FFFFFFF;
        return ByteBuffer.wrap(fatTable, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x0FFFFFFF;
    }

    private static String extractFat32ShortName(byte[] buf, int offset) {
        StringBuilder name = new StringBuilder();
        StringBuilder ext = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            char c = (char) buf[offset + i];
            if (c != ' ' && c != 0) name.append(c);
        }
        for (int i = 8; i < 11; i++) {
            char c = (char) buf[offset + i];
            if (c != ' ' && c != 0) ext.append(c);
        }
        if (ext.length() > 0) {
            return name.toString().toLowerCase() + "." + ext.toString().toLowerCase();
        }
        return name.toString().toLowerCase();
    }

    private static String extractFat32Lfn(byte[] buf, int offset) {
        StringBuilder sb = new StringBuilder();
        int[] indices = {1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30};
        for (int idx : indices) {
            if (offset + idx + 2 > buf.length) break;
            char c = (char) ByteBuffer.wrap(buf, offset + idx, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
            if (c == 0 || c == 0xFFFF) break;
            sb.append(c);
        }
        return sb.toString();
    }

    private static List<RecoveredEntry> parseExFat(ByteSource source, long partOffset, byte[] boot, boolean isBackup) throws IOException {
        List<RecoveredEntry> entries = new ArrayList<>();
        try {
            ByteBuffer b = ByteBuffer.wrap(boot);
            b.order(ByteOrder.LITTLE_ENDIAN);

            int bytesPerSectorShift = b.get(109) & 0xFF;
            if (bytesPerSectorShift < 9 || bytesPerSectorShift > 12) bytesPerSectorShift = 9; // 512 bytes default

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
            logData("Error parsing exFAT: " + e.getMessage());
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
                if (entryType == 0x00) break; // end of directory
                
                // CRITICAL UPDATE: Skip deleted entries (bit 7 clear for allocation status or file attributes)
                if ((entryType & 0x80) == 0) continue; 
                if (entryType != 0x85) continue; // File directory entry

                if (i + 64 > read) break;
                int nameLength = clusterData[i + 35] & 0xFF;
                long startingCluster = ByteBuffer.wrap(clusterData, i + 52, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
                long dataLength = ByteBuffer.wrap(clusterData, i + 56, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();

                StringBuilder fileName = new StringBuilder();
                for (int n = 0; n < nameLength && (i + 64 + 2 + n * 2 + 2) <= read; n++) {
                    char c = (char) ByteBuffer.wrap(clusterData, i + 64 + 2 + n * 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                    if (c == 0) break;
                    fileName.append(c);
                }

                String name = fileName.length() > 0 ? fileName.toString() : "unnamed";
                long fileDataOffset = clusterHeapStart + (startingCluster - 2) * clusterSize;
                
                if (fileDataOffset >= 0 && fileDataOffset < source.getSize()) {
                    FileType type = FileTypeDetector.detectTypeByNameOrHeader(source, name, fileDataOffset, dataLength);
                    entries.add(new RecoveredEntry(name, currentPath, fileDataOffset, dataLength, type, false, false));
                }
            }
            break;
        }
    }
}
