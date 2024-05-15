package org.wallentines.serverswitcher;

import org.wallentines.mcore.*;
import org.wallentines.mcore.lang.LangManager;
import org.wallentines.mcore.lang.LangRegistry;
import org.wallentines.mcore.sql.SQLModule;
import org.wallentines.mcore.text.TextColor;
import org.wallentines.mcore.InventoryGUI;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.Functions;
import org.wallentines.mdcfg.serializer.ConfigContext;
import org.wallentines.mdcfg.serializer.SerializeResult;
import org.wallentines.mdcfg.sql.Condition;
import org.wallentines.mdcfg.sql.DataType;
import org.wallentines.mdcfg.sql.QueryResult;
import org.wallentines.mdcfg.sql.SQLConnection;
import org.wallentines.mdproxy.jwt.FileKeyStore;
import org.wallentines.mdproxy.jwt.KeyStore;
import org.wallentines.midnightlib.registry.Identifier;
import org.wallentines.midnightlib.registry.RegistryBase;
import org.wallentines.midnightlib.registry.StringRegistry;

import java.io.File;
import java.security.PublicKey;
import java.util.concurrent.CompletableFuture;

public class ServerSwitcher extends ServerSwitcherAPI {

    public static final Identifier RECONNECT_COOKIE = new Identifier("mdp", "rc");
    private final RegistryBase<String, ServerInfo> serverRegistry;
    private final LangManager langManager;
    private final Functions.F2<Player, ServerInfo, Boolean> sender;
    private final MainConfig mainConfig;
    private final UpdateManager updateManager;
    private InventoryGUI gui;


    private ServerSwitcher(Server server, File configFolder, LangRegistry defaults, Functions.F2<Player, ServerInfo, Boolean> sender) {

        this.sender = sender;

        if(!configFolder.isDirectory() && !configFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create config directory!");
        }

        File langDir = new File(configFolder, "lang");
        if(!langDir.isDirectory() && !langDir.mkdirs()) {
            throw new IllegalStateException("Unable to create lang directory!");
        }

        KeyStore keyStore = new FileKeyStore(configFolder, FileKeyStore.DEFAULT_TYPES);
        this.mainConfig = new MainConfig(configFolder, keyStore);

        this.serverRegistry = new StringRegistry<>(false, false, true);
        this.langManager = new LangManager(defaults, langDir);
        this.updateManager = new UpdateManager(server, this);

        reload();

        this.gui = generateGUI();
    }

    @Override
    public CompletableFuture<StatusCode> reload() {

        this.langManager.reload();
        this.mainConfig.reload();
        this.updateManager.reload(mainConfig.messengerName);

        return sync();
    }

    @Override
    public CompletableFuture<StatusCode> sync() {

        return connectDatabase().thenApply(this::sync);
    }


    public RegistryBase<String, ServerInfo> getServerRegistry() {
        return serverRegistry;
    }

    public String getServerName() {
        return mainConfig.serverName;
    }

    @Override
    public CompletableFuture<StatusCode> registerServer(String server, ServerInfo info) {

        return connectDatabase().thenApply(conn -> {

            if(conn == null) {
                return StatusCode.DB_CONNECT_FAILED;
            }

            try {
                sync(conn);
                if (serverRegistry.contains(server)) {
                    LOGGER.error("Unable to register server " + server + "! A server with that name already exists!");
                    return StatusCode.SERVER_EXISTS;
                }

                if (conn.insert("servers", ServerInfo.SERIALIZER.serialize(ConfigContext.INSTANCE, info).getOrThrow().asSection().with("name", server))
                        .execute()[0] != 1) {

                    LOGGER.error("Unable to register server " + server + "!");
                    return StatusCode.INSERT_FAILED;
                }

                serverRegistry.register(server, info);

                updateManager.sendUpdate();
                return StatusCode.SUCCESS;

            } catch (Throwable ex) {
                LOGGER.error("An exception occurred while registering a server!", ex);
                return StatusCode.UNKNOWN_ERROR;
            }
        });

    }

    @Override
    public CompletableFuture<StatusCode> updateServer(String server, ServerInfo info) {

        return connectDatabase().thenApply(conn -> {

            if(conn == null) {
                return StatusCode.DB_CONNECT_FAILED;
            }

            try {
                sync(conn);
                if(!serverRegistry.contains(server)) {
                    LOGGER.error("Unable to update server " + server + "! No server with that name exists!");
                    return StatusCode.SERVER_NOT_EXISTS;
                }

                if (conn.update("servers")
                        .withRow(ServerInfo.SERIALIZER.serialize(ConfigContext.INSTANCE, info).getOrThrow().asSection())
                        .where(Condition.equals("name", DataType.VARCHAR.create(server)))
                        .execute()[0] != 1) {

                    LOGGER.error("Unable to update server " + server + "!");
                    return StatusCode.UPDATE_FAILED;
                }

                serverRegistry.remove(server);
                serverRegistry.register(server, info);

                updateManager.sendUpdate();
                return StatusCode.SUCCESS;

            } catch (Throwable ex) {
                LOGGER.error("An exception occurred while updating a server!", ex);
                return StatusCode.UNKNOWN_ERROR;
            }
        });
    }

    @Override
    public CompletableFuture<StatusCode> removeServer(String server) {

        return connectDatabase().thenApply(conn -> {

            if(conn == null) {
                return StatusCode.DB_CONNECT_FAILED;
            }

            try {
                sync(conn);
                if(!serverRegistry.contains(server)) {
                    LOGGER.error("Unable to remove server " + server + "! No server with that name exists!");
                    return StatusCode.SERVER_NOT_EXISTS;
                }

                if (conn.delete("servers")
                        .where(Condition.equals("name", DataType.VARCHAR.create(server)))
                        .execute()[0] != 1) {

                    LOGGER.error("Unable to remove server " + server + "!");
                    return StatusCode.DELETE_FAILED;
                }

                serverRegistry.remove(server);

                updateManager.sendUpdate();
                return StatusCode.SUCCESS;

            } catch (Throwable ex) {
                LOGGER.error("An exception occurred while removing a server!", ex);
                return StatusCode.UNKNOWN_ERROR;
            }
        });

    }

    @Override
    public boolean sendToServer(Player player, ServerInfo info) {
        return sender.apply(player, info);
    }

    @Override
    public InventoryGUI getServerGUI() {
        return gui;
    }

    @Override
    public LangManager getLangManager() {
        return langManager;
    }


    private StatusCode sync(SQLConnection connection) {

        if(connection == null) {
            return StatusCode.DB_CONNECT_FAILED;
        }

        QueryResult res;

        try {
            res = connection.select("servers").execute();
        } catch (Throwable th) {
            LOGGER.error("An exception occurred while syncing with the database!", th);
            return StatusCode.UNKNOWN_ERROR;
        }

        serverRegistry.clear();

        boolean requiresKey = false;
        for(int i = 0 ; i < res.rows() ; i++) {
            ConfigSection sec = SERVER_INFO_SCHEMA.createSection(res.get(i));

            SerializeResult<ServerInfo> info = ServerInfo.SERIALIZER.deserialize(ConfigContext.INSTANCE, sec);
            if(!info.isComplete()) {
                LOGGER.error("Unable to deserialize server with id " + sec.getInt("id") + " (" + sec.getString("name") + ")! " + info.getError());
                continue;
            }

            ServerInfo out = info.getOrThrow();
            if(out.proxyBackend() != null) {
                requiresKey = true;
            }
            String name = sec.getString("name");
            if(serverRegistry.contains(name)) {
                LOGGER.error("Found duplicate server with ID " + name + "!");
                continue;
            }

            serverRegistry.register(name, out);
        }

        if(requiresKey && mainConfig.key == null) {
            LOGGER.warn("There is no public key available in the data folder, but MidnightProxy backend switching was requested! Please put an RSA public key generated by your MidnightProxy server in the data folder in a file called key.pub");
        }

        regenerateGUI();

        return StatusCode.SUCCESS;
    }

    private CompletableFuture<SQLConnection> connectDatabase() {

        ServerModule mod = Server.RUNNING_SERVER.get().getModuleManager().getModuleById(SQLModule.ID);
        if(!(mod instanceof SQLModule sql)) {
            throw new IllegalStateException("SQL module is not loaded!");
        }

        return sql.connect(mainConfig.getConfig().getSection("storage")).exceptionally(th -> {
            LOGGER.error("An error occurred while connecting to the database!", th);
            return null;
        }).thenApply(conn -> {

            if(conn.hasTable("meta")) {
                int version = conn.select("meta").execute().get(0).getValue(0, DataType.INTEGER);
                if(version > SCHEMA_VERSION) {
                    throw new IllegalStateException("Detected a database used with a newer version of ServerSwitcher!");
                }
            } else {
                conn.createTable("meta", META_SCHEMA).execute();
                conn.insert("meta", new ConfigSection().with("schema", SCHEMA_VERSION)).execute();
            }

            if(!conn.hasTable("servers")) {
                conn.createTable("servers", SERVER_INFO_SCHEMA).execute();
            }

            return conn;
        });
    }

    private InventoryGUI generateGUI() {

        RegistryBase<String, ServerInfo> guiServers = new StringRegistry<>();
        for(String key : serverRegistry.getIds()) {
            ServerInfo si = serverRegistry.get(key);
            if(si == null) continue;

            if(si.inGUI()) {
                guiServers.register(key, si);
            }
        }
        guiServers = guiServers.freeze();
        int servers = guiServers.getSize();

        InventoryGUI gui;

        if(servers <= 54) {

            gui = InventoryGUI.create(getLangManager().component("gui.title"), servers);

        } else {

            UnresolvedItemStack next = new UnresolvedItemStack(ItemStack.Builder.glassPaneWithColor(TextColor.GREEN), getLangManager().component("gui.next_page"), null);
            UnresolvedItemStack prev = new UnresolvedItemStack(ItemStack.Builder.glassPaneWithColor(TextColor.GREEN), getLangManager().component("gui.prev_page"), null);

            PagedInventoryGUI pGui = InventoryGUI.createPaged(getLangManager().component("gui.title.paged"), PagedInventoryGUI.SizeProvider.dynamic(5), servers);
            pGui.addBottomReservedRow(PagedInventoryGUI.RowProvider.pageControls(next, prev));

            gui = pGui;

        }

        int index = 0;
        for (ServerInfo si : guiServers) {

            String id = guiServers.getId(si);

            UnresolvedItemStack is = si.itemOrDefault(id);
            gui.setItem(index, is, (player, type) -> sendToServer(player, si));
            index++;
        }
        gui.update();

        return gui;
    }

    private void regenerateGUI() {

        InventoryGUI newGui = generateGUI();
        if(gui != null) gui.moveViewers(newGui);
        gui = newGui;
    }


    public PublicKey getKey() {
        return mainConfig.key;
    }

    public static void init(Server server, File configFolder, LangRegistry registry, Functions.F2<Player, ServerInfo, Boolean> sender) {
        INSTANCE.set(new ServerSwitcher(server, configFolder, registry, sender));
    }

    public static void shutdown() {
        INSTANCE.reset();
    }

    public boolean shouldClearReconnect() {
        return mainConfig.clearReconnect;
    }

    public int getJWTTimeout() {
        return mainConfig.jwtTimeout;
    }

}
