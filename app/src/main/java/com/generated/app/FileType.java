package com.generated.app;

public enum FileType {
    JPEG("JPEG", "image/jpeg", new byte[][]{{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}}, new byte[][]{{(byte) 0xFF, (byte) 0xD9}}, 20 * 1024 * 1024L),
    PNG("PNG", "image/png", new byte[][]{{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}}, new byte[][]{{(byte) 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82}}, 50 * 1024 * 1024L),
    GIF("GIF", "image/gif", new byte[][]{{0x47, 0x49, 0x46, 0x38, 0x37, 0x61}, {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}}, new byte[][]{{0x00, 0x3B}}, 30 * 1024 * 1024L),
    PDF("PDF", "application/pdf", new byte[][]{{0x25, 0x50, 0x44, 0x46}}, new byte[][]{{0x25, 0x25, 0x45, 0x4F, 0x46}}, 100 * 1024 * 1024L),
    ZIP("ZIP", "application/zip", new byte[][]{{0x50, 0x4B, 0x03, 0x04}}, null, 200 * 1024 * 1024L),
    MP3("MP3", "audio/mpeg", new byte[][]{{(byte) 0xFF, (byte) 0xFB}, {(byte) 0xFF, (byte) 0xF3}, {(byte) 0xFF, (byte) 0xF2}, {'I', 'D', '3'}}, null, 50 * 1024 * 1024L),
    WAV("WAV", "audio/wav", new byte[][]{{'R', 'I', 'F', 'F'}}, null, 100 * 1024 * 1024L),
    MP4("MP4", "video/mp4", new byte[][]{{'f', 't', 'y', 'p'}}, null, 500 * 1024 * 1024L);

    private final String name;
    private final String mimeType;
    private final byte[][] signatures;
    private final byte[][] footers;
    private final long maxSafetySize;

    FileType(String name, String mimeType, byte[][] signatures, byte[][] footers, long maxSafetySize) {
        this.name = name;
        this.mimeType = mimeType;
        this.signatures = signatures;
        this.footers = footers;
        this.maxSafetySize = maxSafetySize;
    }

    public String getName() {
        return name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public byte[][] getSignatures() {
        return signatures;
    }

    public byte[][] getFooters() {
        return footers;
    }

    public long getMaxSafetySize() {
        return maxSafetySize;
    }
}
