package org.wallentines.serverswitcher;

import net.minecraft.network.protocol.handshake.ClientIntentionPacket;

public interface HandshakeHolder {

    ClientIntentionPacket getHandshake();

    void setHandshake(ClientIntentionPacket packet);

}
