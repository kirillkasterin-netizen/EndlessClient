package endless.ere.client.screens.morecosmetics;

import java.lang.reflect.Method;

/**
 * Тонкая обёртка над {@code com.cosmeticsmod.morecosmetics.MoreCosmeticsAPI}.
 * Если самого мода MoreCosmetics нет на classpath, класс грузится без ошибок,
 * а все методы превращаются в no-op (возвращают {@code false} / ничего не делают).
 *
 * <p>Это нужно, чтобы наш модуль и панель не валили клиент NoClassDefFoundError'ом
 * у пользователей, у которых MoreCosmetics не установлен.
 */
public final class MoreCosmeticsBridge {

    private static final boolean AVAILABLE;
    private static final Method M_IS_CLOAK;
    private static final Method M_SET_CLOAK;
    private static final Method M_IS_NAMETAG;
    private static final Method M_SET_NAMETAG;
    private static final Method M_OPEN_UI;
    private static final Method M_ON_TICK;

    static {
        Class<?> api = null;
        try {
            api = Class.forName("com.cosmeticsmod.morecosmetics.MoreCosmeticsAPI");
        } catch (Throwable ignored) {
        }
        AVAILABLE = api != null;
        M_IS_CLOAK   = lookup(api, "isCloakEnabled");
        M_SET_CLOAK  = lookup(api, "setCloakEnabled", boolean.class);
        M_IS_NAMETAG = lookup(api, "isNametagEnabled");
        M_SET_NAMETAG = lookup(api, "setNametagEnabled", boolean.class);
        M_OPEN_UI    = lookup(api, "openUI", boolean.class);
        M_ON_TICK    = lookup(api, "onTick");
    }

    private MoreCosmeticsBridge() {}

    private static Method lookup(Class<?> cls, String name, Class<?>... params) {
        if (cls == null) return null;
        try {
            return cls.getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static boolean isCloakEnabled() {
        return invokeBool(M_IS_CLOAK);
    }

    public static void setCloakEnabled(boolean enabled) {
        invokeVoid(M_SET_CLOAK, enabled);
    }

    public static boolean isNametagEnabled() {
        return invokeBool(M_IS_NAMETAG);
    }

    public static void setNametagEnabled(boolean enabled) {
        invokeVoid(M_SET_NAMETAG, enabled);
    }

    public static void openUI(boolean queued) {
        invokeVoid(M_OPEN_UI, queued);
    }

    public static void onTick() {
        invokeVoid(M_ON_TICK);
    }

    private static boolean invokeBool(Method m) {
        if (m == null) return false;
        try {
            Object r = m.invoke(null);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void invokeVoid(Method m, Object... args) {
        if (m == null) return;
        try {
            m.invoke(null, args);
        } catch (Throwable ignored) {
        }
    }
}
