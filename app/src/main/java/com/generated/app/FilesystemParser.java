package com.generated.app;

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

    private static String recoveryMethodUsed = "טרם אותרה מערכת קבצים";

    public static String getRecoveryMethodUsed() {
        return recoveryMethodUsed;
    }

    public static List<RecoveredEntry> parseFilesystem(ByteSource source) throws IOException {
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

        // Rule 1: Never treat LBA 0 as a boot sector automatically. LBA 0 is checked ONLY for MBR/GPT table.
        boolean hasValidMbrSig = (sector0[510] == (byte) 0x55 && sector0[511] == (byte) 0xAA);
        boolean hasValidPartitionEntries = false;

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

                // Rule 1a: partition type != 0, startLBA > 0 and size > 0
                if (pType != 0 && pType != 0xEE && startSector > 0 && pSectors > 0) {
                    hasValidPartitionEntries = true;
                    long off = startSector * 512;
                    if (off < totalSize && !candidatePartitionOffsets.contains(off)) {
                        candidatePartitionOffsets.add(off);
                    }
                } else if (pType == 0xEE) {
                    // GPT Header parsing
                    hasValidPartitionEntries = true;
                    byte[] lba1 = new byte[512];
                    if (source.readAt(512, lba1, 512) == 512) {
                        String gptSig = new String(lba1, 0, 8, StandardCharsets.US_ASCII);
                        if ("EFI PART".equals(gptSig)) {
                            partitionInfoBuilder.append("זוהה GPT (EFI PART) בסקטור 1.\n");
                            ByteBuffer gptBb = ByteBuffer.wrap(lba1);
                            gptBb.order(ByteOrder.LITTLE_ENDIAN);
                            long entriesArrayLba = gptBb.getLong(72);
                            int entryCount = gptBb.getInt(80);
                            int entrySize = gptBb.getInt(84);
                            
                            long gptOff = entriesArrayLba * 512;
                            if (gptOff > 0 && gptOff < totalSize) {
                                byte[] entryBuf = new byte[Math.min(entryCount * entrySize, 16384)];
                                if (source.readAt(gptOff, entryBuf, entryBuf.length) > 0) {
                                    ByteBuffer eBb = ByteBuffer.wrap(entryBuf);
                                    eBb.order(ByteOrder.LITTLE_ENDIAN);
                                    for (int e = 0; e < entryCount && e * entrySize + 40 <= entryBuf.length; e++) {
                                        long firstLba = eBb.getLong(e * entrySize + 32);
                                        long lastLba = eBb.getLong(e * entrySize + 40);
                                        if (firstLba > 0 && lastLba >= firstLba) {
                                            long partOff = firstLba * 512;
                                            if (partOff < totalSize && !candidatePartitionOffsets.contains(partOff)) {
                                                candidatePartitionOffsets.add(partOff);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Rule 1b: ALWAYS ALSO search for boot sectors in first 256 MB in 64 KB reads
        scanBootSectorsFirst256MB(source, totalSize, candidatePartitionOffsets, partitionInfoBuilder);
        DiagnosticReporter.setPartitionInfo(partitionInfoBuilder.toString());

        List<RecoveredEntry> bestEntries = new ArrayList<>();
        String bestMethod = "טרם אותרה מערכת קבצים";
        int maxFilesFound = 0;

        // Rule 1c: Try every candidate and keep the one that yields the most valid files
        StringBuilder skippedLogBuilder = new StringBuilder();
        for (long partOff : candidatePartitionOffsets) {
            DiagnosticReporter.setChosenStartLba(String.valueOf(partOff / 512));
            List<RecoveredEntry> candidateEntries = new ArrayList<>();

            byte[] bootSector = new byte[512];
            if (source.readAt(partOff, bootSector, 512) == 512) {
                // Try exFAT
                if (isValidExFat(bootSector)) {
                    candidateEntries = parseExFat(source, partOff, bootSector, skippedLogBuilder);
                    if (!candidateEntries.isEmpty()) {
                        if (candidateEntries.size() > maxFilesFound) {
                            maxFilesFound = candidateEntries.size();
                            bestEntries = candidateEntries;
                            bestMethod = "מערכת קבצים exFAT (LBA " + (partOff / 512) + ")";
                        }
                        continue;
                    }
                }

                // Try FAT32
                if (isValidFat32(bootSector)) {
                    candidateEntries = parseFat32(source, partOff, bootSector, false, skippedLogBuilder);
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

            // Rule 5: Fallbacks (a) Backup boot sector
            long backupOffExFat = partOff + 12 * 512;
            if (backupOffExFat + 512 <= totalSize && source.readAt(backupOffExFat, bootSector, 512) == 512 && isValidExFat(bootSector)) {
                candidateEntries = parseExFat(source, backupOffExFat, bootSector, skippedLogBuilder);
                if (!candidateEntries.isEmpty() && candidateEntries.size() > maxFilesFound) {
                    maxFilesFound = candidateEntries.size();
                    bestEntries = candidateEntries;
                    bestMethod = "מקטע איתחול מגובי exFAT (Backup Boot Sector)";
                    continue;
                }
            }

            long backupOffFat32 = partOff + 6 * 512;
            if (backupOffFat32 + 512 <= totalSize && source.readAt(backupOffFat32, bootSector, 512) == 512 && isValidFat32(bootSector)) {
                candidateEntries = parseFat32(source, backupOffFat32, bootSector, false, skippedLogBuilder);
                if (!candidateEntries.isEmpty() && candidateEntries.size() > maxFilesFound) {
                    maxFilesFound = candidateEntries.size();
                    bestEntries = candidateEntries;
                    bestMethod = "מקטע איתחול מגובי FAT32 (Backup Boot Sector)";
                    continue;
                }
            }

            // Rule 5: Fallbacks (b) Second FAT
            if (source.readAt(partOff, bootSector, 512) == 512 && isValidFat32(bootSector)) {
                candidateEntries = parseFat32(source, partOff, bootSector, true, skippedLogBuilder);
                if (!candidateEntries.isEmpty() && candidateEntries.size() > maxFilesFound) {
                    maxFilesFound = candidateEntries.size();
                    bestEntries = candidateEntries;
                    bestMethod = "טבלת FAT שנייה מגובה (Second FAT)";
                    continue;
                }
            }

            // Rule 5: Fallbacks (c) Orphan directory scan
            candidateEntries = scanOrphanDirectories(source, partOff, totalSize, skippedLogBuilder);
            if (!candidateEntries.isEmpty() && candidateEntries.size() > maxFilesFound) {
                maxFilesFound = candidateEntries.size();
                bestEntries = candidateEntries;
                bestMethod = "סריקת תיקיות יתומות (Orphan Directory Scan)";
            }
        }

        if (!bestEntries.isEmpty()) {
            recoveryMethodUsed = bestMethod;
            DiagnosticReporter.setFsTypeDetected(bestMethod);
            DiagnosticReporter.setFilesFoldersFound(String.valueOf(bestEntries.size()));
            return bestEntries;
        }

        // Rule 6: Never report success when nothing was found.
        DiagnosticReporter.log("כל שלבי סריקת מערכת הקבצים החזירו 0 פריטים תקפים.");
        DiagnosticReporter.setFilesFoldersFound("0 (מערכת קבצים ריקה/פגומה)");
        DiagnosticReporter.setSkippedEntriesLog(skippedLogBuilder.toString());
        return new ArrayList<>();
    }

    private static void scanBootSectorsFirst256MB(ByteSource source, long totalSize, List<Long> candidateOffsets, StringBuilder partitionInfo) {
        long limit = Math.min(totalSize, 256L * 1024L * 1024L);
        byte[] readBuffer = new byte[65536]; // 64 KB reads
        long currentOffset = 0;

        while (currentOffset < limit) {
            int toRead = (int) Math.min(readBuffer.length, limit - currentOffset);
            try {
                int read = source.readAt(currentOffset, readBuffer, toRead);
                if (read <= 0) break;

                for (int i = 0; i <= read - 512; i += 512) {
                    long absoluteOffset = currentOffset + i;
                    long lba = absoluteOffset / 512;

                    // Check 55 AA at 510
                    if (readBuffer[i + 510] != (byte) 0x55 || readBuffer[i + 511] != (byte) 0xAA) {
                        continue;
                    }

                    // Check "EXFAT   " at offset 3
                    boolean isExFatHit = true;
                    String exFatOem = new String(readBuffer, i + 3, 8, StandardCharsets.US_ASCII);
                    if (!"EXFAT   ".equals(exFatOem)) {
                        isExFatHit = false;
                    }

                    // Check "FAT32   " at offset 82
                    boolean isFat32Hit = true;
                    String fat32Oem = new String(readBuffer, i + 82, 8, StandardCharsets.US_ASCII);
                    if (!"FAT32   ".equals(fat32Oem)) {
                        isFat32Hit = false;
                    }

                    // Check "NTFS    " at offset 3
                    boolean isNtfsHit = true;
                    String ntfsOem = new String(readBuffer, i + 3, 8, StandardCharsets.US_ASCII);
                    if (!"NTFS    ".equals(ntfsOem)) {
                        isNtfsHit = false;
                    }

                    if (isExFatHit || isFat32Hit || isNtfsHit) {
                        long actualStart = absoluteOffset;
                        // Verify backup sector rule: if sector X+12 (exFAT) or X+6 (FAT32) also matches, start is X; else check backup
                        if (isExFatHit) {
                            byte[] nextSector = new byte[512];
                            if (absoluteOffset + 12 * 512 < totalSize && source.readAt(absoluteOffset + 12 * 512, nextSector, 512) == 512) {
                                if (nextSector[510] == (byte) 0x55 && nextSector[511] == (byte) 0xAA) {
                                    actualStart = absoluteOffset; // is primary
                                } else {
                                    actualStart = absoluteOffset - 12 * 512; // was backup
                                }
                            }
                        } else if (isFat32Hit) {
                            actualStart = absoluteOffset;
                        }

                        if (actualStart >= 0 && actualStart < totalSize && !candidateOffsets.contains(actualStart)) {
                            candidateOffsets.add(actualStart);
                            partitionInfo.append("זוהה מקטע איתחול ב-LBA ").append(actualStart / 512).append("\n");
                        }
                    }
                }
            } catch (IOException ignored) {}
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
            int sectorsPerClusterShift = b.get(110) & 0xFF;
            int numberOfFats = b.get(111) & 0xFF;
            long clusterCount = b.getInt(92) & 0xFFFFFFFFL;
            long rootDirCluster = b.getInt(96) & 0xFFFFFFFFL;

            // Rule 4 Validation: BytesPerSectorShift 9..12, SectorsPerClusterShift <= 25 - BytesPerSectorShift, NumberOfFats 1 or 2, RootDirCluster in 2..ClusterCount+1
            if (bytesPerSectorShift < 9 || bytesPerSectorShift > 12) return false;
            if (sectorsPerClusterShift > 25 - bytesPerSectorShift) return false;
            if (numberOfFats != 1 && numberOfFats != 2) return false;
            if (rootDirCluster < 2 || rootDirCluster > clusterCount + 1) return false;

            return true;
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

            // Rule 4 Validation: bytesPerSector in {512,1024,2048,4096}, sectorsPerCluster a power of two 1..128, reserved > 0, numFATs 1 or 2, sectorsPerFAT > 0, rootCluster >= 2
            if (bytesPerSector != 512 && bytesPerSector != 1024 && bytesPerSector != 2048 && bytesPerSector != 4096) return false;
            if (sectorsPerCluster == 0 || (sectorsPerCluster & (sectorsPerCluster - 1)) != 0 || sectorsPerCluster > 128) return false;
            if (reserved == 0) return false;
            if (numFATs != 1 && numFATs != 2) return false;
            if (sectorsPerFAT == 0) return false;
            if (rootCluster < 2) return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<RecoveredEntry> parseExFat(ByteSource source, long partOffset, byte[] boot, StringBuilder skippedLog) throws IOException {
        List<RecoveredEntry> entries = new ArrayList<>();
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
            int fatBytesToRead = (int) Math.min(fatLength * bytesPerSector, 8 * 1024 * 1024); // Rule: cap at 8 MB
            byte[] fatTable = new byte[fatBytesToRead];
            source.readAt(fatStartOffset, fatTable, fatBytesToRead);

            traverseExFatDirectory(source, clusterHeapStart, clusterSize, rootDirCluster, fatTable, clusterCount, "/", entries, new HashSet<Long>(), skippedLog);
        } catch (Exception e) {
            DiagnosticReporter.log("שגיאה בפענוח exFAT: " + e.getMessage());
        }
        return entries;
    }

    private static void traverseExFatDirectory(ByteSource source, long clusterHeapStart, long clusterSize, long startCluster, byte[] fatTable, long clusterCount, String currentPath, List<RecoveredEntry> entries, Set<Long> visitedClusters, StringBuilder skippedLog) throws IOException {
        long currentCluster = startCluster;
        int maxClusters = (int) Math.min(clusterCount, 5000); // Rule 4: Stop directory chain after ClusterCount links (loop protection)

        while (currentCluster >= 2 && currentCluster < 0xFFFFFFF8 && maxClusters-- > 0) {
            if (!visitedClusters.add(currentCluster)) break;

            long clusterOffset = clusterHeapStart + (currentCluster - 2) * clusterSize;
            if (clusterOffset < 0 || clusterOffset + clusterSize > source.getSize()) break;

            byte[] clusterData = new byte[(int) clusterSize];
            int read = source.readAt(clusterOffset, clusterData, (int) clusterSize);
            if (read <= 0) break;

            for (int i = 0; i < read - 31; i += 32) {
                int entryType = clusterData[i] & 0xFF;
                if (entryType == 0x00) break; // End of directory
                if ((entryType & 0x80) == 0) {
                    skippedLog.append("exFAT deleted entry skipped (type 0x").append(Integer.toHexString(entryType)).append(")\n");
                    continue;
                }
                if (entryType != 0x85) continue; // File directory entry

                if (i + 64 > read) break;
                int secondaryCount = clusterData[i + 1] & 0xFF;
                int attr = ByteBuffer.wrap(clusterData, i + 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                boolean isDir = (attr & 0x10) != 0;

                long startingCluster = 0;
                long dataLength = 0;
                boolean noFatChain = false;
                StringBuilder fileName = new StringBuilder();

                for (int s = 1; s <= secondaryCount && (i + s * 32 + 31) < read; s++) {
                    int secType = clusterData[i + s * 32] & 0xFF;
                    if (secType == 0xC0) {
                        int flags = clusterData[i + s * 32 + 1] & 0xFF;
                        noFatChain = (flags & 0x02) != 0; // Bit 1 NoFatChain
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
                FileType type = isDir ? null : FileTypeDetector.detectTypeByNameOrHeader(source, name, fileDataOffset, dataLength);
                byte[] rawNameBytes = name.getBytes(StandardCharsets.UTF_16LE);

                entries.add(new RecoveredEntry(name, currentPath, fileDataOffset, dataLength, type, false, isDir, System.currentTimeMillis(), rawNameBytes));

                if (isDir && startingCluster >= 2) {
                    String subPath = currentPath.equals("/") ? "/" + name : currentPath + "/" + name;
                    traverseExFatDirectory(source, clusterHeapStart, clusterSize, startingCluster, fatTable, clusterCount, subPath, entries, new HashSet<>(visitedClusters), skippedLog);
                }
            }

            currentCluster = getNextExFatCluster(fatTable, currentCluster);
        }
    }

    private static long getNextExFatCluster(byte[] fatTable, long cluster) {
        int index = (int) (cluster * 4);
        if (index + 4 > fatTable.length) return 0xFFFFFFF8L;
        return ByteBuffer.wrap(fatTable, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    }

    private static List<RecoveredEntry> parseFat32(ByteSource source, long partOffset, byte[] boot, boolean useSecondFatCopy, StringBuilder skippedLog) throws IOException {
        List<RecoveredEntry> entries = new ArrayList<>();
        try {
            ByteBuffer b = ByteBuffer.wrap(boot);
            b.order(ByteOrder.LITTLE_ENDIAN);

            int bytesPerSector = b.getShort(11) & 0xFFFF;
            int sectorsPerCluster = b.get(13) & 0xFF;
            int reserved = b.getShort(14) & 0xFFFF;
            int numFATs = b.get(16) & 0xFF;
            long sectorsPerFAT = b.getInt(36) & 0xFFFFFFFFL;
            long rootCluster = b.getInt(44) & 0xFFFFFFFFL;

            long fatStartOffset = partOffset + (long) reserved * bytesPerSector;
            if (useSecondFatCopy && numFATs > 1) {
                fatStartOffset += sectorsPerFAT * bytesPerSector;
                DiagnosticReporter.log("משתמש בטבלת FAT שנייה באופסט " + fatStartOffset);
            }

            long clusterRegionStart = partOffset + (long) reserved * bytesPerSector + (long) numFATs * sectorsPerFAT * bytesPerSector;
            long clusterSize = (long) sectorsPerCluster * bytesPerSector;

            DiagnosticReporter.setFirstDataSector(String.valueOf(clusterRegionStart / 512));

            int fatBytesToRead = (int) Math.min(sectorsPerFAT * bytesPerSector, 8 * 1024 * 1024); // Rule: cap at 8 MB
            byte[] fatTable = new byte[fatBytesToRead];
            source.readAt(fatStartOffset, fatTable, fatBytesToRead);

            traverseFat32Directory(source, clusterRegionStart, clusterSize, rootCluster, fatTable, "/", entries, new HashSet<Long>(), skippedLog);
        } catch (Exception e) {
            DiagnosticReporter.log("שגיאה בפענוח FAT32: " + e.getMessage());
        }
        return entries;
    }

    private static void traverseFat32Directory(ByteSource source, long clusterRegionStart, long clusterSize, long startCluster, byte[] fatTable, String currentPath, List<RecoveredEntry> entries, Set<Long> visitedClusters, StringBuilder skippedLog) throws IOException {
        long currentCluster = startCluster;
        int maxClusters = 5000; // Rule 4: Loop protection
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
                if ((attr & 0x08) != 0) {
                    lfnBuffer.clear();
                    continue; // Volume label
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
        if (lfn.isEmpty()) return shortName;
        return lfn;
    }

    private static long getNextFat32Cluster(byte[] fatTable, long cluster) {
        int index = (int) (cluster * 4);
        if (index + 4 > fatTable.length) return 0x0FFFFFFF;
        return ByteBuffer.wrap(fatTable, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x0FFFFFFF;
    }

    private static List<RecoveredEntry> scanOrphanDirectories(ByteSource source, long partOffset, long totalSize, StringBuilder skippedLog) {
        List<RecoveredEntry> entries = new ArrayList<>();
        // Rule 5(c): Orphan directory scan in data area
        long scanLimit = Math.min(totalSize, partOffset + 512 * 1024 * 1024L);
        byte[] clusterBuf = new byte[4096];
        for (long off = partOffset + 4096; off + 4096 <= scanLimit; off += 4096) {
            try {
                if (source.readAt(off, clusterBuf, 4096) == 4096) {
                    // Check FAT32 dot entry "." at offset 0
                    if (clusterBuf[0] == '.' && clusterBuf[1] == ' ' && (clusterBuf[11] & 0x10) != 0) {
                        String name = "orphan_dir_" + Long.toHexString(off);
                        entries.add(new RecoveredEntry(name, "/", off, 4096, null, false, true, System.currentTimeMillis(), name.getBytes()));
                    }
                }
            } catch (Exception ignored) {}
        }
        return entries;
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
}
