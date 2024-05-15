package org.wallentines.serverswitcher;

import org.wallentines.mcore.ItemStack;
import org.wallentines.mcore.UnresolvedItemStack;
import org.wallentines.mcore.text.Component;
import org.wallentines.mcore.text.TextColor;
import org.wallentines.mdcfg.codec.BinaryCodec;
import org.wallentines.mdcfg.serializer.ObjectSerializer;
import org.wallentines.mdcfg.serializer.Serializer;
import org.wallentines.midnightlib.math.Color;

import java.util.concurrent.atomic.AtomicInteger;

public record ServerInfo(String hostname, Integer port, String proxyBackend, String permission, boolean inGUI, UnresolvedItemStack item) {


    public static final Serializer<ServerInfo> SERIALIZER = ObjectSerializer.create(
            Serializer.STRING.entry("hostname", ServerInfo::hostname).optional(),
            Serializer.INT.entry("port", ServerInfo::port).optional(),
            Serializer.STRING.entry("backend", ServerInfo::proxyBackend).optional(),
            Serializer.STRING.entry("permission", ServerInfo::proxyBackend).optional(),
            Serializer.BOOLEAN.entry("in_gui", ServerInfo::inGUI).orElse(true),
            UnresolvedItemStack.SERIALIZER.mapToBlob(new BinaryCodec(BinaryCodec.Compression.ZSTD)).entry("item", ServerInfo::item).optional(),
            ServerInfo::new
    );

    public UnresolvedItemStack itemOrDefault(String serverId) {

        if(item == null) {

            Component name = serverId == null ? Component.empty() : Component.text(serverId);
            return new UnresolvedItemStack(ItemStack.Builder.woolWithColor(Color.WHITE).withName(name.withColor(TextColor.AQUA)), null, null);
        }
        return item;
    }


}
