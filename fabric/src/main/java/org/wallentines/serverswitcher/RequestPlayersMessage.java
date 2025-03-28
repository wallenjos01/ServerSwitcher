package org.wallentines.serverswitcher;

import org.wallentines.smi.Message;

import java.nio.ByteBuffer;

public record RequestPlayersMessage(String server) {

    public static final String CHANNEL = "serverswitcher:request_players";

    public Message toMessage() {
        ByteBuffer buf = ByteBuffer.allocate(server.length() + 4);
        buf.putInt(server.length());
        buf.put(server.getBytes());
        return new Message(CHANNEL, buf);
    }

    public static RequestPlayersMessage decode(ByteBuffer buffer) {

        int length = buffer.getInt();
        byte[] server = new byte[length];

        return new RequestPlayersMessage(new String(server));
    }
}
