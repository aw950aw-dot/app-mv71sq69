package com.generated.app;

public class RecoveredEntry {
    private final long id;
    private final long parentId;
    private final String name;
    private final String path;
    private final long offset;
    private final long length;
    private final FileType type;
    private final boolean isDeleted;
    private final boolean isDirectory;
    private final long modifiedTime;
    private final byte[] rawNameBytes;
    private final long firstCluster;
    
    private int selectedState;
    private long cachedRecursiveSize;
    private int cachedRecursiveFileCount;
    private boolean isUnreadable;

    public RecoveredEntry(long id, long parentId, String name, String path, long offset, long length, FileType type, boolean isDeleted, boolean isDirectory, long modifiedTime, byte[] rawNameBytes, long firstCluster) {
        this.id = id;
        this.parentId = parentId;
        this.name = name != null ? name : "unnamed";
        this.path = path != null ? path : "/";
        this.offset = offset;
        this.length = length;
        this.type = type;
        this.isDeleted = isDeleted;
        this.isDirectory = isDirectory;
        this.modifiedTime = modifiedTime;
        this.rawNameBytes = rawNameBytes != null ? rawNameBytes : new byte[0];
        this.firstCluster = firstCluster;
        this.selectedState = 0;
        this.isUnreadable = false;
    }

    public RecoveredEntry(String name, String path, long offset, long length, FileType type, boolean isDeleted, boolean isDirectory, long modifiedTime, byte[] rawNameBytes, long firstCluster) {
        this(0, 0, name, path, offset, length, type, isDeleted, isDirectory, modifiedTime, rawNameBytes, firstCluster);
    }

    public long getId() {
        return id;
    }

    public long getParentId() {
        return parentId;
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

    public long getFirstCluster() {
        return firstCluster;
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

    public boolean isUnreadable() {
        return isUnreadable;
    }

    public void setUnreadable(boolean unreadable) {
        isUnreadable = unreadable;
    }
}
