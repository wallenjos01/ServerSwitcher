package org.wallentines.serverswitcher;

import org.wallentines.mcore.ItemStack;
import org.wallentines.mcore.UnresolvedItemStack;
import org.wallentines.mcore.lang.PlaceholderManager;
import org.wallentines.mcore.lang.PlaceholderSupplier;
import org.wallentines.mcore.text.Component;
import org.wallentines.mcore.text.TextColor;
import org.wallentines.mdcfg.codec.BinaryCodec;
import org.wallentines.mdcfg.serializer.ObjectSerializer;
import org.wallentines.mdcfg.serializer.Serializer;
import org.wallentines.midnightlib.math.Color;

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

    public static void registerPlaceholders(PlaceholderManager manager) {

        manager.registerSupplier("server_info_hostname", PlaceholderSupplier.inline(ctx -> ctx.onValueOr(ServerInfo.class, ServerInfo::hostname, "")));
        manager.registerSupplier("server_info_port", PlaceholderSupplier.inline(ctx -> ctx.onValueOr(ServerInfo.class, si -> si.port == null ? "" : si.port.toString(), "")));
        manager.registerSupplier("server_info_backend", PlaceholderSupplier.inline(ctx -> ctx.onValueOr(ServerInfo.class, ServerInfo::proxyBackend, "")));
        manager.registerSupplier("server_info_permission", PlaceholderSupplier.inline(ctx -> ctx.onValueOr(ServerInfo.class, ServerInfo::permission, "")));
        manager.registerSupplier("server_info_in_gui", PlaceholderSupplier.inline(ctx -> ctx.onValueOr(ServerInfo.class, ServerInfo::inGUI, true).toString()));

    }


}
