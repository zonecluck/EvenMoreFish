package com.oheers.fish.effects;

import java.util.Locale;

/**
 * Pure deterministic timeline for the LeDatRNG-style fishing catch roll animation,
 * including the Stage-1 7-slot Legendary Material Wheel and the skyward "Byebye!"
 * wiggle and fade-out sequence.
 */
public final class CatchRollTimeline {

    public enum Phase {
        SLIDE_IN,
        ROLLING,
        REVEAL,
        SHOWCASE,
        HOLD,
        BYEBYE_ASCEND,
        COMPLETE
    }

    public enum TierStyle {
        STANDARD,
        EPIC,
        LEGENDARY,
        MYTHICAL;

        public boolean isEpicOrAbove() {
            return this == EPIC || this == LEGENDARY || this == MYTHICAL;
        }

        public boolean isLegendaryOrAbove() {
            return this == LEGENDARY || this == MYTHICAL;
        }

        public int heartbeatCount() {
            return switch (this) {
                case MYTHICAL -> 4;
                case LEGENDARY -> 3;
                case EPIC -> 2;
                case STANDARD -> 0;
            };
        }

        public static TierStyle forRarity(String rarityId, int sortIndex, double weight) {
            if (rarityId != null) {
                String lower = rarityId.trim().toLowerCase(Locale.ROOT);
                if (lower.contains("mythic") || lower.contains("godlike") || lower.contains("ultra")
                        || lower.contains("anomaly") || lower.contains("celestial") || lower.contains("divine")
                        || lower.contains("than_thoai") || lower.contains("thanthoai")) {
                    return MYTHICAL;
                }
                if (lower.contains("legend") || lower.contains("huyen_thoai") || lower.contains("huyenthoai")) {
                    return LEGENDARY;
                }
                if (lower.contains("epic") || lower.contains("su_thi") || lower.contains("suthi")) {
                    return EPIC;
                }
            }
            if (sortIndex >= 6 || (Double.isFinite(weight) && weight > 0.0 && weight <= 0.5)) {
                return MYTHICAL;
            }
            if (sortIndex >= 5 || (Double.isFinite(weight) && weight > 0.0 && weight <= 1.5)) {
                return LEGENDARY;
            }
            if (sortIndex >= 4 || (Double.isFinite(weight) && weight > 0.0 && weight <= 5.0)) {
                return EPIC;
            }
            return STANDARD;
        }
    }

    public record Durations(
            int slideInTicks,
            int rollingTicks,
            int revealTicks,
            int showcaseTicks,
            int holdTicks,
            int byebyeAscendTicks,
            int rouletteSteps
    ) {
        public Durations {
            slideInTicks = Math.max(2, slideInTicks);
            rollingTicks = Math.max(4, rollingTicks);
            revealTicks = Math.max(2, revealTicks);
            showcaseTicks = Math.max(2, showcaseTicks);
            holdTicks = Math.max(2, holdTicks);
            byebyeAscendTicks = Math.max(4, byebyeAscendTicks);
            rouletteSteps = Math.max(2, rouletteSteps);
        }

        public int totalTicks() {
            return slideInTicks + rollingTicks + revealTicks + showcaseTicks + holdTicks + byebyeAscendTicks;
        }

        /**
         * Tuned for 1-tick (20 TPS) smooth interpolation cadence.
         */
        public static Durations forTier(TierStyle tier) {
            return switch (tier == null ? TierStyle.STANDARD : tier) {
                case MYTHICAL -> new Durations(10, 36, 10, 26, 16, 28, 14);
                case LEGENDARY -> new Durations(10, 32, 10, 24, 14, 26, 12);
                case EPIC -> new Durations(8, 28, 8, 20, 12, 24, 10);
                case STANDARD -> new Durations(8, 22, 8, 16, 10, 22, 8);
            };
        }
    }

    public record Frame(
            Phase phase,
            double progress,
            double offsetX,
            double offsetY,
            double scaleMultiplier,
            double opacity,
            double rollRadians,
            double yawRadians,
            boolean revealResult,
            int rouletteStep
    ) {
    }

    /**
     * Pose of one of the 7 slots in the LeDatRNG-style Stage-1 Legendary Material Wheel.
     */
    public record WheelSlotPose(
            double offsetRight,
            double offsetY,
            float scale,
            int brightness,
            boolean visible
    ) {
    }

    /**
     * Sampled state of the 7-slot Stage-1 Material Wheel at a given tick.
     */
    public record WheelFrame(
            double progress,
            int highlightedIndex,
            boolean converging,
            boolean breakthroughTriggered,
            WheelSlotPose[] slots
    ) {
    }

    public static final int WHEEL_SLOT_COUNT = 7;
    public static final int WHEEL_DURATION_TICKS = 26;
    private static final double WHEEL_RING_RADIUS = 0.62;
    private static final double WHEEL_SPIN_END = 0.72;

    private static final float TEXT_LINE_HEIGHT = 10.0f / 40.0f;
    private static final float MULTILINE_TEXT_GAP = 0.05f;

    private CatchRollTimeline() {
    }

    public static Frame sample(Durations durations, long tick) {
        int cursor = (int) Math.max(0L, tick);

        int slide = durations.slideInTicks();
        if (cursor < slide) {
            double p = progress(cursor, slide);
            double eased = easeOutCubic(p);
            double visibility = smoothstep(p);
            return new Frame(
                    Phase.SLIDE_IN,
                    p,
                    0.0,
                    -0.72 * (1.0 - eased),
                    0.35 + 0.65 * visibility,
                    visibility,
                    0.08 * Math.sin(p * Math.PI * 2.0),
                    (1.0 - eased) * Math.PI,
                    false,
                    0
            );
        }
        cursor -= slide;

        int rolling = durations.rollingTicks();
        if (cursor < rolling) {
            double p = progress(cursor, rolling);
            double easedStep = 1.0 - Math.pow(1.0 - p, 1.50);
            int step = Math.min(durations.rouletteSteps() - 1, (int) Math.floor(easedStep * durations.rouletteSteps()));
            double pulse = 1.0 + 0.085 * Math.sin(p * Math.PI * 5.0);
            double bobY = 0.055 * Math.sin(p * Math.PI * 4.0);
            // Subtle high-frequency horizontal tension vibration in the last 25% of the roll
            double tensionShakeX = p > 0.75 ? Math.sin(p * Math.PI * 24.0) * 0.025 * ((p - 0.75) / 0.25) : 0.0;
            double rockZ = 0.10 * Math.sin(p * Math.PI * 6.0);
            double spinY = easedStep * Math.PI * 4.0;
            return new Frame(
                    Phase.ROLLING,
                    p,
                    tensionShakeX,
                    bobY,
                    pulse,
                    1.0,
                    rockZ,
                    spinY,
                    false,
                    step
            );
        }
        cursor -= rolling;

        int reveal = durations.revealTicks();
        if (cursor < reveal) {
            double p = progress(cursor, reveal);
            double overshoot = 1.0 + 0.24 * (1.0 - p) * Math.sin(p * Math.PI);
            double popY = 0.09 * Math.sin(p * Math.PI);
            return new Frame(
                    Phase.REVEAL,
                    p,
                    0.0,
                    popY,
                    overshoot,
                    1.0,
                    0.0,
                    0.0,
                    true,
                    durations.rouletteSteps()
            );
        }
        cursor -= reveal;

        int showcase = durations.showcaseTicks();
        if (cursor < showcase) {
            double p = progress(cursor, showcase);
            double envelope = Math.sin(p * Math.PI);
            double heartbeat = Math.sin(p * Math.PI * 4.0);
            double scale = 1.0 + envelope * (0.18 + 0.06 * heartbeat);
            double heroElevation = 0.14 * envelope + 0.015 * heartbeat;
            double tiltZ = 0.065 * Math.sin(p * Math.PI * 2.0);
            double yawY = easeOutCubic(p) * Math.PI * 2.0;
            return new Frame(
                    Phase.SHOWCASE,
                    p,
                    0.0,
                    heroElevation,
                    scale,
                    1.0,
                    tiltZ,
                    yawY,
                    true,
                    durations.rouletteSteps()
            );
        }
        cursor -= showcase;

        int hold = durations.holdTicks();
        if (cursor < hold) {
            double p = progress(cursor, hold);
            return new Frame(
                    Phase.HOLD,
                    p,
                    0.0,
                    0.0,
                    1.0,
                    1.0,
                    0.0,
                    0.0,
                    true,
                    durations.rouletteSteps()
            );
        }
        cursor -= hold;

        int byebye = durations.byebyeAscendTicks();
        if (cursor < byebye) {
            double p = progress(cursor, byebye);
            // Smooth skyward acceleration
            double ascendEase = p * (0.32 + 0.68 * p);
            double offsetY = ascendEase * 2.55;

            // Playful "Byebye!" side-to-side wave & gentle horizontal S-curve sway
            double waveEnvelope = 1.0 - 0.28 * p;
            double rollZ = Math.sin(p * Math.PI * 7.0) * 0.45 * waveEnvelope;
            double swayX = Math.sin(p * Math.PI * 3.5) * 0.18 * waveEnvelope;
            double yawY = Math.sin(p * Math.PI * 3.5) * 0.30 * waveEnvelope;

            // Smooth fade-out and scale taper as it reaches the sky
            double fadeEase = easeInCubic(p);
            double opacity = Math.max(0.0, 1.0 - fadeEase);
            double scale = Math.max(0.20, 1.0 - 0.80 * (p * p));

            return new Frame(
                    Phase.BYEBYE_ASCEND,
                    p,
                    swayX,
                    offsetY,
                    scale,
                    opacity,
                    rollZ,
                    yawY,
                    true,
                    durations.rouletteSteps()
            );
        }

        return new Frame(
                Phase.COMPLETE,
                1.0,
                0.0,
                2.55,
                0.20,
                0.0,
                0.0,
                0.0,
                true,
                durations.rouletteSteps()
        );
    }

    /**
     * Samples the LeDatRNG-style Stage-1 7-slot Material Wheel for Legendary/Mythical catches.
     */
    public static WheelFrame sampleWheel(TierStyle tierStyle, int tick, int totalWheelTicks, float baseScale) {
        int duration = Math.max(8, totalWheelTicks);
        double p = progress(tick, duration);
        boolean isMythical = tierStyle == TierStyle.MYTHICAL;
        int winningIndex = isMythical ? 6 : 5; // 5 = Netherite Ingot (Legendary), 6 = Elytra (Mythical)

        WheelSlotPose[] slots = new WheelSlotPose[WHEEL_SLOT_COUNT];
        if (p < WHEEL_SPIN_END) {
            double spinProgress = p / WHEEL_SPIN_END;
            double eased = easeOutCubic(spinProgress);
            int targetStep = isMythical && spinProgress < 0.84 ? 5 : winningIndex;
            boolean breakthrough = isMythical && spinProgress >= 0.84;
            int currentHighlight = Math.min(targetStep, (int) Math.floor(eased * (targetStep + 1)));
            double rotationOffset = eased * Math.PI * 2.0;

            for (int i = 0; i < WHEEL_SLOT_COUNT; i++) {
                double baseAngle = (Math.PI / 2.0) - (i * 2.0 * Math.PI / WHEEL_SLOT_COUNT);
                double angle = baseAngle + rotationOffset;
                double offsetRight = Math.cos(angle) * WHEEL_RING_RADIUS;
                double offsetY = Math.sin(angle) * WHEEL_RING_RADIUS;
                boolean isHighlighted = (i == currentHighlight);
                float scale = isHighlighted ? baseScale * 1.58f : baseScale * 0.72f;
                int brightness = isHighlighted ? 15 : 9;
                slots[i] = new WheelSlotPose(offsetRight, offsetY, scale, brightness, true);
            }
            return new WheelFrame(p, currentHighlight, false, breakthrough, slots);
        }

        double lockProgress = Math.min(1.0, (p - WHEEL_SPIN_END) / (1.0 - WHEEL_SPIN_END));
        double easedLock = easeOutCubic(lockProgress);
        for (int i = 0; i < WHEEL_SLOT_COUNT; i++) {
            double angle = (Math.PI / 2.0) - (i * 2.0 * Math.PI / WHEEL_SLOT_COUNT);
            double startRight = Math.cos(angle) * WHEEL_RING_RADIUS;
            double startY = Math.sin(angle) * WHEEL_RING_RADIUS;
            if (i == winningIndex) {
                double right = startRight * (1.0 - easedLock);
                double y = startY * (1.0 - easedLock);
                float winScale = (float) (baseScale * (1.42 + 0.24 * Math.sin(lockProgress * Math.PI)));
                slots[i] = new WheelSlotPose(right, y, winScale, 15, true);
            } else {
                float fadeScale = (float) Math.max(0.02, baseScale * 0.72 * (1.0 - easedLock));
                int brightness = (int) Math.round(9 * (1.0 - easedLock));
                slots[i] = new WheelSlotPose(startRight, startY, fadeScale, brightness, lockProgress < 0.55);
            }
        }
        return new WheelFrame(p, winningIndex, true, false, slots);
    }

    /**
     * Checks whether the current tick inside ROLLING should fire a LeDatRNG Warden Heartbeat.
     * Accelerates using 1.0 - Math.pow(1.0 - factor, 1.65) in the final 16 ticks of ROLLING.
     */
    public static boolean isHeartbeatTick(Durations durations, TierStyle tierStyle, long elapsedTick) {
        int beats = tierStyle == null ? 0 : tierStyle.heartbeatCount();
        if (beats <= 0) {
            return false;
        }
        int rollingCursor = (int) elapsedTick - durations.slideInTicks();
        int rollingTicks = durations.rollingTicks();
        if (rollingCursor < 0 || rollingCursor >= rollingTicks) {
            return false;
        }
        int windowTicks = Math.min(16, rollingTicks);
        int windowStart = Math.max(0, rollingTicks - windowTicks);
        if (rollingCursor < windowStart) {
            return false;
        }
        if (beats == 1) {
            return rollingCursor == windowStart;
        }
        for (int i = 0; i < beats; i++) {
            double factor = i / (double) (beats - 1);
            double accelerated = 1.0 - Math.pow(1.0 - factor, 1.65);
            int targetTick = windowStart + (int) Math.round(accelerated * Math.max(0, windowTicks - 3));
            if (rollingCursor == targetTick) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the current tick is inside the 2-tick LeDatRNG Reveal Silence Window
     * right before REVEAL for Epic+ catches.
     */
    public static boolean isInRevealSilenceWindow(Durations durations, TierStyle tierStyle, long elapsedTick) {
        if (tierStyle == null || !tierStyle.isEpicOrAbove()) {
            return false;
        }
        int revealStart = durations.slideInTicks() + durations.rollingTicks();
        return elapsedTick >= Math.max(0, revealStart - 2) && elapsedTick < revealStart;
    }

    /**
     * Compensates for TextDisplay growing upward when newline characters are present,
     * keeping the multi-line text block anchored cleanly above the ItemDisplay.
     */
    public static float textVerticalTranslation(int extraLines, float textScale) {
        if (extraLines <= 0) {
            return 0.0f;
        }
        return -extraLines * TEXT_LINE_HEIGHT * textScale - MULTILINE_TEXT_GAP;
    }

    public static byte toTextOpacityByte(double normalizedOpacity) {
        double clamped = Math.max(0.0, Math.min(1.0, normalizedOpacity));
        if (clamped >= 0.92) {
            return (byte) -1;
        }
        int alpha = Math.max(26, (int) Math.round(clamped * 254.0));
        return (byte) alpha;
    }

    public static int toDisplayBrightness(double normalizedOpacity) {
        double clamped = Math.max(0.0, Math.min(1.0, normalizedOpacity));
        return (int) Math.round(clamped * 15.0);
    }

    private static double progress(int tick, int duration) {
        if (duration <= 1) {
            return 1.0;
        }
        if (tick <= 0) {
            return 0.0;
        }
        return Math.min(1.0, tick / (double) (duration - 1));
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double easeOutCubic(double t) {
        double inv = 1.0 - t;
        return 1.0 - inv * inv * inv;
    }

    private static double easeInCubic(double t) {
        return t * t * t;
    }
}
