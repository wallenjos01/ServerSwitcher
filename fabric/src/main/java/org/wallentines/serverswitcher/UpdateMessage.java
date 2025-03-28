package org.wallentines.serverswitcher;

import org.wallentines.smi.Message;

import java.nio.ByteBuffer;

public record UpdateMessage(long time) {

    public static final String CHANNEL = "serverswitcher:update";

    public Message toMessage() {
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES);
        buf.putLong(time);
        return new Message(CHANNEL, buf);
    }

    public static UpdateMessage decode(ByteBuffer buffer) {
        return new UpdateMessage(buffer.getLong());
    }
}
