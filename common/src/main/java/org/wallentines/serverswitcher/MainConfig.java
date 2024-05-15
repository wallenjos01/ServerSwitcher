package org.wallentines.serverswitcher;

import org.wallentines.mcore.MidnightCoreAPI;
import org.wallentines.mdcfg.ConfigObject;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.codec.FileWrapper;
import org.wallentines.mdcfg.serializer.ConfigContext;
import org.wallentines.mdproxy.jwt.KeyStore;
import org.wallentines.mdproxy.jwt.KeyType;

import java.io.File;
import java.security.PublicKey;

class MainConfig {


    private static final ConfigSection DEFAULT_CONFIG = new ConfigSection()
            .with("server", "server")
            .with("proxy_type", ProxyType.NONE.getId())
            .with("messenger", "default")
            .with("storage", new ConfigSection()
                    .with("table_prefix", "svs_"))
            .with("clear_reconnect_cookie", true)
            .with("jwt_expire_sec", 5);

    private final FileWrapper<ConfigObject> config;
    private final KeyStore keyStore;

    String serverName;
    PublicKey key;
    boolean clearReconnect;
    int jwtTimeout;
    String messengerName;
    ProxyType proxyType;

    public MainConfig(File configFolder, KeyStore keyStore) {

        this.config = MidnightCoreAPI.FILE_CODEC_REGISTRY.findOrCreate(ConfigContext.INSTANCE, "config", configFolder, DEFAULT_CONFIG);
        if(!this.config.getFile().exists()) {
            this.config.save();
        }

        this.keyStore = keyStore;
        reload();
    }

    public void reload() {

        this.config.load();
        ConfigSection sec = getConfig();

        this.serverName = sec.getString("server");

        key = keyStore.getKey("key", KeyType.RSA_PUBLIC);

        this.clearReconnect = sec.getBoolean("clear_reconnect_cookie");
        this.jwtTimeout = sec.getInt("jwt_expire_sec");

        this.messengerName = sec.getString("messenger");

        this.proxyType = ProxyType.byId(sec.getString("proxyType"));
    }

    public ConfigSection getConfig() {
        return config.getRoot().asSection();
    }

}
