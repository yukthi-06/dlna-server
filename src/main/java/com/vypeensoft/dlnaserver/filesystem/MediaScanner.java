package com.vypeensoft.dlnaserver.filesystem;

import com.vypeensoft.dlnaserver.content.MediaCatalog;
import com.vypeensoft.dlnaserver.metadata.MetadataExtractor;
import com.vypeensoft.dlnaserver.model.MediaContainer;
import com.vypeensoft.dlnaserver.model.MediaItem;
import com.vypeensoft.dlnaserver.util.MimeTypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Recursively scans directories to discover media files.
 */
public class MediaScanner {
    private static final Logger log = LoggerFactory.getLogger(MediaScanner.class);

    private final MediaCatalog catalog;
    private final File rootDir;

    public MediaScanner(MediaCatalog catalog, File rootDir) {
        this.catalog = catalog;
        this.rootDir = rootDir;
    }

    /**
     * Starts scanning the media root directory.
     */
    public void scan() {
        log.info("Starting media scan of: {}", rootDir.getAbsolutePath());
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            log.error("Media directory does not exist or is not a directory: {}", rootDir.getAbsolutePath());
            return;
        }
        
        long startTime = System.currentTimeMillis();
        // Get or create root container
        MediaContainer rootContainer = catalog.getOrCreateContainer(rootDir, rootDir);
        scanDirectory(rootDir, rootContainer);
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("Media scan complete. Found {} containers and {} items in {} ms",
                catalog.getContainers().size(), catalog.getItems().size(), duration);
    }

    /**
     * Recursively scans a directory and adds contents to the catalog under the parent container.
     */
    private void scanDirectory(File directory, MediaContainer container) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                if (file.getName().startsWith(".")) {
                    continue; // Skip hidden folders
                }
                MediaContainer childContainer = catalog.getOrCreateContainer(file, rootDir);
                scanDirectory(file, childContainer);
            } else if (file.isFile()) {
                scanFile(file, container.getId());
            }
        }
    }

    /**
     * Scans a single file. If it is a supported media type, adds it to the catalog.
     */
    public void scanFile(File file, String containerId) {
        String mimeType = MimeTypeResolver.getMimeType(file.getName());
        if ("application/octet-stream".equals(mimeType)) {
            return; // Unsupported file type
        }

        // Avoid adding duplicates (check by path)
        if (catalog.getIdByPath(file.getAbsolutePath()).isPresent()) {
            return;
        }

        try {
            String id = catalog.generateItemId();
            String title = MetadataExtractor.extractTitle(file);
            long size = file.length();
            long durationMs = MetadataExtractor.extractDurationMs(file, mimeType);

            MediaItem item = new MediaItem(
                id,
                containerId,
                title,
                file,
                mimeType,
                size,
                durationMs
            );
            catalog.addItem(item);
        } catch (Exception e) {
            log.error("Failed to scan file: {}", file.getAbsolutePath(), e);
        }
    }
}
