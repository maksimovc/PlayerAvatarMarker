package dev.thenexusgates.playeravatarmarker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class PlayerAvatarHyEssentialsXVanishProvider implements PlayerAvatarVanishProvider {

    private static final String PLUGIN_CLASS_NAME = "xyz.thelegacyvoyage.hyessentialsx.HyEssentialsXPlugin";
    private static final String PLUGIN_IDENTIFIER = "xyz.thelegacyvoyage:HyEssentialsX";
    private static final String PLUGIN_GETTER = "getInstance";
    private static final String VANISH_MANAGER_FIELD = "vanishManager";
    private static final String VANISH_MANAGER_CLASS_NAME = "xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager";
    private static final String IS_ENABLED_METHOD = "isEnabled";
    private static final String GET_VANISHED_PLAYERS_METHOD = "getVanishedPlayers";
    private static final String GET_VANISH_MANAGER_METHOD = "getVanishManager";
    private static final String VANISHED_FIELD = "vanished";

    // Cached references to avoid repeated reflection overhead
    private final AtomicReference<Object> cachedVanishManager = new AtomicReference<>();
    private volatile Method cachedIsEnabledMethod;

    @Override
    public String name() {
        return "HyEssentialsX";
    }

    @Override
    public boolean isAvailable() {
        try {
            return resolveVanishManager() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isVanished(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        try {
            Object manager = resolveVanishManager();
            if (manager == null) {
                return false;
            }
            return checkIsVanished(manager, playerUuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    boolean checkIsVanished(Object manager, UUID playerUuid) {
        // Try isEnabled(UUID) first (primary method in VanishManager)
        try {
            Method method = resolveIsEnabledMethod(manager);
            if (method != null) {
                Object result = method.invoke(manager, playerUuid);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        // Fallback: try getVanishedPlayers() and check containsKey/contains
        try {
            Method getPlayers = manager.getClass().getMethod(GET_VANISHED_PLAYERS_METHOD);
            Object players = getPlayers.invoke(manager);
            if (players instanceof java.util.Collection<?> col) {
                return col.contains(playerUuid);
            }
            if (players instanceof java.util.Map<?, ?> map) {
                return map.containsKey(playerUuid);
            }
        } catch (Throwable ignored) {
        }

        try {
            Object vanished = readNamedField(manager, VANISHED_FIELD);
            if (vanished instanceof java.util.Map<?, ?> map) {
                return map.containsKey(playerUuid);
            }
            if (vanished instanceof java.util.Collection<?> col) {
                return col.contains(playerUuid);
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private Object resolveVanishManager() {
        Object cached = cachedVanishManager.get();
        if (isManagerInstance(cached)) {
            return cached;
        }
        if (cached != null) {
            cachedVanishManager.compareAndSet(cached, null);
        }

        Object manager = resolveManagerFromPluginLookup();
        if (manager != null) {
            cachedVanishManager.compareAndSet(null, manager);
            return manager;
        }

        manager = resolveManagerFromStaticPlugin();
        if (manager != null) {
            cachedVanishManager.compareAndSet(null, manager);
            return manager;
        }
        return null;
    }

    private Object resolveManagerFromPluginLookup() {
        try {
            return resolveManagerFromPlugin(findHyEssentialsPlugin());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object findHyEssentialsPlugin() {
        try {
            return PlayerAvatarVanishReflection.resolveEnabledPluginInstance(
                    PLUGIN_CLASS_NAME,
                    PLUGIN_IDENTIFIER,
                    PLUGIN_GETTER);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Object resolveManagerFromStaticPlugin() {
        try {
            Class<?> pluginClass = PlayerAvatarVanishReflection.loadClass(PLUGIN_CLASS_NAME, null);
            if (pluginClass == null) {
                return null;
            }
            Object plugin = PlayerAvatarVanishReflection.invokeStaticNoArgs(pluginClass, PLUGIN_GETTER);
            return resolveManagerFromPlugin(plugin);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object resolveManagerFromPlugin(Object plugin) {
        if (plugin == null) {
            return null;
        }

        Object manager = readVanishManagerField(plugin);
        if (manager != null) {
            return manager;
        }

        manager = readByMethod(plugin, GET_VANISH_MANAGER_METHOD);
        if (manager != null) {
            return isManagerInstance(manager) ? manager : null;
        }

        return null;
    }

    Object readVanishManagerField(Object plugin) {
        if (plugin == null) {
            return null;
        }
        for (Class<?> type = plugin.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(VANISH_MANAGER_FIELD);
                field.setAccessible(true);
                Object value = field.get(plugin);
                return isManagerInstance(value) ? value : null;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Object readByMethod(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
        }

        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private Object readNamedField(Object target, String fieldName) {
        if (target == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }

        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private boolean isManagerClass(Class<?> type) {
        return type != null && VANISH_MANAGER_CLASS_NAME.equals(type.getName());
    }

    private boolean isManagerInstance(Object candidate) {
        return candidate != null && isManagerClass(candidate.getClass());
    }

    private Method resolveIsEnabledMethod(Object manager) {
        Method cached = cachedIsEnabledMethod;
        if (cached != null) {
            return cached;
        }
        try {
            Method method = manager.getClass().getMethod(IS_ENABLED_METHOD, UUID.class);
            cachedIsEnabledMethod = method;
            return method;
        } catch (Throwable ignored) {
        }
        // Fallback: search declared methods
        for (Method method : manager.getClass().getDeclaredMethods()) {
            if (!IS_ENABLED_METHOD.equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (param.equals(UUID.class) || param.isAssignableFrom(UUID.class)) {
                try {
                    method.setAccessible(true);
                    cachedIsEnabledMethod = method;
                    return method;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }
}


