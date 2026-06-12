package com.vypeensoft.dlnaserver.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MimeTypeResolverTest {

    @Test
    public void testGetMimeType() {
        assertEquals("video/mp4", MimeTypeResolver.getMimeType("movie.mp4"));
        assertEquals("video/x-matroska", MimeTypeResolver.getMimeType("movie.mkv"));
        assertEquals("audio/mpeg", MimeTypeResolver.getMimeType("song.mp3"));
        assertEquals("image/jpeg", MimeTypeResolver.getMimeType("photo.jpg"));
        assertEquals("image/png", MimeTypeResolver.getMimeType("photo.png"));
        assertEquals("application/octet-stream", MimeTypeResolver.getMimeType("document.pdf"));
        assertEquals("application/octet-stream", MimeTypeResolver.getMimeType("no_extension"));
    }

    @Test
    public void testMediaCategoryChecking() {
        assertTrue(MimeTypeResolver.isVideo("video/mp4"));
        assertFalse(MimeTypeResolver.isVideo("audio/mpeg"));

        assertTrue(MimeTypeResolver.isAudio("audio/flac"));
        assertFalse(MimeTypeResolver.isAudio("image/png"));

        assertTrue(MimeTypeResolver.isImage("image/jpeg"));
        assertFalse(MimeTypeResolver.isImage("video/x-msvideo"));
    }

    @Test
    public void testDlnaProfileMapping() {
        assertEquals("MP4_SP_AAC_L2", MimeTypeResolver.getDlnaProfile("video/mp4"));
        assertEquals("MP3", MimeTypeResolver.getDlnaProfile("audio/mpeg"));
        assertEquals("JPEG_LRG", MimeTypeResolver.getDlnaProfile("image/jpeg"));
        assertEquals("*", MimeTypeResolver.getDlnaProfile("application/octet-stream"));
    }
}
