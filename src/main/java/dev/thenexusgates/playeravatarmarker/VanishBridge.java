package dev.thenexusgates.playeravatarmarker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflective bridge to three server-side vanish mods:
 *   1. EliteEssentials  (com.eliteessentials)
 *   2. EssentialsPlus   (de.fof1092.essentialsplus)
 *   3. HyEssentialsX    (xyz.thelegacyvoyage.hyessentialsx)
 *
 * Returns true from {@link #isVanished(UUID)} when any of the installed mods
 * reports the player as vanished. Fails silently if none is present.
 */
final class VanishBridge {

    /** Sentinel that means "we already tried to load and nothing is available". */
    private static final ModBridge[] NONE = new ModBridge[0];

    private static volatile ModBridge[] bridges;

    private VanishBridge() {}

    /**
     * Returns {@code true} if the given player UUID is currently vanished
     * according to any installed vanish mod.
     */
    static boolean isVanished(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        for (ModBridge bridge : resolveBridges()) {
            try {
                if (bridge.isVanished(uuid)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Lazy initialisation
    // -------------------------------------------------------------------------

    private static ModBridge[] resolveBridges() {
        ModBridge[] current = bridges;
        if (current != null) {
            return current;
        }
        synchronized (VanishBridge.class) {
            current = bridges;
            if (current != null) {
                return current;
            }
            bridges = current = loadBridges();
            return current;
        }
    }

    private static ModBridge[] loadBridges() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        ModBridge ee  = loadEliteEssentialsBridge(cl);
        ModBridge ep  = loadEssentialsPlusBridge(cl);
        ModBridge hye = loadHyEssentialsXBridge(cl);

        int count = (ee != null ? 1 : 0) + (ep != null ? 1 : 0) + (hye != null ? 1 : 0);
        if (count == 0) {
            return NONE;
        }
        ModBridge[] result = new ModBridge[count];
        int i = 0;
        if (ee  != null) result[i++] = ee;
        if (ep  != null) result[i++] = ep;
        if (hye != null) result[i]   = hye;
        return result;
    }

    // -------------------------------------------------------------------------
    // EliteEssentials
    //   EliteEssentials.getInstance()           -> plugin instance
    //   plugin.getVanishService()               -> VanishService
    //   vanishService.isVanished(UUID)          -> boolean
    // -------------------------------------------------------------------------

    private static ModBridge loadEliteEssentialsBridge(ClassLoader cl) {
        try {
            Class<?> pluginClass       = loadClass("com.eliteessentials.EliteEssentials", cl);
            Class<?> vanishServiceClass = loadClass("com.eliteessentials.services.VanishService", cl);

            Method getInstance       = pluginClass.getMethod("getInstance");
            Method getVanishService  = pluginClass.getMethod("getVanishService");
            Method isVanished        = vanishServiceClass.getMethod("isVanished", UUID.class);

            return uuid -> {
                Object plugin = getInstance.invoke(null);
                if (plugin == null) return false;
                Object service = getVanishService.invoke(plugin);
                if (service == null) return false;
                return (boolean) isVanished.invoke(service, uuid);
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // EssentialsPlus
    //   VanishManager.getInstance()             -> VanishManager singleton
    //   manager.isVanished(UUID)                -> boolean
    // -------------------------------------------------------------------------

    private static ModBridge loadEssentialsPlusBridge(ClassLoader cl) {
        try {
            Class<?> managerClass = loadClass(
                    "de.fof1092.essentialsplus.features.player.status.vanish.VanishManager", cl);

            Method getInstance = managerClass.getMethod("getInstance");
            Method isVanished  = managerClass.getMethod("isVanished", UUID.class);

            return uuid -> {
                Object manager = getInstance.invoke(null);
                if (manager == null) return false;
                return (boolean) isVanished.invoke(manager, uuid);
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // HyEssentialsX
    //   HyEssentialsXPlugin.getInstance()       -> plugin instance
    //   plugin.<field vanishManager>            -> VanishManager (via reflection)
    //   manager.isEnabled(UUID)                 -> boolean
    // -------------------------------------------------------------------------

    private static ModBridge loadHyEssentialsXBridge(ClassLoader cl) {
        try {
            Class<?> pluginClass  = loadClass(
                    "xyz.thelegacyvoyage.hyessentialsx.HyEssentialsXPlugin", cl);
            Class<?> managerClass = loadClass(
                    "xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager", cl);

            Method getInstance = pluginClass.getMethod("getInstance");
            Method isEnabled   = managerClass.getMethod("isEnabled", UUID.class);

            Field vanishManagerField = findField(pluginClass, "vanishManager");
            if (vanishManagerField == null) {
                return null;
            }
            vanishManagerField.setAccessible(true);

            return uuid -> {
                Object plugin = getInstance.invoke(null);
                if (plugin == null) return false;
                Object manager = vanishManagerField.get(plugin);
                if (manager == null) return false;
                return (boolean) isEnabled.invoke(manager, uuid);
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Class<?> loadClass(String name, ClassLoader contextLoader) throws ClassNotFoundException {
        if (contextLoader != null) {
            try {
                return Class.forName(name, false, contextLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        try {
            return Class.forName(name, false, VanishBridge.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
        }
        return Class.forName(name);
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Internal functional interface
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface ModBridge {
        boolean isVanished(UUID uuid) throws Exception;
    }
}
