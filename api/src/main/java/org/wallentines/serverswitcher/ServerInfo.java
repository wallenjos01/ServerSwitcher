package org.wallentines.serverswitcher;

import org.wallentines.mdcfg.serializer.ObjectSerializer;
import org.wallentines.mdcfg.serializer.Serializer;

public record ServerInfo(String hostname, Integer port, String proxyBackend, String permission, String namespace) {

    public static final Serializer<ServerInfo> SERIALIZER = ObjectSerializer.create(
            Serializer.STRING.entry("hostname", ServerInfo::hostname).optional(),
            Serializer.INT.entry("port", ServerInfo::port).optional(),
            Serializer.STRING.entry("proxy_backend", ServerInfo::proxyBackend).optional(),
            Serializer.STRING.entry("permission", ServerInfo::proxyBackend).optional(),
            Serializer.STRING.entry("namespace", ServerInfo::namespace).orElse("default"),
            ServerInfo::new
    );

}
