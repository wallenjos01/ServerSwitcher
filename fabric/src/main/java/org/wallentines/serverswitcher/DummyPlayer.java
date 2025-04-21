package org.wallentines.serverswitcher;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;

public class DummyPlayer extends ServerPlayer {
    public DummyPlayer(MinecraftServer minecraftServer, GameProfile gameProfile) {
        super(minecraftServer, minecraftServer.overworld(), gameProfile, ClientInformation.createDefault());
    }
}
