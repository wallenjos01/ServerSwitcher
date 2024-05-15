package org.wallentines.serverswitcher;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.level.ServerPlayer;
import org.wallentines.mcore.Player;
import org.wallentines.mcore.Server;
import org.wallentines.mcore.lang.LangRegistry;
import org.wallentines.mcore.lang.PlaceholderManager;
import org.wallentines.mcore.util.ConversionUtil;
import org.wallentines.mdcfg.codec.JSONCodec;
import org.wallentines.serverswitcher.mixin.AccessorPacketListener;
import org.wallentines.serverswitcher.util.JWTUtil;

import java.io.IOException;
import java.security.PublicKey;

public class Init implements ModInitializer {

    @Override
    public void onInitialize() {

        Server.START_EVENT.register(this, srv -> {

            try {
                LangRegistry reg = LangRegistry.fromConfig(JSONCodec.loadConfig(getClass().getResourceAsStream("/serverswitcher/en_us.json")).asSection(), PlaceholderManager.INSTANCE);
                ServerSwitcher.init(srv, srv.getConfigDirectory().resolve("ServerSwitcher").toFile(), reg, Init::sendToServer);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to find lang defaults in the mod jar!", e);
            }

        });

        Server.STOP_EVENT.register(this, ev -> {
            ServerSwitcher.shutdown();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ServerCommand.register(dispatcher);
            ServersCommand.register(dispatcher);
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

    static {
        ServerInfo.registerPlaceholders(PlaceholderManager.INSTANCE);
    }


    private static boolean sendToServer(Player player, ServerInfo inf) {

        ServerPlayer spl = ConversionUtil.validate(player);

        ServerSwitcher sw = (ServerSwitcher) ServerSwitcherAPI.getInstance();
        ClientIntentionPacket pck = ((HandshakeHolder) ((AccessorPacketListener) spl.connection).getConnection()).getHandshake();

        String hostname = inf.hostname() == null ? pck.hostName() : inf.hostname();
        int port = inf.port() == null ? pck.port() : inf.port();

        if (inf.proxyBackend() != null) {

            PublicKey key = sw.getKey();
            if (key == null) {
                return false;
            }

            String jwt = JWTUtil.createJWT(
                    key,
                    sw.getJWTTimeout(),
                    hostname,
                    port,
                    pck.protocolVersion(),
                    spl.getUsername(),
                    spl.getUUID(),
                    inf.proxyBackend());

            spl.connection.send(new ClientboundStoreCookiePacket(ConversionUtil.toResourceLocation(ServerSwitcherAPI.COOKIE_ID), jwt.getBytes()));

        }

        spl.connection.send(new ClientboundTransferPacket(hostname, port));
        return true;
    }

}
