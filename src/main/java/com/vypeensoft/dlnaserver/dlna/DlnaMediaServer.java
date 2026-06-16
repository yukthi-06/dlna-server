package com.vypeensoft.dlnaserver.dlna;

import com.vypeensoft.dlnaserver.content.ContentDirectoryService;
import com.vypeensoft.dlnaserver.content.MediaCatalog;
import com.vypeensoft.dlnaserver.util.DlnaProfileCycler;
import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.binding.annotations.AnnotationLocalServiceBinder;
import org.jupnp.model.DefaultServiceManager;
import org.jupnp.model.ValidationException;
import org.jupnp.model.meta.*;
import org.jupnp.model.types.DeviceType;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Function;

/**
 * Manages the UPnP/DLNA service stack, constructing the MediaServer device
 * and publishing it to the local network using Cling.
 */
public class DlnaMediaServer {
    private static final Logger log = LoggerFactory.getLogger(DlnaMediaServer.class);

    private final String serverName;
    private final MediaCatalog catalog;
    private final Function<String, String> urlProvider;
    private final DlnaProfileCycler cycler;
    private UpnpService upnpService;
    private LocalDevice localDevice;

    public DlnaMediaServer(String serverName, MediaCatalog catalog,
                           Function<String, String> urlProvider, DlnaProfileCycler cycler) {
        this.serverName = serverName;
        this.catalog = catalog;
        this.urlProvider = urlProvider;
        this.cycler = cycler;
    }

    /**
     * Starts the UPnP/DLNA Media Server.
     */
    public void start() throws ValidationException {
        log.info("Starting UPnP/DLNA service for: {}", serverName);
        
        // jUPnP 3.x: no-arg constructor is OSGi-only. For standalone Java, must pass configuration.
        upnpService = new UpnpServiceImpl(new DefaultUpnpServiceConfiguration());
        upnpService.startup();

        // Unique Device Name generated from server name
        UDN udn = UDN.uniqueSystemIdentifier(serverName + "-" + UUID.nameUUIDFromBytes(serverName.getBytes()));
        
        DeviceType deviceType = new UDADeviceType("MediaServer", 1);
        DeviceDetails deviceDetails = new DeviceDetails(
            serverName,
            new ManufacturerDetails("Vypeensoft"),
            new ModelDetails("Java DLNA Server", "A pure Java lightweight DLNA server", "1.0.0")
        );

        // Bind ContentDirectory service
        LocalService<ContentDirectoryService> cds = new AnnotationLocalServiceBinder()
                .read(ContentDirectoryService.class);
        cds.setManager(new DefaultServiceManager<>(cds, ContentDirectoryService.class) {
            @Override
            protected ContentDirectoryService createServiceInstance() {
                return new ContentDirectoryService(catalog, urlProvider, cycler);
            }
        });

        // Bind ConnectionManager service
        LocalService<ConnectionManagerService> cms = new AnnotationLocalServiceBinder()
                .read(ConnectionManagerService.class);
        cms.setManager(new DefaultServiceManager<>(cms, ConnectionManagerService.class) {
            @Override
            protected ConnectionManagerService createServiceInstance() {
                return new ConnectionManagerService();
            }
        });

        // Build LocalDevice
        localDevice = new LocalDevice(
            new DeviceIdentity(udn),
            deviceType,
            deviceDetails,
            new LocalService[]{cds, cms}
        );

        // Register in UPnP stack to broadcast presence
        upnpService.getRegistry().addDevice(localDevice);
        
        log.info("DLNA Media Server registered. UDN: {}", udn);
    }

    /**
     * Shuts down the UPnP service stack.
     */
    public void stop() {
        if (upnpService != null) {
            log.info("Stopping UPnP/DLNA service...");
            upnpService.shutdown();
            log.info("UPnP/DLNA service stopped");
        }
    }
}
