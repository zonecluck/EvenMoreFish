package com.oheers.fish;

import com.oheers.fish.fishing.simulator.engine.NativeHookBridge;
import org.bukkit.entity.FishHook;

/** Paper 1.21 adapter; deliberately NOT compiled into the 1.20 shared core. */
public final class Paper121NativeHookBridge implements NativeHookBridge {
    @Override
    public boolean supported() { return true; }

    @Override
    public boolean suppressBite(FishHook hook, int waitTicks) {
        // Never use setBiteChance(0): it is a documented no-op on modern Minecraft.
        if (!hook.isValid()) return false;
        try {
            hook.setApplyLure(false);
            hook.setWaitTime(waitTicks, waitTicks);
            hook.setWaitTime(waitTicks);
            return hook.getWaitTime() > 500;
        } catch (IllegalArgumentException | IllegalStateException | NoSuchMethodError | UnsupportedOperationException error) {
            return false;
        }
    }
}
