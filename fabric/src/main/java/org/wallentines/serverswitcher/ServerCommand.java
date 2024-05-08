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
import net.minecraft.server.level.ServerPlayer;
import org.wallentines.mcore.lang.CustomPlaceholder;
import org.wallentines.mcore.text.WrappedComponent;
import org.wallentines.midnightlib.registry.RegistryBase;

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
        if (!sw.sendToServer(spl, inf)) {
            ctx.getSource().sendFailure(WrappedComponent.resolved(sw.getLangManager().component("error.server_not_available", CustomPlaceholder.inline("server", serverId)), ctx.getSource()));
            return 0;
        }

        return 1;
    }

}
