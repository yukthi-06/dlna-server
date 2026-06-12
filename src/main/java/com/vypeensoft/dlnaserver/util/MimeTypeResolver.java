package com.vypeensoft.dlnaserver.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Utility to map file extensions to MIME types and media categories.
 */
public final class MimeTypeResolver {

    private static final Map<String, String> EXT_TO_MIME = new HashMap<>();
    private static final Map<String, String> MIME_TO_DLNA = new HashMap<>();

    static {
        // Videos
        EXT_TO_MIME.put("mp4", "video/mp4");
        EXT_TO_MIME.put("mkv", "video/x-matroska");
        EXT_TO_MIME.put("avi", "video/x-msvideo");
        EXT_TO_MIME.put("mov", "video/quicktime");

        // Audio
        EXT_TO_MIME.put("mp3", "audio/mpeg");
        EXT_TO_MIME.put("flac", "audio/flac");
        EXT_TO_MIME.put("wav", "audio/wav");
        EXT_TO_MIME.put("m4a", "audio/mp4");
        EXT_TO_MIME.put("aac", "audio/aac");

        // Images
        EXT_TO_MIME.put("jpg", "image/jpeg");
        EXT_TO_MIME.put("jpeg", "image/jpeg");
        EXT_TO_MIME.put("png", "image/png");

        // DLNA Profile Names (e.g. DLNA.ORG_PN)
        MIME_TO_DLNA.put("video/mp4", "MP4_SP_AAC_L2");
        MIME_TO_DLNA.put("video/x-matroska", "MKV");
        MIME_TO_DLNA.put("video/x-msvideo", "AVI");
        MIME_TO_DLNA.put("video/quicktime", "MOV");
        MIME_TO_DLNA.put("audio/mpeg", "MP3");
        MIME_TO_DLNA.put("audio/flac", "FLAC");
        MIME_TO_DLNA.put("audio/wav", "LPCM");
        MIME_TO_DLNA.put("audio/mp4", "AAC_ADTS");
        MIME_TO_DLNA.put("audio/aac", "AAC_ADTS");
        MIME_TO_DLNA.put("image/jpeg", "JPEG_LRG");
        MIME_TO_DLNA.put("image/png", "PNG_LRG");
    }

    private MimeTypeResolver() {
        // Prevent instantiation
    }

    /**
     * Resolves the MIME type of a file name or path.
     *
     * @param filename the file name
     * @return the MIME type, or "application/octet-stream" if unknown
     */
    public static String getMimeType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "application/octet-stream";
        }
        String ext = filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        return EXT_TO_MIME.getOrDefault(ext, "application/octet-stream");
    }

    /**
     * Returns the set of supported file extensions.
     */
    public static Set<String> getSupportedExtensions() {
        return EXT_TO_MIME.keySet();
    }

    /**
     * Checks if the given MIME type represents a video.
     */
    public static boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }

    /**
     * Checks if the given MIME type represents audio.
     */
    public static boolean isAudio(String mimeType) {
        return mimeType != null && mimeType.startsWith("audio/");
    }

    /**
     * Checks if the given MIME type represents an image.
     */
    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * Gets the DLNA profile/org-pn mapping for a MIME type.
     */
    public static String getDlnaProfile(String mimeType) {
        return MIME_TO_DLNA.getOrDefault(mimeType, "*");
    }
}
