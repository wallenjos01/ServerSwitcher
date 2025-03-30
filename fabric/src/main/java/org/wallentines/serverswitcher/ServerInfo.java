package org.wallentines.serverswitcher;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wallentines.mdcfg.ConfigObject;
import org.wallentines.mdcfg.ConfigPrimitive;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.codec.BinaryCodec;
import org.wallentines.mdcfg.mc.api.ConfigOps;
import org.wallentines.mdcfg.mc.api.RegistryContext;
import org.wallentines.mdcfg.registry.Registry;
import org.wallentines.mdcfg.serializer.*;
import org.wallentines.mdcfg.sql.*;
import org.wallentines.pseudonym.mc.api.ConfigTextParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

public record ServerInfo(int id, String name, String address, short port, @Nullable String backend, @Nullable String permission,
                         @Nullable Component displayName, @Nullable Component prefix, @Nullable ItemStack icon) {


    private static final Logger log = LoggerFactory.getLogger(ServerInfo.class);

    public ItemStack getDisplay(Player player) {

        if(icon == null) {
            return new ItemStack(
                    Holder.direct(Items.WHITE_WOOL),
                    1,
                    DataComponentPatch.builder()
                            .set(DataComponents.ITEM_NAME, getName())
                            .build());
        }

        ItemStack out = icon.copy();
        out.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.ITEM_NAME, getName())
                .build());

        return out;
    }

    public Component getName() {
        return Objects.requireNonNullElseGet(displayName, () -> Component.literal(name));
    }

    public Component getPrefix() {
        return Objects.requireNonNullElseGet(prefix, this::getName);
    }

    public boolean canUse(SharedSuggestionProvider sender) {
        return permission == null || Permissions.check(sender, permission);
    }

    public boolean canUse(ServerPlayer player) {
        return permission == null || Permissions.check(player, permission);
    }

    public static ServerInfo insert(String name, String address, short port, SQLConnection connection) {

        UpdateResult res = connection.insert(Tables.SERVER_NAME, Tables.SERVER_SCHEMA, List.of("sId"))
            .addRow(new ConfigSection()
                .with("name", name)
                .with("address", address)
                .with("port", port))
            .execute();

        if(!res.hasGeneratedKeys() || res.getGeneratedKeys().rows() == 0) {
            return null;
        }

        int id = res.getGeneratedKeys().get(0).getInt("sId");
        return new ServerInfo(id, name, address, port, null, null, null, null, null);
    }

    public static void updateAddress(int id, String address, SQLConnection connection) {
        connection.update(Tables.SERVER_NAME, Tables.SERVER_SCHEMA)
                .withValue("address", DataType.VARCHAR.create(address))
                .where(Condition.equals("sId", DataType.INTEGER.create(id)))
                .execute();
    }

    public static void updatePort(int id, short port, SQLConnection connection) {
        connection.update(Tables.SERVER_NAME, Tables.SERVER_SCHEMA)
                .withValue("port", DataType.SMALLINT.create(port))
                .where(Condition.equals("sId", DataType.INTEGER.create(id)))
                .execute();
    }

    public static void updateBackend(int id, String backend, SQLConnection connection) {
        connection.update(Tables.SERVER_NAME, Tables.SERVER_SCHEMA)
                .withValue("backend", DataType.VARCHAR.create(backend))
                .where(Condition.equals("sId", DataType.INTEGER.create(id)))
                .execute();
    }

    public static void updatePermission(int id, String permission, SQLConnection connection) {
        connection.update(Tables.SERVER_NAME, Tables.SERVER_SCHEMA)
                .withValue("permission", DataType.VARCHAR.create(permission))
                .where(Condition.equals("sId", DataType.INTEGER.create(id)))
                .execute();
    }

    public static void updateDisplayName(int id, Component displayName, SQLConnection connection) {
        connection.update(Tables.SERVER_NAME, Tables.SERVER_SCHEMA)
                .withValue("displayName", DataType.VARCHAR.create(displayName == null ? null : ConfigTextParser.INSTANCE.serialize(displayName)))
                .where(Condition.equals("sId", DataType.INTEGER.create(id)))
                .execute();
    }

    public static void updatePrefix(int id, Component prefix, SQLConnection connection) {
        connection.update(Tables.SERVER_NAME, Tables.SERVER_SCHEMA)
                .withValue("prefix", DataType.VARCHAR.create(prefix == null ? null : ConfigTextParser.INSTANCE.serialize(prefix)))
                .where(Condition.equals("sId", DataType.INTEGER.create(id)))
                .execute();
    }

    public static void updateIcon(int id, ItemStack icon, SQLConnection connection) {
        if(icon == null) {

            connection.update(Tables.SERVER_NAME, Tables.SERVER_SCHEMA)
                    .withRow(new ConfigSection().with("icon", ConfigPrimitive.NULL))
                    .where(Condition.equals("sId", DataType.INTEGER.create(id)))
                    .execute();

        } else {
            ByteBuffer buffer;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                new BinaryCodec(BinaryCodec.Compression.ZSTD).encode(ConfigContext.INSTANCE, ITEM_SERIALIZER, icon, baos);
                buffer = ByteBuffer.wrap(baos.toByteArray());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            connection.update(Tables.SERVER_NAME, Tables.SERVER_SCHEMA)
                    .withValue("icon", DataType.VARBINARY.create(buffer))
                    .where(Condition.equals("sId", DataType.INTEGER.create(id)))
                    .execute();
        }
    }

    public static void remove(ServerInfo info, SQLConnection connection) {
        connection.delete(Tables.SERVER_NAME)
                .where(Condition.equals("sId", DataType.INTEGER.create(info.id)))
                .execute();
    }

    private static final Serializer<ItemStack> ITEM_SERIALIZER = new Serializer<>() {

        @Override
        public <O> SerializeResult<O> serialize(SerializeContext<O> context, ItemStack value) {

            DynamicOps<ConfigObject> ops;
            if(context instanceof RegistryContext<O> reg) {
                ops = RegistryOps.create(ConfigOps.INSTANCE, reg.getContextValue());
            } else {
                ops = ConfigOps.INSTANCE;
            }
            DataResult<ConfigObject> out = ItemStack.CODEC.encode(value, ops, ops.empty());
            if(out.isError()) {
                return SerializeResult.failure(out.error().get().message());
            }

            return SerializeResult.success(ConfigContext.INSTANCE.convert(context, out.getOrThrow()));
        }

        @Override
        public <O> SerializeResult<ItemStack> deserialize(SerializeContext<O> context, O value) {

            DynamicOps<ConfigObject> ops;
            if(context instanceof RegistryContext<O> reg) {
                ops = RegistryOps.create(ConfigOps.INSTANCE, reg.getContextValue());
            } else {
                ops = ConfigOps.INSTANCE;
            }
            DataResult<Pair<ItemStack, ConfigObject>> out = ItemStack.CODEC.decode(ops, context.convert(ConfigContext.INSTANCE, value));
            if(out.isError()) {
                return SerializeResult.failure(out.error().get().message());
            }

            return SerializeResult.success(out.getOrThrow().getFirst());
        }
    };


    public static final Serializer<ServerInfo> SERIALIZER = ObjectSerializer.create(
            Serializer.INT.entry("sId", ServerInfo::id),
            Serializer.STRING.entry("name", ServerInfo::name),
            Serializer.STRING.entry("address", ServerInfo::address),
            Serializer.SHORT.entry("port", ServerInfo::port),
            Serializer.STRING.entry("backend", ServerInfo::backend).optional(),
            Serializer.STRING.entry("permission", ServerInfo::permission).optional(),
            ConfigTextParser.INSTANCE.entry("displayName", ServerInfo::displayName).optional(),
            ConfigTextParser.INSTANCE.entry("prefix", ServerInfo::prefix).optional(),
            ITEM_SERIALIZER
                    .mapToBlob(new BinaryCodec(BinaryCodec.Compression.ZSTD))
                    .entry("icon", ServerInfo::icon)
                    .optional(),
            ServerInfo::new);

    public static Registry<String, ServerInfo> queryAll(MinecraftServer server, SQLConnection connection) {

        Registry<String, ServerInfo> out = Registry.createStringRegistry();
        QueryResult res;
        try {
            res = connection.select(Tables.SERVER_NAME).execute();
        } catch (Exception ex) {
            log.error("Unable to query the database!", ex);
            return out;
        }

        for(int i = 0 ; i < res.rows() ; i++) {
            SerializeResult<ServerInfo> infoRes = SERIALIZER.deserialize(new RegistryContext<>(ConfigContext.INSTANCE, server.registryAccess()), res.get(i).toConfigSection());
            if(infoRes.isComplete()) {
                ServerInfo info = infoRes.getOrThrow();
                out.register(info.name, info);
            } else {
                log.error("Failed to deserialize server info", infoRes.getError());
            }
        }

        return out;
    }

}
