package com.oheers.fish.autofish;

import com.oheers.fish.fishing.pool.FishingPoolService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-side, GrimAC-safe bridge to execute native Fishing Rod casts and reels
 * without injecting fake client packets into Netty.
 * Includes AI Smart Water Aim Assist to gently guide the server-side bobber
 * into nearby water within the player's natural front cone.
 */
public final class NativeFishingRightClickBridge {

    private static final ClassValue<NmsBridge> NMS_BRIDGE = new ClassValue<>() {
        @Override
        protected NmsBridge computeValue(Class<?> craftPlayerClass) {
            return NmsBridge.resolve(craftPlayerClass);
        }
    };

    private NativeFishingRightClickBridge() {
    }

    /**
     * Performs a human-like server-side cast of the fishing rod in {@code hand}.
     *
     * @return the newly spawned {@link FishHook}, or {@code null} if casting was cancelled or failed.
     */
    @Nullable
    public static FishHook castRod(@NotNull Player player, @NotNull EquipmentSlot hand) {
        NmsBridge bridge = NMS_BRIDGE.get(player.getClass());
        if (bridge.isAvailable()) {
            try {
                if (bridge.invokeUseItem(player, hand)) {
                    swingHand(player, hand);
                    FishHook spawned = findActiveHook(player);
                    if (spawned != null) {
                        applySmartWaterAimAndJitter(player, spawned);
                        return spawned;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }

        // Pure Bukkit fallback
        try {
            FishHook hook = player.launchProjectile(FishHook.class);
            applySmartWaterAimAndJitter(player, hook);
            PlayerFishEvent event = new PlayerFishEvent(player, null, hook, hand, PlayerFishEvent.State.FISHING);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                hook.remove();
                return null;
            }
            swingHand(player, hand);
            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.ENTITY_FISHING_BOBBER_THROW,
                    SoundCategory.NEUTRAL,
                    0.5f,
                    0.4f / (ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f)
            );
            return hook;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * Performs a server-side reel-in of an active {@link FishHook} when a fish is biting.
     */
    public static boolean reelBitingHook(
            @NotNull Player player,
            @NotNull EquipmentSlot hand,
            @NotNull FishHook hook
    ) {
        if (!hook.isValid()) {
            return false;
        }
        NmsBridge bridge = NMS_BRIDGE.get(player.getClass());
        if (bridge.isAvailable()) {
            try {
                if (bridge.invokeUseItem(player, hand)) {
                    swingHand(player, hand);
                    return true;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }

        // Pure Bukkit fallback for reeling in a catch
        try {
            Location hookLoc = hook.getLocation();
            Item caughtEntity = hook.getWorld().dropItem(hookLoc, new ItemStack(Material.COD));
            PlayerFishEvent event = new PlayerFishEvent(player, caughtEntity, hook, hand, PlayerFishEvent.State.CAUGHT_FISH);
            event.setExpToDrop(ThreadLocalRandom.current().nextInt(1, 7));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                caughtEntity.remove();
                return false;
            }
            if (caughtEntity.isValid()) {
                Location playerLoc = player.getLocation();
                double dx = playerLoc.getX() - hookLoc.getX();
                double dy = playerLoc.getY() - hookLoc.getY();
                double dz = playerLoc.getZ() - hookLoc.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                caughtEntity.setVelocity(new Vector(dx * 0.1, dy * 0.1 + Math.sqrt(dist) * 0.08, dz * 0.1));
            }
            hook.remove();
            swingHand(player, hand);
            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.ENTITY_FISHING_BOBBER_RETRIEVE,
                    SoundCategory.NEUTRAL,
                    1.0f,
                    0.4f / (ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f)
            );
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Simulates a human missed bite or empty reel-in.
     */
    public static void simulateMissedBiteOrReel(
            @NotNull Player player,
            @NotNull EquipmentSlot hand,
            @NotNull FishHook hook,
            boolean keepHookInWater
    ) {
        if (!hook.isValid()) {
            return;
        }
        try {
            swingHand(player, hand);
            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.ENTITY_FISHING_BOBBER_RETRIEVE,
                    SoundCategory.NEUTRAL,
                    0.75f,
                    0.85f
            );
            PlayerFishEvent failed = new PlayerFishEvent(player, null, hook, hand, PlayerFishEvent.State.FAILED_ATTEMPT);
            Bukkit.getPluginManager().callEvent(failed);
            if (!keepHookInWater && hook.isValid()) {
                PlayerFishEvent reelIn = new PlayerFishEvent(player, null, hook, hand, PlayerFishEvent.State.REEL_IN);
                Bukkit.getPluginManager().callEvent(reelIn);
                hook.remove();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Nullable
    public static FishHook findActiveHook(@NotNull Player player) {
        try {
            for (FishHook hook : player.getWorld().getEntitiesByClass(FishHook.class)) {
                if (hook.isValid() && player.equals(hook.getShooter())) {
                    return hook;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return null;
    }

    public static void swingHand(@NotNull Player player, @NotNull EquipmentSlot hand) {
        try {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.swingOffHand();
            } else {
                player.swingMainHand();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    /**
     * Applies AI Smart Water Aim Assist (if the player's aim is slightly off the edge of a pond)
     * plus natural Gaussian micro-jitter so every cast lands organically in water.
     */
    private static void applySmartWaterAimAndJitter(@NotNull Player player, @NotNull FishHook hook) {
        try {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            Vector currentVel = hook.getVelocity();

            if (!doesDirectRayHitWater(player)) {
                Location waterTarget = findBestWaterInFrontCone(player);
                if (waterTarget != null) {
                    Location eye = player.getEyeLocation();
                    Vector toWater = waterTarget.toVector().subtract(eye.toVector());
                    double horizontalDist = Math.sqrt(toWater.getX() * toWater.getX() + toWater.getZ() * toWater.getZ());
                    if (horizontalDist > 1.2D && horizontalDist < 11.0D) {
                        double speed = Math.max(0.45D, currentVel.length());
                        Vector desiredDir = toWater.clone().setY(toWater.getY() + horizontalDist * 0.16D).normalize().multiply(speed);
                        Vector delta = desiredDir.subtract(currentVel);
                        if (delta.lengthSquared() > 0.16D * 0.16D) {
                            delta.normalize().multiply(0.16D);
                        }
                        currentVel.add(delta.multiply(0.65D));
                    }
                }
            }

            double jx = rng.nextGaussian() * 0.011D;
            double jy = rng.nextGaussian() * 0.005D;
            double jz = rng.nextGaussian() * 0.011D;
            hook.setVelocity(currentVel.add(new Vector(jx, jy, jz)));
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static boolean doesDirectRayHitWater(@NotNull Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = eye.getWorld();
        if (world == null) return true;
        for (double d = 2.0D; d <= 9.0D; d += 0.85D) {
            Location probe = eye.clone().add(dir.clone().multiply(d));
            if (FishingPoolService.isWater(probe) || FishingPoolService.isWater(probe.clone().subtract(0, 0.65D, 0))) {
                return true;
            }
            Block block = probe.getBlock();
            if (!block.isPassable() && !FishingPoolService.isWater(probe)) {
                return false;
            }
        }
        return false;
    }

    @Nullable
    private static Location findBestWaterInFrontCone(@NotNull Player player) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) return null;

        float baseYaw = eye.getYaw();
        float basePitch = Math.max(-25.0f, Math.min(45.0f, eye.getPitch()));
        Location bestLoc = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        int[] yawOffsets = {0, -10, 10, -20, 20, -28, 28};
        int[] pitchOffsets = {0, 8, -8, 16, 22};

        for (int dyaw : yawOffsets) {
            for (int dpitch : pitchOffsets) {
                Location rayLoc = eye.clone();
                rayLoc.setYaw(baseYaw + dyaw);
                rayLoc.setPitch(Math.max(-35.0f, Math.min(60.0f, basePitch + dpitch)));
                Vector dir = rayLoc.getDirection().normalize();

                for (double d = 2.5D; d <= 8.5D; d += 1.0D) {
                    Location probe = eye.clone().add(dir.clone().multiply(d));
                    Location surfaceCheck = probe.clone().subtract(0, 0.5D, 0);
                    if (FishingPoolService.isWater(surfaceCheck) && probe.getBlock().isPassable()) {
                        double score = 20.0D - Math.abs(dyaw) * 0.35D - Math.abs(dpitch) * 0.25D - Math.abs(d - 4.5D) * 1.1D;
                        // Bonus for open water neighbors
                        if (FishingPoolService.isWater(surfaceCheck.clone().add(1, 0, 0))) score += 2.0D;
                        if (FishingPoolService.isWater(surfaceCheck.clone().add(-1, 0, 0))) score += 2.0D;
                        if (FishingPoolService.isWater(surfaceCheck.clone().add(0, 0, 1))) score += 2.0D;
                        if (FishingPoolService.isWater(surfaceCheck.clone().add(0, 0, -1))) score += 2.0D;
                        if (score > bestScore) {
                            bestScore = score;
                            bestLoc = surfaceCheck.getBlock().getLocation().add(0.5D, 0.85D, 0.5D);
                        }
                        break;
                    }
                    if (!probe.getBlock().isPassable() && !FishingPoolService.isWater(probe)) {
                        break;
                    }
                }
            }
        }
        return bestLoc;
    }

    private record NmsBridge(
            @Nullable Method getHandleMethod,
            @Nullable Field gameModeField,
            @Nullable Method levelMethod,
            @Nullable Field levelField,
            @Nullable Method getItemInHandMethod,
            @Nullable Method useItemMethod,
            @Nullable Object mainHandEnum,
            @Nullable Object offHandEnum
    ) {
        boolean isAvailable() {
            return getHandleMethod != null
                    && gameModeField != null
                    && (levelMethod != null || levelField != null)
                    && getItemInHandMethod != null
                    && useItemMethod != null
                    && mainHandEnum != null
                    && offHandEnum != null;
        }

        boolean invokeUseItem(@NotNull Player player, @NotNull EquipmentSlot slot) throws ReflectiveOperationException {
            Object serverPlayer = getHandleMethod.invoke(player);
            if (serverPlayer == null) return false;
            Object gameMode = gameModeField.get(serverPlayer);
            if (gameMode == null) return false;
            Object level = levelMethod != null ? levelMethod.invoke(serverPlayer) : levelField.get(serverPlayer);
            if (level == null) return false;
            Object nmsHand = (slot == EquipmentSlot.OFF_HAND) ? offHandEnum : mainHandEnum;
            Object nmsItem = getItemInHandMethod.invoke(serverPlayer, nmsHand);
            if (nmsItem == null) return false;
            useItemMethod.invoke(gameMode, serverPlayer, level, nmsItem, nmsHand);
            return true;
        }

        static NmsBridge resolve(Class<?> craftPlayerClass) {
            try {
                Method getHandle = craftPlayerClass.getMethod("getHandle");
                Class<?> serverPlayerClass = getHandle.getReturnType();

                Field gameModeField = null;
                for (Class<?> c = serverPlayerClass; c != null; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        String simple = f.getType().getSimpleName();
                        if (simple.contains("PlayerGameMode") || simple.contains("PlayerInteractManager")) {
                            f.trySetAccessible();
                            gameModeField = f;
                            break;
                        }
                    }
                    if (gameModeField != null) break;
                }
                if (gameModeField == null) {
                    return empty();
                }

                Method useItemMethod = null;
                Class<?> handEnumClass = null;
                Class<?> nmsItemClass = null;
                Class<?> levelClass = null;
                for (Method m : gameModeField.getType().getMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 4
                            && params[0].isAssignableFrom(serverPlayerClass)
                            && params[3].isEnum()) {
                        useItemMethod = m;
                        levelClass = params[1];
                        nmsItemClass = params[2];
                        handEnumClass = params[3];
                        break;
                    }
                }
                if (useItemMethod == null || handEnumClass == null || nmsItemClass == null || levelClass == null) {
                    return empty();
                }

                Object[] handConstants = handEnumClass.getEnumConstants();
                if (handConstants == null || handConstants.length < 2) {
                    return empty();
                }
                Object mainHand = handConstants[0];
                Object offHand = handConstants[1];

                Method getItemInHand = null;
                for (Method m : serverPlayerClass.getMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1 && params[0] == handEnumClass && nmsItemClass.isAssignableFrom(m.getReturnType())) {
                        getItemInHand = m;
                        break;
                    }
                }

                Method levelMethod = null;
                Field levelField = null;
                for (Method m : serverPlayerClass.getMethods()) {
                    if (m.getParameterCount() == 0 && levelClass.isAssignableFrom(m.getReturnType())) {
                        levelMethod = m;
                        break;
                    }
                }
                if (levelMethod == null) {
                    for (Class<?> c = serverPlayerClass; c != null; c = c.getSuperclass()) {
                        for (Field f : c.getDeclaredFields()) {
                            if (levelClass.isAssignableFrom(f.getType())) {
                                f.trySetAccessible();
                                levelField = f;
                                break;
                            }
                        }
                        if (levelField != null) break;
                    }
                }

                return new NmsBridge(
                        getHandle,
                        gameModeField,
                        levelMethod,
                        levelField,
                        getItemInHand,
                        useItemMethod,
                        mainHand,
                        offHand
                );
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return empty();
            }
        }

        private static NmsBridge empty() {
            return new NmsBridge(null, null, null, null, null, null, null, null);
        }
    }
}
