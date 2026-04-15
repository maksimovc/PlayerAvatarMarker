package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class PlayerAvatarControlCommand extends AbstractPlayerCommand {

    private final PlayerAvatarMarkerPlugin plugin;

    PlayerAvatarControlCommand(PlayerAvatarMarkerPlugin plugin) {
        super("playeravatar", "Open the PlayerAvatarMarker control page");
        this.plugin = plugin;
        setAllowsExtraArguments(true);
        addAliases("pam", "avatarmarker");
    }

    @Override
    protected void execute(CommandContext context,
                           Store<EntityStore> store,
                           Ref<EntityStore> entityRef,
                           PlayerRef playerRef,
                           World world) {
        if (!PlayerAvatarPermissions.canOpenUi(playerRef)) {
            PlayerAvatarPermissions.sendUseDenied(playerRef);
            return;
        }

        String input = context.getInputString();
        String[] args = input == null || input.isBlank() ? new String[0] : input.trim().split("\\s+");
        int start = 0;
        if (args.length > 0 && ("playeravatar".equalsIgnoreCase(args[0]) || "pam".equalsIgnoreCase(args[0]) || "avatarmarker".equalsIgnoreCase(args[0]))) {
            start = 1;
        }

        if (args.length > start) {
            playerRef.sendMessage(Message.raw(PlayerAvatarUiText.choose(
                    playerRef,
                    "Usage: /playeravatar",
                    "Використання: /playeravatar")));
            return;
        }

        plugin.openControlPage(store, entityRef, playerRef);
    }
}