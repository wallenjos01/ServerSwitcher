package org.wallentines.serverswitcher;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.jetbrains.annotations.Nullable;
import org.wallentines.mcore.lang.CustomPlaceholder;
import org.wallentines.mcore.text.WrappedComponent;

import java.util.HashMap;
import java.util.Map;

public class ServerSwitcherCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        CommandNode<CommandSourceStack> add = dispatcher.register(Commands.literal("svs")
                .requires(Permissions.require("serverswitcher.admin", 4))
                .then(Commands.literal("add"))
        ).getChild("add");

        CommandNode<CommandSourceStack> edit = dispatcher.register(Commands.literal("svs")
                .requires(Permissions.require("serverswitcher.admin", 4))
                .then(Commands.literal("edit"))
        ).getChild("edit");
        dispatcher.register(Commands.literal("svs")
            .requires(Permissions.require("serverswitcher.admin", 4))
            .then(Commands.literal("add")
                .then(Commands.literal("-h").then(Commands.argument("host", StringArgumentType.word()).redirect(add, ctx -> WrappedContext.preserveArgument(ctx, "host", String.class))))
                .then(Commands.literal("-p").then(Commands.argument("port", IntegerArgumentType.integer(1, 65535)).redirect(add, ctx -> WrappedContext.preserveArgument(ctx, "port", Integer.class))))
                .then(Commands.literal("-b").then(Commands.argument("backend", StringArgumentType.word()).redirect(add, ctx -> WrappedContext.preserveArgument(ctx, "backend", String.class))))
                .then(Commands.literal("-P").then(Commands.argument("permission", StringArgumentType.string()).redirect(add, ctx -> WrappedContext.preserveArgument(ctx, "permission", String.class))))
                .then(Commands.literal("-n").then(Commands.argument("namespace", StringArgumentType.word()).redirect(add, ctx -> WrappedContext.preserveArgument(ctx, "namespace", String.class))))
                .then(Commands.argument("server", StringArgumentType.word())
                    .executes(ServerSwitcherCommand::executeAdd)
                )
            )
            .then(Commands.literal("edit")
                .then(Commands.literal("-h").then(Commands.argument("host", StringArgumentType.word()).redirect(edit, ctx -> WrappedContext.preserveArgument(ctx, "host", String.class))))
                .then(Commands.literal("-p").then(Commands.argument("port", IntegerArgumentType.integer(1, 65535)).redirect(edit, ctx -> WrappedContext.preserveArgument(ctx, "port", Integer.class))))
                .then(Commands.literal("-b").then(Commands.argument("backend", StringArgumentType.word()).redirect(edit, ctx -> WrappedContext.preserveArgument(ctx, "backend", String.class))))
                .then(Commands.literal("-P").then(Commands.argument("permission", StringArgumentType.string()).redirect(edit, ctx -> WrappedContext.preserveArgument(ctx, "permission", String.class))))
                .then(Commands.literal("-n").then(Commands.argument("namespace", StringArgumentType.word()).redirect(edit, ctx -> WrappedContext.preserveArgument(ctx, "namespace", String.class))))
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


    private static ServerInfo readServerInfo(CommandContext<CommandSourceStack> ctx) {
        String host = null;
        Integer port = null;
        String backend = null;
        String permission = null;
        String namespace = null;

        CommandSourceStack stack = ctx.getSource();
        WrappedContext arg = (WrappedContext) stack.getSigningContext();

        if(arg.arguments.containsKey("host")) {
            host = (String) arg.arguments.get("host");
        }
        if(arg.arguments.containsKey("port")) {
            port = (int) arg.arguments.get("port");
        }
        if(arg.arguments.containsKey("backend")) {
            backend = (String) arg.arguments.get("backend");
        }
        if(arg.arguments.containsKey("permission")) {
            permission = (String) arg.arguments.get("permission");
        }
        if(arg.arguments.containsKey("namespace")) {
            namespace = (String) arg.arguments.get("namespace");
        }

        ServerInfo out = new ServerInfo(host, port, backend, permission, namespace);
        ServerSwitcherAPI.LOGGER.warn("Read server: " + out);

        return out;
    }


    // Absolutely disgusting jank to preserve arguments across redirects
    private static class WrappedContext implements CommandSigningContext {

        private final CommandSigningContext internal;
        private final Map<String, Object> arguments;

        public WrappedContext(CommandSigningContext original) {
            this.internal = original;
            this.arguments = new HashMap<>();
        }

        public void addArgument(String key, Object arg) {
            arguments.put(key, arg);
        }

        @Nullable
        @Override
        public PlayerChatMessage getArgument(String string) {
            return internal.getArgument(string);
        }

        public static CommandSourceStack preserveArgument(CommandContext<CommandSourceStack> ctx, String arg, Class<?> argClazz) {

            CommandSigningContext src = ctx.getSource().getSigningContext();
            WrappedContext ac;
            if(src instanceof WrappedContext) {
                ac = (WrappedContext) src;
            } else {
                ac = new WrappedContext(src);
            }

            ac.addArgument(arg, ctx.getArgument(arg, argClazz));
            return ctx.getSource().withSigningContext(ac, ctx.getSource().getChatMessageChainer());
        }
    }

}
