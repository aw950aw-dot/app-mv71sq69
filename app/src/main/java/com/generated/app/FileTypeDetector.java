package com.generated.app;

import java.io.IOException;

public class FileTypeDetector {

    public static FileType detectTypeByNameOrHeader(ByteSource source, String fileName, long offset, long length) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return FileType.JPEG;
        if (lower.endsWith(".png")) return FileType.PNG;
        if (lower.endsWith(".gif")) return FileType.GIF;
        if (lower.endsWith(".pdf")) return FileType.PDF;
        if (lower.endsWith(".zip") || lower.endsWith(".apk") || lower.endsWith(".jar")) return FileType.ZIP;
        if (lower.endsWith(".mp3")) return FileType.MP3;
        if (lower.endsWith(".wav")) return FileType.WAV;
        if (lower.endsWith(".mp4") || lower.endsWith(".m4a")) return FileType.MP4;

        // Fallback to signature check at offset
        try {
            byte[] header = new byte[16];
            int read = source.readAt(offset, header, 16);
            if (read > 0) {
                for (FileType type : FileType.values()) {
                    byte[][] sigs = type.getSignatures();
                    if (sigs == null) continue;
                    for (byte[] sig : sigs) {
                        if (read >= sig.length) {
                            boolean match = true;
                            for (int i = 0; i < sig.length; i++) {
                                if (header[i] != sig[i]) {
                                    match = false;
                                    break;
                                }
                            }
                            if (match) return type;
                        }
                    }
                }
            }
        } catch (IOException ignored) {}

        return FileType.JPEG; // Default fallback
    }
}
