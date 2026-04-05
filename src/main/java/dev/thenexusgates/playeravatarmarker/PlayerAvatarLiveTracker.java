package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class PlayerAvatarLiveTracker {

    private static final ConcurrentMap<UUID, LiveSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private PlayerAvatarLiveTracker() {}

    static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        PacketAdapters.registerInbound((PlayerPacketWatcher) (playerRef, packet) -> {
            if (!(packet instanceof ClientMovement movement) || playerRef == null) {
                return;
            }

            UUID playerUuid = playerRef.getUuid();
            if (playerUuid == null) {
                return;
            }

            Vector3d position = toVector(movement.absolutePosition);
            Vector3f lookRotation = toRotation(movement.lookOrientation);
            Vector3f bodyRotation = toRotation(movement.bodyOrientation);
            Vector3f rotation = lookRotation != null ? lookRotation : bodyRotation;

            if (position == null && rotation == null) {
                return;
            }

            SNAPSHOTS.compute(playerUuid, (ignored, existing) -> merge(existing, position, rotation));
        });
    }

    static void remove(UUID playerUuid) {
        if (playerUuid != null) {
            SNAPSHOTS.remove(playerUuid);
        }
    }

    static Vector3d resolvePosition(PlayerRef ref) {
        if (ref == null) {
            return null;
        }

        LiveSnapshot snapshot = snapshot(ref.getUuid());
        if (snapshot != null && snapshot.position() != null) {
            return new Vector3d(snapshot.position());
        }

        Transform transform = ref.getTransform();
        if (transform == null || transform.getPosition() == null) {
            return null;
        }

        return new Vector3d(transform.getPosition());
    }

    static Vector3f resolveRotation(PlayerRef ref) {
        if (ref == null) {
            return null;
        }

        LiveSnapshot snapshot = snapshot(ref.getUuid());
        if (snapshot != null && snapshot.rotation() != null) {
            return new Vector3f(snapshot.rotation());
        }

        Vector3f headRotation = ref.getHeadRotation();
        return headRotation != null ? new Vector3f(headRotation) : null;
    }

    static Transform resolveTransform(PlayerRef ref) {
        Vector3d position = resolvePosition(ref);
        if (position == null) {
            return null;
        }

        Vector3f rotation = resolveRotation(ref);
        return new Transform(position, rotation != null ? rotation : Vector3f.ZERO);
    }

    private static LiveSnapshot snapshot(UUID playerUuid) {
        return playerUuid != null ? SNAPSHOTS.get(playerUuid) : null;
    }

    private static LiveSnapshot merge(LiveSnapshot existing, Vector3d position, Vector3f rotation) {
        Vector3d mergedPosition = position != null
                ? position
                : existing != null && existing.position() != null ? new Vector3d(existing.position()) : null;
        Vector3f mergedRotation = rotation != null
                ? rotation
                : existing != null && existing.rotation() != null ? new Vector3f(existing.rotation()) : null;
        return new LiveSnapshot(mergedPosition, mergedRotation);
    }

    private static Vector3d toVector(Position position) {
        return position != null ? new Vector3d(position.x, position.y, position.z) : null;
    }

    private static Vector3f toRotation(Direction direction) {
        if (direction == null) {
            return null;
        }

        Vector3f rotation = new Vector3f();
        rotation.setYaw(direction.yaw);
        rotation.setPitch(direction.pitch);
        rotation.setRoll(direction.roll);
        return rotation;
    }

    private record LiveSnapshot(Vector3d position, Vector3f rotation) {}
}