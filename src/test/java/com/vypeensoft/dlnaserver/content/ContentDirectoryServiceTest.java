package com.vypeensoft.dlnaserver.content;

import com.vypeensoft.dlnaserver.model.MediaItem;
import org.jupnp.support.model.BrowseFlag;
import org.jupnp.support.model.BrowseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class ContentDirectoryServiceTest {

    @TempDir
    File tempDir;

    @Test
    public void testBrowseMetadata() throws Exception {
        MediaCatalog catalog = new MediaCatalog();
        
        File sampleFile = new File(tempDir, "song.mp3");
        assertTrue(sampleFile.createNewFile());

        MediaItem item = new MediaItem(
            "i-1",
            MediaCatalog.ROOT_ID,
            "My Song",
            sampleFile,
            "audio/mpeg",
            1024L,
            120000L
        );
        catalog.addItem(item);

        ContentDirectoryService service = new ContentDirectoryService(catalog, id -> "http://127.0.0.1:8200/stream?id=" + id);
        
        // Browse metadata of item
        BrowseResult result = service.browse("i-1", BrowseFlag.METADATA, "*", 0, 10, null);
        
        assertNotNull(result);
        assertEquals(1, result.getCount());
        assertTrue(result.getResult().contains("My Song"), "XML output must contain item title");
        assertTrue(result.getResult().contains("audio/mpeg"), "XML output must contain MIME type");
        assertTrue(result.getResult().contains("http://127.0.0.1:8200/stream?id=i-1"), "XML output must contain stream URL");
    }

    @Test
    public void testBrowseChildren() throws Exception {
        MediaCatalog catalog = new MediaCatalog();
        
        File sampleFile = new File(tempDir, "video.mp4");
        assertTrue(sampleFile.createNewFile());

        MediaItem item = new MediaItem(
            "i-2",
            MediaCatalog.ROOT_ID,
            "My Video",
            sampleFile,
            "video/mp4",
            2048L,
            -1L
        );
        catalog.addItem(item);

        ContentDirectoryService service = new ContentDirectoryService(catalog, id -> "http://127.0.0.1:8200/stream?id=" + id);
        
        // Browse children of root
        BrowseResult result = service.browse(MediaCatalog.ROOT_ID, BrowseFlag.DIRECT_CHILDREN, "*", 0, 10, null);
        
        assertNotNull(result);
        assertTrue(result.getCount() >= 1);
        assertTrue(result.getResult().contains("My Video"));
        assertTrue(result.getResult().contains("video/mp4"));
    }
}
