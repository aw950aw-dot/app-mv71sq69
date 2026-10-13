package com.generated.app;

public class CarvedFile {
    private final long offset;
    private final long length;
    private final FileType type;

    public CarvedFile(long offset, long length, FileType type) {
        this.offset = offset;
        this.length = length;
        this.type = type;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CarvedFile that = (CarvedFile) o;
        return offset == that.offset && length == that.length && type == that.type;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(offset);
        result = 31 * result + Long.hashCode(length);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CarvedFile{" +
                "offset=" + offset +
                ", length=" + length +
                ", type=" + type +
                '}';
    }
}
