package org.wallentines.serverswitcher;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import org.wallentines.mcore.Server;
import org.wallentines.mcore.lang.LangRegistry;
import org.wallentines.mcore.lang.PlaceholderManager;
import org.wallentines.mcore.util.ConversionUtil;
import org.wallentines.mdcfg.codec.JSONCodec;

import java.io.IOException;

public class Init implements ModInitializer {

    @Override
    public void onInitialize() {

        Server.START_EVENT.register(this, ev -> {

            try {
                LangRegistry reg = LangRegistry.fromConfig(JSONCodec.loadConfig(getClass().getResourceAsStream("/serverswitcher/en_us.json")).asSection(), PlaceholderManager.INSTANCE);
                ServerSwitcher.init(ev.getConfigDirectory().resolve("ServerSwitcher").toFile(), reg);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to find lang defaults in the mod jar!", e);
            }

        });

        Server.STOP_EVENT.register(this, ev -> {
            ServerSwitcher.shutdown();
        });

        Server.RUNNING_SERVER.setEvent.register(this, srv -> {
            CommandDispatcher<CommandSourceStack> dispatcher = ConversionUtil.validate(srv).getCommands().getDispatcher();
            ServerCommand.register(dispatcher);
            ServerSwitcherCommand.register(dispatcher);
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
