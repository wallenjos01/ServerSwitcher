package org.wallentines.serverswitcher;

import org.wallentines.mcore.Server;
import org.wallentines.mcore.messenger.Messenger;
import org.wallentines.mcore.messenger.MessengerModule;
import org.wallentines.mcore.messenger.ServerMessengerModule;

import java.util.Objects;

class UpdateManager {

    private static final String UPDATED_MESSAGE = "svs_updated";

    private final Server server;
    private final ServerSwitcher serverSwitcher;

    private String messengerName;
    private Messenger messenger;


    UpdateManager(Server server, ServerSwitcher serverSwitcher) {
        this.server = server;
        this.serverSwitcher = serverSwitcher;

        server.getModuleManager().onLoad.register(this, ev -> {
            if(messengerName != null && ev.getModule() instanceof MessengerModule) {
                setupMessenger((MessengerModule) ev.getModule(), messengerName);
            }
        });
    }

    void reload(String messengerName) {
        if(!Objects.equals(this.messengerName, messengerName)) {
            if(messengerName == null) {
                messenger.unsubscribe(this, UPDATED_MESSAGE);
                messenger = null;
                return;
            }
            messenger = null;

            MessengerModule mod = server.getModuleManager().getModule(ServerMessengerModule.class);

            if(mod != null) {
                setupMessenger(mod, messengerName);
            }
        }
        this.messengerName = messengerName;
    }

    private void setupMessenger(MessengerModule module, String messengerName) {

        messenger = module.getMessenger(messengerName);
        if(messenger == null) {
            ServerSwitcherAPI.LOGGER.error("Unable to find messenger with name " + messengerName);
            return;
        }

        messenger.subscribe(this, UPDATED_MESSAGE, msg -> serverSwitcher.sync());
    }

    void sendUpdate() {
        if(messenger != null && !messenger.isShutdown()) {
            messenger.publish(UPDATED_MESSAGE, "");
        }
    }
}
