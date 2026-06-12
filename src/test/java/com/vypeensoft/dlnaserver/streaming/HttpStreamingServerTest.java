package com.vypeensoft.dlnaserver.streaming;

import com.vypeensoft.dlnaserver.content.MediaCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import static org.junit.jupiter.api.Assertions.*;

public class HttpStreamingServerTest {

    @Test
    public void testHostAndPortSetup() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        MediaCatalog catalog = new MediaCatalog();
        HttpStreamingServer server = new HttpStreamingServer(freePort, catalog);

        assertEquals(freePort, server.getPort());
        assertNotNull(server.getHostAddress());
        assertFalse(server.getHostAddress().isEmpty());

        String streamUrl = server.getStreamUrl("i-123");
        assertTrue(streamUrl.contains("/stream?id=i-123"));
        assertTrue(streamUrl.contains(String.valueOf(freePort)));
    }

    @Test
    public void testLifecycle() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        MediaCatalog catalog = new MediaCatalog();
        HttpStreamingServer server = new HttpStreamingServer(freePort, catalog);

        // Start and immediately stop to verify port binding logic
        assertDoesNotThrow(server::start);
        assertDoesNotThrow(server::stop);
    }
}
