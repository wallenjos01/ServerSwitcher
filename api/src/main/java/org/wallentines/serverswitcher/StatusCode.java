package org.wallentines.serverswitcher;

public enum StatusCode {

    SUCCESS(null),
    DB_CONNECT_FAILED("error.db_connect_failed"),
    SERVER_EXISTS("error.server_exists"),
    SERVER_NOT_EXISTS("error.server_not_exists"),
    INSERT_FAILED("error.insert_failed"),
    UPDATE_FAILED("error.update_failed"),
    DELETE_FAILED("error.delete_failed"),
    UNKNOWN_ERROR("error.unknown_error");

    public final String langKey;

    StatusCode(String langKey) {
        this.langKey = langKey;
    }
}
