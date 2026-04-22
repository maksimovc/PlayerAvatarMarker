package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerAvatarPlayerModelSupport {

    private static final Set<UUID> ENSURED_PLAYER_MODELS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private PlayerAvatarPlayerModelSupport() {
    }

    static void forget(UUID playerUuid) {
        if (playerUuid != null) {
            ENSURED_PLAYER_MODELS.remove(playerUuid);
        }
    }

    static void ensureRenderable(PlayerRef playerRef) {
        try {
            if (playerRef == null) {
                return;
            }

            UUID playerUuid = playerRef.getUuid();
            if (playerUuid == null || ENSURED_PLAYER_MODELS.contains(playerUuid)) {
                return;
            }

            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return;
            }

            Store<EntityStore> store = entityRef.getStore();
            if (store == null) {
                return;
            }

            PlayerSkinComponent skinComponent = store.getComponent(entityRef, PlayerSkinComponent.getComponentType());
            if (skinComponent == null || skinComponent.getPlayerSkin() == null) {
                return;
            }

            if (store.getComponent(entityRef, ModelComponent.getComponentType()) != null) {
                ENSURED_PLAYER_MODELS.add(playerUuid);
                return;
            }

            CosmeticsModule cosmeticsModule = CosmeticsModule.get();
            if (cosmeticsModule == null) {
                return;
            }

            var model = cosmeticsModule.createModel(skinComponent.getPlayerSkin());
            if (model == null) {
                return;
            }

            store.putComponent(entityRef, ModelComponent.getComponentType(), new ModelComponent(model));
            skinComponent.setNetworkOutdated();
            ENSURED_PLAYER_MODELS.add(playerUuid);
        } catch (Exception ignored) {
        }
    }
}

