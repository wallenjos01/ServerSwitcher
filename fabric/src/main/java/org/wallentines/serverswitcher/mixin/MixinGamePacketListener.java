package org.wallentines.serverswitcher.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.wallentines.pseudonym.lang.LocaleHolder;
import org.wallentines.serverswitcher.ServerSwitcher;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinGamePacketListener {

    @Shadow public ServerPlayer player;

    @WrapOperation(method="removePlayerFromWorld", at=@At(value="INVOKE", target="Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void redirectLeaveMessage(PlayerList instance, Component component, boolean bl, Operation<Void> original) {
        ServerSwitcher ss = ServerSwitcher.getInstance(instance.getServer());
        if(!ss.getConfig().globalJoin()) {
            original.call(instance, component, bl);
            return;
        }

        if(ss.transferred.remove(player.getUUID())) return;

        original.call(instance, ss.getLangManager().getMessage("message.leave", ((LocaleHolder) player).getLanguage(), player, ss.getLocalServerInfo()), bl);
        ss.broadcastLeave(player.getGameProfile());
    }

}