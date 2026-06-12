package com.vypeensoft.dlnaserver.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Command line interface arguments parser.
 */
public class CliParser {
    private static final Logger log = LoggerFactory.getLogger(CliParser.class);

    private String mediaDir;
    private String serverName = "Java DLNA Server";
    private int port = 8200;
    private boolean helpRequested = false;

    /**
     * Parses CLI arguments.
     *
     * @param args the command-line arguments
     * @return true if parsing succeeded and program should start; false if help was shown or errors occurred.
     */
    public boolean parse(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printHelp();
                helpRequested = true;
                return false;
            } else if ("--media-dir".equals(arg)) {
                if (i + 1 < args.length) {
                    mediaDir = args[++i];
                } else {
                    System.err.println("Error: --media-dir requires a directory path.");
                    return false;
                }
            } else if ("--name".equals(arg)) {
                if (i + 1 < args.length) {
                    serverName = args[++i];
                } else {
                    System.err.println("Error: --name requires a server name.");
                    return false;
                }
            } else if ("--port".equals(arg)) {
                if (i + 1 < args.length) {
                    try {
                        port = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Error: --port must be an integer.");
                        return false;
                    }
                } else {
                    System.err.println("Error: --port requires a port number.");
                    return false;
                }
            } else {
                // If it is a positional argument, treat it as the media directory (supporting simple run: jar path)
                if (mediaDir == null) {
                    mediaDir = arg;
                } else {
                    System.err.println("Error: Unknown argument " + arg);
                    printHelp();
                    return false;
                }
            }
        }

        if (mediaDir == null) {
            System.err.println("Error: Media directory path is required.");
            printHelp();
            return false;
        }

        File file = new File(mediaDir);
        if (!file.exists() || !file.isDirectory()) {
            System.err.println("Error: Specified media directory does not exist or is not a directory: " + mediaDir);
            return false;
        }

        return true;
    }

    private void printHelp() {
        System.out.println("Usage: java -jar dlna-server.jar [options] <media-dir>");
        System.out.println("Options:");
        System.out.println("  --media-dir <path>  Path to the media library folder (required if not passed as a positional arg)");
        System.out.println("  --name <name>       Name of the DLNA Server (default: 'Java DLNA Server')");
        System.out.println("  --port <port>       Port for streaming HTTP server (default: 8200)");
        System.out.println("  --help, -h          Show this help message");
    }

    public String getMediaDir() {
        return mediaDir;
    }

    public String getServerName() {
        return serverName;
    }

    public int getPort() {
        return port;
    }

    public boolean isHelpRequested() {
        return helpRequested;
    }
}
