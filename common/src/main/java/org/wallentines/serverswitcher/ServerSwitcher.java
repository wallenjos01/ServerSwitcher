package org.wallentines.serverswitcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wallentines.mcore.MidnightCoreAPI;
import org.wallentines.mcore.Server;
import org.wallentines.mcore.ServerModule;
import org.wallentines.mcore.sql.SQLModule;
import org.wallentines.mdcfg.ConfigObject;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.codec.FileWrapper;
import org.wallentines.mdcfg.serializer.ConfigContext;
import org.wallentines.mdcfg.serializer.SerializeResult;
import org.wallentines.mdcfg.sql.DataType;
import org.wallentines.mdcfg.sql.QueryResult;
import org.wallentines.mdcfg.sql.SQLConnection;
import org.wallentines.mdproxy.jwt.FileKeyStore;
import org.wallentines.mdproxy.jwt.KeyStore;
import org.wallentines.mdproxy.jwt.KeyType;
import org.wallentines.midnightlib.registry.Identifier;
import org.wallentines.midnightlib.registry.RegistryBase;
import org.wallentines.midnightlib.registry.StringRegistry;
import org.wallentines.midnightlib.types.ResettableSingleton;
import org.wallentines.midnightlib.types.Singleton;

import java.io.*;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Random;

public class ServerSwitcher extends ServerSwitcherAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerSwitcher");

    public static final Identifier RECONNECT_COOKIE = new Identifier("mdp", "rc");
    private final RegistryBase<String, ServerInfo> serverRegistry;
    private final FileWrapper<ConfigObject> config;
    private final KeyStore keyStore;
    private String serverName;
    private String namespace;
    private PublicKey key;
    private boolean clearReconnect;
    private int jwtTimeout;

    private static final ConfigSection DEFAULT_CONFIG = new ConfigSection()
            .with("server", "lobby")
            .with("namespace", "default")
            .with("clear_reconnect_cookie", true)
            .with("jwt_expire_sec", 5)
            .with("storage", new ConfigSection()
                    .with("table_prefix", "sws_"));

    private ServerSwitcher(File configFolder) {

        this.keyStore = new FileKeyStore(configFolder, FileKeyStore.DEFAULT_TYPES);

        if(!configFolder.isDirectory() && !configFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create config directory!");
        }

        this.config = MidnightCoreAPI.FILE_CODEC_REGISTRY.findOrCreate(ConfigContext.INSTANCE, "config", configFolder, DEFAULT_CONFIG);
        if(!this.config.getFile().exists()) {
            this.config.save();
        }

        this.serverRegistry = new StringRegistry<>();

        reload();
    }

    @Override
    public void reload() {

        this.config.load();
        ConfigSection sec = getConfig();

        this.serverName = sec.getString("server");
        this.namespace = sec.getString("namespace");

        key = keyStore.getKey("key", KeyType.RSA_PUBLIC);

        this.clearReconnect = sec.getBoolean("clear_reconnect_cookie");
        this.jwtTimeout = sec.getInt("jwt_timeout_ms");

        sync();
    }

    @Override
    public void sync() {

        ServerModule mod = Server.RUNNING_SERVER.get().getModuleManager().getModuleById(SQLModule.ID);
        if(!(mod instanceof SQLModule sql)) {
            throw new IllegalStateException("SQL module is not loaded!");
        }

        sql.connect(getConfig().getSection("storage")).thenAccept(conn -> {
            if(conn.hasTable("meta")) {
                int version = conn.select("meta").execute().get(0).getValue(0, DataType.INTEGER);
                if(version > SCHEMA_VERSION) {
                    throw new IllegalStateException("Detected a database used with a newer version of ServerSwitcher!");
                }
            } else {
                conn.createTable("meta", META_SCHEMA).execute();
                conn.insert("meta", new ConfigSection().with("schema", SCHEMA_VERSION)).execute();
            }

            serverRegistry.clear();
            if(conn.hasTable("servers")) {

                QueryResult res = conn.select("servers").execute();
                boolean requiresKey = false;
                for(int i = 0 ; i < res.rows() ; i++) {
                    ConfigSection sec = SERVER_INFO_SCHEMA.createSection(res.get(i));

                    SerializeResult<ServerInfo> info = ServerInfo.SERIALIZER.deserialize(ConfigContext.INSTANCE, sec);
                    if(!info.isComplete()) {
                        LOGGER.error("Unable to deserialize server with id " + sec.getInt("id") + " (" + sec.getString("name") + ")!");
                        continue;
                    }

                    ServerInfo out = info.getOrThrow();
                    if(out.proxyBackend() != null) {
                        requiresKey = true;
                    }
                    serverRegistry.register(sec.getString("name"), out);
                }

                if(requiresKey && key == null) {
                    LOGGER.warn("There is no public key available in the data folder, but MidnightProxy backend switching was requested! Please put an RSA public key generated by your MidnightProxy server in the data folder in a file called key.pub");
                }

            } else {
                conn.createTable("servers", SERVER_INFO_SCHEMA).execute();
            }
        }).exceptionally(th -> {
            LOGGER.error("An error occurred while connecting to the database!", th);
            return null;
        });

    }

    private ConfigSection getConfig() {
        return config.getRoot().asSection();
    }

    public RegistryBase<String, ServerInfo> getServerRegistry() {
        return serverRegistry;
    }

    public String getServerName() {
        return serverName;
    }

    public String getNamespace() {
        return namespace;
    }

    public PublicKey getKey() {
        return key;
    }

    public static void init(File configFolder) {
        INSTANCE.set(new ServerSwitcher(configFolder));
    }

    public static void shutdown() {
        INSTANCE.reset();
    }

    public boolean shouldClearReconnect() {
        return clearReconnect;
    }

    public int getJWTTimeout() {
        return jwtTimeout;
    }

}
