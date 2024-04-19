package org.wallentines.serverswitcher;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.tree.CommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.wallentines.mcore.lang.CustomPlaceholder;
import org.wallentines.mcore.text.WrappedComponent;
import org.wallentines.serverswitcher.mixin.AccessorCommandContext;

import java.util.Map;

public class ServerSwitcherCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("svs")
                .requires(Permissions.require("serverswitcher.admin", 4))
                .then(Commands.literal("add"));


        LiteralArgumentBuilder<CommandSourceStack> edit = Commands.literal("svs")
                .requires(Permissions.require("serverswitcher.admin", 4))
                .then(Commands.literal("edit"));


        dispatcher.register(Commands.literal("svs")
            .requires(Permissions.require("serverswitcher.admin", 4))
            .then(addFlags(Commands.literal("add"), dispatcher.register(add).getChild("add"))
                .then(Commands.argument("server", StringArgumentType.word())
                    .executes(ServerSwitcherCommand::executeAdd)
                )
            )
            .then(addFlags(Commands.literal("edit"), dispatcher.register(edit).getChild("edit"))
                .then(Commands.argument("server", StringArgumentType.word())
                    .suggests(ServerCommand.SUGGEST_SERVERS)
                    .executes(ServerSwitcherCommand::executeEdit)
                )
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("server", StringArgumentType.word())
                    .suggests(ServerCommand.SUGGEST_SERVERS)
                    .executes(ServerSwitcherCommand::executeRemove)
                )
            )
            .then(Commands.literal("sync")
                .executes(ServerSwitcherCommand::executeSync)
            )
            .then(Commands.literal("reload")
                .executes(ServerSwitcherCommand::executeReload)
            )
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addFlags(ArgumentBuilder<CommandSourceStack, ?> builder, CommandNode<CommandSourceStack> redirect) {
        return builder
            .then(Commands.literal("-h").then(Commands.argument("host", StringArgumentType.word()).redirect(redirect)))
            .then(Commands.literal("-p").then(Commands.argument("port", IntegerArgumentType.integer(1, 65535)).redirect(redirect)))
            .then(Commands.literal("-b").then(Commands.argument("backend", StringArgumentType.word()).redirect(redirect)))
            .then(Commands.literal("-P").then(Commands.argument("permission", StringArgumentType.string()).redirect(redirect)))
            .then(Commands.literal("-n").then(Commands.argument("namespace", StringArgumentType.word()).redirect(redirect)));
    }


    private static int executeAdd(CommandContext<CommandSourceStack> ctx) {

        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        if(api == null) {
            ctx.getSource().sendFailure(Component.literal("ServerSwitcher is not loaded!"));
            return 0;
        }

        String server = ctx.getArgument("server", String.class);
        ServerInfo inf = readServerInfo(ctx);

        if(inf.hostname() == null && inf.proxyBackend() == null) {
            ctx.getSource().sendFailure(Component.literal("Server must have hostname or backend!"));
            return 0;
        }

        api.registerServer(server, inf).thenAccept(res -> {

            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(() -> WrappedComponent.resolved(api.getLangManager().component("command.add", CustomPlaceholder.inline("server", server))), true);
            } else {
                ctx.getSource().sendFailure(WrappedComponent.resolved(api.getLangManager().component(res.langKey)));
            }
        });

        return 1;

    }

    private static int executeEdit(CommandContext<CommandSourceStack> ctx) {
        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        if(api == null) {
            ctx.getSource().sendFailure(Component.literal("ServerSwitcher is not loaded!"));
            return 0;
        }

        String server = ctx.getArgument("server", String.class);
        ServerInfo inf = readServerInfo(ctx);

        api.updateServer(server, inf).thenAccept(res -> {
            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(() -> WrappedComponent.resolved(api.getLangManager().component("command.update", CustomPlaceholder.inline("server", server))), true);
            } else {
                ctx.getSource().sendFailure(WrappedComponent.resolved(api.getLangManager().component(res.langKey)));
            }

        });

        return 1;
    }

    private static int executeRemove(CommandContext<CommandSourceStack> ctx) {
        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        if(api == null) {
            ctx.getSource().sendFailure(Component.literal("ServerSwitcher is not loaded!"));
            return 0;
        }

        String server = ctx.getArgument("server", String.class);

        api.removeServer(server).thenAccept(res -> {
            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(() -> WrappedComponent.resolved(api.getLangManager().component("command.remove", CustomPlaceholder.inline("server", server))), true);
            } else {
                ctx.getSource().sendFailure(WrappedComponent.resolved(api.getLangManager().component(res.langKey)));
            }
        });

        return 1;
    }

    private static int executeSync(CommandContext<CommandSourceStack> ctx) {

        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        if(api == null) {
            ctx.getSource().sendFailure(Component.literal("ServerSwitcher is not loaded!"));
            return 0;
        }

        api.sync().thenAccept(res -> {
            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(() -> WrappedComponent.resolved(api.getLangManager().component("command.sync")), true);
            } else {
                ctx.getSource().sendFailure(WrappedComponent.resolved(api.getLangManager().component(res.langKey)));
            }
        });

        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {

        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        if(api == null) {
            ctx.getSource().sendFailure(Component.literal("ServerSwitcher is not loaded!"));
            return 0;
        }

        api.reload().thenAccept(res -> {
            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(() -> WrappedComponent.resolved(api.getLangManager().component("command.reload")), true);
            } else {
                ctx.getSource().sendFailure(WrappedComponent.resolved(api.getLangManager().component(res.langKey)));
            }
        });

        return 1;
    }


    @SuppressWarnings("unchecked")
    private static ServerInfo readServerInfo(CommandContext<CommandSourceStack> ctx) {
        String host = null;
        Integer port = null;
        String backend = null;
        String permission = null;
        String namespace = null;

        Map<String, ParsedArgument<CommandSourceStack, ?>> args = ((AccessorCommandContext<CommandSourceStack>) ctx).getArguments();

        if(args.containsKey("host")) {
            host = ctx.getArgument("host", String.class);
        }
        if(args.containsKey("port")) {
            port = ctx.getArgument("port", Integer.class);
        }
        if(args.containsKey("backend")) {
            backend = ctx.getArgument("backend", String.class);
        }
        if(args.containsKey("permission")) {
            permission = ctx.getArgument("permission", String.class);
        }
        if(args.containsKey("namespace")) {
            namespace = ctx.getArgument("namespace", String.class);
        }

        ServerInfo out = new ServerInfo(host, port, backend, permission, namespace);
        ServerSwitcherAPI.LOGGER.warn("Read server: " + out);

        return out;
    }
}
