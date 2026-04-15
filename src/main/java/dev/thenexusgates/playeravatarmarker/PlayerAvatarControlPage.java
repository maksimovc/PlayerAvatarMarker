package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class PlayerAvatarControlPage extends InteractiveCustomUIPage<PlayerAvatarControlPage.PageData> {

    static final class PageData {
        static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .build();

        String action;
    }

    private record RowModel(UUID uuid, String playerName, String metaText, String assetPath, boolean self) {
    }

    private static final String UI_PAGE = "Pages/PlayerAvatarControl.ui";
    private static final String UI_ROW = "Pages/PlayerAvatarPlayer_row.ui";
    private static final String GROUP_ROOT = "#RowGroup";
    private static final String LOCKED_BACKGROUND = "#3e4655";

    private final PlayerAvatarMarkerPlugin plugin;

    PlayerAvatarControlPage(PlayerRef playerRef, PlayerAvatarMarkerPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(Ref<EntityStore> entityRef,
                      UICommandBuilder cmd,
                      UIEventBuilder evt,
                      Store<EntityStore> store) {
        render(cmd, evt);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> entityRef,
                                Store<EntityStore> store,
                                PageData data) {
        if (data == null || data.action == null || data.action.isBlank()) {
            return;
        }

        switch (data.action) {
            case "Close" -> {
                plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NAVIGATE);
                close();
            }
            case "Refresh" -> {
                plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NAVIGATE);
                refresh();
            }
            default -> {
                if (data.action.startsWith("Toggle|")) {
                    handleToggle(data.action.substring("Toggle|".length()));
                }
            }
        }
    }

    private void handleToggle(String surfaceName) {
        PlayerAvatarSurface surface;
        try {
            surface = PlayerAvatarSurface.valueOf(surfaceName);
        } catch (IllegalArgumentException exception) {
            plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NEGATIVE);
            return;
        }

        if (!PlayerAvatarPermissions.canEditSurface(playerRef, surface)) {
            PlayerAvatarPermissions.sendSurfaceDenied(playerRef, surface);
            plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NEGATIVE);
            return;
        }

        PlayerAvatarPlayerSettings settings = plugin.resolvePlayerSettings(playerRef);
        boolean nextEnabled = !settings.isEnabled(surface);
        settings.setEnabled(surface, nextEnabled);
        plugin.applyPlayerSettings(playerRef, settings);
        plugin.getUiSounds().play(playerRef, nextEnabled ? PlayerAvatarUiSounds.Cue.POSITIVE : PlayerAvatarUiSounds.Cue.NAVIGATE);
        refresh();
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        render(cmd, evt);
        sendUpdate(cmd, evt, true);
    }

    private void render(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.append(UI_PAGE);

        bindClick(evt, "#CloseButton", "Close");
        bindClick(evt, "#RefreshButton", "Refresh");
        bindClick(evt, "#MapToggleButton", "Toggle|MAP");
        bindClick(evt, "#MinimapToggleButton", "Toggle|MINIMAP");
        bindClick(evt, "#CompassToggleButton", "Toggle|COMPASS");

        PlayerAvatarPlayerSettings settings = plugin.resolvePlayerSettings(playerRef);
        List<RowModel> rows = buildRows();

        cmd.set("#Title.Text", t("Player Avatar Control", "Керування аватарками гравців"));
        cmd.set("#RefreshButtonLabel.Text", t("Refresh", "Оновити"));
        cmd.set("#CloseButtonLabel.Text", t("Close", "Закрити"));
        cmd.set("#SettingsTitle.Text", t("Surface Settings", "Налаштування поверхонь"));
        cmd.set("#PlayersTitle.Text", t("Online Players", "Гравці онлайн"));
        cmd.set("#PlayersCount.Text", PlayerAvatarUiText.format(playerRef, "%d online", "%d онлайн", rows.size()));
        cmd.set("#MapLabel.Text", t("Large Map", "Велика мапа"));
        cmd.set("#MinimapLabel.Text", t("Minimap", "Мінімапа"));
        cmd.set("#CompassLabel.Text", t("Compass / BetterMap", "Компас / BetterMap"));
        cmd.set("#MapHint.Text", t("Show player avatars on the M map.", "Показувати аватарки гравців на мапі M."));
        cmd.set("#MinimapHint.Text", t("Show player avatars in FastMiniMap.", "Показувати аватарки гравців у FastMiniMap."));
        cmd.set("#CompassHint.Text", t("Show player avatars in the compass radar.", "Показувати аватарки гравців у compass radar."));

        applySurfaceButton(cmd, "#MapToggleButton", "#MapToggleButtonLabel", PlayerAvatarSurface.MAP, settings.mapEnabled);
        applySurfaceButton(cmd, "#MinimapToggleButton", "#MinimapToggleButtonLabel", PlayerAvatarSurface.MINIMAP, settings.minimapEnabled);
        applySurfaceButton(cmd, "#CompassToggleButton", "#CompassToggleButtonLabel", PlayerAvatarSurface.COMPASS, settings.compassEnabled);

        cmd.clear(GROUP_ROOT);
        if (rows.isEmpty()) {
            cmd.set("#EmptyState.Visible", true);
            cmd.set("#EmptyState.Text", t("No players are currently online.", "Зараз немає гравців онлайн."));
        } else {
            cmd.set("#EmptyState.Visible", false);
        }

        int index = 0;
        for (RowModel row : rows) {
            cmd.append(GROUP_ROOT, UI_ROW);
            String rowId = GROUP_ROOT + "[" + index++ + "]";
            cmd.set(rowId + " #PlayerName.Text", row.playerName());
            cmd.set(rowId + " #PlayerMeta.Text", row.metaText());
            cmd.set(rowId + " #RowAccent.Background", row.self() ? "#d4a843" : "#6f86a8");
            if (row.assetPath() != null) {
                cmd.set(rowId + " #Avatar.AssetPath", row.assetPath());
                cmd.set(rowId + " #Avatar.Visible", true);
            } else {
                cmd.set(rowId + " #Avatar.Visible", false);
            }
        }
    }

    private void applySurfaceButton(UICommandBuilder cmd,
                                    String selector,
                                    String labelSelector,
                                    PlayerAvatarSurface surface,
                                    boolean enabled) {
        boolean editable = PlayerAvatarPermissions.canEditSurface(playerRef, surface);
        String surfaceLabel = switch (surface) {
            case MAP -> t("Map", "Мапа");
            case MINIMAP -> t("Minimap", "Мінімапа");
            case COMPASS -> t("Compass", "Компас");
        };
        String suffix = editable
                ? (enabled ? t("On", "Увімк.") : t("Off", "Вимк."))
                : t("Locked", "Заблок.");
        cmd.set(labelSelector + ".Text", surfaceLabel + ": " + suffix);
        cmd.set(selector + ".Background", editable ? (enabled ? "#3f6b73" : "#5f4045") : LOCKED_BACKGROUND);
    }

    private List<RowModel> buildRows() {
        List<PlayerRef> players = new ArrayList<>(plugin.getActivePlayers());
        UUID viewerUuid = playerRef == null ? null : playerRef.getUuid();
        players.sort(Comparator
                .comparing((PlayerRef ref) -> !isSelf(ref, viewerUuid))
                .thenComparing(ref -> safeName(ref).toLowerCase(Locale.ROOT)));

        PlayerAvatarConfig config = PlayerAvatarMarkerPlugin.getConfig();
        int avatarSize = PlayerAvatarMarkerSupport.getAvatarSize(config);
        List<RowModel> rows = new ArrayList<>(players.size());
        for (PlayerRef ref : players) {
            UUID uuid = ref.getUuid();
            if (uuid == null) {
                continue;
            }

            String playerName = safeName(ref);
            PlayerAvatarMarkerSupport.AvatarVisual visual =
                    PlayerAvatarMarkerSupport.resolveAvatarVisual(uuid, playerName, avatarSize);
            String assetPath = PlayerAvatarAssetPack.toUiAssetPath(visual.markerImage());
            boolean self = isSelf(ref, viewerUuid);
            String metaText = self
                    ? t("You", "Ви")
                    : t("Online", "Онлайн");
            rows.add(new RowModel(uuid, playerName, metaText, assetPath, self));
        }
        return rows;
    }

    private String safeName(PlayerRef ref) {
        if (ref == null) {
            return t("Unknown player", "Невідомий гравець");
        }
        String playerName = ref.getUsername();
        if (playerName == null || playerName.isBlank()) {
            UUID uuid = ref.getUuid();
            return uuid != null ? uuid.toString().substring(0, 8) : t("Unknown player", "Невідомий гравець");
        }
        return playerName;
    }

    private boolean isSelf(PlayerRef ref, UUID viewerUuid) {
        return ref != null && viewerUuid != null && viewerUuid.equals(ref.getUuid());
    }

    private void bindClick(UIEventBuilder evt, String selector, String action) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action), false);
    }

    private String t(String english, String ukrainian) {
        return PlayerAvatarUiText.choose(playerRef, english, ukrainian);
    }
}