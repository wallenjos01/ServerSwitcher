package org.wallentines.serverswitcher;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.level.ServerPlayer;
import org.wallentines.mcore.lang.CustomPlaceholder;
import org.wallentines.mcore.text.WrappedComponent;
import org.wallentines.mcore.util.ConversionUtil;
import org.wallentines.midnightlib.registry.RegistryBase;
import org.wallentines.serverswitcher.mixin.AccessorPacketListener;
import org.wallentines.serverswitcher.util.JWTUtil;

import java.security.PublicKey;

public class ServerCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("server")
                .requires(Permissions.require("serverswitcher.server", 3))
                .then(Commands.argument("server", StringArgumentType.word())
                        .suggests(SUGGEST_SERVERS)
                        .executes(ServerCommand::execute)
                )
        );
    }

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_SERVERS = (ctx, builder) -> {
        ServerSwitcherAPI sw = ServerSwitcher.getInstance();
        return SharedSuggestionProvider.suggest(sw.getServerRegistry().getIds().stream().filter(id -> {
            if(id.equals(sw.getServerName())) return false;
            ServerInfo inf = sw.getServerRegistry().get(id);
            if(inf == null) return false;
            return inf.permission() == null || Permissions.check(ctx.getSource(), inf.permission(), 3);
        }), builder);
    };

    private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        String serverId = ctx.getArgument("server", String.class);
        ServerSwitcher sw = (ServerSwitcher) ServerSwitcher.getInstance();

        if(serverId.equals(sw.getServerName())) {
            ctx.getSource().sendFailure(WrappedComponent.resolved(sw.getLangManager().component("error.already_connected"), ctx.getSource()));
            return 0;
        }

        RegistryBase<String, ServerInfo> reg = sw.getServerRegistry();

        ServerInfo inf = reg.get(serverId);
        if (inf == null) {
            ctx.getSource().sendFailure(WrappedComponent.resolved(sw.getLangManager().component("error.server_not_exists"), ctx.getSource()));
            return 0;
        }

        ServerPlayer spl = ctx.getSource().getPlayerOrException();
        if (!sendToServer(spl, inf)) {
            ctx.getSource().sendFailure(WrappedComponent.resolved(sw.getLangManager().component("error.server_not_available", CustomPlaceholder.inline("server", serverId)), ctx.getSource()));
            return 0;
        }

        return 1;
    }

    public static boolean sendToServer(ServerPlayer spl, ServerInfo inf) {

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
