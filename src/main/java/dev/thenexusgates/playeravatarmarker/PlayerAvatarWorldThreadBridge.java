package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class PlayerAvatarWorldThreadBridge {

    private static final long WAIT_TIMEOUT_MS = 250L;

    private PlayerAvatarWorldThreadBridge() {}

    static <T> T call(PlayerRef playerRef, Supplier<T> supplier, T fallback, String operation) {
        if (playerRef == null || supplier == null) {
            return fallback;
        }

        World world = resolveWorld(playerRef);
        if (world == null) {
            return fallback;
        }

        if (isWorldThread(world)) {
            return safeCall(supplier, fallback, operation);
        }

        AtomicReference<T> result = new AtomicReference<>(fallback);
        CountDownLatch latch = new CountDownLatch(1);
        world.execute(() -> {
            try {
                result.set(safeCall(supplier, fallback, operation));
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return result.get();
    }

    private static World resolveWorld(PlayerRef playerRef) {
        UUID worldUuid = playerRef.getWorldUuid();
        Universe universe = Universe.get();
        if (worldUuid == null || universe == null) {
            return null;
        }
        return universe.getWorld(worldUuid);
    }

    private static boolean isWorldThread(World world) {
        if (world == null) {
            return false;
        }

        String threadName = Thread.currentThread().getName();
        return threadName != null && threadName.contains("WorldThread - " + world.getName());
    }

    private static <T> T safeCall(Supplier<T> supplier, T fallback, String operation) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return fallback;
        }
    }
}