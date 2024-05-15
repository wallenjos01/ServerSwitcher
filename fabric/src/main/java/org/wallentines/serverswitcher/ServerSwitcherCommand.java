package org.wallentines.serverswitcher;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.nbt.CompoundTag;
import org.wallentines.brigpatch.mixin.AccessorCommandContext;
import org.wallentines.mcore.MidnightCoreAPI;
import org.wallentines.mcore.UnresolvedItemStack;
import org.wallentines.mcore.lang.CustomPlaceholder;
import org.wallentines.mcore.lang.LangManager;
import org.wallentines.mcore.text.MutableComponent;
import org.wallentines.mcore.util.NBTContext;
import org.wallentines.mdcfg.serializer.SerializeResult;

import java.util.Map;

public class ServerSwitcherCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("svs")
                .requires(Permissions.require("serverswitcher.admin", 4))
                .then(Commands.literal("add").then(Commands.argument("server", StringArgumentType.word())));


        LiteralArgumentBuilder<CommandSourceStack> edit = Commands.literal("svs")
                .requires(Permissions.require("serverswitcher.admin", 4))
                .then(Commands.literal("edit").then(Commands.argument("server", StringArgumentType.word())));


        dispatcher.register(Commands.literal("svs")
            .requires(Permissions.require("serverswitcher.admin", 4))
            .then(Commands.literal("add")
                .then(addFlags(Commands.argument("server", StringArgumentType.word()),
                        dispatcher.register(add)
                                .getChild("add")
                                .getChild("server"),
                        ServerSwitcherCommand::executeAdd)
                )
            )
            .then(Commands.literal("edit")
                .then(addFlags(Commands.argument("server", StringArgumentType.word()).suggests(SUGGEST_ALL_SERVERS),
                    dispatcher.register(edit)
                            .getChild("edit")
                            .getChild("server"),
                    ServerSwitcherCommand::executeEdit)
                )
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("server", StringArgumentType.word())
                    .suggests(SUGGEST_ALL_SERVERS)
                    .executes(ServerSwitcherCommand::executeRemove)
                )
            )
            .then(Commands.literal("list")
                .executes(ServerSwitcherCommand::executeList)
            )
            .then(Commands.literal("info")
                .then(Commands.argument("server", StringArgumentType.word())
                    .suggests(SUGGEST_ALL_SERVERS)
                    .executes(ServerSwitcherCommand::executeInfo)
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

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_ALL_SERVERS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ServerSwitcher.getInstance().getServerRegistry().getIds(), builder);

    private static ArgumentBuilder<CommandSourceStack, ?> addFlags(
            ArgumentBuilder<CommandSourceStack, ?> builder,
            CommandNode<CommandSourceStack> redirect,
            Command<CommandSourceStack> execute) {
        return builder
            .then(Commands.literal("-h").then(Commands.argument("host", StringArgumentType.string()).executes(execute).redirect(redirect)))
            .then(Commands.literal("-p").then(Commands.argument("port", IntegerArgumentType.integer(1, 65535)).executes(execute).redirect(redirect)))
            .then(Commands.literal("-b").then(Commands.argument("backend", StringArgumentType.string()).executes(execute).redirect(redirect)))
            .then(Commands.literal("-P").then(Commands.argument("permission", StringArgumentType.string()).executes(execute).redirect(redirect)))
            .then(Commands.literal("-g").then(Commands.argument("in_gui", BoolArgumentType.bool()).executes(execute).redirect(redirect)))
            .then(Commands.literal("-i").then(Commands.argument("item", CompoundTagArgument.compoundTag()).executes(execute).redirect(redirect)))
            .executes(execute);
    }


    private static int executeAdd(CommandContext<CommandSourceStack> ctx) {

        try {
            ServerSwitcherAPI api = ServerSwitcher.getInstance();
            String server = ctx.getArgument("server", String.class);
            ServerInfo inf = readServerInfo(ctx);
            if (inf == null) return 0;

            if (inf.hostname() == null && inf.proxyBackend() == null) {
                ctx.getSource().sendFailure(api.getLangManager().component("error.not_enough_info"));
                return 0;
            }

            api.registerServer(server, inf).thenAccept(res -> {

                if (res == StatusCode.SUCCESS) {
                    ctx.getSource().sendSuccess(api.getLangManager().component("command.add", CustomPlaceholder.inline("server", server)), true);
                } else {
                    ctx.getSource().sendFailure(api.getLangManager().component(res.langKey));
                }
            });

            return 1;

        } catch (Throwable th) {
            MidnightCoreAPI.LOGGER.error("An error occurred while adding a server!", th);
            return 0;
        }
    }

    private static int executeEdit(CommandContext<CommandSourceStack> ctx) {
        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        String server = ctx.getArgument("server", String.class);
        ServerInfo inf = readServerInfo(ctx);
        if(inf == null) return 0;

        api.updateServer(server, inf).thenAccept(res -> {
            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(api.getLangManager().component("command.edit", CustomPlaceholder.inline("server", server)), true);
            } else {
                ctx.getSource().sendFailure(api.getLangManager().component(res.langKey));
            }

        });

        return 1;
    }

    private static int executeRemove(CommandContext<CommandSourceStack> ctx) {
        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        String server = ctx.getArgument("server", String.class);

        api.removeServer(server).thenAccept(res -> {
            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(api.getLangManager().component("command.remove", CustomPlaceholder.inline("server", server)), true);
            } else {
                ctx.getSource().sendFailure(api.getLangManager().component(res.langKey));
            }
        });

        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {

        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        LangManager manager = api.getLangManager();

        MutableComponent out = MutableComponent.empty();
        for(String id : api.getServerRegistry().getIds()) {
            out.addChild(org.wallentines.mcore.text.Component.text("\n"));
            out.addChild(manager.component("command.list.entry", CustomPlaceholder.inline("id", id)).resolveFor(ctx.getSource()));
        }

        ctx.getSource().sendSuccess(manager.component("command.list", CustomPlaceholder.of("ids", out.toComponent())), false);

        return 1;
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx) {

        ServerSwitcherAPI api = ServerSwitcher.getInstance();
        LangManager manager = api.getLangManager();
        String server = ctx.getArgument("server", String.class);
        ServerInfo si = api.getServerRegistry().get(server);

        ctx.getSource().sendSuccess(manager.component("command.info", si, CustomPlaceholder.inline("server", server)), false);

        return 1;
    }


    private static int executeSync(CommandContext<CommandSourceStack> ctx) {

        ServerSwitcherAPI api = ServerSwitcher.getInstance();

        api.sync().thenAccept(res -> {
            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(api.getLangManager().component("command.sync"), true);
            } else {
                ctx.getSource().sendFailure(api.getLangManager().component(res.langKey));
            }
        });

        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {

        ServerSwitcherAPI api = ServerSwitcher.getInstance();

        api.reload().thenAccept(res -> {
            if(res == StatusCode.SUCCESS) {
                ctx.getSource().sendSuccess(api.getLangManager().component("command.reload"), true);
            } else {
                ctx.getSource().sendFailure(api.getLangManager().component(res.langKey));
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
        boolean inGui = true;
        UnresolvedItemStack item = null;

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
        if(args.containsKey("in_gui")) {
            inGui = ctx.getArgument("in_gui", Boolean.class);
        }
        if(args.containsKey("item")) {
            CompoundTag encodedItem = ctx.getArgument("item", CompoundTag.class);

            SerializeResult<UnresolvedItemStack> uis = UnresolvedItemStack.SERIALIZER.deserialize(NBTContext.INSTANCE, encodedItem);
            if(!uis.isComplete()) {
                MidnightCoreAPI.LOGGER.warn("Unable to parse display item! " + uis.getError());
                ctx.getSource().sendFailure(ServerSwitcher.getInstance().getLangManager().component("error.invalid_item"));
                return null;
            }

            item = uis.getOrThrow();
        }

        return new ServerInfo(host, port, backend, permission, inGui, item);
    }
}
