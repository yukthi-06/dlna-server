package com.vypeensoft.dlnaserver.filesystem;

import com.vypeensoft.dlnaserver.content.MediaCatalog;
import com.vypeensoft.dlnaserver.model.MediaContainer;
import com.vypeensoft.dlnaserver.model.MediaItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

public class MediaScannerTest {

    @TempDir
    File tempDir;

    @Test
    public void testScanningPopulatesCatalog() throws IOException {
        // Create sample subfolders and files
        File videoFolder = new File(tempDir, "Videos");
        assertTrue(videoFolder.mkdir());
        
        File videoFile = new File(videoFolder, "sample_video.mp4");
        assertTrue(videoFile.createNewFile());
        Files.writeString(videoFile.toPath(), "mock content");

        File audioFile = new File(tempDir, "song.mp3");
        assertTrue(audioFile.createNewFile());
        
        File txtFile = new File(tempDir, "ignore.txt");
        assertTrue(txtFile.createNewFile());

        MediaCatalog catalog = new MediaCatalog();
        MediaScanner scanner = new MediaScanner(catalog, tempDir);
        scanner.scan();

        // Check containers: ROOT, Videos, and rootMediaDir itself
        // Wait, root dir gets mapped to ROOT_ID or a root container.
        // Let's verify the size
        assertFalse(catalog.getContainers().isEmpty(), "Catalog should contain folders");
        
        // Assert that the mp4 and mp3 were added, but ignore.txt was not
        long itemCount = catalog.getItems().values().stream()
                .filter(i -> i.file().getName().endsWith(".mp4") || i.file().getName().endsWith(".mp3"))
                .count();
        assertEquals(2, itemCount, "Both mp4 and mp3 should be scanned");

        long ignoreCount = catalog.getItems().values().stream()
                .filter(i -> i.file().getName().endsWith(".txt"))
                .count();
        assertEquals(0, ignoreCount, "TXT files should be ignored");

        // Verify root container has children
        MediaContainer root = catalog.getContainer(MediaCatalog.ROOT_ID).orElseThrow();
        assertFalse(root.getChildItemIds().isEmpty() && root.getChildContainerIds().isEmpty());
    }
}
