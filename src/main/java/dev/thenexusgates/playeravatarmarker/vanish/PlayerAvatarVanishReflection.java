package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class PlayerAvatarVanishReflection {

    private static final ClassLoader CLASS_LOADER = PlayerAvatarVanishReflection.class.getClassLoader();
    private static final long CLASS_CACHE_TTL_MS = 15_000L;
    private static final long PLUGIN_INSTANCE_CACHE_TTL_MS = 5_000L;
    private static final Object MISSING = new Object();
    private static final ConcurrentHashMap<String, CacheEntry> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PluginLookupKey, CacheEntry> PLUGIN_INSTANCE_CACHE = new ConcurrentHashMap<>();

    private PlayerAvatarVanishReflection() {}

    static void clearCaches() {
        CLASS_CACHE.clear();
        PLUGIN_INSTANCE_CACHE.clear();
    }

    static Object resolveEnabledPluginInstance(String className,
                                              String identifier,
                                              String getterName) throws ReflectiveOperationException {
        long now = System.currentTimeMillis();
        PluginLookupKey lookupKey = new PluginLookupKey(className, normalizeIdentifier(identifier), getterName);
        Object cachedPlugin = getCachedValue(PLUGIN_INSTANCE_CACHE.get(lookupKey), now);
        if (cachedPlugin != null || isCachedMiss(PLUGIN_INSTANCE_CACHE.get(lookupKey), now)) {
            return cachedPlugin;
        }

        Object loadedPlugin = findEnabledPlugin(className, identifier);
        if (loadedPlugin != null) {
            PLUGIN_INSTANCE_CACHE.put(lookupKey, new CacheEntry(loadedPlugin, now + PLUGIN_INSTANCE_CACHE_TTL_MS));
            return loadedPlugin;
        }

        Class<?> pluginClass = loadClass(className, null);
        if (pluginClass == null) {
            PLUGIN_INSTANCE_CACHE.put(lookupKey, new CacheEntry(MISSING, now + PLUGIN_INSTANCE_CACHE_TTL_MS));
            return null;
        }

        Object plugin = invokeStaticNoArgs(pluginClass, getterName);
        Object resolved = isPluginEnabled(plugin) ? plugin : null;
        PLUGIN_INSTANCE_CACHE.put(
                lookupKey,
                new CacheEntry(resolved != null ? resolved : MISSING, now + PLUGIN_INSTANCE_CACHE_TTL_MS));
        return resolved;
    }

    static Object findEnabledPlugin(String className, String identifier) {
        return findEnabledPlugin(type -> matchesPlugin(type, className), identifier);
    }

    static Object findEnabledPlugin(Predicate<Class<?>> classMatcher, String identifier) {
        PluginManager pluginManager = PluginManager.get();
        if (pluginManager == null) {
            return null;
        }

        String normalizedIdentifier = normalizeIdentifier(identifier);
        for (PluginBase plugin : pluginManager.getPlugins()) {
            if (plugin == null || !plugin.isEnabled()) {
                continue;
            }

            boolean identifierMatches = normalizedIdentifier == null
                    || normalizedIdentifier.equals(normalizeIdentifier(plugin.getIdentifier().toString()));
            boolean classMatches = classMatcher == null || classMatcher.test(plugin.getClass());
            if (identifierMatches && classMatches) {
                return plugin;
            }
        }

        return null;
    }

    static Object readFieldValue(Class<?> type, Object target, Predicate<Field> matcher) throws ReflectiveOperationException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (matcher != null && !matcher.test(field)) {
                    continue;
                }

                field.setAccessible(true);
                return field.get(target);
            }
        }

        return null;
    }

    static Class<?> loadClass(String className, ClassLoader preferredLoader) {
        if (className == null || className.isBlank()) {
            return null;
        }

        long now = System.currentTimeMillis();
        CacheEntry cached = CLASS_CACHE.get(className);
        Object cachedValue = getCachedValue(cached, now);
        if (cachedValue instanceof Class<?> cachedClass) {
            return cachedClass;
        }
        if (isCachedMiss(cached, now)) {
            return null;
        }

        ClassLoader[] loaders = new ClassLoader[] {
                preferredLoader,
                Thread.currentThread().getContextClassLoader(),
                CLASS_LOADER
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }

            try {
                Class<?> resolvedClass = Class.forName(className, false, loader);
                CLASS_CACHE.put(className, new CacheEntry(resolvedClass, now + CLASS_CACHE_TTL_MS));
                return resolvedClass;
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }

        CLASS_CACHE.put(className, new CacheEntry(MISSING, now + CLASS_CACHE_TTL_MS));
        return null;
    }

    static Object invokeStaticNoArgs(Class<?> type, String methodName) throws ReflectiveOperationException {
        return invokeNoArgs(type, null, methodName);
    }

    static Object invokeNoArgs(Class<?> type, Object target, String methodName) throws ReflectiveOperationException {
        Method method = type.getMethod(methodName);
        return method.invoke(target);
    }

    static boolean invokeBoolean(Class<?> type,
                                 Object target,
                                 String methodName,
                                 Class<?> parameterType,
                                 Object argument) throws ReflectiveOperationException {
        Method method = type.getMethod(methodName, parameterType);
        return Boolean.TRUE.equals(method.invoke(target, argument));
    }

    static boolean invokeBoolean(Class<?> type, Object target, String methodName) throws ReflectiveOperationException {
        Method method = type.getMethod(methodName);
        return Boolean.TRUE.equals(method.invoke(target));
    }

    private static boolean isPluginEnabled(Object plugin) throws ReflectiveOperationException {
        return plugin != null && invokeBoolean(plugin.getClass(), plugin, "isEnabled");
    }

    private static boolean matchesPlugin(Class<?> type, String className) {
        return type != null && className != null && className.equals(type.getName());
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        return identifier.toLowerCase(Locale.ROOT).trim();
    }

    private static Object getCachedValue(CacheEntry entry, long now) {
        if (entry == null || entry.expiresAtMs() < now || entry.value() == MISSING) {
            return null;
        }
        return entry.value();
    }

    private static boolean isCachedMiss(CacheEntry entry, long now) {
        return entry != null && entry.expiresAtMs() >= now && entry.value() == MISSING;
    }

    private record PluginLookupKey(String className, String identifier, String getterName) {
    }

    private record CacheEntry(Object value, long expiresAtMs) {
    }
}

