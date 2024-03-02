package org.wallentines.serverswitcher.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.wallentines.serverswitcher.HandshakeHolder;

@Mixin(Connection.class)
@Implements(@Interface(iface= HandshakeHolder.class, prefix = "serverswitcher$"))
public abstract class MixinConnection implements HandshakeHolder {

    @Unique
    private ClientIntentionPacket serverswitcher$handshake;

    public ClientIntentionPacket serverswitcher$getHandshake() {
        return serverswitcher$handshake;
    }

    public void serverswitcher$setHandshake(ClientIntentionPacket packet) {
        this.serverswitcher$handshake = packet;
    }

}
