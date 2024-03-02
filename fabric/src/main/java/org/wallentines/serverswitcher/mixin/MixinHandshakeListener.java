package org.wallentines.serverswitcher.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.wallentines.serverswitcher.HandshakeHolder;

@Mixin(ServerHandshakePacketListenerImpl.class)
public class MixinHandshakeListener {

    @Shadow @Final private Connection connection;

    @Inject(method="beginLogin", at=@At(value="INVOKE", target="Lnet/minecraft/network/Connection;setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V"))
    private void onLogin(ClientIntentionPacket cip, boolean bl, CallbackInfo ci) {
        ((HandshakeHolder) connection).setHandshake(cip);
    }

}
