package com.vypeensoft.dlnaserver.content;

import com.vypeensoft.dlnaserver.model.MediaContainer;
import com.vypeensoft.dlnaserver.model.MediaItem;
import com.vypeensoft.dlnaserver.util.DlnaProfileCycler;
import com.vypeensoft.dlnaserver.util.MimeTypeResolver;
import org.jupnp.model.types.ErrorCode;
import org.jupnp.support.contentdirectory.AbstractContentDirectoryService;
import org.jupnp.support.contentdirectory.ContentDirectoryException;
import org.jupnp.support.contentdirectory.DIDLParser;
import org.jupnp.support.model.BrowseFlag;
import org.jupnp.support.model.BrowseResult;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.ProtocolInfo;
import org.jupnp.support.model.Res;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.WriteStatus;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;
import org.jupnp.support.model.item.MusicTrack;
import org.jupnp.support.model.item.Photo;
import org.jupnp.support.model.item.VideoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Custom ContentDirectoryService that serves in-memory media catalog items
 * to UPnP control points using DIDL-Lite XML format.
 */
public class ContentDirectoryService extends AbstractContentDirectoryService {
    private static final Logger log = LoggerFactory.getLogger(ContentDirectoryService.class);

    private final MediaCatalog catalog;
    private final Function<String, String> urlProvider;
    private final DlnaProfileCycler cycler;

    public ContentDirectoryService(MediaCatalog catalog, Function<String, String> urlProvider,
                                   DlnaProfileCycler cycler) {
        this.catalog = catalog;
        this.urlProvider = urlProvider;
        this.cycler = cycler;
    }

    @Override
    public BrowseResult browse(String objectId, BrowseFlag browseFlag,
                               String filter, long firstResult, long maxResults,
                               SortCriterion[] orderBy) throws ContentDirectoryException {
        try {
            log.debug("Browse Request: objectId={}, flag={}, firstResult={}, maxResults={}",
                    objectId, browseFlag, firstResult, maxResults);

            DIDLContent didl = new DIDLContent();

            if (browseFlag == BrowseFlag.METADATA) {
                if (MediaCatalog.ROOT_ID.equals(objectId)) {
                    didl.addContainer(buildContainer(catalog.getContainer(MediaCatalog.ROOT_ID).orElseThrow()));
                } else if (objectId.startsWith("c-")) {
                    MediaContainer container = catalog.getContainer(objectId)
                            .orElseThrow(() -> new ContentDirectoryException(
                                    ErrorCode.ACTION_FAILED, "Container not found: " + objectId));
                    didl.addContainer(buildContainer(container));
                } else if (objectId.startsWith("i-")) {
                    MediaItem item = catalog.getItem(objectId)
                            .orElseThrow(() -> new ContentDirectoryException(
                                    ErrorCode.ACTION_FAILED, "Item not found: " + objectId));
                    // BROWSE METADATA for a specific item = user clicked on it.
                    // Advance the cycler so a fresh candidate profile is tried on this play attempt.
                    didl.addItem(buildItem(item, true));
                }
                String xml = new DIDLParser().generate(didl);
                log.info("[Browse METADATA] objectId={} → DIDL-Lite XML:\n{}", objectId, xml);
                return new BrowseResult(xml, 1, 1);

            } else {
                // DIRECT_CHILDREN
                if (!objectId.equals(MediaCatalog.ROOT_ID) && !objectId.startsWith("c-")) {
                    return new BrowseResult(new DIDLParser().generate(didl), 0, 0);
                }

                MediaContainer currentContainer = catalog.getContainer(objectId)
                        .orElseThrow(() -> new ContentDirectoryException(
                                ErrorCode.ACTION_FAILED, "Container not found: " + objectId));

                List<Container> subContainers = new ArrayList<>();
                for (String childId : currentContainer.getChildContainerIds()) {
                    catalog.getContainer(childId).ifPresent(c -> subContainers.add(buildContainer(c)));
                }

                List<Item> subItems = new ArrayList<>();
                for (String childId : currentContainer.getChildItemIds()) {
                    catalog.getItem(childId).ifPresent(i -> subItems.add(buildItem(i, false)));
                }

                int totalMatches = subContainers.size() + subItems.size();
                int start = (int) firstResult;
                int limit = maxResults == 0 ? totalMatches : (int) maxResults;
                int end = Math.min(start + limit, totalMatches);
                int count = 0;

                if (start < totalMatches) {
                    for (int i = start; i < end; i++) {
                        if (i < subContainers.size()) {
                            didl.addContainer(subContainers.get(i));
                        } else {
                            didl.addItem(subItems.get(i - subContainers.size()));
                        }
                        count++;
                    }
                }

                String xml = new DIDLParser().generate(didl);
                log.info("[Browse CHILDREN] objectId={} returned {}/{} items. DIDL-Lite XML:\n{}",
                        objectId, count, totalMatches, xml);
                return new BrowseResult(xml, count, totalMatches);
            }

        } catch (ContentDirectoryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error executing browse for objectId: {}", objectId, e);
            throw new ContentDirectoryException(ErrorCode.ACTION_FAILED, e.getMessage());
        }
    }

    private Container buildContainer(MediaContainer mc) {
        int childCount = mc.getChildContainerIds().size() + mc.getChildItemIds().size();
        // StorageFolder(id, parentId, title, creator, childCount, storageUsed)
        StorageFolder folder = new StorageFolder(
                mc.getId(),
                mc.getParentId(),
                mc.getName(),
                "Java DLNA Server",
                childCount,
                -1L
        );
        folder.setWriteStatus(WriteStatus.NOT_WRITABLE);
        return folder;
    }

    /**
     * Builds a UPnP Item from a MediaItem.
     *
     * @param item    the media item
     * @param advance if {@code true} and the item is video/mp4, advance the profile cycler
     *                (called on BROWSE METADATA = user clicked to play); if {@code false},
     *                use the current candidate without advancing (called on folder listing).
     */
    private Item buildItem(MediaItem item, boolean advance) {
        String streamUrl = urlProvider.apply(item.id());
        String mimeType = item.mimeType();

        String id = item.id();
        String parentId = item.parentId();
        String title = item.title();
        final String creator = "Java DLNA Server";

        if (item.isVideo() && "video/mp4".equals(mimeType)) {
            // Cycle through DLNA profiles so the TV can find one it accepts.
            // advance=true when user clicked the item (BROWSE METADATA), so we try the next profile.
            // advance=false when listing the folder (BROWSE DIRECT_CHILDREN), so we show the current one.
            String protocolInfoStr = advance ? cycler.advanceAndGet() : cycler.getCurrentProtocolInfo();

            ProtocolInfo protocolInfo = new ProtocolInfo(protocolInfoStr);
            Res res = new Res(protocolInfo, item.size(), streamUrl);
            if (item.durationMs() > 0) {
                long totalSec = item.durationMs() / 1000;
                long hours = totalSec / 3600;
                long minutes = (totalSec % 3600) / 60;
                long seconds = totalSec % 60;
                res.setDuration(String.format("%02d:%02d:%02d.000", hours, minutes, seconds));
            }

            VideoItem vi = new VideoItem();
            vi.setId(id);
            vi.setParentID(parentId);
            vi.setTitle(title);
            vi.setCreator(creator);
            vi.addResource(res);
            log.info("[BuildItem-MP4-Cycler] title='{}' advance={} protocolInfo='{}'",
                    title, advance, protocolInfoStr);
            return vi;
        }

        // --- All other MIME types: single <res> element ---

        // Build protocolInfo matching MiniDLNA's format for LG NetCast (47LA6200 and similar).
        // LG uses DLNA.ORG_PN to select the hardware decoder — '*' means unknown decoder → rejected.
        // FLAGS=01700000 for AV (streaming transfer mode), 00D00000 for images (interactive mode).
        String protocolInfoStr;
        if (item.isImage()) {
            // Images: OP=00 (no seek), interactive transfer, JPEG_LRG / PNG_LRG profile
            String pn = MimeTypeResolver.getDlnaProfile(mimeType); // JPEG_LRG, PNG_LRG
            protocolInfoStr = "http-get:*:" + mimeType + ":DLNA.ORG_PN=" + pn
                    + ";DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=00D00000000000000000000000000000";
        } else {
            // Audio/Video non-MP4: OP=01 (byte-range seek), streaming transfer
            String pn = MimeTypeResolver.getDlnaProfile(mimeType);
            if (pn != null && !pn.isEmpty() && !"*".equals(pn)) {
                protocolInfoStr = "http-get:*:" + mimeType + ":DLNA.ORG_PN=" + pn
                        + ";DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
            } else {
                // No known DLNA profile (MKV, AVI, MOV, FLAC) — omit PN, keep flags
                protocolInfoStr = "http-get:*:" + mimeType
                        + ":DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
            }
        }
        ProtocolInfo protocolInfo = new ProtocolInfo(protocolInfoStr);

        Res res = new Res(protocolInfo, item.size(), streamUrl);
        log.info("[BuildItem] title='{}' url='{}' mimeType='{}' size={} protocolInfo='{}'",
                item.title(), streamUrl, mimeType, item.size(), protocolInfoStr);

        if (item.durationMs() > 0) {
            long totalSec = item.durationMs() / 1000;
            long hours = totalSec / 3600;
            long minutes = (totalSec % 3600) / 60;
            long seconds = totalSec % 60;
            res.setDuration(String.format("%02d:%02d:%02d.000", hours, minutes, seconds));
        }

        if (item.isVideo()) {
            VideoItem vi = new VideoItem();
            vi.setId(id);
            vi.setParentID(parentId);
            vi.setTitle(title);
            vi.setCreator(creator);
            vi.addResource(res);
            return vi;
        } else if (item.isAudio()) {
            MusicTrack mt = new MusicTrack();
            mt.setId(id);
            mt.setParentID(parentId);
            mt.setTitle(title);
            mt.setCreator(creator);
            mt.addResource(res);
            return mt;
        } else {
            Photo ph = new Photo();
            ph.setId(id);
            ph.setParentID(parentId);
            ph.setTitle(title);
            ph.setCreator(creator);
            ph.addResource(res);
            return ph;
        }
    }
}
