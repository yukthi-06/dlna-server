package com.vypeensoft.dlnaserver.content;

import com.vypeensoft.dlnaserver.model.MediaContainer;
import com.vypeensoft.dlnaserver.model.MediaItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe in-memory media catalog.
 * Manages UPnP containers and items.
 */
public class MediaCatalog {
    private static final Logger log = LoggerFactory.getLogger(MediaCatalog.class);
    
    public static final String ROOT_ID = "0";
    
    private final Map<String, MediaContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, MediaItem> items = new ConcurrentHashMap<>();
    
    // Maps filesystem path to UPnP ID
    private final Map<String, String> pathToIdMap = new ConcurrentHashMap<>();
    
    private final AtomicInteger containerIdGenerator = new AtomicInteger(0);
    private final AtomicInteger itemIdGenerator = new AtomicInteger(0);

    public MediaCatalog() {
        // Initialize root container
        containers.put(ROOT_ID, new MediaContainer(ROOT_ID, "-1", "Root"));
    }

    /**
     * Gets or creates a container for a directory.
     */
    public synchronized MediaContainer getOrCreateContainer(File directory, File rootMediaDir) {
        String path = directory.getAbsolutePath();
        String existingId = pathToIdMap.get(path);
        if (existingId != null) {
            MediaContainer container = containers.get(existingId);
            if (container != null) {
                return container;
            }
        }

        // Determine parent container ID
        String parentId = ROOT_ID;
        File parentDir = directory.getParentFile();
        if (parentDir != null && path.startsWith(rootMediaDir.getAbsolutePath()) && !path.equals(rootMediaDir.getAbsolutePath())) {
            MediaContainer parentContainer = getOrCreateContainer(parentDir, rootMediaDir);
            parentId = parentContainer.getId();
        }

        String name = directory.getName();
        if (path.equals(rootMediaDir.getAbsolutePath())) {
            name = "Media Library";
        }

        String id = "c-" + containerIdGenerator.incrementAndGet();
        MediaContainer container = new MediaContainer(id, parentId, name);
        containers.put(id, container);
        pathToIdMap.put(path, id);

        // Register with parent
        MediaContainer parent = containers.get(parentId);
        if (parent != null) {
            parent.addChildContainerId(id);
        }

        log.debug("Created container: id={}, parentId={}, name={}, path={}", id, parentId, name, path);
        return container;
    }

    /**
     * Adds a media item to the catalog.
     */
    public synchronized void addItem(MediaItem item) {
        items.put(item.id(), item);
        pathToIdMap.put(item.file().getAbsolutePath(), item.id());

        MediaContainer parent = containers.get(item.parentId());
        if (parent != null) {
            parent.addChildItemId(item.id());
        }
        log.debug("Added item to catalog: id={}, parentId={}, title={}, path={}", item.id(), item.parentId(), item.title(), item.file().getAbsolutePath());
    }

    /**
     * Generates a new unique ID for an item.
     */
    public String generateItemId() {
        return "i-" + itemIdGenerator.incrementAndGet();
    }

    /**
     * Finds a container by ID.
     */
    public Optional<MediaContainer> getContainer(String id) {
        return Optional.ofNullable(containers.get(id));
    }

    /**
     * Finds an item by ID.
     */
    public Optional<MediaItem> getItem(String id) {
        return Optional.ofNullable(items.get(id));
    }

    /**
     * Finds the ID of a filesystem path.
     */
    public Optional<String> getIdByPath(String absolutePath) {
        return Optional.ofNullable(pathToIdMap.get(absolutePath));
    }

    /**
     * Removes an item by its filesystem path.
     */
    public synchronized void removeItemByPath(String absolutePath) {
        String id = pathToIdMap.remove(absolutePath);
        if (id != null) {
            MediaItem item = items.remove(id);
            if (item != null) {
                MediaContainer parent = containers.get(item.parentId());
                if (parent != null) {
                    parent.removeChildItemId(id);
                }
                log.debug("Removed item from catalog: id={}, title={}, path={}", id, item.title(), absolutePath);
            }
        }
    }

    /**
     * Removes a container by its filesystem path.
     */
    public synchronized void removeContainerByPath(String absolutePath) {
        String id = pathToIdMap.remove(absolutePath);
        if (id != null) {
            MediaContainer container = containers.remove(id);
            if (container != null) {
                // Remove from parent
                MediaContainer parent = containers.get(container.getParentId());
                if (parent != null) {
                    parent.removeChildContainerId(id);
                }
                // Recursively remove children if any are left (though scanner/watcher should handle them)
                for (String childContainerId : container.getChildContainerIds()) {
                    MediaContainer child = containers.get(childContainerId);
                    if (child != null) {
                        removeContainerByPath(absolutePath + File.separator + child.getName());
                    }
                }
                log.debug("Removed container from catalog: id={}, name={}, path={}", id, container.getName(), absolutePath);
            }
        }
    }

    /**
     * Clears the entire catalog (retaining only ROOT).
     */
    public synchronized void clear() {
        containers.clear();
        items.clear();
        pathToIdMap.clear();
        containerIdGenerator.set(0);
        itemIdGenerator.set(0);
        containers.put(ROOT_ID, new MediaContainer(ROOT_ID, "-1", "Root"));
        log.debug("Cleared media catalog");
    }

    public Map<String, MediaContainer> getContainers() {
        return containers;
    }

    public Map<String, MediaItem> getItems() {
        return items;
    }
}
