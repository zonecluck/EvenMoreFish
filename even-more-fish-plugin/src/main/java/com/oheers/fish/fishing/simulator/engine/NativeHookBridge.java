package com.oheers.fish.fishing.simulator.engine;

import org.bukkit.entity.FishHook;

/** Runtime-specific native bite suppression. No packet/backend assumptions here. */
public interface NativeHookBridge {
    boolean supported();

    /** On the Paper owner thread, arm/refresh a hook; returns false if verification failed. */
    boolean suppressBite(FishHook hook, int waitTicks);

    static NativeHookBridge unsupported() {
        return new NativeHookBridge() {
            @Override public boolean supported() { return false; }
            @Override public boolean suppressBite(FishHook hook, int waitTicks) { return false; }
        };
    }
}
