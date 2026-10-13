package com.generated.app;

import java.io.IOException;
import java.util.Arrays;

public class Carver {

    private static final int CHUNK_SIZE = 2 * 1024 * 1024; // 2 MB
    private static final int OVERLAP_SIZE = 64 * 1024; // 64 KB overlap to handle signatures/footers spanning chunks

    public void carve(ByteSource source, CarverCallback callback) throws IOException {
        long totalSize = source.getSize();
        if (totalSize <= 0) {
            if (callback != null) {
                callback.onProgress(0, 0);
            }
            return;
        }

        long currentOffset = 0;
        byte[] chunk = new byte[CHUNK_SIZE + OVERLAP_SIZE];

        while (currentOffset < totalSize) {
            int bytesToRead = (int) Math.min(chunk.length, totalSize - currentOffset);
            int bytesRead = source.readAt(currentOffset, chunk, bytesToRead);
            if (bytesRead <= 0) {
                break;
            }

            // Scan this chunk for signatures
            int scanLimit = Math.min(bytesRead, CHUNK_SIZE);
            if (currentOffset + scanLimit > totalSize) {
                scanLimit = (int) (totalSize - currentOffset);
            }

            for (int i = 0; i < scanLimit; i++) {
                FileType matchedType = matchSignature(chunk, i, bytesRead);
                if (matchedType != null) {
                    long absoluteStart = currentOffset + i;
                    long fileLength = determineFileLength(source, absoluteStart, totalSize, matchedType);
                    
                    if (fileLength > 0) {
                        CarvedFile carved = new CarvedFile(absoluteStart, fileLength, matchedType);
                        if (callback != null) {
                            callback.onFileFound(carved);
                        }
                    }
                }
            }

            currentOffset += CHUNK_SIZE;
            if (callback != null) {
                callback.onProgress(Math.min(currentOffset, totalSize), totalSize);
            }

            if (currentOffset >= totalSize) {
                break;
            }
        }
    }

    private FileType matchSignature(byte[] buffer, int index, int validLength) {
        for (FileType type : FileType.values()) {
            byte[][] sigs = type.getSignatures();
            if (sigs == null) continue;

            for (byte[] sig : sigs) {
                // Special handling for MP4 'ftyp' which starts 4 bytes in ('isom', 'mp42', etc. preceded by size)
                if (type == FileType.MP4) {
                    if (index >= 4 && index + sig.length <= validLength) {
                        if (matchAt(buffer, index, sig)) {
                            // Verify preceding 4 bytes represent a reasonable box size or brand context
                            return type;
                        }
                    }
                } else {
                    if (index + sig.length <= validLength) {
                        if (matchAt(buffer, index, sig)) {
                            return type;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean matchAt(byte[] buffer, int index, byte[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            if (buffer[index + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private long determineFileLength(ByteSource source, long startOffset, long totalSize, FileType type) {
        long maxSize = Math.min(type.getMaxSafetySize(), totalSize - startOffset);
        if (maxSize <= 0) return 1024; // fallback

        byte[][] footers = type.getFooters();
        if (footers == null || footers.length == 0) {
            // No strict footer, use default safety size or logical bounds (capped at reasonable chunk/file limits)
            return Math.min(4096, maxSize);
        }

        // Read forward in chunks to find footer
        long searchLength = maxSize;
        long readOffset = startOffset;
        byte[] readBuffer = new byte[65536]; // 64KB read buffer
        long bytesSearched = 0;

        while (bytesSearched < searchLength) {
            int toRead = (int) Math.min(readBuffer.length, searchLength - bytesSearched);
            try {
                int n = source.readAt(readOffset, readBuffer, toRead);
                if (n <= 0) break;

                for (int i = 0; i < n; i++) {
                    for (byte[] footer : footers) {
                        if (i + footer.length <= n) {
                            if (matchAt(readBuffer, i, footer)) {
                                long footerAbsolutePos = readOffset + i;
                                return (footerAbsolutePos - startOffset) + footer.length;
                            }
                        }
                    }
                }

                readOffset += n;
                bytesSearched += n;
            } catch (IOException e) {
                break;
            }
        }

        // Fallback if footer not found within max safety size
        return Math.min(8192, totalSize - startOffset);
    }
}
