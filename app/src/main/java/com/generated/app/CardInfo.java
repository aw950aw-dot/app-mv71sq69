package com.generated.app;

public class CardInfo {
    private final String readerName;
    private final long totalSizeBytes;
    private final long partitionStartSector;
    private final boolean isFat32;
    private final int bytesPerSector;
    private final int sectorsPerCluster;
    private final int reservedSectors;
    private final int fatCount;
    private final long sectorsPerFat;
    private final long totalClusters;
    private final long volumeSerial;
    private final long usedSizeBytes;
    private final long freeSizeBytes;
    private final String errorText;
    private final long measuredSpeedBytesPerSec;
    private final long regularScanEstMinSec;
    private final long regularScanEstMaxSec;
    private final long deepScanEstMinSec;
    private final long deepScanEstMaxSec;

    public CardInfo(String readerName, long totalSizeBytes, long partitionStartSector,
                    boolean isFat32, int bytesPerSector, int sectorsPerCluster,
                    int reservedSectors, int fatCount, long sectorsPerFat,
                    long totalClusters, long volumeSerial, long usedSizeBytes,
                    long freeSizeBytes, String errorText,
                    long measuredSpeedBytesPerSec,
                    long regularScanEstMinSec, long regularScanEstMaxSec,
                    long deepScanEstMinSec, long deepScanEstMaxSec) {
        this.readerName = readerName;
        this.totalSizeBytes = totalSizeBytes;
        this.partitionStartSector = partitionStartSector;
        this.isFat32 = isFat32;
        this.bytesPerSector = bytesPerSector;
        this.sectorsPerCluster = sectorsPerCluster;
        this.reservedSectors = reservedSectors;
        this.fatCount = fatCount;
        this.sectorsPerFat = sectorsPerFat;
        this.totalClusters = totalClusters;
        this.volumeSerial = volumeSerial;
        this.usedSizeBytes = usedSizeBytes;
        this.freeSizeBytes = freeSizeBytes;
        this.errorText = errorText;
        this.measuredSpeedBytesPerSec = measuredSpeedBytesPerSec;
        this.regularScanEstMinSec = regularScanEstMinSec;
        this.regularScanEstMaxSec = regularScanEstMaxSec;
        this.deepScanEstMinSec = deepScanEstMinSec;
        this.deepScanEstMaxSec = deepScanEstMaxSec;
    }

    public String getReaderName() { return readerName; }
    public long getTotalSizeBytes() { return totalSizeBytes; }
    public long getPartitionStartSector() { return partitionStartSector; }
    public boolean isFat32() { return isFat32; }
    public int getBytesPerSector() { return bytesPerSector; }
    public int getSectorsPerCluster() { return sectorsPerCluster; }
    public int getReservedSectors() { return reservedSectors; }
    public int getFatCount() { return fatCount; }
    public long getSectorsPerFat() { return sectorsPerFat; }
    public long getTotalClusters() { return totalClusters; }
    public long getVolumeSerial() { return volumeSerial; }
    public long getUsedSizeBytes() { return usedSizeBytes; }
    public long getFreeSizeBytes() { return freeSizeBytes; }
    public String getErrorText() { return errorText; }
    public long getMeasuredSpeedBytesPerSec() { return measuredSpeedBytesPerSec; }
    public long getRegularScanEstMinSec() { return regularScanEstMinSec; }
    public long getRegularScanEstMaxSec() { return regularScanEstMaxSec; }
    public long getDeepScanEstMinSec() { return deepScanEstMinSec; }
    public long getDeepScanEstMaxSec() { return deepScanEstMaxSec; }
}
