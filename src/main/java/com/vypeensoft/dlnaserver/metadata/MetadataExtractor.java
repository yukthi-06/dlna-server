package com.vypeensoft.dlnaserver.metadata;

import com.vypeensoft.dlnaserver.util.MimeTypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * Utility to extract metadata from files.
 */
public final class MetadataExtractor {
    private static final Logger log = LoggerFactory.getLogger(MetadataExtractor.class);

    private MetadataExtractor() {
        // Prevent instantiation
    }

    /**
     * Extracts title, size, MIME type, and duration (estimates for MP3 if possible).
     *
     * @param file the media file
     * @return extracted duration in milliseconds, or -1 if unavailable
     */
    public static long extractDurationMs(File file, String mimeType) {
        if (file == null || !file.exists()) {
            return -1;
        }

        if ("audio/mpeg".equalsIgnoreCase(mimeType)) {
            // Try to extract duration of MP3 by parsing basic frame info
            return estimateMp3DurationMs(file);
        }

        // Return -1 (unknown) for other media if not easily parseable
        return -1;
    }

    /**
     * Parses the title of a file from its name.
     */
    public static String extractTitle(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        // Replace underscores/dashes with spaces to make it user-friendly
        return name.replace('_', ' ').replace('-', ' ');
    }

    /**
     * Estimates MP3 duration in milliseconds by reading the first frame or estimation based on file size.
     */
    private static long estimateMp3DurationMs(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = file.length();
            if (fileLength < 1024) return -1;

            byte[] header = new byte[4];
            long pos = 0;

            // Skip ID3v2 tag if present
            raf.seek(0);
            raf.readFully(header);
            if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                int major = header[3] & 0xFF;
                byte[] sizeBytes = new byte[4];
                raf.readFully(sizeBytes);
                // ID3v2 tag size is 4 bytes synchsafe integers (7 bits per byte)
                int tagSize = ((sizeBytes[0] & 0x7F) << 21) |
                              ((sizeBytes[1] & 0x7F) << 14) |
                              ((sizeBytes[2] & 0x7F) << 7) |
                              (sizeBytes[3] & 0x7F);
                pos = tagSize + 10; // Tag size doesn't include the 10-byte header
                if (pos < fileLength) {
                    raf.seek(pos);
                } else {
                    pos = 0;
                    raf.seek(0);
                }
            }

            // Sync with first frame header
            raf.readFully(header);
            for (int i = 0; i < 4096 && pos + i + 4 < fileLength; i++) {
                int h0 = header[0] & 0xFF;
                int h1 = header[1] & 0xFF;
                if (h0 == 0xFF && (h1 & 0xE0) == 0xE0) {
                    // Found sync!
                    int layer = (h1 >> 1) & 3;
                    int bitrateIndex = (header[2] & 0xF0) >> 4;
                    int sampleRateIndex = (header[2] & 0x0C) >> 2;

                    // Bitrate table for MPEG-1 Layer 3 (Layer III = 1)
                    int bitrate = getMp3Bitrate(layer, bitrateIndex);
                    if (bitrate > 0) {
                        long audioBytes = fileLength - pos;
                        // Duration = (audioBytes * 8) / bitrate (kbps) -> ms
                        return (audioBytes * 8) / bitrate;
                    }
                    break;
                }
                pos++;
                raf.seek(pos);
                raf.readFully(header);
            }
        } catch (Exception e) {
            log.debug("Could not extract exact MP3 duration for {}: {}", file.getName(), e.getMessage());
        }

        // Fallback: assume 128kbps average bitrate for rough estimation
        long fileSize = file.length();
        return (fileSize * 8) / 128; // (Size * 8 bits) / 128 kbps = milliseconds
    }

    private static int getMp3Bitrate(int layer, int index) {
        // MPEG1 Layer 3 Bitrates
        if (layer == 1) { // Layer III
            int[] bitrates = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1};
            if (index >= 0 && index < bitrates.length) {
                return bitrates[index];
            }
        }
        return 128; // default fallback in kbps
    }
}
