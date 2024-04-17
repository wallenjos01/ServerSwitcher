package org.wallentines.serverswitcher;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import org.wallentines.mcore.Server;
import org.wallentines.mcore.util.ConversionUtil;

public class Init implements ModInitializer {

    @Override
    public void onInitialize() {

        Server.START_EVENT.register(this, ev -> {
            ServerSwitcher.init(ev.getConfigDirectory().resolve("ServerSwitcher").toFile());
        });

        Server.STOP_EVENT.register(this, ev -> {
            ServerSwitcher.shutdown();
        });

        Server.RUNNING_SERVER.setEvent.register(this, srv -> {
            ServerCommand.register(ConversionUtil.validate(srv).getCommands().getDispatcher());
        });

        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) -> {
            ServerSwitcher sw = (ServerSwitcher) ServerSwitcher.INSTANCE.get();
            if(sw.shouldClearReconnect()) {
                handler.send(new ClientboundStoreCookiePacket(ConversionUtil.toResourceLocation(ServerSwitcher.RECONNECT_COOKIE), new byte[0]));
                handler.send(new ClientboundStoreCookiePacket(ConversionUtil.toResourceLocation(ServerSwitcherAPI.COOKIE_ID), new byte[0]));
            }
        });

    }
}
