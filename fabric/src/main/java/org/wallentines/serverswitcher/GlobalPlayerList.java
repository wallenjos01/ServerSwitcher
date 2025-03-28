package org.wallentines.serverswitcher;

import com.mojang.authlib.GameProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GlobalPlayerList {

    public final Map<UUID, Entry> players;

    public GlobalPlayerList() {
        this.players = new HashMap<>();
    }

    public record Entry(GameProfile profile, String server) {

    }

}
