package org.wallentines.serverswitcher.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.wallentines.pseudonym.PipelineContext;
import org.wallentines.serverswitcher.ServerSwitcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public class MixinPlayerInfoPacket {

    @WrapOperation(method="createPlayerInitializing", at=@At(value="NEW", target="(Ljava/util/EnumSet;Ljava/util/Collection;)Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket;"))
    private static ClientboundPlayerInfoUpdatePacket redirectPlayerInitializing(EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions, Collection<ServerPlayer> players, Operation<ClientboundPlayerInfoUpdatePacket> original) {

        if(players.isEmpty()) return original.call(actions, players);

        ServerSwitcher ss = ServerSwitcher.getInstance(players.iterator().next().level().getServer());
        ClientboundPlayerInfoUpdatePacket pck = original.call(actions, players);

        if(!ss.getConfig().globalTab()) return pck;

        List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>();
        for(ClientboundPlayerInfoUpdatePacket.Entry e : pck.entries()) {

            Component displayName;
            if(e.displayName() == null) {
                displayName = Component.literal(e.profile().name());
            } else {
                displayName = e.displayName();
            }

            entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                    e.profileId(),
                    e.profile(),
                    e.listed(),
                    e.latency(),
                    e.gameMode(),
                    ss.getLangManager().getMessageFor("tab.entry", "en_us", PipelineContext.builder(ss.getLocalServerInfo()).withContextPlaceholder("display_name", Component.class, displayName).build()),
                    e.showHat(),
                    e.listOrder(),
                    e.chatSession()
            ));
        }

        ((AccessorClientboundPlayerInfoPacket) pck).setEntries(entries);
        return pck;
    }

}
