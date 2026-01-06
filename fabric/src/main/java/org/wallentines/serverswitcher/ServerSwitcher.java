package org.wallentines.serverswitcher;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wallentines.databridge.api.ServerStateObjects;
import org.wallentines.jwt.FileKeyStore;
import org.wallentines.jwt.KeyStore;
import org.wallentines.jwt.KeyType;
import org.wallentines.mdcfg.ConfigObject;
import org.wallentines.mdcfg.codec.FileWrapper;
import org.wallentines.mdcfg.mc.api.ServerConfigFolders;
import org.wallentines.mdcfg.mc.api.ServerSQLManager;
import org.wallentines.mdcfg.registry.Registry;
import org.wallentines.mdcfg.serializer.ConfigContext;
import org.wallentines.mdcfg.sql.SQLConnection;
import org.wallentines.pseudonym.*;
import org.wallentines.pseudonym.lang.LangManager;
import org.wallentines.pseudonym.lang.LangProvider;
import org.wallentines.pseudonym.lang.LangRegistry;
import org.wallentines.pseudonym.lang.LocaleHolder;
import org.wallentines.pseudonym.mc.api.ServerPlaceholders;
import org.wallentines.serverswitcher.mixin.AccessorClientboundPlayerInfoPacket;
import org.wallentines.smi.Messenger;
import org.wallentines.smi.MessengerManager;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ServerSwitcher {

    public static final Identifier SWITCH_COOKIE = Identifier.tryBuild("serverswitcher", "switch");
    public static final Identifier BACKEND_COOKIE = Identifier.tryBuild("serverswitcher", "backend");

    public static final Logger LOGGER = LoggerFactory.getLogger(ServerSwitcher.class);

    private final MinecraftServer server;
    private final FileWrapper<ConfigObject> configFile;
    private final LangManager<PartialMessage<String>, Component> lang;
    private final KeyStore keyStore;
    private final GlobalPlayerList globalPlayerList;
    private MainConfig config;
    private Messenger messenger;
    private ServerInfo localInfo;

    public final Set<UUID> transferred = new HashSet<>();

    private long lastUpdated;

    private Registry<String, ServerInfo> servers;

    public ServerSwitcher(MinecraftServer server) {

        this.server = server;
        Path dataFolder = ServerConfigFolders.createConfigFolder(server, "ServerSwitcher").orElseThrow();
        try {
            Files.createDirectories(dataFolder.resolve("lang"));
            Files.createDirectories(dataFolder.resolve("keys"));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to create config folder!", ex);
        }

        this.configFile = ServerConfigFolders.FILE_CODEC_REGISTRY.findOrCreate(ConfigContext.INSTANCE, "config", dataFolder, MainConfig.DEFAULT_CONFIG);

        PlaceholderManager placeholders = ServerPlaceholders.getServerPlaceholders(server);
        MessagePipeline<String, PartialMessage<String>> parser = MessagePipeline.parser(placeholders);
        LangRegistry<PartialMessage<String>> langDefaults = LangRegistry.builder(parser)
                .add("command.error.invalid_server", "That is not a valid server!")
                .add("command.error.add", "Unable to add server")
                .add("command.error.invalid_item", "That is not a valid display item!")
                .add("command.error.sync", "Unable to sync with database!")
                .add("command.error.db", "An error occurred while writing to the database!")
                .add("command.add", "Server <server_name> added")
                .add("command.remove", "Server <server_name> removed")
                .add("command.edit", "Server <server_name> updated")
                .add("command.reload", "ServerSwitcher configuration reloaded")
                .add("command.sync", "ServerSwitcher database synchronized.")
                .add("message.join", "&e<player_name> joined the game in <server_name>")
                .add("message.leave", "&e<player_name> left the game")
                .add("message.transfer", "&e<player_name> traveled to <server_name>")
                .add("message.server_hover", "Click to travel to <server_name>")
                .add("gui.title", "Servers")
                .add("tab.entry", "&8[<server_prefix>&8] &f<display_name>")
                .add("text.unknown_server", "&7Unknown")
                .add("text.unknown_prefix", "&7?")
                .build();

        this.lang = new LangManager<>(Component.class, langDefaults, LangProvider.forDirectory(dataFolder.resolve("lang"), ServerConfigFolders.FILE_CODEC_REGISTRY, parser), ServerPlaceholders.COMPONENT_RESOLVER);
        this.keyStore = new FileKeyStore(dataFolder.resolve("keys"), FileKeyStore.DEFAULT_TYPES);

        this.globalPlayerList = new GlobalPlayerList();
        reload();
    }

    public MainConfig getConfig() {
        return config;
    }

    public LangManager<PartialMessage<String>, Component> getLangManager() {
        return lang;
    }

    public Registry<String, ServerInfo> getServers() {
        return servers;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public void reload() {

        unregisterMessenger();
        if(keyStore.getKey("cookie", KeyType.AES) == null) {
            try {
                KeyGenerator gen = KeyGenerator.getInstance("AES");
                gen.init(256);
                SecretKey key = gen.generateKey();
                keyStore.setKey("cookie", KeyType.AES, key);
            } catch (GeneralSecurityException ex) {
                LOGGER.error("Unable to generate cookie key!", ex);
            }
        }

        globalPlayerList.players.clear();

        configFile.load();
        this.config = MainConfig.SERIALIZER.deserialize(ConfigContext.INSTANCE, configFile.getRoot()).getOrThrow();

        sync().thenAccept(_ignored -> {
            try {
                messenger = MessengerManager.getMessenger(config.messenger());
            } catch (Throwable ex) {
                LOGGER.error("Unable to initialize messenger!", ex);
            }
            if(messenger != null) {
                messenger.subscribe(UpdateMessage.CHANNEL, this, msg -> {
                    long updated = UpdateMessage.decode(msg.payload).time();
                    if (updated > this.lastUpdated) {
                        sync();
                    }
                });
                messenger.subscribe(PlayerActionMessage.CHANNEL, this, msg -> {
                    if(!getConfig().globalTab() && !getConfig().globalJoin()) return;
                    processMessage(PlayerActionMessage.decode(msg.payload));
                });

                if(getConfig().globalTab()) {
                    messenger.publish(new RequestPlayersMessage(config.server()).toMessage());
                }

                messenger.subscribe(RequestPlayersMessage.CHANNEL, this, msg -> {
                    if(!getConfig().globalTab()) return;
                    String serverName = RequestPlayersMessage.decode(msg.payload).server();
                    if(serverName.equals(config.server())) return;

                    for(ServerPlayer pl : server.getPlayerList().getPlayers()) {
                        messenger.publish(new PlayerActionMessage(PlayerActionMessage.Type.UPDATE, pl.getGameProfile(), config.server()).toMessage());
                    }
                });
            }
        });
    }

    public CompletableFuture<Void> sync() {

        this.lastUpdated = System.currentTimeMillis();
        return connectDatabase().thenAccept(conn -> {
            if(!Tables.setupTables(conn)) return;
            servers = ServerInfo.queryAll(server, conn).freeze();
            localInfo = servers.get(config.server());
        });
    }

    public CompletableFuture<SQLConnection> connectDatabase() {
        return ServerSQLManager.getPresetRegistry(server)
                .connect(configFile.getRoot().asSection().getSection("db"))
                .whenComplete((conn, th) -> {
                    if(th != null) {
                        LOGGER.error("An error occurred while communicating with the database!", th);
                    }
                });
    }

    @Nullable
    public ServerInfo getLocalServerInfo() {
        return localInfo;
    }

    public void pushUpdate() {
        UpdateMessage msg = new UpdateMessage(System.currentTimeMillis());
        if(messenger != null) {
            try {
                messenger.publish(msg.toMessage());
            } catch (Exception ex) {
                LOGGER.error("Unable to push update message!", ex);
            }
        }
        sync();
    }

    public void broadcastJoin(GameProfile profile) {
        if(localInfo == null || messenger == null) return;
        messenger.publish(new PlayerActionMessage(PlayerActionMessage.Type.JOIN, profile, localInfo.name()).toMessage());
    }

    public void broadcastLeave(GameProfile profile) {
        if(localInfo == null || messenger == null) return;
        messenger.publish(new PlayerActionMessage(PlayerActionMessage.Type.LEAVE, profile, localInfo.name()).toMessage());
    }

    public void broadcastTransfer(GameProfile profile) {
        if(localInfo == null || messenger == null) return;
        messenger.publish(new PlayerActionMessage(PlayerActionMessage.Type.TRANSFER, profile, localInfo.name()).toMessage());
    }

    public GlobalPlayerList getGlobalPlayerList() {
        return globalPlayerList;
    }

    private void unregisterMessenger() {
        if(messenger != null) {
            messenger.unsubscribe(UpdateMessage.CHANNEL, this);
            messenger.unsubscribe(PlayerActionMessage.CHANNEL, this);
            messenger.unsubscribe(RequestPlayersMessage.CHANNEL, this);
        }
    }

    private void processMessage(PlayerActionMessage msg) {

        if(msg.server().equals(config.server())) {
            return;
        }
        if(server.getPlayerList().getPlayer(msg.profile().id()) != null) {
            return;
        }

        if(config.globalTab()) {
            PipelineContext ctx = PipelineContext.builder()
                    .add(servers.get(msg.server()))
                    .add(new DummyPlayer(server, msg.profile()))
                    .withContextPlaceholder("display_name", msg.profile().name())
                    .build();

            switch (msg.type()) {
                case LEAVE: {
                    server.getPlayerList().getPlayers().forEach(pl ->
                            pl.sendSystemMessage(lang.getMessageFor("message.leave", ((LocaleHolder) pl).getLanguage(), ctx)));
                    break;
                }
                case JOIN: {
                    server.getPlayerList().getPlayers().forEach(pl ->
                            pl.sendSystemMessage(lang.getMessageFor("message.join", ((LocaleHolder) pl).getLanguage(), ctx)));
                    break;
                }
                case TRANSFER: {
                    server.getPlayerList().getPlayers().forEach(pl ->
                            pl.sendSystemMessage(lang.getMessageFor("message.transfer", ((LocaleHolder) pl).getLanguage(), ctx)));
                    break;
                }
                default: 
                    return;
            }

            if(msg.type() == PlayerActionMessage.Type.LEAVE) {
                if(globalPlayerList.players.remove(msg.profile().id()) != null) {
                    server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(msg.profile().id())));
                }
            } else {

                EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT
                );

                if(msg.type() == PlayerActionMessage.Type.JOIN || !globalPlayerList.players.containsKey(msg.profile().id())) {
                    actions.add(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
                }

                globalPlayerList.players.put(msg.profile().id(), new GlobalPlayerList.Entry(msg.profile(), msg.server()));

                ClientboundPlayerInfoUpdatePacket pck = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
                ClientboundPlayerInfoUpdatePacket.Entry ent = new ClientboundPlayerInfoUpdatePacket.Entry(
                        msg.profile().id(),
                        msg.profile(),
                        true,
                        0,
                        GameType.SURVIVAL,
                        lang.getMessageFor("tab.entry", "en_us", ctx),
                        true,
                        0,
                        null
                );
                ((AccessorClientboundPlayerInfoPacket) pck).setEntries(List.of(ent));
                server.getPlayerList().broadcastAll(pck);
            }
        }
    }

    private static final Identifier STATE_OBJECT = Identifier.tryBuild("serverswitcher", "state");
    public static ServerSwitcher getInstance(MinecraftServer server) {
        return ServerStateObjects.getStateObject(server, ServerSwitcher.class, STATE_OBJECT).orElseThrow();
    }

    public static Optional<ServerSwitcher> getOptional(MinecraftServer server) {
        return ServerStateObjects.getStateObject(server, ServerSwitcher.class, STATE_OBJECT);
    }


    public static ServerSwitcher create(MinecraftServer server, @Nullable ServerSwitcher previous) {
        return new ServerSwitcher(server);
    }

    public static void destroy(MinecraftServer server, ServerSwitcher instance) {
        instance.unregisterMessenger();
    }

    static {

        PlaceholderManager placeholders = ServerPlaceholders.getGlobalPlaceholders();
        placeholders.register(new Placeholder<>("server_name", Component.class, ctx ->
                ctx.context().getFirst(ServerInfo.class).map(ServerInfo::getName).or(() ->
                        ctx.context().getFirst(MinecraftServer.class).map(server ->
                        ServerSwitcher.getInstance(server)
                                .getLangManager()
                                .getMessageFor("text.unknown_server", ctx.context()))), null));

        placeholders.register(new Placeholder<>("server_prefix", Component.class, ctx ->
                ctx.context().getFirst(ServerInfo.class).map(ServerInfo::getPrefix).or(() ->
                        ctx.context().getFirst(MinecraftServer.class).map(server ->
                        ServerSwitcher.getInstance(server)
                                .getLangManager()
                                .getMessageFor("text.unknown_prefix", ctx.context()))), null));

        placeholders.register(new Placeholder<>("local_server_name", Component.class, ctx ->
                ctx.context().getFirst(MinecraftServer.class).flatMap(server ->
                        ServerSwitcher.getOptional(server).flatMap(ss ->
                                Optional.ofNullable(ss.localInfo).map(ServerInfo::getName).or(() ->
                                        Optional.ofNullable(ss.getLangManager().getMessageFor("text.unknown_server", ctx.context()))))),
                null));

        placeholders.register(new Placeholder<>("local_server_prefix", Component.class, ctx ->
                ctx.context().getFirst(MinecraftServer.class).flatMap(server ->
                        ServerSwitcher.getOptional(server).flatMap(ss ->
                                Optional.ofNullable(ss.localInfo).map(ServerInfo::getPrefix).or(() ->
                                        Optional.ofNullable(ss.getLangManager().getMessageFor("text.unknown_prefix", ctx.context()))))),
                null));

        placeholders.register(new Placeholder<Component, Void>("player_server_name", Component.class, ctx ->
            ctx.context().getFirst(MinecraftServer.class).flatMap(ServerSwitcher::getOptional).flatMap(ss -> {
                return ctx.context().getFirst(Player.class).map(Entity::getUUID).or(() -> ctx.context().getFirst(UUID.class)).flatMap(uuid -> {
                    if(ss.server.getPlayerList().getPlayer(uuid) != null) return Optional.of(ss.localInfo.getName());
                    GlobalPlayerList.Entry ent = ss.globalPlayerList.players.get(uuid);
                    ServerInfo info = null;
                    if(ent == null || (info = ss.servers.get(ent.server())) == null) {
                        return Optional.empty();
                    }
                    return Optional.of(info.getName());
                }).or(() -> Optional.of(ss.getLangManager().getMessageFor("text.unknown_server", ctx.context())));
            }), null));

        placeholders.register(new Placeholder<Component, Void>("player_server_prefix", Component.class, ctx ->
            ctx.context().getFirst(MinecraftServer.class).flatMap(ServerSwitcher::getOptional).flatMap(ss -> {
                return ctx.context().getFirst(Player.class).map(Entity::getUUID).or(() -> ctx.context().getFirst(UUID.class)).flatMap(uuid -> {
                    if(ss.server.getPlayerList().getPlayer(uuid) != null) return Optional.of(ss.localInfo.getPrefix());
                    GlobalPlayerList.Entry ent = ss.globalPlayerList.players.get(uuid);
                    ServerInfo info;
                    if(ent == null || (info = ss.servers.get(ent.server())) == null) {
                        return Optional.empty();
                    }
                    return Optional.of(info.getPrefix());
                }).or(() -> Optional.of(ss.getLangManager().getMessageFor("text.unknown_prefix", ctx.context())));
            }), null));

    }

}
