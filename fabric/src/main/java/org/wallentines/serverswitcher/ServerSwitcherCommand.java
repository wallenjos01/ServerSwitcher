package org.wallentines.serverswitcher;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.wallentines.brigpatch.mixin.AccessorCommandContext;
import org.wallentines.mcore.IdentifierArgument;
import org.wallentines.mcore.lang.CustomPlaceholder;
import org.wallentines.mcore.lang.LangManager;
import org.wallentines.mcore.text.MutableComponent;
import org.wallentines.mcore.text.WrappedComponent;
import org.wallentines.midnightlib.registry.Identifier;

import java.util.Map;

public class ServerSwitcherCommand {

    private static final IdentifierArgument DEFAULT = new IdentifierArgument("default");

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
                .then(Commands.argument("server", DEFAULT)
                    .executes(ServerSwitcherCommand::executeAdd)
                )
            )
            .then(addFlags(Commands.literal("edit"), dispatcher.register(edit).getChild("edit"))
                .then(Commands.argument("server", DEFAULT)
                    .suggests(SUGGEST_ALL_SERVERS)
                    .executes(ServerSwitcherCommand::executeEdit)
                )
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("server", DEFAULT)
                    .suggests(SUGGEST_ALL_SERVERS)
                    .executes(ServerSwitcherCommand::executeRemove)
                )
            )
            .then(Commands.literal("list")
                .executes(ServerSwitcherCommand::executeList)
            )
            .then(Commands.literal("sync")
                .executes(ServerSwitcherCommand::executeSync)
            )
            .then(Commands.literal("reload")
                .executes(ServerSwitcherCommand::executeReload)
            )
        );
    }

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_ALL_SERVERS = (ctx, builder) -> {
        ServerSwitcherAPI sw = ServerSwitcher.getInstance();
        return DEFAULT.suggest(sw.getAllServers().stream(), builder);
    };

    private static ArgumentBuilder<CommandSourceStack, ?> addFlags(ArgumentBuilder<CommandSourceStack, ?> builder, CommandNode<CommandSourceStack> redirect) {
        return builder
            .then(Commands.literal("-h").then(Commands.argument("host", StringArgumentType.string()).redirect(redirect)))
            .then(Commands.literal("-p").then(Commands.argument("port", IntegerArgumentType.integer(1, 65535)).redirect(redirect)))
            .then(Commands.literal("-b").then(Commands.argument("backend", StringArgumentType.string()).redirect(redirect)))
            .then(Commands.literal("-P").then(Commands.argument("permission", StringArgumentType.string()).redirect(redirect)))
            .then(Commands.literal("-n").then(Commands.argument("namespace", StringArgumentType.string()).redirect(redirect)));
    }


    private static int executeAdd(CommandContext<CommandSourceStack> ctx) {

        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        if(api == null) {
            ctx.getSource().sendFailure(Component.literal("ServerSwitcher is not loaded!"));
            return 0;
        }

        Identifier server = ctx.getArgument("server", Identifier.class);
        ServerInfo inf = readServerInfo(ctx);

        if(inf.hostname() == null && inf.proxyBackend() == null) {
            ctx.getSource().sendFailure(WrappedComponent.resolved(api.getLangManager().component("error.not_enough_info")));
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

        Identifier server = ctx.getArgument("server", Identifier.class);
        ServerInfo inf = readServerInfo(ctx);

        api.updateServer(server, inf).thenAccept(res -> {
            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(() -> WrappedComponent.resolved(api.getLangManager().component("command.edit", CustomPlaceholder.inline("server", server))), true);
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

        Identifier server = ctx.getArgument("server", Identifier.class);

        api.removeServer(server).thenAccept(res -> {
            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(() -> WrappedComponent.resolved(api.getLangManager().component("command.remove", CustomPlaceholder.inline("server", server))), true);
            } else {
                ctx.getSource().sendFailure(WrappedComponent.resolved(api.getLangManager().component(res.langKey)));
            }
        });

        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {

        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        if(api == null) {
            ctx.getSource().sendFailure(Component.literal("ServerSwitcher is not loaded!"));
            return 0;
        }
        LangManager manager = api.getLangManager();

        ctx.getSource().sendSuccess(() -> {

            MutableComponent out = MutableComponent.empty();
            for(Identifier id : api.getAllServers()) {
                out.addChild(org.wallentines.mcore.text.Component.text("\n"));
                out.addChild(manager.component("command.list.entry", CustomPlaceholder.inline("id", id)));
            }

            return WrappedComponent.resolved(manager.component("command.list", CustomPlaceholder.of("ids", out.toComponent())));
        }, true);

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

        return new ServerInfo(host, port, backend, permission, namespace);
    }
}
