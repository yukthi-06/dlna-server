# Java DLNA/UPnP AV Media Server

A complete, lightweight, cross-platform CLI application that exposes a local folder as a DLNA media library, allowing smart TVs, media players (VLC, Kodi), and other devices on the same local network to automatically discover and stream audio, video, and image files.

## Features

- **SSDP Auto-Discovery**: Automatically advertises its presence on the local network. Discovered instantly by standard UPnP/DLNA clients.
- **Hierarchical Browsing**: Exposes folder structures exactly as they are laid out on the filesystem.
- **HTTP Byte-Range Streaming**: Supports seeking (rewind/fast-forward) during playback using efficient, low-memory chunked streaming.
- **MIME & DLNA Profiles**: Maps popular media extensions (MP4, MKV, AVI, MP3, FLAC, WAV, JPEG, PNG, etc.) to standard DLNA profile properties.
- **Dynamic Updates**: Watches the filesystem for changes (additions, modifications, and deletions) and updates the media catalog in real-time.
- **Zero Configuration**: Binds to the primary active network interface automatically.

---

## Prerequisites

- **Java Development Kit (JDK) 21 or higher**
- **Apache Maven 3.8+**

---

## Build Instructions

To build the executable fat jar containing all dependencies, run:

```bash
mvn clean package
```

The compiled standalone executable JAR will be generated in the `target/` directory:
- `target/dlna-server.jar`

---

## Run Instructions

Start the server by passing the path of the media directory to expose:

```bash
java -jar target/dlna-server.jar /path/to/media/folder
```

### Command Line Options

You can customize the server name and the HTTP streaming port:

```bash
java -jar target/dlna-server.jar --media-dir "/path/to/media" --name "My Media Server" --port 8200
```

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `--media-dir` | Path | (Required) | The local folder containing media files to stream. Can also be passed as the first positional argument. |
| `--name` | String | `Java DLNA Server` | The friendly name that will show up on your TV/VLC. |
| `--port` | Integer | `8200` | The port for the embedded streaming server. |
| `--help` / `-h` | Flag | - | Show help message and exit. |

---

## Architecture Overview

The application is structured using a clean, modular architecture:

- `cli`: Handles command-line option parsing and validation (`CliParser`).
- `discovery`: Manages UPnP/SSDP server advertisement.
- `dlna`: Holds UPnP device registry and service binding wrappers (`DlnaMediaServer`, `ConnectionManagerService`).
- `content`: Implements the `ContentDirectoryService` mapping filesystem directories into DIDL-Lite containers/items, backed by the thread-safe `MediaCatalog`.
- `streaming`: Includes `HttpStreamingServer`, which uses JDK's built-in `HttpServer` with a thread-pool executor to handle GET/HEAD requests, range parsing, and buffered streaming.
- `filesystem`: Tracks file additions/modifications using recursive Java NIO `WatchService` (`FileWatcher`) and scans directories (`MediaScanner`).
- `metadata`: Resolves media lengths and attributes (`MetadataExtractor`).
- `util`: Resolves extensions to MIME types (`MimeTypeResolver`).

---

## DLNA Compatibility Notes

The server is compatible with:
- **VLC Media Player** (PC, Mac, iOS, Android) under *Local Network -> Universal Plug 'n' Play*.
- **Kodi** media center.
- **Smart TVs** (Samsung, LG, Sony, Roku, etc.).
- **Windows Media Player**.
- **BubbleUPnP / localcast** (Android/iOS DLNA controllers).

Ensure that:
1. The host running the server and the playback device are connected to the **same local area network (LAN/Wi-Fi)**.
2. The network profile on Windows is set to **Private** (or firewall rules allow incoming TCP traffic on the configured port and UDP traffic on port 1900 for UPnP SSDP).

---

## Troubleshooting

### Server is running but does not appear in the clients
- **Network Interface**: Cling automatically selects an active network interface. If you have virtual interfaces (e.g., VirtualBox, VMware, WSL, Docker), ensure the primary physical interface is being advertised.
- **Firewall**: Ensure your firewall does not block:
  - **UDP 1900** (SSDP discovery)
  - **TCP <your-port>** (HTTP streaming)

### Cannot seek during playback on some devices
- Seek functionality relies on the client requesting bytes via HTTP Range requests. The server fully supports byte-ranges, but some older DLNA clients might require transcoding or specific container parameters. Make sure files are in standard containers like `.mp4` or `.mp3`.
