package com.generated.app;

public class RecoveredEntry {
    private final String name;
    private final String path;
    private final long offset;
    private final long length;
    private final FileType type;
    private final boolean isDeleted;
    private final boolean isDirectory;
    private final long modifiedTime;
    private final byte[] rawNameBytes; // 32 bytes directory entry or raw blob
    
    private int selectedState; // 0 = none, 1 = selected, 2 = partial
    private long cachedRecursiveSize;
    private int cachedRecursiveFileCount;

    public RecoveredEntry(String name, String path, long offset, long length, FileType type, boolean isDeleted, boolean isDirectory, long modifiedTime, byte[] rawNameBytes) {
        this.name = name != null ? name : "unnamed";
        this.path = path != null ? path : "/";
        this.offset = offset;
        this.length = length;
        this.type = type;
        this.isDeleted = isDeleted;
        this.isDirectory = isDirectory;
        this.modifiedTime = modifiedTime;
        this.rawNameBytes = rawNameBytes != null ? rawNameBytes : new byte[0];
        this.selectedState = 0; // Rule 2: Nothing is selected by default
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getOffset() {
        return offset;
    }

    public long getLength() {
        return length;
    }

    public FileType getType() {
        return type;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public long getModifiedTime() {
        return modifiedTime;
    }

    public byte[] getRawNameBytes() {
        return rawNameBytes;
    }

    public int getSelectedState() {
        return selectedState;
    }

    public void setSelectedState(int selectedState) {
        this.selectedState = selectedState;
    }

    public boolean isSelected() {
        return selectedState == 1;
    }

    public boolean isPartial() {
        return selectedState == 2;
    }

    public long getCachedRecursiveSize() {
        return cachedRecursiveSize;
    }

    public void setCachedRecursiveSize(long size) {
        this.cachedRecursiveSize = size;
    }

    public int getCachedRecursiveFileCount() {
        return cachedRecursiveFileCount;
    }

    public void setCachedRecursiveFileCount(int count) {
        this.cachedRecursiveFileCount = count;
    }
}
