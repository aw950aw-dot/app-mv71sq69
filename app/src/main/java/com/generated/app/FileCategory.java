package com.generated.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class FileCategory {
    public static final String PHOTOS = "PHOTOS";
    public static final String VIDEOS = "VIDEOS";
    public static final String MUSIC = "MUSIC";
    public static final String DOCUMENTS = "DOCUMENTS";
    public static final String ARCHIVES_APPS = "ARCHIVES_APPS";
    public static final String OTHER = "OTHER";

    private static final Set<String> PHOTO_EXTS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "jpe", "png", "gif", "webp", "heic", "heif", "bmp", "dng"
    ));

    private static final Set<String> VIDEO_EXTS = new HashSet<>(Arrays.asList(
            "mp4", "mov", "mkv", "avi", "3gp", "webm", "m4v", "mpg", "mpeg", "mts"
    ));

    private static final Set<String> MUSIC_EXTS = new HashSet<>(Arrays.asList(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "amr"
    ));

    private static final Set<String> DOC_EXTS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "rtf", "csv"
    ));

    private static final Set<String> ARCHIVE_EXTS = new HashSet<>(Arrays.asList(
            "zip", "rar", "7z", "apk"
    ));

    public static String determineCategory(String fileName, boolean isDirectory) {
        if (isDirectory || fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return OTHER;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
        if (PHOTO_EXTS.contains(ext)) {
            return PHOTOS;
        }
        if (VIDEO_EXTS.contains(ext)) {
            return VIDEOS;
        }
        if (MUSIC_EXTS.contains(ext)) {
            return MUSIC;
        }
        if (DOC_EXTS.contains(ext)) {
            return DOCUMENTS;
        }
        if (ARCHIVE_EXTS.contains(ext)) {
            return ARCHIVES_APPS;
        }
        return OTHER;
    }
}
