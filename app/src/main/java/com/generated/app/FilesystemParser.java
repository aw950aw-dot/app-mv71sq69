package com.generated.app;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilesystemParser {

    private static final String TAG = "FilesystemParser";

    public static List<RecoveredEntry> parseFilesystem(ByteSource source) throws IOException {
        long totalSize = source.getSize();
        if (totalSize < 512) return new ArrayList<>();

        byte[] sector0 = new byte[512];
        if (source.readAt(0, sector0, 512) < 512) {
            return new ArrayList<>();
        }

        long partitionOffset = 0;

        // Check MBR partition table at offset 446
        boolean hasValidMbr = (sector0[510] == (byte) 0x55 && sector0[511] == (byte) 0xAA);
        if (hasValidMbr) {
            // Check first partition entry
            int p1Offset = 446;
            int bootIndicator = sector0[p1Offset] & 0xFF;
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

        // Read boot sector at partitionOffset
        byte[] bootSector = new byte[512];
        if (source.readAt(partitionOffset, bootSector, 512) < 512) {
            return new ArrayList<>();
        }

        // Check FAT32 / FAT16 / exFAT
        if (isFat32(bootSector)) {
            return parseFat32(source, partitionOffset, bootSector);
        } else if (isExFat(bootSector)) {
            return parseExFat(source, partitionOffset, bootSector);
        }

        // If no recognized filesystem boot sector at partitionOffset, try offset 0 directly
        if (partitionOffset != 0) {
            if (isFat32(sector0)) {
                return parseFat32(source, 0, sector0);
            } else if (isExFat(sector0)) {
                return parseExFat(source, 0, sector0);
            }
        }

        return new ArrayList<>();
    }

    private static boolean isFat32(byte[] boot) {
        // FAT32 OEM name or BPB checks
        int bytesPerSector = ByteBuffer.wrap(boot, 11, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
        int fatSize16 = ByteBuffer.wrap(boot, 22, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
        long totalSectors32 = ByteBuffer.wrap(boot, 32, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        int fat32Flags = boot[66] & 0xFF;
        
        return bytesPerSector == 512 && fatSize16 == 0 && totalSectors32 > 0;
    }

    private static boolean isExFat(byte[] boot) {
        // exFAT OEM name "EXFAT   " at offset 3
        String oem = new String(boot, 3, 8, StandardCharsets.US_ASCII);
        return "EXFAT   ".equals(oem);
    }

    private static List<RecoveredEntry> parseFat32(ByteSource source, long partOffset, byte[] boot) throws IOException {
        List<RecoveredEntry> entries = new ArrayList<>();

        ByteBuffer b = ByteBuffer.wrap(boot);
        b.order(ByteOrder.LITTLE_ENDIAN);

        int bytesPerSector = b.getShort(11) & 0xFFFF;
        int sectorsPerCluster = b.get(13) & 0xFF;
        int reservedSectorCount = b.getShort(14) & 0xFFFF;
        int fatCount = b.get(16) & 0xFF;
        long fatSize32 = b.getInt(36) & 0xFFFFFFFFL;
        long rootCluster = b.getInt(44) & 0xFFFFFFFFL;

        long fatStartOffset = partOffset + (long) reservedSectorCount * bytesPerSector;
        long clusterRegionStart = fatStartOffset + (long) fatCount * fatSize32 * bytesPerSector;
        long clusterSize = (long) sectorsPerCluster * bytesPerSector;

        // Read FAT table for chain walking
        int fatBytesToRead = (int) Math.min(fatSize32 * bytesPerSector, 2 * 1024 * 1024);
        byte[] fatTable = new byte[fatBytesToRead];
        source.readAt(fatStartOffset, fatTable, fatBytesToRead);

        // Traverse directories starting from rootCluster
        traverseFat32Directory(source, clusterRegionStart, clusterSize, rootCluster, fatTable, "/", entries);

        return entries;
    }

    private static void traverseFat32Directory(ByteSource source, long clusterRegionStart, long clusterSize, long startCluster, byte[] fatTable, String currentPath, List<RecoveredEntry> entries) throws IOException {
        long currentCluster = startCluster;
        int maxClusters = 1000; // safety guard against infinite loops

        while (currentCluster >= 2 && currentCluster < 0x0FFFFFF8 && maxClusters-- > 0) {
            long clusterOffset = clusterRegionStart + (currentCluster - 2) * clusterSize;
            byte[] clusterData = new byte[(int) clusterSize];
            int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
            if (read <= 0) break;

            // Parse directory entries (32 bytes each)
            String pendingLfn = "";
            for (int i = 0; i < read; i += 32) {
                byte firstByte = clusterData[i];
                if (firstByte == 0x00) break; // end of directory
                if (firstByte == (byte) 0xE5) {
                    // Deleted entry, can be recovered if valid
                } else if ((clusterData[i + 11] & 0x08) != 0 && (clusterData[i + 11] & 0x0F) == 0x0F) {
                    // LFN entry
                    String lfnPart = extractFat32Lfn(clusterData, i);
                    pendingLfn = lfnPart + pendingLfn;
                    continue;
                }

                boolean isDeleted = (firstByte == (byte) 0xE5);
                int attr = clusterData[i + 11] & 0xFF;
                boolean isDir = (attr & 0x10) != 0;
                boolean isVolumeId = (attr & 0x08) != 0;

                if (isVolumeId) {
                    pendingLfn = "";
                    continue;
                }

                String shortName = extractFat32ShortName(clusterData, i);
                String fileName = !pendingLfn.isEmpty() ? pendingLfn : shortName;
                pendingLfn = "";

                if (fileName.equals(".") || fileName.equals("..")) continue;

                int clusterHigh = ByteBuffer.wrap(clusterData, i + 20, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                int clusterLow = ByteBuffer.wrap(clusterData, i + 26, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                long fileCluster = ((long) clusterHigh << 16) | clusterLow;
                long fileLength = ByteBuffer.wrap(clusterData, i + 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;

                if (fileCluster >= 2) {
                    long fileDataOffset = clusterRegionStart + (fileCluster - 2) * clusterSize;
                    FileType type = FileTypeDetector.detectTypeByNameOrHeader(source, fileName, fileDataOffset, fileLength);

                    String fullPath = currentPath.equals("/") ? "/" + fileName : currentPath + "/" + fileName;
                    entries.add(new RecoveredEntry(fileName, currentPath, fileDataOffset, fileLength, type, isDeleted, isDir));

                    if (isDir && fileCluster != startCluster) {
                        traverseFat32Directory(source, clusterRegionStart, clusterSize, fileCluster, fatTable, fullPath, entries);
                    }
                }
            }

            currentCluster = getNextFat32Cluster(fatTable, currentCluster);
        }
    }

    private static long getNextFat32Cluster(byte[] fatTable, long cluster) {
        int index = (int) (cluster * 4);
        if (index + 4 > fatTable.length) return 0x0FFFFFFF;
        long next = ByteBuffer.wrap(fatTable, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x0FFFFFFF;
        return next;
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
            char c = (char) ByteBuffer.wrap(buf, offset + idx, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
            if (c == 0 || c == 0xFFFF) break;
            sb.append(c);
        }
        return sb.toString();
    }

    private static List<RecoveredEntry> parseExFat(ByteSource source, long partOffset, byte[] boot) throws IOException {
        List<RecoveredEntry> entries = new ArrayList<>();
        ByteBuffer b = ByteBuffer.wrap(boot);
        b.order(ByteOrder.LITTLE_ENDIAN);

        int bytesPerSectorShift = b.get(109) & 0xFF;
        int sectorsPerClusterShift = b.get(110) & 0xFF;
        long clusterHeapOffset = b.getInt(72) & 0xFFFFFFFFL;
        long firstClusterOfRootDirectory = b.getInt(96) & 0xFFFFFFFFL;

        int bytesPerSector = 1 << bytesPerSectorShift;
        long clusterSize = (long) bytesPerSector * (1 << sectorsPerClusterShift);
        long clusterHeapStart = partOffset + clusterHeapOffset * bytesPerSector;

        // Traverse exFAT root directory
        traverseExFatDirectory(source, clusterHeapStart, clusterSize, firstClusterOfRootDirectory, "/", entries);

        return entries;
    }

    private static void traverseExFatDirectory(ByteSource source, long clusterHeapStart, long clusterSize, long startCluster, String currentPath, List<RecoveredEntry> entries) throws IOException {
        long currentCluster = startCluster;
        int maxClusters = 500;

        while (currentCluster >= 2 && maxClusters-- > 0) {
            long clusterOffset = clusterHeapStart + (currentCluster - 2) * clusterSize;
            byte[] clusterData = new byte[(int) clusterSize];
            int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
            if (read <= 0) break;

            for (int i = 0; i < read; i += 32) {
                int entryType = clusterData[i] & 0xFF;
                if (entryType == 0x00) break; // end of directory
                if (entryType != 0x85) continue; // File directory entry

                // Read Stream Extension Entry (next 32 bytes)
                if (i + 64 > read) break;
                int streamFlags = clusterData[i + 33] & 0xFF;
                int nameLength = clusterData[i + 35] & 0xFF;
                long startingCluster = ByteBuffer.wrap(clusterData, i + 52, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
                long dataLength = ByteBuffer.wrap(clusterData, i + 56, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();

                // Read File Name Entry (next 32 bytes)
                StringBuilder fileName = new StringBuilder();
                for (int n = 0; n < nameLength && (i + 64 + n * 2 + 2) <= read; n++) {
                    char c = (char) ByteBuffer.wrap(clusterData, i + 64 + 2 + n * 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                    if (c == 0) break;
                    fileName.append(c);
                }

                String name = fileName.length() >  0 ? fileName.toString() : "unnamed";
                long fileDataOffset = clusterHeapStart + (startingCluster - 2) * clusterSize;
                FileType type = FileTypeDetector.detectTypeByNameOrHeader(source, name, fileDataOffset, dataLength);

                boolean isDeleted = (entryType & 0x80) == 0;
                entries.add(new RecoveredEntry(name, currentPath, fileDataOffset, dataLength, type, isDeleted, false));
            }
            break; // Single cluster root simplification for robustness
        }
    }
}
