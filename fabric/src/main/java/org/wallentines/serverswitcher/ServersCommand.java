package org.wallentines.serverswitcher;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.wallentines.mcore.MidnightCoreAPI;

public class ServersCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("servers")
                .requires(Permissions.require("serverswitcher.servers", 3))
                .executes(ServersCommand::execute)
        );
    }

    public static int execute(CommandContext<CommandSourceStack> ctx) {

        try {
            ServerSwitcherAPI api = ServerSwitcherAPI.getInstance();
            ServerPlayer spl = ctx.getSource().getPlayerOrException();

            api.getServerGUI().open(spl);
            return 1;

        } catch (Throwable th) {
            MidnightCoreAPI.LOGGER.error("An error occurred while opening a server menu!", th);
            return 0;
        }
    }

}
