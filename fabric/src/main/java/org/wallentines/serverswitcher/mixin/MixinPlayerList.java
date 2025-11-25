package org.wallentines.serverswitcher.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.wallentines.cookieapi.api.PlayerCookies;
import org.wallentines.jwt.JWT;
import org.wallentines.jwt.JWTReader;
import org.wallentines.jwt.JWTVerifier;
import org.wallentines.pseudonym.PipelineContext;
import org.wallentines.pseudonym.lang.LocaleHolder;
import org.wallentines.serverswitcher.GlobalPlayerList;
import org.wallentines.serverswitcher.ServerSwitcher;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Mixin(PlayerList.class)
public class MixinPlayerList {

    @WrapOperation(method="placeNewPlayer", at=@At(value="INVOKE", target="Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void redirectJoinMessage(PlayerList instance, Component component, boolean bl, Operation<Void> original, @Local(argsOnly = true) ServerPlayer spl) {

        ServerSwitcher ss = ServerSwitcher.getInstance(instance.getServer());

        // Global tablist
        ss.getGlobalPlayerList().players.remove(spl.getUUID());
        if(ss.getConfig().globalTab()) {
            for (GlobalPlayerList.Entry e : ss.getGlobalPlayerList().players.values()) {

                EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT
                );

                PipelineContext ctx = PipelineContext.builder()
                        .add(ss.getServers().get(e.server()))
                        .withContextPlaceholder("display_name", e.profile().name())
                        .build();

                ClientboundPlayerInfoUpdatePacket pck = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
                ClientboundPlayerInfoUpdatePacket.Entry ent = new ClientboundPlayerInfoUpdatePacket.Entry(
                        e.profile().id(),
                        e.profile(),
                        true,
                        0,
                        GameType.SURVIVAL,
                        ss.getLangManager().getMessageFor("tab.entry", ((LocaleHolder) spl).getLanguage(), ctx),
                        true,
                        0,
                        null
                );
                ((AccessorClientboundPlayerInfoPacket) pck).setEntries(List.of(ent));
                spl.connection.send(pck);
            }
        }

        // Global join messages
        if(ss.getConfig().globalJoin()) {
            try {
                PlayerCookies.getCookie(spl, ServerSwitcher.SWITCH_COOKIE).orTimeout(5000L, TimeUnit.MILLISECONDS).thenAccept(data -> {
                    if (data == null) {
                        original.call(instance, ss.getLangManager().getMessageFor("message.join", ((LocaleHolder) spl).getLanguage(), PipelineContext.of(spl, ss.getLocalServerInfo())), bl);
                        ss.broadcastJoin(spl.getGameProfile());
                    } else {
                        JWT jwt = JWTReader.readAny(new String(data, StandardCharsets.UTF_8), ss.getKeyStore().supplier("cookie")).getOrThrow();
                        if (new JWTVerifier().verify(jwt)) {
                            original.call(instance, ss.getLangManager().getMessageFor("message.transfer", ((LocaleHolder) spl).getLanguage(), PipelineContext.of(spl, ss.getLocalServerInfo())), bl);
                            ss.broadcastTransfer(spl.getGameProfile());
                        }
                    }
                }).join();
            } catch (Exception e) {
                ServerSwitcher.LOGGER.error("An error occurred while trying to send global join message for {}", spl.getName(), e);
                original.call(instance, component, bl);
            }
        } else {
            original.call(instance, component, bl);
        }
    }



}
