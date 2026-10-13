package com.generated.app;

import java.util.ArrayList;
import java.util.List;

public class RecoveredEntry {
    private final String name;
    private final String path;
    private final long offset;
    private final long length;
    private final FileType type;
    private final boolean isDeleted;
    private final boolean isDirectory;
    private final long modifiedTime;
    private final byte[] rawNameBytes;
    
    private boolean selected;
    private final List<RecoveredEntry> children = new ArrayList<>();
    private RecoveredEntry parent;

    public RecoveredEntry(String name, String path, long offset, long length, FileType type, boolean isDeleted, boolean isDirectory, long modifiedTime, byte[] rawNameBytes) {
        this.name = name;
        this.path = path;
        this.offset = offset;
        this.length = length;
        this.type = type;
        this.isDeleted = isDeleted;
        this.isDirectory = isDirectory;
        this.modifiedTime = modifiedTime;
        this.rawNameBytes = rawNameBytes;
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

    public long getModifiedTime() {
        return modifiedTime;
    }

    public byte[] getRawNameBytes() {
        return rawNameBytes;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        for (RecoveredEntry child : children) {
            child.setSelected(selected);
        }
    }

    public List<RecoveredEntry> getChildren() {
        return children;
    }

    public void addChild(RecoveredEntry child) {
        child.parent = this;
        children.add(child);
    }

    public RecoveredEntry getParent() {
        return parent;
    }

    public long getRecursiveSize() {
        if (!isDirectory) return length;
        long sum = 0;
        for (RecoveredEntry child : children) {
            sum += child.getRecursiveSize();
        }
        return sum;
    }

    public int getRecursiveFileCount() {
        if (!isDirectory) return 1;
        int count = 0;
        for (RecoveredEntry child : children) {
            count += child.getRecursiveFileCount();
        }
        return count;
    }

    public void updateSelectionStateFromChildren() {
        if (!isDirectory) return;
        if (children.isEmpty()) return;
        boolean allSelected = true;
        boolean noneSelected = true;
        for (RecoveredEntry child : children) {
            if (child.isDirectory()) {
                child.updateSelectionStateFromChildren();
            }
            if (child.isSelected()) {
                noneSelected = false;
            } else {
                allSelected = false;
            }
        }
        if (allSelected) {
            this.selected = true;
        } else if (noneSelected) {
            this.selected = false;
        } else {
            this.selected = false; // partial
        }
    }
}
