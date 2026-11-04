package com.oheers.fish.fishing.simulator.model;

import com.oheers.fish.fishing.simulator.population.FishVisualModel;
import java.util.Objects;
import java.util.regex.Pattern;

/** One immutable viewer-specific visual decision. Identity and catch/reward stay in FishAgent. */
public record FishRenderSpec(FishVisualModel vanilla, String material, String itemModel,
                             float scale, int interpolationTicks) {
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public FishRenderSpec {
        Objects.requireNonNull(vanilla, "vanilla");
        if (material == null) material = "";
        if (itemModel == null) itemModel = "";
        if (itemModel.isEmpty() != material.isEmpty())
            throw new IllegalArgumentException("material and itemModel must be supplied together");
        if (!itemModel.isEmpty() && (!KEY.matcher(itemModel).matches() ||
            !material.matches("[A-Z_]+") || material.length() > 64 ||
            itemModel.contains("..") || itemModel.endsWith("/") || itemModel.contains("//")))
            throw new IllegalArgumentException("invalid custom render descriptor");
        if (!Float.isFinite(scale) || scale <= 0 || scale > 3 || interpolationTicks < 0 || interpolationTicks > 8)
            throw new IllegalArgumentException("invalid display scale/interpolation");
    }
    public static FishRenderSpec vanilla(FishVisualModel model) {
        return new FishRenderSpec(model, "", "", 1, 0);
    }
    public boolean isCustom() { return !itemModel.isEmpty(); }
}
