package com.vypeensoft.dlnaserver.filesystem;

import com.vypeensoft.dlnaserver.content.MediaCatalog;
import com.vypeensoft.dlnaserver.model.MediaContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

/**
 * Background watcher that monitors the media directory for real-time changes
 * and dynamically updates the in-memory media catalog.
 */
public class FileWatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(FileWatcher.class);

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private final MediaCatalog catalog;
    private final File rootDir;
    private final MediaScanner scanner;
    private volatile boolean running;
    private Thread thread;

    public FileWatcher(MediaCatalog catalog, File rootDir, MediaScanner scanner) throws IOException {
        this.catalog = catalog;
        this.rootDir = rootDir;
        this.scanner = scanner;
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>();
        this.running = false;
    }

    /**
     * Starts watching the directory in a separate thread.
     */
    public synchronized void start() {
        if (running) return;
        running = true;
        
        try {
            registerAll(rootDir.toPath());
        } catch (IOException e) {
            log.error("Failed to initialize directory watcher keys", e);
            return;
        }

        thread = new Thread(this, "FileWatcherThread");
        thread.setDaemon(true);
        thread.start();
        log.info("Filesystem watcher started on: {}", rootDir.getAbsolutePath());
    }

    /**
     * Stops watching.
     */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        try {
            watcher.close();
        } catch (IOException e) {
            log.error("Error closing watch service", e);
        }
        if (thread != null) {
            thread.interrupt();
        }
        log.info("Filesystem watcher stopped");
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                                            StandardWatchEventKinds.ENTRY_DELETE,
                                            StandardWatchEventKinds.ENTRY_MODIFY);
        keys.put(key, dir);
        log.debug("Watching directory: {}", dir);
    }

    private void registerAll(final Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName() != null && dir.getFileName().toString().startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public void run() {
        while (running) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                log.warn("WatchKey not recognized!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path name = ev.context();
                Path child = dir.resolve(name);

                log.debug("Event detected: {} -> {}", kind.name(), child);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    handleCreate(child);
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    handleDelete(child);
                } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    handleModify(child);
                }
            }

            // Reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    log.warn("All directories are unwatched. Stopping watcher.");
                    break;
                }
            }
        }
    }

    private void handleCreate(Path path) {
        File file = path.toFile();
        if (file.isDirectory()) {
            try {
                registerAll(path);
                // Create container in catalog
                catalog.getOrCreateContainer(file, rootDir);
                // Scan folder contents
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        handleCreate(child.toPath());
                    }
                }
            } catch (IOException e) {
                log.error("Failed to register and watch newly created directory: {}", path, e);
            }
        } else {
            // Find parent container
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                catalog.getIdByPath(parentDir.getAbsolutePath()).ifPresent(containerId -> 
                    scanner.scanFile(file, containerId)
                );
            }
        }
    }

    private void handleDelete(Path path) {
        String absPath = path.toAbsolutePath().toString();
        // Since we don't know if it was a file or directory after deletion,
        // we can try removing both container and item mappings from catalog
        catalog.removeItemByPath(absPath);
        catalog.removeContainerByPath(absPath);
    }

    private void handleModify(Path path) {
        File file = path.toFile();
        if (file.isFile()) {
            String absPath = file.getAbsolutePath();
            // Re-scan: remove first then add back to update size/metadata
            catalog.removeItemByPath(absPath);
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                catalog.getIdByPath(parentDir.getAbsolutePath()).ifPresent(containerId -> 
                    scanner.scanFile(file, containerId)
                );
            }
        }
    }
}
