package org.wallentines.serverswitcher;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.wallentines.mcore.lang.CustomPlaceholder;
import org.wallentines.mcore.text.WrappedComponent;
import org.wallentines.serverswitcher.mixin.AccessorCommandContext;

import java.util.List;

public class ServerSwitcherCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {


        CommandNode<CommandSourceStack> node = dispatcher.register(Commands.literal("svs")
            .requires(Permissions.require("serverswitcher.admin", 4))
            .then(Commands.literal("add")
                .then(Commands.argument("server", StringArgumentType.word())
                    .executes(ServerSwitcherCommand::executeAdd)
                )
            )
            .then(Commands.literal("edit")
                .then(Commands.argument("server", StringArgumentType.word())
                    .executes(ServerSwitcherCommand::executeEdit)
                )
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("server", StringArgumentType.word())
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

        addFlags(node.getChild("add").getChild("server"));
        addFlags(node.getChild("edit").getChild("server"));
    }

    private static void addFlags(CommandNode<CommandSourceStack> baseCmd) {

        baseCmd.addChild(Commands.literal("-h").then(Commands.argument("host", StringArgumentType.word())).fork(baseCmd, context -> List.of(context.getSource())).build());
        baseCmd.addChild(Commands.literal("-p").then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))).fork(baseCmd, context -> List.of(context.getSource())).build());
        baseCmd.addChild(Commands.literal("-b").then(Commands.argument("backend", StringArgumentType.word())).fork(baseCmd, context -> List.of(context.getSource())).build());
        baseCmd.addChild(Commands.literal("-P").then(Commands.argument("permission", StringArgumentType.string())).fork(baseCmd, context -> List.of(context.getSource())).build());
        baseCmd.addChild(Commands.literal("-n").then(Commands.argument("namespace", StringArgumentType.word())).fork(baseCmd, context -> List.of(context.getSource())).build());

    }

    private static int executeAdd(CommandContext<CommandSourceStack> ctx) {

        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        if(api == null) {
            ctx.getSource().sendFailure(Component.literal("ServerSwitcher is not loaded!"));
            return 0;
        }

        String server = ctx.getArgument("server", String.class);
        ServerInfo inf = readServerInfo(ctx);

        api.registerServer(server, inf).thenAccept(res -> {

            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(() -> WrappedComponent.resolved(api.getLangManager().component("command.add", CustomPlaceholder.inline("server", server))), true);
            } else {
                ctx.getSource().sendFailure(api.getLangManager().component(res.langKey));
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
                ctx.getSource().sendFailure(api.getLangManager().component(res.langKey));
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
                ctx.getSource().sendFailure(api.getLangManager().component(res.langKey));
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
                ctx.getSource().sendFailure(api.getLangManager().component(res.langKey));
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
                ctx.getSource().sendFailure(api.getLangManager().component(res.langKey));
            }
        });

        return 1;
    }


    private static ServerInfo readServerInfo(CommandContext<CommandSourceStack> ctx) {
        String host = null;
        Integer port = null;
        String backend = null;
        String permission = null;
        String namespace = null;

        AccessorCommandContext<CommandSourceStack> acc = (AccessorCommandContext<CommandSourceStack>) ctx;
        if(acc.getArguments().containsKey("host")) {
            host = ctx.getArgument("host", String.class);
        }
        if(acc.getArguments().containsKey("port")) {
            port = ctx.getArgument("port", Integer.class);
        }
        if(acc.getArguments().containsKey("backend")) {
            backend = ctx.getArgument("backend", String.class);
        }
        if(acc.getArguments().containsKey("permission")) {
            permission = ctx.getArgument("permission", String.class);
        }
        if(acc.getArguments().containsKey("namespace")) {
            namespace = ctx.getArgument("namespace", String.class);
        }

        return new ServerInfo(host, port, backend, permission, namespace);
    }

}
