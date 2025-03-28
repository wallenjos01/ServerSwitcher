package org.wallentines.serverswitcher;

import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.serializer.ConfigContext;
import org.wallentines.mdcfg.serializer.ObjectSerializer;
import org.wallentines.mdcfg.serializer.Serializer;

public record MainConfig(String server, String messenger, boolean globalTab,
                         boolean globalJoin, ConfigSection databaseConfig) {

    public static final MainConfig DEFAULT = new MainConfig(
            "lobby",
            "default",
            true,
            true,
            new ConfigSection()
                    .with("preset", "default")
                    .with("table_prefix", "ss_"));

    public static final Serializer<MainConfig> SERIALIZER = ObjectSerializer.create(
            Serializer.STRING.entry("server", MainConfig::server),
            Serializer.STRING.entry("messenger", MainConfig::messenger),
            Serializer.BOOLEAN.entry("global_tab", MainConfig::globalTab),
            Serializer.BOOLEAN.entry("global_join", MainConfig::globalJoin),
            ConfigSection.SERIALIZER.entry("db", MainConfig::databaseConfig),
            MainConfig::new
    );

    public static final ConfigSection DEFAULT_CONFIG = SERIALIZER.serialize(ConfigContext.INSTANCE, DEFAULT).getOrThrow().asSection();
}
