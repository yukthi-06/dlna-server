package com.vypeensoft.dlnaserver.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Utility to map file extensions to MIME types and media categories.
 */
public final class MimeTypeResolver {

    private static final Map<String, String> EXT_TO_MIME = new HashMap<>();
    private static final Map<String, String> MIME_TO_DLNA = new HashMap<>();

    /**
     * Ordered list of DLNA.ORG_PN profiles for video/mp4, from highest to lowest priority.
     * LG NetCast TVs (e.g. 47LA6200) require multiple &lt;res&gt; elements, each with a different
     * profile, so the TV can select the decoder it recognises. Mirrors MiniDLNA LG behaviour.
     * Priority order:
     *   1. AVC_MP4_MP_HD_1080i_AAC  – H.264 Main Profile, Full HD, AAC stereo
     *   2. AVC_MP4_MP_HD_720p_AAC  – H.264 Main Profile, 720p, AAC stereo
     *   3. AVC_MP4_BL_L3L_SD_AAC   – H.264 Baseline Level 3 Low, SD, AAC (broadest compat)
     */
    public static final List<String> VIDEO_MP4_PROFILES = Collections.unmodifiableList(Arrays.asList(
            "AVC_MP4_MP_HD_1080i_AAC",
            "AVC_MP4_MP_HD_720p_AAC",
            "AVC_MP4_BL_L3L_SD_AAC"
    ));

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

        // DLNA Profile Names (DLNA.ORG_PN) — must use standardised profile IDs.
        // Invalid profiles cause "format not supported" on DLNA TVs even for valid files.
        // NOTE: video/mp4 uses VIDEO_MP4_PROFILES list (multiple res elements) — not a single entry here.
        MIME_TO_DLNA.put("video/x-matroska",   "");   // MKV has no official DLNA profile → use wildcard
        MIME_TO_DLNA.put("video/x-msvideo",    "");   // AVI has no official DLNA profile → use wildcard
        MIME_TO_DLNA.put("video/quicktime",    "");   // MOV has no official DLNA profile → use wildcard
        MIME_TO_DLNA.put("audio/mpeg",         "MP3");
        MIME_TO_DLNA.put("audio/flac",         "");   // FLAC has no official DLNA PN → use wildcard
        MIME_TO_DLNA.put("audio/wav",          "LPCM");
        MIME_TO_DLNA.put("audio/mp4",          "AAC_ISO_320");
        MIME_TO_DLNA.put("audio/aac",          "AAC_ADTS_320");
        MIME_TO_DLNA.put("image/jpeg",         "JPEG_LRG");
        MIME_TO_DLNA.put("image/png",          "PNG_LRG");
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
     * For video/mp4, returns the first (highest-priority) profile from VIDEO_MP4_PROFILES.
     * Use {@link #getVideoMp4Profiles()} when building multi-res DIDL for LG TVs.
     */
    public static String getDlnaProfile(String mimeType) {
        if ("video/mp4".equals(mimeType)) {
            return VIDEO_MP4_PROFILES.get(0);
        }
        return MIME_TO_DLNA.getOrDefault(mimeType, "*");
    }

    /**
     * Returns the ordered list of DLNA.ORG_PN profiles for video/mp4.
     * Used to emit multiple &lt;res&gt; elements in DIDL-Lite so that LG NetCast TVs
     * (e.g. 47LA6200) can select the hardware decoder they support.
     */
    public static List<String> getVideoMp4Profiles() {
        return VIDEO_MP4_PROFILES;
    }
}
