package com.oheers.fish.fishing.pool;

import com.oheers.fish.EvenMoreFish;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.Comparator;
import java.util.Locale;

/** Shared operations keep all command variants on the same persistent pool contract. */
public final class PoolCommands {
    public static final String PERMISSION = "emf.admin.pool";
    private PoolCommands() {}

    public static void help(CommandSender sender) {
        send(sender, "/emf admin pool create <id> <radius> [speed-percent] [rare-multiplier]");
        send(sender, "/emf admin pool remove <id> | list | info <id>");
    }

    public static int create(CommandSender sender, String id, double radius, Double speed, Double multiplier) {
        FishingPoolService service = service(sender);
        if (service == null) return 0;
        if (!(sender instanceof Player player)) {
            error(sender, "Hãy tạo hồ trong game tại vị trí bạn muốn."); return 0;
        }
        Location center = player.getLocation();
        RayTraceResult target = player.rayTraceBlocks(32, FluidCollisionMode.ALWAYS);
        if (target != null && target.getHitBlock() != null && target.getHitBlock().getType() == Material.WATER)
            center = target.getHitBlock().getLocation().add(0.5, 1, 0.5);
        try {
            FishingPool pool = service.create(id, center, radius, speed == null ? service.defaultSpeed() : speed,
                multiplier == null ? service.defaultMultiplier() : multiplier);
            if (EvenMoreFish.getInstance().getSimulatorModule() != null) {
                EvenMoreFish.getInstance().getSimulatorModule().refreshHolograms();
            }
            send(sender, "Đã tạo và lưu hồ " + describe(pool));
            if (!service.enabled()) send(sender, "Hồ đã lưu; bật fishing-pools.enabled để áp dụng ưu đãi.");
            return 1;
        } catch (Exception failure) {
            error(sender, "Không thể tạo hồ: " + failure.getMessage()); return 0;
        }
    }

    public static int remove(CommandSender sender, String id) {
        FishingPoolService service = service(sender);
        if (service == null) return 0;
        try {
            if (!service.remove(id)) { error(sender, "Không tìm thấy hồ: " + id); return 0; }
            if (EvenMoreFish.getInstance().getSimulatorModule() != null) {
                EvenMoreFish.getInstance().getSimulatorModule().refreshHolograms();
            }
            send(sender, "Đã xóa và lưu hồ: " + id); return 1;
        } catch (Exception failure) { error(sender, "Không thể lưu hồ: " + failure.getMessage()); return 0; }
    }

    public static int list(CommandSender sender) {
        FishingPoolService service = service(sender);
        if (service == null) return 0;
        send(sender, "Hồ câu: " + service.pools().size() + (service.enabled() ? " (đang bật)" : " (đang tắt)"));
        service.pools().stream().sorted(Comparator.comparing(FishingPool::id)).forEach(pool -> send(sender, describe(pool)));
        return 1;
    }

    public static int info(CommandSender sender, String id) {
        FishingPoolService service = service(sender);
        if (service == null) return 0;
        FishingPool pool = service.get(id);
        if (pool == null) { error(sender, "Không tìm thấy hồ: " + id); return 0; }
        send(sender, describe(pool));
        send(sender, String.format(Locale.ROOT, "Tâm: %s (%.2f, %.2f, %.2f)", pool.world(), pool.x(), pool.y(), pool.z()));
        return 1;
    }

    private static FishingPoolService service(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION)) { error(sender, "Bạn không có quyền quản lý hồ câu."); return null; }
        FishingPoolService service = EvenMoreFish.getInstance().getFishingPools();
        if (service == null) error(sender, "Hệ thống hồ câu chưa sẵn sàng.");
        return service;
    }

    private static String describe(FishingPool pool) {
        return String.format(Locale.ROOT, "%s · bán kính %.1f · giảm chờ %.1f%% · trọng số cá hiếm x%.2f",
            pool.id(), pool.radius(), pool.speedPercent(), pool.rareMultiplier());
    }

    private static void send(CommandSender sender, String message) { sender.sendMessage(Component.text(message, NamedTextColor.AQUA)); }
    private static void error(CommandSender sender, String message) { sender.sendMessage(Component.text(message, NamedTextColor.RED)); }
}
