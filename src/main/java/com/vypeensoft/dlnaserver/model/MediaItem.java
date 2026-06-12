package com.vypeensoft.dlnaserver.model;

import java.io.File;

/**
 * Immutable record representing a media file.
 */
public record MediaItem(
    String id,
    String parentId,
    String title,
    File file,
    String mimeType,
    long size,
    long durationMs
) {
    /**
     * Checks if the item is a video.
     */
    public boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video/");
    }

    /**
     * Checks if the item is audio.
     */
    public boolean isAudio() {
        return mimeType != null && mimeType.startsWith("audio/");
    }

    /**
     * Checks if the item is an image.
     */
    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
