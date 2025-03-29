package org.wallentines.serverswitcher;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.wallentines.cookieapi.api.PlayerCookies;
import org.wallentines.invmenu.api.InventoryMenu;
import org.wallentines.invmenu.api.PagedInventoryMenu;
import org.wallentines.jwt.CryptCodec;
import org.wallentines.jwt.JWTBuilder;
import org.wallentines.jwt.KeyCodec;
import org.wallentines.jwt.KeyType;
import org.wallentines.mdcfg.registry.Registry;
import org.wallentines.pseudonym.lang.LocaleHolder;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class ServerCommand {


    private final Supplier<ServerSwitcher.Holder> data;

    private ServerCommand(Supplier<ServerSwitcher.Holder> data) {
        this.data = data;
    }

    private LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> builder) {
        return builder
                .then(Commands.argument("server", StringArgumentType.word())
                        .suggests((ctx, sb) -> {
                            Registry<String, ServerInfo> inf = data.get().get().getServers();
                            return SharedSuggestionProvider.suggest(inf.valueStream().filter(si -> si.canUse(ctx.getSource())).map(inf::getId), sb);
                        })
                        .executes(this::executeServer)
                )
                .executes(this::executeServerGui);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(String id, LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext buildCtx, Supplier<ServerSwitcher.Holder> data) {
        return new ServerCommand(data).build(builder);
    }

    private int executeServer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerSwitcher ss = data.get().get();
        String serverId = StringArgumentType.getString(ctx, "server");
        ServerInfo server = ss.getServers().get(serverId);

        if(server == null || !server.canUse(ctx.getSource())) {
            ctx.getSource().sendFailure(ss.getLangManager().getMessage("command.error.invalid_server", ctx.getSource().getEntity()));
            return 0;
        }

        ServerPlayer spl = ctx.getSource().getPlayerOrException();
        transfer(spl, server, ss);
        return 1;
    }

    private void transfer(ServerPlayer spl, ServerInfo server, ServerSwitcher ss) {

        String cookie = new JWTBuilder()
                .withClaim("from", ss.getConfig().server())
                .issuedNow()
                .expiresIn(60L)
                .encrypted(KeyCodec.A256KW(ss.getKeyStore().getKey("cookie", KeyType.AES)), CryptCodec.A256CBC_HS512())
                .asString()
                .getOrThrow();

        PlayerCookies.setCookie(spl, ServerSwitcher.SWITCH_COOKIE, cookie.getBytes(StandardCharsets.UTF_8));

        if(server.backend() != null) {
            String token = new JWTBuilder()
                    .withClaim("backend", server.backend())
                    .issuedNow()
                    .expiresIn(60L)
                    .encrypted(KeyCodec.RSA_OAEP(ss.getKeyStore().getKey("proxy", KeyType.RSA_PUBLIC)), CryptCodec.A256CBC_HS512())
                    .asString()
                    .getOrThrow();

            PlayerCookies.setCookie(spl, ServerSwitcher.BACKEND_COOKIE, token.getBytes(StandardCharsets.UTF_8));
        }

        ss.transferred.add(spl.getUUID());
        spl.connection.send(new ClientboundTransferPacket(server.address(), server.port()));
    }

    private int executeServerGui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerSwitcher ss = data.get().get();
        ServerPlayer spl = ctx.getSource().getPlayerOrException();

        Registry<String, ServerInfo> servers = ss.getServers();

        InventoryMenu gui;

        if(servers.getSize() > 6 * 9) {
            PagedInventoryMenu pgui = PagedInventoryMenu.create(pCtx ->
                    ss.getLangManager().getMessage(
                            "gui.title",
                            pCtx.getFirst(LocaleHolder.class).map(LocaleHolder::getLanguage).orElse(null),
                            pCtx),
                    PagedInventoryMenu.SizeProvider.dynamic(6));

            pgui.addBottomReservedRow(PagedInventoryMenu.RowProvider.pageControls(
                    iCtx -> new ItemStack(Holder.direct(Items.LIME_STAINED_GLASS_PANE), 1, DataComponentPatch.builder()
                            .set(DataComponents.ITEM_NAME, ss.getLangManager().getMessageFor("gui.next", iCtx))
                            .build()),
                    iCtx -> new ItemStack(Holder.direct(Items.RED_STAINED_GLASS_PANE), 1, DataComponentPatch.builder()
                            .set(DataComponents.ITEM_NAME, ss.getLangManager().getMessageFor("gui.prev", iCtx))
                            .build())
            ));
            gui = pgui;
        } else {
            gui = InventoryMenu.create(pCtx -> ss.getLangManager().getMessageFor("gui.title", pCtx), servers.getSize());
        }

        int index = 0;
        for(ServerInfo server : servers.values()) {
            if(server.canUse(spl)) {
                gui.setItem(index++, server.getDisplay(spl), (player, type) -> transfer(spl, server, ss));
            }
        }

        gui.open(spl);

        return 1;
    }


}
