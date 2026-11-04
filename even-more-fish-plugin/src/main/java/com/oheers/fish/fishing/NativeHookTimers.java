package com.oheers.fish.fishing;

import org.bukkit.entity.FishHook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Read-only optional counters. Missing native mappings never become an invented countdown. */
final class NativeHookTimers {
    enum Phase { UNKNOWN, WAITING, APPROACH, BITE }
    record Reading(Phase phase, int remainingTicks) {
        static Reading unknown() { return new Reading(Phase.UNKNOWN, -1); }
    }

    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            Method approach = publicMethod(type, "getTimeUntilBite");
            if (approach != null && approach.getReturnType() != int.class) approach = null;
            Method handle = publicMethod(type, "getHandle");
            Class<?> nativeType = handle == null ? null : handle.getReturnType();
            return new Access(approach, handle, integerField(nativeType, "timeUntilHooked"),
                integerField(nativeType, "nibble"));
        }
    };

    private NativeHookTimers() {}

    static Reading read(FishHook hook, boolean biting) {
        try {
            Access access = ACCESS.get(hook.getClass());
            if (biting) {
                // BITE is fired before nibble is initialized: callers sample on a later server tick.
                int ticks = access.nativeValue(hook, access.nibble());
                return ticks >= 0 ? new Reading(Phase.BITE, ticks) : Reading.unknown();
            }
            int approach = access.approach() == null
                ? access.nativeValue(hook, access.timeUntilHooked())
                : (int) access.approach().invoke(hook);
            if (approach > 0) return new Reading(Phase.APPROACH, approach);
            int wait = hook.getWaitTime();
            return wait > 0 ? new Reading(Phase.WAITING, wait) : Reading.unknown();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return Reading.unknown();
        }
    }

    private record Access(Method approach, Method handle, Field timeUntilHooked, Field nibble) {
        int nativeValue(FishHook hook, Field field) throws ReflectiveOperationException {
            if (handle == null) return -1;
            Object nativeHook = handle.invoke(hook);
            if (nativeHook == null) return -1;
            if (field != null) return field.getInt(nativeHook);
            return -1;
        }
    }

    private static Method publicMethod(Class<?> type, String name) {
        try {
            Method best = null;
            for (Method m : type.getMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 0) continue;
                if (best == null || best.getReturnType().isAssignableFrom(m.getReturnType())) {
                    best = m;
                }
            }
            return best != null ? best : type.getMethod(name);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    private static Field integerField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                return field.getType() == int.class && field.trySetAccessible() ? field : null;
            } catch (NoSuchFieldException absent) {
                // Only exact named fields are accepted; guessing obfuscated integer fields is unsafe.
            } catch (RuntimeException inaccessible) { return null; }
        }
        return null;
    }
}
