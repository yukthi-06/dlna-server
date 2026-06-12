package com.vypeensoft.dlnaserver.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Class representing a media folder container.
 */
public class MediaContainer {
    private final String id;
    private final String parentId;
    private final String name;
    private final List<String> childContainerIds = new CopyOnWriteArrayList<>();
    private final List<String> childItemIds = new CopyOnWriteArrayList<>();

    public MediaContainer(String id, String parentId, String name) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public List<String> getChildContainerIds() {
        return childContainerIds;
    }

    public List<String> getChildItemIds() {
        return childItemIds;
    }

    /**
     * Adds a sub-container ID if it doesn't already exist.
     */
    public void addChildContainerId(String containerId) {
        if (!childContainerIds.contains(containerId)) {
            childContainerIds.add(containerId);
        }
    }

    /**
     * Adds a media item ID if it doesn't already exist.
     */
    public void addChildItemId(String itemId) {
        if (!childItemIds.contains(itemId)) {
            childItemIds.add(itemId);
        }
    }

    /**
     * Removes a sub-container ID.
     */
    public void removeChildContainerId(String containerId) {
        childContainerIds.remove(containerId);
    }

    /**
     * Removes a media item ID.
     */
    public void removeChildItemId(String itemId) {
        childItemIds.remove(itemId);
    }

    /**
     * Clears all children.
     */
    public void clear() {
        childContainerIds.clear();
        childItemIds.clear();
    }
}
