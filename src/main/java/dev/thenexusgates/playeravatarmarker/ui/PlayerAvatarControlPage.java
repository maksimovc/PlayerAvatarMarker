package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class PlayerAvatarControlPage extends InteractiveCustomUIPage<PlayerAvatarControlPage.PageData> {

    static final class PageData {
        static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .build();

        String action;
    }

    private static final String UI_PAGE = "Pages/PlayerAvatarControl.ui";
    private static final String UI_ROW = "Pages/PlayerAvatarPlayer_row.ui";
    private static final String GROUP_ROOT = "#RowGroup";
    private static final int PAGE_SIZE = 6;
    private static final String BULK_GREEN = "#4d6488";
    private static final String BULK_RED = "#5a3f46";
    private static final String BULK_MIXED = "#8a6a39";
    private static final String LOCKED_BACKGROUND = "#3e4655";

    private final PlayerAvatarMarkerPlugin plugin;
    private final AtomicBoolean refreshQueued = new AtomicBoolean(false);

    private volatile boolean dismissed;
    private volatile int currentPage;

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
    public void onDismiss(Ref<EntityStore> entityRef,
                          Store<EntityStore> store) {
        dismissed = true;
        super.onDismiss(entityRef, store);
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
            case "Page|Prev" -> changePage(currentPage - 1);
            case "Page|Next" -> changePage(currentPage + 1);
            default -> {
                if (data.action.startsWith("Bulk|")) {
                    handleBulkToggle(data.action.substring("Bulk|".length()));
                } else if (data.action.startsWith("Toggle|")) {
                    handleToggle(data.action.substring("Toggle|".length()));
                }
            }
        }
    }

    private void handleToggle(String payload) {
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NEGATIVE);
            return;
        }

        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException exception) {
            plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NEGATIVE);
            return;
        }

        PlayerAvatarSurface surface;
        try {
            surface = PlayerAvatarSurface.valueOf(parts[1]);
        } catch (IllegalArgumentException exception) {
            plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NEGATIVE);
            return;
        }

        if (!PlayerAvatarPermissions.canEditSurface(playerRef, surface)) {
            PlayerAvatarPermissions.sendSurfaceDenied(playerRef, surface);
            plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NEGATIVE);
            return;
        }

        if (PlayerAvatarPlayerSettings.isSelfForcedHidden(surface, playerRef.getUuid(), targetUuid)) {
            plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NEGATIVE);
            refresh();
            return;
        }

        PlayerAvatarPlayerSettings settings = plugin.resolvePlayerSettings(playerRef);
        boolean nextEnabled = !settings.isEnabledFor(surface, playerRef.getUuid(), targetUuid);
        settings.toggleTarget(surface, playerRef.getUuid(), targetUuid);
        plugin.applyPlayerSettings(playerRef, settings);
        plugin.getUiSounds().play(playerRef, nextEnabled ? PlayerAvatarUiSounds.Cue.POSITIVE : PlayerAvatarUiSounds.Cue.NAVIGATE);
        refresh();
    }

    private void handleBulkToggle(String surfaceName) {
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

        List<PlayerAvatarControlPageModels.RowModel> rows = buildRows();
        if (rows.isEmpty()) {
            plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NEGATIVE);
            return;
        }

        if (bulkState(rows, surface) == PlayerAvatarControlPageModels.SurfaceBulkState.EMPTY) {
            plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NEGATIVE);
            refresh();
            return;
        }

        PlayerAvatarPlayerSettings settings = plugin.resolvePlayerSettings(playerRef);
        boolean enable = bulkState(rows, surface) != PlayerAvatarControlPageModels.SurfaceBulkState.ALL_ENABLED;
        settings.setAll(surface, enable);
        plugin.applyPlayerSettings(playerRef, settings);
        plugin.getUiSounds().play(playerRef, enable ? PlayerAvatarUiSounds.Cue.POSITIVE : PlayerAvatarUiSounds.Cue.NAVIGATE);
        refresh();
    }

    private void changePage(int requestedPage) {
        int rowCount = buildRows().size();
        int pageCount = rowCount == 0 ? 1 : (int) Math.ceil(rowCount / (double) PAGE_SIZE);
        int clamped = Math.max(0, Math.min(requestedPage, pageCount - 1));
        if (clamped == currentPage) {
            plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NEGATIVE);
            return;
        }

        currentPage = clamped;
        plugin.getUiSounds().play(playerRef, PlayerAvatarUiSounds.Cue.NAVIGATE);
        refresh();
    }

    private void refresh() {
        if (dismissed) {
            return;
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        render(cmd, evt);
        sendUpdate(cmd, evt, true);
    }

    private void queueRefresh() {
        if (dismissed || !refreshQueued.compareAndSet(false, true)) {
            return;
        }

        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
            refreshQueued.set(false);
            refresh();
        });
    }

    private void render(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.append(UI_PAGE);

        bindClick(evt, "#CloseButton", "Close");
        bindClick(evt, "#RefreshButton", "Refresh");
        bindClick(evt, "#MapBulkButton", "Bulk|MAP");
        bindClick(evt, "#MinimapBulkButton", "Bulk|MINIMAP");
        bindClick(evt, "#CompassBulkButton", "Bulk|COMPASS");
        bindClick(evt, "#PrevPageButton", "Page|Prev");
        bindClick(evt, "#NextPageButton", "Page|Next");

        List<PlayerAvatarControlPageModels.RowModel> rows = buildRows();
        PlayerAvatarControlPageModels.PageSlice page = PlayerAvatarControlPageModels.pageSlice(rows, currentPage, PAGE_SIZE);
        currentPage = page.pageIndex();

        cmd.set("#Title.Text", t("Player Avatar Control", "Керування аватарками гравців"));
        cmd.set("#RefreshButtonLabel.Text", t("Refresh", "Оновити"));
        cmd.set("#CloseButtonLabel.Text", t("Close", "Закрити"));
        cmd.set("#PlayersTitle.Text", t("Online Players", "Гравці онлайн"));
        cmd.set("#PlayersCount.Text", PlayerAvatarUiText.format(playerRef, "%d players online", "%d гравців онлайн", rows.size()));
        cmd.set("#PageIndicator.Text", rows.isEmpty()
                ? t("No players online", "Немає гравців онлайн")
                : PlayerAvatarUiText.format(playerRef, "Page %d/%d", "Сторінка %d/%d", page.pageIndex() + 1, page.pageCount()));
        cmd.set("#PrevPageButtonLabel.Text", t("Previous", "Назад"));
        cmd.set("#NextPageButtonLabel.Text", t("Next", "Далі"));
        cmd.set("#MapStatusLabel.Text", t("Map", "Мапа"));
        cmd.set("#MinimapStatusLabel.Text", t("Minimap", "Мінімапа"));
        cmd.set("#CompassStatusLabel.Text", t("Compass", "Компас"));
        cmd.set("#MapBulkButtonLabel.Text", t("Map", "Мапа"));
        cmd.set("#MinimapBulkButtonLabel.Text", t("Minimap", "Мінімапа"));
        cmd.set("#CompassBulkButtonLabel.Text", t("Compass", "Компас"));

        int enabledMap = countEnabled(rows, PlayerAvatarSurface.MAP);
        int enabledMinimap = countEnabled(rows, PlayerAvatarSurface.MINIMAP);
        int enabledCompass = countEnabled(rows, PlayerAvatarSurface.COMPASS);
        int totalMap = countEligible(rows, PlayerAvatarSurface.MAP);
        int totalMinimap = countEligible(rows, PlayerAvatarSurface.MINIMAP);
        int totalCompass = countEligible(rows, PlayerAvatarSurface.COMPASS);
        cmd.set("#MapStatusCount.Text", PlayerAvatarUiText.format(playerRef, "%d/%d on", "%d/%d увімк.", enabledMap, totalMap));
        cmd.set("#MinimapStatusCount.Text", PlayerAvatarUiText.format(playerRef, "%d/%d on", "%d/%d увімк.", enabledMinimap, totalMinimap));
        cmd.set("#CompassStatusCount.Text", PlayerAvatarUiText.format(playerRef, "%d/%d on", "%d/%d увімк.", enabledCompass, totalCompass));
        cmd.set("#MapStatusDot.Background", statusDotBackground(enabledMap, totalMap));
        cmd.set("#MinimapStatusDot.Background", statusDotBackground(enabledMinimap, totalMinimap));
        cmd.set("#CompassStatusDot.Background", statusDotBackground(enabledCompass, totalCompass));

        applyBulkButton(cmd, "#MapBulkButton", rows, PlayerAvatarSurface.MAP);
        applyBulkButton(cmd, "#MinimapBulkButton", rows, PlayerAvatarSurface.MINIMAP);
        applyBulkButton(cmd, "#CompassBulkButton", rows, PlayerAvatarSurface.COMPASS);
        cmd.set("#PrevPageButton.Visible", page.pageCount() > 1);
        cmd.set("#NextPageButton.Visible", page.pageCount() > 1);
        cmd.set("#PrevPageButton.Background", page.pageIndex() > 0 ? "#343850" : LOCKED_BACKGROUND);
        cmd.set("#NextPageButton.Background", page.pageIndex() + 1 < page.pageCount() ? "#343850" : LOCKED_BACKGROUND);

        cmd.clear(GROUP_ROOT);
        if (rows.isEmpty()) {
            cmd.set("#EmptyState.Visible", true);
            cmd.set("#EmptyState.Text", t("No players are currently online.", "Зараз немає гравців онлайн."));
        } else {
            cmd.set("#EmptyState.Visible", false);
        }

        int index = 0;
        for (PlayerAvatarControlPageModels.RowModel row : page.rows()) {
            cmd.append(GROUP_ROOT, UI_ROW);
            String rowId = GROUP_ROOT + "[" + index++ + "]";
            boolean canToggleMinimap = isEligible(row, PlayerAvatarSurface.MINIMAP);
            boolean canToggleCompass = isEligible(row, PlayerAvatarSurface.COMPASS);
            cmd.set(rowId + " #PlayerName.Text", row.playerName());
            cmd.set(rowId + " #PlayerMeta.Text", row.metaText());
            cmd.set(rowId + " #MapToggleButtonLabel.Text", toggleText(PlayerAvatarSurface.MAP, row.mapEnabled()));
            cmd.set(rowId + " #MinimapToggleButtonLabel.Text", toggleText(PlayerAvatarSurface.MINIMAP, row.minimapEnabled()));
            cmd.set(rowId + " #CompassToggleButtonLabel.Text", toggleText(PlayerAvatarSurface.COMPASS, row.compassEnabled()));
            cmd.set(rowId + " #MapToggleButton.Background", rowButtonBackground(PlayerAvatarSurface.MAP, row.mapEnabled()));
            cmd.set(rowId + " #MinimapToggleButton.Background", rowButtonBackground(PlayerAvatarSurface.MINIMAP, row.minimapEnabled()));
            cmd.set(rowId + " #CompassToggleButton.Background", rowButtonBackground(PlayerAvatarSurface.COMPASS, row.compassEnabled()));
            cmd.set(rowId + " #MinimapToggleButton.Visible", canToggleMinimap);
            cmd.set(rowId + " #CompassToggleButton.Visible", canToggleCompass);
            cmd.set(rowId + " #RowAccent.Background", row.visibilityState().isGhosted()
                    ? "#9d7fd1"
                    : row.self() ? "#d4a843" : row.mapEnabled() || row.minimapEnabled() || row.compassEnabled() ? "#6f86a8" : "#5a3f46");
            cmd.set(rowId + " #VisibilityBadge.Text", row.visibilityBadge());
            cmd.set(rowId + " #VisibilityBadge.Visible", row.visibilityBadge() != null && !row.visibilityBadge().isBlank());
            bindClick(evt, rowId + " #MapToggleButton", "Toggle|" + row.uuid() + "|MAP");
            if (canToggleMinimap) {
                bindClick(evt, rowId + " #MinimapToggleButton", "Toggle|" + row.uuid() + "|MINIMAP");
            }
            if (canToggleCompass) {
                bindClick(evt, rowId + " #CompassToggleButton", "Toggle|" + row.uuid() + "|COMPASS");
            }
            if (row.assetPath() != null && !row.assetPath().isBlank()) {
                cmd.set(rowId + " #Avatar.AssetPath", row.assetPath());
                cmd.set(rowId + " #Avatar.Visible", true);
            } else {
                cmd.set(rowId + " #Avatar.Visible", false);
            }
        }
    }

    private void applyBulkButton(UICommandBuilder cmd, String selector, List<PlayerAvatarControlPageModels.RowModel> rows, PlayerAvatarSurface surface) {
        PlayerAvatarControlPageModels.SurfaceBulkState state = bulkState(rows, surface);
        cmd.set(selector + ".Background", switch (state) {
            case ALL_ENABLED -> BULK_GREEN;
            case ALL_DISABLED -> BULK_RED;
            case MIXED -> BULK_MIXED;
            case EMPTY -> LOCKED_BACKGROUND;
        });
    }

    private List<PlayerAvatarControlPageModels.RowModel> buildRows() {
        return PlayerAvatarControlPageModels.buildRows(playerRef, plugin, this::queueRefresh, this::queueRefresh);
    }

    private int countEnabled(List<PlayerAvatarControlPageModels.RowModel> rows, PlayerAvatarSurface surface) {
        return PlayerAvatarControlPageModels.countEnabled(rows, surface, playerRef.getUuid());
    }

    private PlayerAvatarControlPageModels.SurfaceBulkState bulkState(List<PlayerAvatarControlPageModels.RowModel> rows, PlayerAvatarSurface surface) {
        return PlayerAvatarControlPageModels.bulkState(rows, surface, playerRef.getUuid());
    }

    private int countEligible(List<PlayerAvatarControlPageModels.RowModel> rows, PlayerAvatarSurface surface) {
        return PlayerAvatarControlPageModels.countEligible(rows, surface, playerRef.getUuid());
    }

    private boolean isEligible(PlayerAvatarControlPageModels.RowModel row, PlayerAvatarSurface surface) {
        return PlayerAvatarControlPageModels.isEligible(row, surface, playerRef.getUuid());
    }

    private String toggleText(PlayerAvatarSurface surface, boolean enabled) {
        String label = switch (surface) {
            case MAP -> t("Map", "Мапа");
            case MINIMAP -> t("Minimap", "Мінімапа");
            case COMPASS -> t("Compass", "Компас");
        };
        return label + ": " + (enabled ? t("On", "Увімк.") : t("Off", "Вимк."));
    }

    private String rowButtonBackground(PlayerAvatarSurface surface, boolean enabled) {
        if (!PlayerAvatarPermissions.canEditSurface(playerRef, surface)) {
            return LOCKED_BACKGROUND;
        }
        return enabled ? BULK_GREEN : BULK_RED;
    }

    private String statusDotBackground(int enabledCount, int total) {
        if (enabledCount == 0 || total == 0) {
            return BULK_RED;
        }
        if (enabledCount == total) {
            return "#6f86a8";
        }
        return "#b88a3e";
    }


    private void bindClick(UIEventBuilder evt, String selector, String action) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action), false);
    }

    private String t(String english, String ukrainian) {
        return PlayerAvatarUiText.choose(playerRef, english, ukrainian);
    }
}