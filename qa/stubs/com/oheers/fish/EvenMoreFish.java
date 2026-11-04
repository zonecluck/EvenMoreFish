package com.oheers.fish;
/** Stub used exclusively by qa/run-offline-smoke.sh; never ship to the plugin jar. */
public class EvenMoreFish {
    private static final EvenMoreFish INSTANCE = new EvenMoreFish();
    public static EvenMoreFish getInstance() { return INSTANCE; }
    public void debug(String message) {}
}
