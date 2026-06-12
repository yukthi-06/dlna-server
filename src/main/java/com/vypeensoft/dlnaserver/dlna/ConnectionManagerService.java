package com.vypeensoft.dlnaserver.dlna;

import org.jupnp.support.model.ProtocolInfo;
import org.jupnp.support.model.ProtocolInfos;

/**
 * Custom ConnectionManagerService that advertises supported streaming protocol infos.
 * Extends jUPnP's built-in ConnectionManagerService using the fully-qualified class name
 * to avoid naming conflicts with our own class.
 */
public class ConnectionManagerService extends org.jupnp.support.connectionmanager.ConnectionManagerService {

    public ConnectionManagerService() {
        super(
            new ProtocolInfos(
                new ProtocolInfo("http-get:*:video/mp4:*"),
                new ProtocolInfo("http-get:*:video/x-matroska:*"),
                new ProtocolInfo("http-get:*:video/x-msvideo:*"),
                new ProtocolInfo("http-get:*:audio/mpeg:*"),
                new ProtocolInfo("http-get:*:audio/flac:*"),
                new ProtocolInfo("http-get:*:audio/wav:*"),
                new ProtocolInfo("http-get:*:audio/mp4:*"),
                new ProtocolInfo("http-get:*:audio/aac:*"),
                new ProtocolInfo("http-get:*:image/jpeg:*"),
                new ProtocolInfo("http-get:*:image/png:*")
            ),
            null // Sink protocol infos (we don't render/receive)
        );
    }
}
