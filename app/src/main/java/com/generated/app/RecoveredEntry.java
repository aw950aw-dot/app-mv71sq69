package com.generated.app;

public class RecoveredEntry {
    private final String name;
    private final String path;
    private final long offset;
    private final long length;
    private final FileType type;
    private final boolean isDeleted;
    private final boolean isDirectory;
    private boolean selected;

    public RecoveredEntry(String name, String path, long offset, long length, FileType type, boolean isDeleted, boolean isDirectory) {
        this.name = name;
        this.path = path;
        this.offset = offset;
        this.length = length;
        this.type = type;
        this.isDeleted = isDeleted;
        this.isDirectory = isDirectory;
        this.selected = true;
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

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
