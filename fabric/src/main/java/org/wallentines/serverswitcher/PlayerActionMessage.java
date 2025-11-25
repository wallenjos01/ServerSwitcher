package org.wallentines.serverswitcher;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import org.wallentines.smi.Message;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

public record PlayerActionMessage(Type type, GameProfile profile, String server) {

    public static final String CHANNEL = "serverswitcher:action";

    public ByteBuffer encode() {

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(type.ordinal());

        buf.writeUUID(profile.id());
        buf.writeUtf(profile.name());

        if (type != Type.LEAVE) {
            ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(buf, profile.properties());
        }

        buf.writeUtf(server);

        if (buf.hasArray()) {
            return ByteBuffer.wrap(buf.array());
        } else {
            ByteBuffer out = ByteBuffer.allocate(buf.readableBytes());
            buf.readBytes(out);
            return out;
        }
    }


    public static PlayerActionMessage decode(ByteBuffer payload) {
        return decode(Unpooled.wrappedBuffer(payload));
    }

    public static PlayerActionMessage decode(ByteBuf payload) {

        FriendlyByteBuf buf = new FriendlyByteBuf(payload);
        Type t = Type.values()[buf.readByte()];

        UUID playerId = buf.readUUID();
        String name = buf.readUtf();

        ImmutableMultimap.Builder<String, Property> builder = ImmutableMultimap.builder();
        if(t != Type.LEAVE) {
            PropertyMap map = ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(buf);
            for(Map.Entry<String, Property> entry : map.entries()) {
                builder.put(entry.getKey(), entry.getValue());
            }
        }

        GameProfile profile = new GameProfile(playerId, name, new PropertyMap(builder.build()));

        String server = buf.readUtf();
        return new PlayerActionMessage(t, profile, server);
    }


    public Message toMessage() {
        return new Message(CHANNEL, encode());
    }

    enum Type {
        JOIN,
        LEAVE,
        TRANSFER,
        UPDATE
    }

}
