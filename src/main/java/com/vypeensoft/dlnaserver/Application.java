package com.vypeensoft.dlnaserver;

import com.vypeensoft.dlnaserver.cli.CliParser;
import com.vypeensoft.dlnaserver.content.MediaCatalog;
import com.vypeensoft.dlnaserver.dlna.DlnaMediaServer;
import com.vypeensoft.dlnaserver.filesystem.FileWatcher;
import com.vypeensoft.dlnaserver.filesystem.MediaScanner;
import com.vypeensoft.dlnaserver.streaming.HttpStreamingServer;
import com.vypeensoft.dlnaserver.util.DlnaProfileCycler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CountDownLatch;

/**
 * Main application entry point. Orchestrates CLI parsing, media scanning,
 * file system watching, HTTP streaming, and UPnP/DLNA service.
 */
public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        CliParser parser = new CliParser();
        if (!parser.parse(args)) {
            if (parser.isHelpRequested()) {
                System.exit(0);
            } else {
                System.exit(1);
            }
        }

        String mediaPath = parser.getMediaDir();
        String name = parser.getServerName();
        int port = parser.getPort();

        log.info("Starting DLNA Media Server: Name='{}', Port={}, Media Folder='{}'", name, port, mediaPath);

        File mediaDir = new File(mediaPath);
        MediaCatalog catalog = new MediaCatalog();

        // Profile cycler — discovers the correct DLNA protocolInfo for this TV automatically.
        // On each click on an MP4, the next candidate profile is tried. When the TV accepts
        // (issues a GET request), the winner is logged to dlna_profile_winner.log.
        DlnaProfileCycler cycler = new DlnaProfileCycler();
        String winner = cycler.loadWinner();
        if (winner != null) {
            log.info("Profile cycler: using previously saved winner profile. Will be tried on first click.");
        } else {
            log.info("Profile cycler: {} candidate profiles loaded. Click an MP4 on the TV to cycle.",
                    DlnaProfileCycler.CANDIDATES.size());
        }

        // HTTP Server Setup
        HttpStreamingServer streamingServer = new HttpStreamingServer(port, catalog, cycler);

        // DLNA Server Setup
        DlnaMediaServer dlnaServer = new DlnaMediaServer(name, catalog, streamingServer::getStreamUrl, cycler);

        // Initial Scan
        MediaScanner scanner = new MediaScanner(catalog, mediaDir);
        scanner.scan();

        // Filesystem Watcher Setup
        FileWatcher watcher = null;
        try {
            watcher = new FileWatcher(catalog, mediaDir, scanner);
        } catch (Exception e) {
            log.error("Failed to initialize file watcher", e);
        }

        // Start Services
        try {
            streamingServer.start();
            dlnaServer.start();
            if (watcher != null) {
                watcher.start();
            }
        } catch (Exception e) {
            log.error("Failed to start DLNA services", e);
            System.exit(1);
        }

        log.info("DLNA Server is now running. Press Ctrl+C to terminate.");

        // Register Shutdown Hook
        FileWatcher finalWatcher = watcher;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Cleaning up...");
            if (finalWatcher != null) {
                finalWatcher.stop();
            }
            dlnaServer.stop();
            streamingServer.stop();
            shutdownLatch.countDown();
        }, "ShutdownHookThread"));

        // Wait for shutdown signal
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            log.warn("Main thread interrupted");
            Thread.currentThread().interrupt();
        }

        log.info("DLNA Server stopped cleanly.");
    }
}
