package org.wallentines.serverswitcher;

import org.jetbrains.annotations.Nullable;
import org.wallentines.midnightlib.registry.Identifier;
import org.wallentines.midnightlib.registry.RegistryBase;
import org.wallentines.midnightlib.types.ResettableSingleton;

public abstract class ServerSwitcherAPI {

    protected static final ResettableSingleton<ServerSwitcherAPI> INSTANCE = new ResettableSingleton<>();

    public static final Identifier COOKIE_ID = new Identifier("serverswitcher", "switch");
    public static @Nullable ServerSwitcherAPI getInstance() {
        return INSTANCE.get();
    }


    public abstract RegistryBase<String, ServerInfo> getServerRegistry();

    public abstract void reload();

    public abstract String getServerName();

}
