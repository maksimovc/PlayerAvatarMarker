package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.Predicate;

final class PlayerAvatarVanishReflection {

    private static final ClassLoader CLASS_LOADER = PlayerAvatarVanishReflection.class.getClassLoader();

    private PlayerAvatarVanishReflection() {}

    static Object resolveEnabledPluginInstance(String className,
                                              String identifier,
                                              String getterName) throws ReflectiveOperationException {
        Object loadedPlugin = findEnabledPlugin(className, identifier);
        if (loadedPlugin != null) {
            return loadedPlugin;
        }

        Class<?> pluginClass = loadClass(className, null);
        if (pluginClass == null) {
            return null;
        }

        Object plugin = invokeStaticNoArgs(pluginClass, getterName);
        return isPluginEnabled(plugin) ? plugin : null;
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
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }

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
}

