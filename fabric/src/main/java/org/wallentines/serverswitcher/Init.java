package org.wallentines.serverswitcher;

import net.fabricmc.api.ModInitializer;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import org.wallentines.fbev.player.PlayerJoinEvent;
import org.wallentines.fbev.server.CommandLoadEvent;
import org.wallentines.mcore.Server;
import org.wallentines.mcore.util.ConversionUtil;
import org.wallentines.midnightlib.event.Event;

public class Init implements ModInitializer {

    @Override
    public void onInitialize() {

        Server.START_EVENT.register(this, ev -> {
            ServerSwitcher.init(ev.getConfigDirectory().resolve("ServerSwitcher").toFile());
        });

        Server.STOP_EVENT.register(this, ev -> {
            ServerSwitcher.shutdown();
        });

        Event.register(CommandLoadEvent.class, this, ev -> {
            ServerCommand.register(ev.dispatcher());
        });

        Event.register(PlayerJoinEvent.class, this, ev -> {
            ServerSwitcher sw = (ServerSwitcher) ServerSwitcher.INSTANCE.get();
            if(sw.shouldClearReconnect()) {
                ev.getPlayer().connection.send(new ClientboundStoreCookiePacket(ConversionUtil.toResourceLocation(ServerSwitcher.RECONNECT_COOKIE), new byte[0]));
                ev.getPlayer().connection.send(new ClientboundStoreCookiePacket(ConversionUtil.toResourceLocation(ServerSwitcherAPI.COOKIE_ID), new byte[0]));
            }
        });

    }
}
