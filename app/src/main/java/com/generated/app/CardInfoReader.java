package com.generated.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public class CardInfoReader {

    public static CardInfo read(Context context, UsbDevice device) {
        if (ScanService.isScanRunning() || RestoreService.isRestoreRunning()) {
            return new CardInfo(device != null ? device.getProductName() : "Unknown", 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, "Scan or Restore service is currently running.", 0, 0, 0, 0, 0);
        }

        if (device == null || context == null) {
            return new CardInfo("Unknown", 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, "Invalid device or context.", 0, 0, 0, 0, 0);
        }

        String readerName = device.getProductName() != null ? device.getProductName() : "USB Card Reader";
        ByteSource source = null;

        try {
            source = new UsbMassStorageSource(context, device);
            long totalSize = source.getSize();
            if (totalSize <= 0) {
                return new CardInfo(readerName, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, "Card size is zero or unreadable.", 0, 0, 0, 0, 0);
            }

            byte[] sector0 = new byte[512];
            long partitionStart = 0;
            if (source.readAt(0, sector0, 512) >= 512) {
                if (sector0[510] == (byte) 0x55 && sector0[511] == (byte) 0xAA) {
                    int pOffset = 446 + 8; // Offset 446 + 8 for first partition start sector
                    ByteBuffer bb = ByteBuffer.wrap(sector0, pOffset, 4);
                    bb.order(ByteOrder.LITTLE_ENDIAN);
                    long startSector = bb.getInt() & 0xFFFFFFFFL;
                    if (startSector > 0 && startSector < totalSize / 512) {
                        partitionStart = startSector;
                    }
                }
            }

            long volumeStartOffset = partitionStart * 512;
            byte[] bootSector = new byte[512];
            boolean isFat32 = false;
            int bytesPerSector = 512;
            int sectorsPerCluster = 0;
            int reservedSectors = 0;
            int fatCount = 0;
            long sectorsPerFat = 0;
            long totalClusters = 0;
            long volumeSerial = 0;
            long usedSizeBytes = 0;
            long freeSizeBytes = totalSize;

            if (volumeStartOffset < totalSize && source.readAt(volumeStartOffset, bootSector, 512) == 512) {
                if (bootSector[510] == (byte) 0x55 && bootSector[511] == (byte) 0xAA) {
                    ByteBuffer b = ByteBuffer.wrap(bootSector);
                    b.order(ByteOrder.LITTLE_ENDIAN);
                    bytesPerSector = b.getShort(11) & 0xFFFF;
                    sectorsPerCluster = b.get(13) & 0xFF;
                    reservedSectors = b.getShort(14) & 0xFFFF;
                    fatCount = b.get(16) & 0xFF;
                    sectorsPerFat = b.getInt(36) & 0xFFFFFFFFL;
                    long rootCluster = b.getInt(44) & 0xFFFF;
                    long totalSectors = b.getShort(19) & 0xFFFF;
                    if (totalSectors == 0) {
                        totalSectors = b.getInt(32) & 0xFFFFFFFFL;
                    }

                    if (bytesPerSector > 0 && sectorsPerCluster > 0 && reservedSectors > 0 && fatCount > 0 && sectorsPerFat > 0 && rootCluster >= 2) {
                        isFat32 = true;
                        volumeSerial = b.getInt(67) & 0xFFFFFFFFL;

                        long dataSectors = totalSectors - (reservedSectors + (fatCount * sectorsPerFat));
                        totalClusters = dataSectors / sectorsPerCluster;
                        long clusterSize = (long) sectorsPerCluster * bytesPerSector;

                        long fatStartOffset = volumeStartOffset + (long) reservedSectors * bytesPerSector;
                        long fatBytesTotal = sectorsPerFat * bytesPerSector;

                        long usedClustersCount = 0;
                        byte[] fatReadBuf = new byte[65536]; // 64 KB reads
                        long fatBytesRead = 0;

                        while (fatBytesRead < fatBytesTotal && fatBytesRead < (totalClusters + 2) * 4) {
                            int toRead = (int) Math.min(fatReadBuf.length, fatBytesTotal - fatBytesRead);
                            int n = source.readAt(fatStartOffset + fatBytesRead, fatReadBuf, toRead);
                            if (n <= 0) break;

                            ByteBuffer fatBb = ByteBuffer.wrap(fatReadBuf, 0, n);
                            fatBb.order(ByteOrder.LITTLE_ENDIAN);

                            for (int i = 0; i <= n - 4; i += 4) {
                                long clusterIndex = (fatBytesRead + i) / 4;
                                if (clusterIndex >= 2 && clusterIndex <= totalClusters + 1) {
                                    long entryVal = fatBb.getInt(i) & 0x0FFFFFFF;
                                    if (entryVal != 0) {
                                        usedClustersCount++;
                                    }
                                }
                            }
                            fatBytesRead += n;
                        }

                        usedSizeBytes = usedClustersCount * clusterSize;
                        freeSizeBytes = Math.max(0, totalSize - usedSizeBytes);
                    }
                }
            }

            // Speed test: read 8 MB from the middle of the card in 64 KB reads and compute bytes per second
            long measuredSpeedBytesPerSec = 0;
            try {
                long middleOffset = totalSize / 2;
                long targetReadBytes = 8L * 1024L * 1024L; // 8 MB
                long bytesToRead = Math.min(targetReadBytes, Math.max(0, totalSize - middleOffset));
                if (bytesToRead > 0) {
                    byte[] testBuf = new byte[65536]; // 64 KB
                    long startTime = System.currentTimeMillis();
                    long bytesActuallyRead = 0;
                    long currentOff = middleOffset;

                    while (bytesActuallyRead < bytesToRead) {
                        int toRead = (int) Math.min(testBuf.length, bytesToRead - bytesActuallyRead);
                        int n = source.readAt(currentOff, testBuf, toRead);
                        if (n <= 0) break;
                        bytesActuallyRead += n;
                        currentOff += n;
                    }

                    long durationMs = System.currentTimeMillis() - startTime;
                    if (durationMs > 0 && bytesActuallyRead > 0) {
                        measuredSpeedBytesPerSec = (bytesActuallyRead * 1000L) / durationMs;
                    }
                }
            } catch (Exception ignored) {}

            if (measuredSpeedBytesPerSec <= 0) {
                measuredSpeedBytesPerSec = 5L * 1024L * 1024L; // Fallback 5 MB/s
            }

            // Deep scan estimation: card size / speed x 1.1
            long deepScanBaseSec = (long) Math.ceil((double) totalSize / measuredSpeedBytesPerSec * 1.1);
            long deepScanMinSec = (long) (deepScanBaseSec * 0.75);
            long deepScanMaxSec = (long) (deepScanBaseSec * 1.25);

            // Regular scan estimation
            long regularScanBaseSec = 0;
            try {
                SharedPreferences prefs = context.getSharedPreferences("sd_rescue_prefs", Context.MODE_PRIVATE);
                long prevDurationMs = prefs.getLong("scan_duration_" + volumeSerial, 0);
                if (prevDurationMs > 0) {
                    regularScanBaseSec = prevDurationMs / 1000L;
                }
            } catch (Exception ignored) {}

            if (regularScanBaseSec <= 0) {
                double usedGb = (double) usedSizeBytes / (1024.0 * 1024.0 * 1024.0);
                regularScanBaseSec = (long) Math.max(30.0, usedGb * 3.0);
            }

            long regularScanMinSec = (long) (regularScanBaseSec * 0.75);
            long regularScanMaxSec = (long) (regularScanBaseSec * 1.25);

            try {
                SharedPreferences prefs = context.getSharedPreferences("sd_rescue_prefs", Context.MODE_PRIVATE);
                prefs.edit().putLong("last_card_serial", volumeSerial).apply();
            } catch (Exception ignored) {}

            return new CardInfo(readerName, totalSize, partitionStart, isFat32, bytesPerSector,
                    sectorsPerCluster, reservedSectors, fatCount, sectorsPerFat, totalClusters,
                    volumeSerial, usedSizeBytes, freeSizeBytes, null,
                    measuredSpeedBytesPerSec, regularScanMinSec, regularScanMaxSec,
                    deepScanMinSec, deepScanMaxSec);

        } catch (Exception e) {
            return new CardInfo(readerName, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, "Error reading card info: " + e.getMessage(), 0, 0, 0, 0, 0);
        } finally {
            if (source != null) {
                try {
                    source.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
