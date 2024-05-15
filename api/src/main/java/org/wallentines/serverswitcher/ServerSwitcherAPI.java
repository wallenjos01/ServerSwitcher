package org.wallentines.serverswitcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wallentines.mcore.Player;
import org.wallentines.mcore.lang.LangManager;
import org.wallentines.mcore.InventoryGUI;
import org.wallentines.mdcfg.sql.Column;
import org.wallentines.mdcfg.sql.Constraint;
import org.wallentines.mdcfg.sql.DataType;
import org.wallentines.mdcfg.sql.TableSchema;
import org.wallentines.midnightlib.registry.Identifier;
import org.wallentines.midnightlib.registry.RegistryBase;
import org.wallentines.midnightlib.types.ResettableSingleton;

import java.util.concurrent.CompletableFuture;

public abstract class ServerSwitcherAPI {

    protected static final ResettableSingleton<ServerSwitcherAPI> INSTANCE = new ResettableSingleton<>();

    public static final Identifier COOKIE_ID = new Identifier("serverswitcher", "switch");
    public static final Logger LOGGER = LoggerFactory.getLogger("ServerSwitcher");

    public static ServerSwitcherAPI getInstance() {
        return INSTANCE.get();
    }


    public abstract RegistryBase<String, ServerInfo> getServerRegistry();

    public abstract String getServerName();

    public abstract CompletableFuture<StatusCode> reload();
    public abstract CompletableFuture<StatusCode> sync();
    public abstract CompletableFuture<StatusCode> registerServer(String server, ServerInfo info);
    public abstract CompletableFuture<StatusCode> updateServer(String server, ServerInfo info);
    public abstract CompletableFuture<StatusCode> removeServer(String server);
    public abstract boolean sendToServer(Player player, ServerInfo info);

    public abstract InventoryGUI getServerGUI();

    public abstract LangManager getLangManager();
    public abstract ProxyType getProxyType();

    public static final Integer SCHEMA_VERSION = 1;

    public static final TableSchema META_SCHEMA = TableSchema.builder()
            .withColumn("schema", DataType.INTEGER)
            .build();

    public static final TableSchema SERVER_INFO_SCHEMA = TableSchema.builder()
            .withColumn(Column.builder("id", DataType.INTEGER).withConstraint(Constraint.PRIMARY_KEY).withConstraint(Constraint.AUTO_INCREMENT))
            .withColumn(Column.builder("name", DataType.VARCHAR(126)).withConstraint(Constraint.NOT_NULL))
            .withColumn("hostname", DataType.VARCHAR(126))
            .withColumn("port", DataType.INTEGER)
            .withColumn("backend", DataType.VARCHAR(126))
            .withColumn("permission", DataType.VARCHAR(126))
            .withColumn("in_gui", DataType.BOOLEAN)
            .withColumn("item", DataType.BLOB(4096))
            .build();

}
