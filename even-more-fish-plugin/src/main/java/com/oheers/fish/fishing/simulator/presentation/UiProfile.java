package com.oheers.fish.fishing.simulator.presentation;

import java.util.Locale;

/** Independent visual preference; never changes fish selection, session or reward state. */
public enum UiProfile {
    OFF(false, false, false, false),
    MINIMAL(true, true, false, false),
    BALANCED(true, true, true, true),
    CINEMATIC(true, true, true, true),
    REDUCED_MOTION(true, true, false, false);

    private final boolean hud, sound, particle, cinematic;
    UiProfile(boolean hud, boolean sound, boolean particle, boolean cinematic) {
        this.hud=hud; this.sound=sound; this.particle=particle; this.cinematic=cinematic;
    }
    public boolean hud() { return hud; }
    public boolean sound() { return sound; }
    public boolean particle() { return particle; }
    public boolean cinematic() { return cinematic; }
    public static UiProfile parse(String value) {
        return UiProfile.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }
}
