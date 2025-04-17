package org.wallentines.serverswitcher;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wallentines.pseudonym.mc.api.ConfigTextParser;

import java.util.function.Supplier;

public class AdminCommand {

    private static final Logger log = LoggerFactory.getLogger(AdminCommand.class);
    private final Supplier<ServerSwitcher> data;

    private AdminCommand(Supplier<ServerSwitcher> data) {
        this.data = data;
    }

    private LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext buildCtx) {
        return builder
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            ServerSwitcher ss = data.get();
                            ss.reload();
                            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.reload", ctx.getSource().getEntity()), false);
                            return 1;
                        })
                )
                .then(Commands.literal("sync")
                        .executes(ctx -> {
                            ServerSwitcher ss = data.get();
                            ss.sync().whenComplete((_ignored, err) -> {
                                if(err == null) {
                                    ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.sync", ctx.getSource().getEntity()), false);
                                } else {
                                    ServerSwitcher.LOGGER.error("Error syncing servers", err);
                                    ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.sync", ctx.getSource().getEntity()));
                                }
                            });
                            return 1;
                        })
                )
                .then(Commands.literal("add")
                        .then(Commands.argument("server", StringArgumentType.word())
                                .then(Commands.argument("address", StringArgumentType.string())
                                        .executes(this::executeAdd)
                                        .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                                .executes(this::executeAddPort)
                                        )
                                )
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("server", StringArgumentType.word())
                                .executes(this::executeRemove)
                        )
                )
                .then(Commands.literal("edit")
                        .then(Commands.argument("server", StringArgumentType.word())
                                .suggests((ctx, sb) -> {
                                    ServerSwitcher ss = data.get();
                                    return SharedSuggestionProvider.suggest(ss.getServers().idStream(), sb);
                                })
                                .then(Commands.literal("address")
                                        .then(Commands.argument("address", StringArgumentType.string())
                                                .executes(this::executeEditAddress)
                                        )
                                )
                                .then(Commands.literal("port")
                                        .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                                .executes(this::executeEditPort)
                                        )
                                )
                                .then(Commands.literal("backend")
                                        .executes(this::executeRemoveBackend)
                                        .then(Commands.argument("backend", StringArgumentType.string())
                                                .executes(this::executeEditBackend)
                                        )
                                )
                                .then(Commands.literal("permission")
                                        .executes(this::executeRemovePermission)
                                        .then(Commands.argument("permission", StringArgumentType.string())
                                                .executes(this::executeEditPermission)
                                        )
                                )
                                .then(Commands.literal("name")
                                        .executes(this::executeRemoveName)
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(this::executeEditName)
                                        )
                                )
                                .then(Commands.literal("prefix")
                                        .executes(this::executeRemovePrefix)
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(this::executeEditPrefix)
                                        )
                                )
                                .then(Commands.literal("icon")
                                        .executes(this::executeRemoveIcon)
                                        .then(Commands.argument("item", ItemArgument.item(buildCtx))
                                                .executes(this::executeEditIcon)
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1,99))
                                                        .executes(this::executeEditIconCount)
                                                )
                                        )
                                )
                        )
                );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(String id, LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext buildCtx, Supplier<ServerSwitcher> data) {
        return new AdminCommand(data).build(builder, buildCtx);
    }

    private int executeAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "server");
        String address = StringArgumentType.getString(ctx, "address");

        doAdd(name, address, (short) 25565, ctx.getSource());

        return 1;
    }

    private int executeAddPort(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        String name = StringArgumentType.getString(ctx, "server");
        String address = StringArgumentType.getString(ctx, "address");
        short port = (short) IntegerArgumentType.getInteger(ctx, "port");

        doAdd(name, address, port, ctx.getSource());

        return 1;
    }

    private void doAdd(String name, String address, short port, CommandSourceStack sender) {

        ServerSwitcher ss = data.get();
        ss.connectDatabase().thenAccept(sql -> {

            ServerInfo info;
            try {
                info = ServerInfo.insert(name, address, port, sql);
                if (info == null) {
                    sender.sendFailure(ss.getLangManager().getMessage("command.error.add", sender.getEntity()));
                    return;
                }
            } catch (Throwable th) {
                log.error("Unable to add to the database!", th);
                sender.sendFailure(ss.getLangManager().getMessage("command.error.db", sender.getEntity()));
                return;
            }

            sender.sendSuccess(() -> ss.getLangManager().getMessage("command.add", sender.getEntity(), info), false);
            ss.pushUpdate();

        });
    }

    private int executeRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.remove(si, sql);
            } catch (Throwable th) {
                log.error("Unable to remove from the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.remove", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeEditAddress(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        String address = StringArgumentType.getString(ctx, "address");

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updateAddress(si.id(), address, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeEditPort(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        int port = IntegerArgumentType.getInteger(ctx, "port");

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updatePort(si.id(), (short) port, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeRemovePermission(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updatePermission(si.id(), null, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeEditPermission(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        String permission = StringArgumentType.getString(ctx, "permission");

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updatePermission(si.id(), permission, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeRemoveBackend(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            ServerInfo info = new ServerInfo(si.id(), si.name(), si.address(), si.port(), null, si.permission(), si.displayName(), si.prefix(), si.icon());
            try {
                ServerInfo.updateBackend(info.id(), null, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), info), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeEditBackend(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        String backend = StringArgumentType.getString(ctx, "backend");

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updateBackend(si.id(), backend, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeRemoveName(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updateDisplayName(si.id(), null, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ss.pushUpdate();
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
        });

        return 1;
    }

    private int executeEditName(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        String text = StringArgumentType.getString(ctx, "text");
        Component display = ConfigTextParser.INSTANCE.parse(text);

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updateDisplayName(si.id(), display, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeRemovePrefix(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updatePrefix(si.id(), null, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeEditPrefix(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        String text = StringArgumentType.getString(ctx, "text");
        Component display = ConfigTextParser.INSTANCE.parse(text);

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updatePrefix(si.id(), display, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeRemoveIcon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updateIcon(si.id(), null, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private int executeEditIcon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return doEditIcon(ctx, 1);
    }

    private int executeEditIconCount(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return doEditIcon(ctx, IntegerArgumentType.getInteger(ctx, "count"));
    }

    private int doEditIcon(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {

        ServerSwitcher ss = data.get();
        ServerInfo si = getServerInfo(ctx);
        if(si == null) return 0;

        ItemInput in = ItemArgument.getItem(ctx, "item");
        ItemStack is = in.createItemStack(count, false);

        ss.connectDatabase().thenAccept(sql -> {
            try {
                ServerInfo.updateIcon(si.id(), is, sql);
            } catch (Throwable th) {
                log.error("Unable to edit the database!", th);
                ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.db", ctx.getSource().getEntity()));
                return;
            }
            ctx.getSource().sendSuccess(() -> ss.getLangManager().getMessage("command.edit", ctx.getSource().getEntity(), si), false);
            ss.pushUpdate();
        });

        return 1;
    }

    private ServerInfo getServerInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        String name = StringArgumentType.getString(ctx, "server");
        ServerSwitcher ss = data.get();
        ServerInfo si = ss.getServers().get(name);

        if(si == null) {
            ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.invalid_server", ctx.getSource().getEntity()));
            return null;
        }

        return si;
    }

}
