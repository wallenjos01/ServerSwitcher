package org.wallentines.serverswitcher;

import org.wallentines.mdcfg.serializer.InlineSerializer;

public enum ProxyType {

    NONE("none"),
    MIDNIGHT("midnight"),
    BUNGEE("bungee");

    final String id;

    ProxyType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static ProxyType byId(String id) {
        for(ProxyType pt : values()) {
            if(pt.id.equals(id)) return pt;
        }
        return null;
    }

    public static final InlineSerializer<ProxyType> SERIALIZER = InlineSerializer.of(ProxyType::getId, ProxyType::byId);

}
