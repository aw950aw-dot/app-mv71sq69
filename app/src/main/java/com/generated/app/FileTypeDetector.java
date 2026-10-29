package com.generated.app;

import java.io.IOException;

public class FileTypeDetector {

    /** Type from the file name only. Never reads from the card (one USB read per file made scans very slow). */
    public static FileType detectTypeByName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return FileType.UNKNOWN;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) return FileType.UNKNOWN;
        String e = fileName.substring(lastDot + 1).toLowerCase();
        switch (e) {
            case "jpg": case "jpeg": return FileType.JPEG;
            case "png": return FileType.PNG;
            case "gif": return FileType.GIF;
            case "pdf": return FileType.PDF;
            case "zip": case "apk": case "jar": return FileType.ZIP;
            case "mp3": return FileType.MP3;
            case "wav": return FileType.WAV;
            case "mp4": case "m4a": return FileType.MP4;
            default: return FileType.UNKNOWN;
        }
    }

    public static FileType detectTypeByNameOrHeader(ByteSource source, String fileName, long offset, long length) {
        if (fileName == null || fileName.isEmpty()) {
            return FileType.UNKNOWN;
        }

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return FileType.UNKNOWN;
        }

        String ext = fileName.substring(lastDot + 1);
        for (int i = 0; i < ext.length(); i++) {
            if (Character.isUpperCase(ext.charAt(i))) {
                return FileType.UNKNOWN; // Uppercase extension rejected per rule 1
            }
        }

        String lowerExt = ext.toLowerCase();
        if (lowerExt.equals("jpg") || lowerExt.equals("jpeg")) return FileType.JPEG;
        if (lowerExt.equals("png")) return FileType.PNG;
        if (lowerExt.equals("gif")) return FileType.GIF;
        if (lowerExt.equals("pdf")) return FileType.PDF;
        if (lowerExt.equals("zip") || lowerExt.equals("apk") || lowerExt.equals("jar")) return FileType.ZIP;
        if (lowerExt.equals("mp3")) return FileType.MP3;
        if (lowerExt.equals("wav")) return FileType.WAV;
        if (lowerExt.equals("mp4") || lowerExt.equals("m4a")) return FileType.MP4;

        try {
            byte[] header = new byte[16];
            int read = source.readAt(offset, header, 16);
            if (read > 0) {
                for (FileType type : FileType.values()) {
                    if (type == FileType.UNKNOWN) continue;
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

        return FileType.UNKNOWN;
    }
}
