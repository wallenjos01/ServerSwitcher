package org.wallentines.serverswitcher;

import org.wallentines.mcore.ItemStack;
import org.wallentines.mcore.text.Component;
import org.wallentines.mcore.text.TextColor;
import org.wallentines.mdcfg.codec.BinaryCodec;
import org.wallentines.mdcfg.serializer.ObjectSerializer;
import org.wallentines.mdcfg.serializer.Serializer;
import org.wallentines.midnightlib.math.Color;

public record ServerInfo(String hostname, Integer port, String proxyBackend, String permission, boolean inGUI, ItemStack item) {

    public static final Serializer<ServerInfo> SERIALIZER = ObjectSerializer.create(
            Serializer.STRING.entry("hostname", ServerInfo::hostname).optional(),
            Serializer.INT.entry("port", ServerInfo::port).optional(),
            Serializer.STRING.entry("proxy_backend", ServerInfo::proxyBackend).optional(),
            Serializer.STRING.entry("permission", ServerInfo::proxyBackend).optional(),
            Serializer.BOOLEAN.entry("in_gui", ServerInfo::inGUI).orElse(true),
            ItemStack.SERIALIZER.mapToBlob(new BinaryCodec(BinaryCodec.Compression.ZSTD)).entry("item", ServerInfo::item).optional(),
            ServerInfo::new
    );

    public ItemStack itemOrDefault(String serverId) {

        if(item == null) {
            return ItemStack.Builder.woolWithColor(Color.WHITE).withName(Component.text(serverId).withColor(TextColor.AQUA)).build();
        }
        return item;
    }


}
