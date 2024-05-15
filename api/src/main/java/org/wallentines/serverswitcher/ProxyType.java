package org.wallentines.serverswitcher;

public enum ProxyType {

    NONE("none"),
    MIDNIGHT("midnightproxy"),
    BUNGEE("bungeecord");

    private final String id;

    ProxyType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static ProxyType byId(String id) {
        for(ProxyType type : values()) {
            if(type.id.equals(id)) return type;
        }
        return NONE;
    }
}
