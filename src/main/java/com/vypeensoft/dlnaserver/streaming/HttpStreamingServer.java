package com.vypeensoft.dlnaserver.streaming;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.vypeensoft.dlnaserver.content.MediaCatalog;
import com.vypeensoft.dlnaserver.model.MediaItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP streaming server.
 * Handles HTTP GET/HEAD requests, byte-range requests (seeking),
 * and serves large files in chunks without reading them fully into memory.
 */
public class HttpStreamingServer {
    private static final Logger log = LoggerFactory.getLogger(HttpStreamingServer.class);
    private static final int BUFFER_SIZE = 65536; // 64KB

    private final int port;
    private final MediaCatalog catalog;
    private HttpServer server;
    private String hostAddress;

    public HttpStreamingServer(int port, MediaCatalog catalog) {
        this.port = port;
        this.catalog = catalog;
        this.hostAddress = resolveHostAddress();
    }

    /**
     * Starts the HTTP server.
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/stream", new StreamHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        log.info("HTTP Streaming Server started at http://{}:{}/stream", hostAddress, port);
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(1);
            log.info("HTTP Streaming Server stopped");
        }
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public int getPort() {
        return port;
    }

    /**
     * Returns the base stream URL for a given media item ID.
     */
    public String getStreamUrl(String itemId) {
        return "http://" + hostAddress + ":" + port + "/stream?id=" + itemId;
    }

    private String resolveHostAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("Error resolving host network interfaces, falling back to localhost: {}", e.getMessage());
        }
        return "127.0.0.1";
    }

    private class StreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            URI uri = exchange.getRequestURI();
            String query = uri.getQuery();
            String itemId = null;
            if (query != null && query.startsWith("id=")) {
                itemId = query.substring(3);
            }

            if (itemId == null) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }

            MediaItem item = catalog.getItem(itemId).orElse(null);
            if (item == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            File file = item.file();
            if (!file.exists() || !file.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            log.debug("HTTP {} request received for item id={}, title='{}', size={} bytes", 
                    method, item.id(), item.title(), item.size());

            long fileLength = file.length();
            String mimeType = item.mimeType();

            // Set default headers
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            
            // DLNA headers to satisfy DLNA requirements
            exchange.getResponseHeaders().set("transferMode.dlna.org", "Streaming");
            exchange.getResponseHeaders().set("contentFeatures.dlna.org", 
                "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000");

            long start = 0;
            long end = fileLength - 1;
            boolean isRangeRequest = false;

            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                isRangeRequest = true;
                String rangeSpec = rangeHeader.substring(6).trim();
                try {
                    int dash = rangeSpec.indexOf('-');
                    if (dash == 0) { // bytes=-500
                        long len = Long.parseLong(rangeSpec.substring(1));
                        start = fileLength - len;
                    } else if (dash == rangeSpec.length() - 1) { // bytes=500-
                        start = Long.parseLong(rangeSpec.substring(0, dash));
                    } else { // bytes=500-1000
                        start = Long.parseLong(rangeSpec.substring(0, dash));
                        end = Long.parseLong(rangeSpec.substring(dash + 1));
                    }
                } catch (NumberFormatException e) {
                    exchange.sendResponseHeaders(416, -1);
                    exchange.close();
                    return;
                }
            }

            // Validate Range boundaries
            if (start < 0 || end >= fileLength || start > end) {
                if (isRangeRequest) {
                    exchange.getResponseHeaders().set("Content-Range", "bytes */" + fileLength);
                    exchange.sendResponseHeaders(416, -1);
                    exchange.close();
                    return;
                } else {
                    start = 0;
                    end = fileLength - 1;
                }
            }

            long responseLength = end - start + 1;

            if (isRangeRequest) {
                exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(responseLength));
                exchange.sendResponseHeaders(206, "HEAD".equalsIgnoreCase(method) ? -1 : responseLength);
                log.debug("Serving Range: bytes {}-{}/{} (size: {})", start, end, fileLength, responseLength);
            } else {
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileLength));
                exchange.sendResponseHeaders(200, "HEAD".equalsIgnoreCase(method) ? -1 : fileLength);
                log.debug("Serving entire file (size: {})", fileLength);
            }

            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.close();
                return;
            }

            // Stream file content starting from 'start' to 'end'
            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 OutputStream os = exchange.getResponseBody()) {
                raf.seek(start);
                byte[] buffer = new byte[BUFFER_SIZE];
                long bytesRemaining = responseLength;

                while (bytesRemaining > 0) {
                    int readLen = (int) Math.min(buffer.length, bytesRemaining);
                    int read = raf.read(buffer, 0, readLen);
                    if (read == -1) {
                        break;
                    }
                    os.write(buffer, 0, read);
                    bytesRemaining -= read;
                }
                os.flush();
            } catch (IOException e) {
                // Connection may be reset by client seeking or leaving stream, log at debug
                log.debug("Streaming interrupted for {}: {}", file.getName(), e.getMessage());
            } finally {
                exchange.close();
            }
        }
    }
}
