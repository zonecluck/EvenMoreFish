package com.oheers.fish.autofish;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.gui.guis.FishIndexMenuManager;
import com.oheers.fish.gui.guis.MainMenuGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 54-slot GUI for the human-like AI Auto-Fishing system (/autofish, /emf autofish).
 * Supports toggling AI Auto-Fishing, buying VIP time packages with bonus perks,
 * upgrading accuracy/speed/reaction/durability with VIP discounts, and configuring smart AI rules.
 */
public final class AutoFishMenuManager implements Listener {

    private static final AutoFishMenuManager INSTANCE = new AutoFishMenuManager();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final DecimalFormat DEC_FORMAT = new DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(Locale.US));

    private static final int INVENTORY_SIZE = 54;

    private static final int SLOT_INFO_BOOK = 4;
    private static final int SLOT_TOGGLE = 13;

    // Row 3: Time packages with VIP Perks
    private static final int SLOT_PKG_15M = 19;
    private static final int SLOT_PKG_1H = 21;
    private static final int SLOT_PKG_3H = 23;
    private static final int SLOT_PKG_8H = 25;

    // Row 4: Skill upgrades
    private static final int SLOT_UPG_MISS = 28;
    private static final int SLOT_UPG_DELAY = 30;
    private static final int SLOT_UPG_REACTION = 32;
    private static final int SLOT_UPG_DURABILITY = 34;

    // Row 6: Smart AI Settings & Navigation
    private static final int SLOT_SETTING_AUTO_SELL = 46;
    private static final int SLOT_SETTING_FULL_INV = 47;
    private static final int SLOT_OPEN_INDEX = 48;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_SETTING_RECAST_MISS = 51;
    private static final int SLOT_SETTING_AUTO_ROD = 52;

    public record TimePackage(
            String id,
            int tier,
            int slot,
            Material material,
            String nameMini,
            long baseSeconds,
            long bonusSeconds,
            double cost,
            String discountTag
    ) {
    }

    private static final List<TimePackage> TIME_PACKAGES = List.of(
            new TimePackage("15M", 1, SLOT_PKG_15M, Material.CLOCK, "<aqua>Gói Khởi Đầu (+15 Phút)</aqua>", 15L * 60L, 2L * 60L, 6_250D, "Tặng +2 Phút • VIP Đồng"),
            new TimePackage("1H", 2, SLOT_PKG_1H, Material.EXPERIENCE_BOTTLE, "<green>Gói Tiêu Chuẩn (+1 Giờ)</green>", 60L * 60L, 10L * 60L, 18_750D, "Tiết kiệm 25% • Tặng +10 Phút"),
            new TimePackage("3H", 3, SLOT_PKG_3H, Material.HEART_OF_THE_SEA, "<light_purple>Gói Chuyên Nghiệp (+3 Giờ)</light_purple>", 3L * 3600L, 35L * 60L, 50_000D, "Tiết kiệm 33% • Tặng +35 Phút"),
            new TimePackage("8H", 4, SLOT_PKG_8H, Material.NETHER_STAR, "<gold>Gói Đại Dương (+8 Giờ)</gold>", 8L * 3600L, 2L * 3600L, 112_500D, "Tiết kiệm 44% • Tặng +2 Giờ")
    );

    private AutoFishMenuManager() {
    }

    public static AutoFishMenuManager getInstance() {
        return INSTANCE;
    }

    public void open(@NotNull Player player) {
        render(player, true);
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AutoFishHolder) {
                player.closeInventory();
            }
        }
    }

    private void render(@NotNull Player player, boolean forceOpen) {
        AutoFishingService service = EvenMoreFish.getInstance().getAutoFishing();
        if (service == null) {
            player.sendMessage(mm("<red>Tính năng Auto Câu Cá hiện không khả dụng!</red>"));
            return;
        }

        UUID playerId = player.getUniqueId();
        AutoFishProfile profile = service.getProfile(playerId);
        boolean active = service.isActive(playerId);
        AutoFishingService.ActiveSession activeSession = service.getActiveSession(playerId);
        double balance = service.getBalance(player);

        Component title = mm("<dark_gray>AI Auto Câu Cá • " + (active ? "<green>Đang Bật</green>" : "<red>Đang Tắt</red>") + "</dark_gray>");
        AutoFishHolder holder = new AutoFishHolder(playerId, title);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title);
        holder.bind(inventory);

        // 1. Base filler panes
        ItemStack grayFiller = createPane(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        ItemStack blackBorder = createPane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, (slot < 9 || slot >= 45) ? blackBorder : grayFiller);
        }

        // Accent panes around toggle button
        ItemStack accentPane = createPane(
                active ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                active ? "<green>⚡ Hệ thống AI đang hoạt động</green>" : "<red>○ Hệ thống AI đang nghỉ</red>",
                List.of(),
                false
        );
        inventory.setItem(12, accentPane);
        inventory.setItem(14, accentPane);

        // 2. Slot 4: Info Book
        int sessionCatches = activeSession != null ? activeSession.getSessionCatches() : 0;
        int sessionMisses = activeSession != null ? activeSession.getSessionMisses() : 0;
        int sessionSaved = activeSession != null ? activeSession.getSessionDurabilitySaved() : 0;
        int sessionAutoSells = activeSession != null ? activeSession.getSessionAutoSells() : 0;
        int focusStreak = activeSession != null ? activeSession.getFocusStreak() : 0;
        inventory.setItem(SLOT_INFO_BOOK, createPane(
                Material.BOOK,
                "<aqua>Thông Tin AI Auto Câu Cá</aqua>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Trạng thái: " + (active ? "<green>Đang hoạt động</green>" : "<red>Đang tạm dừng</red>"),
                        "<dark_gray> ○ <white>Hạng Gói Auto: " + profile.getActiveTierDisplayName(),
                        "<dark_gray> ○ <white>Thời gian còn lại: <yellow>" + AutoFishProfile.formatDuration(profile.getRemainingSeconds()) + "</yellow>",
                        "<dark_gray> ○ <white>Tổng cá đã Auto: <green>" + String.format(Locale.US, "%,d", profile.getTotalAutoCatches()) + "</green> con",
                        " ",
                        "<dark_gray> ○ <white>Thống kê AI phiên hiện tại:",
                        "  <dark_gray> -<green> Cá câu trúng:</green> <dark_gray>(<white>" + sessionCatches + " con • Combo x" + focusStreak + "<dark_gray>)",
                        "  <dark_gray> -<red> Lần giật trượt:</red> <dark_gray>(<white>" + sessionMisses + " lần<dark_gray>)",
                        "  <dark_gray> -<gold> Độ bền giữ lại:</gold> <dark_gray>(<white>" + sessionSaved + " điểm<dark_gray>)",
                        "  <dark_gray> -<aqua> Lần AI tự bán cá:</aqua> <dark_gray>(<white>" + sessionAutoSells + " lần<dark_gray>)",
                        " ",
                        "<#A8D8EA>AI tự căn mặt nước, tự đổi cần",
                        "<white>và tự bán cá như người thật!"
                ),
                false
        ));

        // 3. Slot 13: Main Toggle Button
        String statusText = active ? "<green>Đang Bật</green>" : "<red>Đang Tắt</red>";
        inventory.setItem(SLOT_TOGGLE, createPane(
                Material.FISHING_ROD,
                active ? "<green><bold>⚡ AI AUTO CÂU CÁ: ĐANG BẬT ⚡</bold></green>" : "<red><bold>○ AI AUTO CÂU CÁ: ĐANG TẮT ○</bold></red>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Trạng thái: " + statusText,
                        "<dark_gray> ○ <white>Thời gian còn lại: <yellow>" + AutoFishProfile.formatDuration(profile.getRemainingSeconds()) + "</yellow>",
                        "<dark_gray> ○ <white>Đặc quyền đang bật: " + profile.getActiveTierDisplayName(),
                        " ",
                        "<dark_gray> ○ <white>Chỉ số AI & Buff hiện tại:",
                        "  <dark_gray> -<gray> Tỉ lệ câu hụt:</gray> <dark_gray>(<green>" + DEC_FORMAT.format(profile.getMissChancePercent()) + "%<dark_gray>)",
                        "  <dark_gray> -<blue> Delay mỗi đợt:</blue> <dark_gray>(<green>" + DEC_FORMAT.format(profile.getCastDelaySeconds()) + "s<dark_gray>)",
                        "  <dark_gray> -<light_purple> Phản xạ giật cần:</light_purple> <dark_gray>(<green>" + DEC_FORMAT.format(profile.getReactionSeconds()) + "s<dark_gray>)",
                        "  <dark_gray> -<gold> Buff VIP (May mắn/Cắn câu):</gold> <dark_gray>(<green>+" + (int) Math.round(profile.getPackageLuckBonus() * 100) + "% / +" + (int) Math.round(profile.getPackageLureBonus() * 100) + "%<dark_gray>)",
                        " ",
                        active ? "<#C797FF>Nhấp chuột <white>để TẮT" : "<#A8D8EA>Nhấp chuột <white>để BẬT",
                        "<white>chế độ AI câu cá tự động!"
                ),
                active
        ));
        holder.putAction(SLOT_TOGGLE, "TOGGLE");

        // 4. Row 3: Time Packages with VIP Perks & First-Purchase / Loyalty Bonuses
        boolean firstPurchaseAvailable = !profile.isFirstPurchaseClaimed();
        int loyaltyPct = profile.getLoyaltyBonusTimePercent();
        for (TimePackage pkg : TIME_PACKAGES) {
            boolean canAfford = balance >= pkg.cost();
            long totalGrantedSeconds = computeGrantedPackageSeconds(pkg, profile);
            int tier = pkg.tier();
            int luckPct = (int) Math.round(AutoFishProfile.computeTierLuckBonus(tier) * 100.0D);
            int lurePct = (int) Math.round(AutoFishProfile.computeTierLureBonus(tier) * 100.0D);
            int doublePct = (int) Math.round(AutoFishProfile.computeTierDoubleCatchBonus(tier) * 100.0D);
            int sellPct = (int) Math.round(AutoFishProfile.computeTierSellBonusPercent(tier));
            int discountPct = AutoFishProfile.computeTierUpgradeDiscountPercent(tier);

            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add("<dark_gray> ○ <white>Thời lượng gốc: <yellow>" + AutoFishProfile.formatDuration(pkg.baseSeconds()) + "</yellow>");
            lore.add("<dark_gray> ○ <white>Tặng kèm gói: <green>+" + AutoFishProfile.formatDuration(pkg.bonusSeconds()) + "</green>");
            if (firstPurchaseAvailable) {
                lore.add("<dark_gray> ○ <white>Ưu đãi mua lần đầu: <gold><bold>NHÂN ĐÔI (x2) + Tặng 1 Cấp Chuẩn Xác!</bold></gold>");
            } else if (loyaltyPct > 0) {
                lore.add("<dark_gray> ○ <white>Thưởng khách thân thiết: <aqua>+" + loyaltyPct + "% thời gian thêm</aqua>");
            }
            lore.add("<dark_gray> ○ <white>Tổng thời gian nhận: <aqua><bold>" + AutoFishProfile.formatDuration(totalGrantedSeconds) + "</bold></aqua>");
            lore.add("<dark_gray> ○ <white>Giá mua: <gold>" + AutoFishingService.formatMoney(pkg.cost()) + "</gold>");
            lore.add(" ");
            lore.add("<dark_gray> ○ <white>Đặc quyền " + AutoFishProfile.getTierDisplayName(tier) + "<white>:");
            lore.add("  <dark_gray> -<gray> May mắn & Cắn câu:</gray> <dark_gray>(<green>+" + luckPct + "% Luck • +" + lurePct + "% Lure<dark_gray>)");
            if (doublePct > 0) {
                lore.add("  <dark_gray> -<blue> Tỉ lệ nhân đôi cá:</blue> <dark_gray>(<green>+" + doublePct + "% x2 Cá<dark_gray>)");
            }
            lore.add("  <dark_gray> -<light_purple> Thưởng giá bán cá:</light_purple> <dark_gray>(<green>+" + sellPct + "% Tiền bán<dark_gray>)");
            if (discountPct > 0) {
                lore.add("  <dark_gray> -<gold> Giảm giá nâng kỹ năng:</gold> <dark_gray>(<green>-" + discountPct + "% Chi phí<dark_gray>)");
            }
            lore.add(" ");
            lore.add(canAfford ? "<#C797FF>Nhấn chuột <white>để mua ngay" : "<red>Số dư chưa đủ <white>để mua");
            lore.add("<white>gói thời gian & đặc quyền VIP!");

            inventory.setItem(pkg.slot(), createPane(
                    pkg.material(),
                    pkg.nameMini(),
                    lore,
                    firstPurchaseAvailable || pkg.tier() >= 3
            ));
            holder.putAction(pkg.slot(), "BUY_PKG:" + pkg.id());
        }

        // 5. Row 4: Skill Upgrades (with VIP Discount)
        inventory.setItem(SLOT_UPG_MISS, buildMissUpgradeItem(profile, balance));
        holder.putAction(SLOT_UPG_MISS, "UPG_MISS");

        inventory.setItem(SLOT_UPG_DELAY, buildDelayUpgradeItem(profile, balance));
        holder.putAction(SLOT_UPG_DELAY, "UPG_DELAY");

        inventory.setItem(SLOT_UPG_REACTION, buildReactionUpgradeItem(profile, balance));
        holder.putAction(SLOT_UPG_REACTION, "UPG_REACTION");

        inventory.setItem(SLOT_UPG_DURABILITY, buildDurabilityUpgradeItem(profile, balance));
        holder.putAction(SLOT_UPG_DURABILITY, "UPG_DURABILITY");

        // 6. Row 6: Smart AI Settings & Navigation
        inventory.setItem(SLOT_SETTING_AUTO_SELL, createPane(
                Material.EMERALD,
                "<green>AI Tự Bán Cá Khi Đầy Túi</green>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Trạng thái: " + (profile.isAutoSellOnFullInventory() ? "<green>Đang Bật</green>" : "<red>Đang Tắt</red>"),
                        "<dark_gray> ○ <white>Thưởng bán VIP: <gold>+" + (int) profile.getPackageSellBonusPercent() + "% giá bán</gold>",
                        " ",
                        "<dark_gray> ○ <white>Tác dụng AI:",
                        "  <dark_gray> -<gray> Tự động bán toàn bộ cá trong túi khi đầy</gray>",
                        "  <dark_gray> -<green> Câu liên tục xuyên đêm không bị gián đoạn</green>",
                        " ",
                        "<#A8D8EA>Nhấp chuột <white>để chuyển đổi",
                        "<white>chế độ AI tự bán cá!"
                ),
                profile.isAutoSellOnFullInventory()
        ));
        holder.putAction(SLOT_SETTING_AUTO_SELL, "TOGGLE_AUTO_SELL");

        inventory.setItem(SLOT_SETTING_FULL_INV, createPane(
                Material.CHEST,
                "<yellow>Tự Dừng Khi Đầy Túi Đồ</yellow>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Trạng thái: " + (profile.isStopOnFullInventory() ? "<green>Đang Bật</green>" : "<red>Đang Tắt</red>"),
                        " ",
                        "<dark_gray> ○ <white>Tác dụng:",
                        "  <dark_gray> -<gray> Dừng câu nếu túi đầy mà không thể bán cá</gray>",
                        "  <dark_gray> -<green> Bảo vệ cá hiếm không bị rơi ra ngoài</green>",
                        " ",
                        "<#A8D8EA>Nhấp chuột <white>để chuyển đổi",
                        "<white>tùy chọn bảo vệ túi đồ!"
                ),
                profile.isStopOnFullInventory()
        ));
        holder.putAction(SLOT_SETTING_FULL_INV, "TOGGLE_FULL_INV");

        inventory.setItem(SLOT_OPEN_INDEX, createPane(
                Material.KNOWLEDGE_BOOK,
                "<aqua>Bộ Sưu Tập Cá (/index)</aqua>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Tính năng: <green>Sổ Tay Sưu Tầm Cá</green>",
                        " ",
                        "<dark_gray> ○ <white>Hỗ trợ:",
                        "  <dark_gray> -<gray> Cá câu từ Auto vẫn mở khóa</gray> <dark_gray>(<green>/index<dark_gray>)",
                        "  <dark_gray> -<gold> Tính điểm đua Top sự kiện bình thường</gold>",
                        " ",
                        "<#C797FF>Nhấn chuột <white>để mở",
                        "<white>giao diện Bộ Sưu Tập Cá!"
                ),
                false
        ));
        holder.putAction(SLOT_OPEN_INDEX, "OPEN_INDEX");

        inventory.setItem(SLOT_CLOSE, createPane(
                Material.BARRIER,
                "<red>Quay Lại / Đóng</red>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Thao tác:",
                        "  <dark_gray> -<yellow> Chuột trái</yellow> <dark_gray>(<white>Về Menu Chính<dark_gray>)",
                        "  <dark_gray> -<red> Chuột phải</red> <dark_gray>(<white>Đóng cửa sổ<dark_gray>)",
                        " ",
                        "<#C797FF>Nhấn chuột <white>để thoát",
                        "<white>bảng điều khiển Auto Câu Cá!"
                ),
                false
        ));
        holder.putAction(SLOT_CLOSE, "CLOSE");

        inventory.setItem(SLOT_SETTING_RECAST_MISS, createPane(
                Material.TRIPWIRE_HOOK,
                "<aqua>Tự Quăng Lại Khi Hụt Cá</aqua>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Trạng thái: " + (profile.isAutoRecastOnMiss() ? "<green>Đang Bật</green>" : "<red>Đang Tắt</red>"),
                        " ",
                        "<dark_gray> ○ <white>Tác dụng:",
                        "  <dark_gray> -<gray> Bật: Thu cần và quăng đợt mới khi hụt</gray>",
                        "  <dark_gray> -<blue> Tắt: Giữ nguyên phao dưới nước chờ tiếp</blue>",
                        " ",
                        "<#A8D8EA>Nhấp chuột <white>để chuyển đổi",
                        "<white>cách xử lý khi giật hụt!"
                ),
                profile.isAutoRecastOnMiss()
        ));
        holder.putAction(SLOT_SETTING_RECAST_MISS, "TOGGLE_RECAST_MISS");

        inventory.setItem(SLOT_SETTING_AUTO_ROD, createPane(
                Material.CARROT_ON_A_STICK,
                "<gold>AI Tự Đổi Cần Câu Dự Phòng</gold>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Trạng thái: " + (profile.isAutoSwitchRod() ? "<green>Đang Bật</green>" : "<red>Đang Tắt</red>"),
                        " ",
                        "<dark_gray> ○ <white>Tác dụng AI:",
                        "  <dark_gray> -<gray> Tự tìm cần câu dự phòng trong túi đồ</gray>",
                        "  <dark_gray> -<gold> Tự động thay thế khi cần đang cầm sắp hỏng</gold>",
                        " ",
                        "<#A8D8EA>Nhấp chuột <white>để chuyển đổi",
                        "<white>chế độ AI tự thay cần câu!"
                ),
                profile.isAutoSwitchRod()
        ));
        holder.putAction(SLOT_SETTING_AUTO_ROD, "TOGGLE_AUTO_ROD");

        if (!forceOpen
                && player.getOpenInventory().getTopInventory().getHolder() instanceof AutoFishHolder existingHolder
                && existingHolder.title().equals(title)) {
            existingHolder.getInventory().setContents(inventory.getContents());
            existingHolder.replaceActionsFrom(holder);
        } else {
            player.openInventory(inventory);
        }
    }

    public static long computeGrantedPackageSeconds(@NotNull TimePackage pkg, @NotNull AutoFishProfile profile) {
        long basePlusGift = pkg.baseSeconds() + pkg.bonusSeconds();
        if (!profile.isFirstPurchaseClaimed()) {
            // First-purchase x2 on base duration + package gift
            return pkg.baseSeconds() * 2L + pkg.bonusSeconds();
        }
        int loyaltyPct = profile.getLoyaltyBonusTimePercent();
        long loyaltyBonusSeconds = Math.round(pkg.baseSeconds() * (loyaltyPct / 100.0D));
        return basePlusGift + loyaltyBonusSeconds;
    }

    @NotNull
    private ItemStack buildMissUpgradeItem(@NotNull AutoFishProfile profile, double balance) {
        int level = profile.getMissReductionLevel();
        int max = AutoFishProfile.MAX_MISS_REDUCTION_LEVEL;
        boolean maxed = level >= max;
        double currentMiss = AutoFishProfile.computeMissChancePercent(level);
        double nextMiss = AutoFishProfile.computeMissChancePercent(Math.min(max, level + 1));
        int discountPct = profile.getUpgradeDiscountPercent();
        double cost = applyDiscount(computeMissUpgradeCost(level), discountPct);

        return createPane(
                Material.TARGET,
                "<aqua>Nâng Cấp: Chuẩn Xác (Giảm Hụt)</aqua>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Cấp độ: <yellow>" + level + "/" + max + "</yellow>",
                        "<dark_gray> ○ <white>Tỉ lệ câu hụt: <green>" + DEC_FORMAT.format(currentMiss) + "%</green>"
                                + (maxed ? " <gold>(TỐI ĐA)</gold>" : " <dark_gray>➔</dark_gray> <aqua>" + DEC_FORMAT.format(nextMiss) + "%</aqua>"),
                        maxed ? "<dark_gray> ○ <white>Chi phí: <green>Đã đạt cấp tối đa</green>"
                                : "<dark_gray> ○ <white>Chi phí nâng: <gold>" + AutoFishingService.formatMoney(cost) + "</gold>"
                                + (discountPct > 0 ? " <green>(-" + discountPct + "% VIP)</green>" : ""),
                        " ",
                        "<dark_gray> ○ <white>Hiệu quả cấp bậc:",
                        "  <dark_gray> -<gray> Cấp 0 (Mặc định)</gray> <dark_gray>(<green>25.0% hụt<dark_gray>)",
                        "  <dark_gray> -<blue> Cấp 5 (Cao Cấp)</blue> <dark_gray>(<green>13.5% hụt<dark_gray>)",
                        "  <dark_gray> -<light_purple> Cấp 8 (Hiếm)</light_purple> <dark_gray>(<green>6.6% hụt<dark_gray>)",
                        "  <dark_gray> -<gold> Cấp 10 (Huyền Thoại)</gold> <dark_gray>(<green>2.0% hụt<dark_gray>)",
                        " ",
                        maxed ? "<#A8D8EA>Bạn đã đạt <white>cấp tối đa"
                                : (balance >= cost ? "<#C797FF>Nhấn chuột <white>để nâng cấp" : "<red>Chưa đủ tiền <white>để nâng cấp"),
                        "<white>độ chuẩn xác khi giật cần!"
                ),
                maxed
        );
    }

    @NotNull
    private ItemStack buildDelayUpgradeItem(@NotNull AutoFishProfile profile, double balance) {
        int level = profile.getCastDelayLevel();
        int max = AutoFishProfile.MAX_CAST_DELAY_LEVEL;
        boolean maxed = level >= max;
        double currentDelay = AutoFishProfile.computeCastDelaySeconds(level);
        double nextDelay = AutoFishProfile.computeCastDelaySeconds(Math.min(max, level + 1));
        int discountPct = profile.getUpgradeDiscountPercent();
        double cost = applyDiscount(computeDelayUpgradeCost(level), discountPct);

        return createPane(
                Material.SUGAR,
                "<green>Nâng Cấp: Nhanh Nhẹn (Giảm Delay)</green>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Cấp độ: <yellow>" + level + "/" + max + "</yellow>",
                        "<dark_gray> ○ <white>Delay mỗi đợt câu: <green>" + DEC_FORMAT.format(currentDelay) + "s</green>"
                                + (maxed ? " <gold>(TỐI ĐA)</gold>" : " <dark_gray>➔</dark_gray> <aqua>" + DEC_FORMAT.format(nextDelay) + "s</aqua>"),
                        maxed ? "<dark_gray> ○ <white>Chi phí: <green>Đã đạt cấp tối đa</green>"
                                : "<dark_gray> ○ <white>Chi phí nâng: <gold>" + AutoFishingService.formatMoney(cost) + "</gold>"
                                + (discountPct > 0 ? " <green>(-" + discountPct + "% VIP)</green>" : ""),
                        " ",
                        "<dark_gray> ○ <white>Hiệu quả cấp bậc:",
                        "  <dark_gray> -<gray> Cấp 0 (Mặc định)</gray> <dark_gray>(<green>4.5s / đợt<dark_gray>)",
                        "  <dark_gray> -<blue> Cấp 5 (Cao Cấp)</blue> <dark_gray>(<green>2.75s / đợt<dark_gray>)",
                        "  <dark_gray> -<light_purple> Cấp 8 (Hiếm)</light_purple> <dark_gray>(<green>1.7s / đợt<dark_gray>)",
                        "  <dark_gray> -<gold> Cấp 10 (Huyền Thoại)</gold> <dark_gray>(<green>1.0s / đợt<dark_gray>)",
                        " ",
                        maxed ? "<#A8D8EA>Bạn đã đạt <white>cấp tối đa"
                                : (balance >= cost ? "<#C797FF>Nhấn chuột <white>để nâng cấp" : "<red>Chưa đủ tiền <white>để nâng cấp"),
                        "<white>tốc độ quăng cần mỗi đợt!"
                ),
                maxed
        );
    }

    @NotNull
    private ItemStack buildReactionUpgradeItem(@NotNull AutoFishProfile profile, double balance) {
        int level = profile.getReactionLevel();
        int max = AutoFishProfile.MAX_REACTION_LEVEL;
        boolean maxed = level >= max;
        double currentReact = AutoFishProfile.computeReactionSeconds(level);
        double nextReact = AutoFishProfile.computeReactionSeconds(Math.min(max, level + 1));
        int discountPct = profile.getUpgradeDiscountPercent();
        double cost = applyDiscount(computeReactionUpgradeCost(level), discountPct);

        return createPane(
                Material.LIGHTNING_ROD,
                "<light_purple>Nâng Cấp: Phản Xạ (Giật Nhanh)</light_purple>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Cấp độ: <yellow>" + level + "/" + max + "</yellow>",
                        "<dark_gray> ○ <white>Phản xạ khi cắn câu: <green>" + DEC_FORMAT.format(currentReact) + "s</green>"
                                + (maxed ? " <gold>(TỐI ĐA)</gold>" : " <dark_gray>➔</dark_gray> <aqua>" + DEC_FORMAT.format(nextReact) + "s</aqua>"),
                        maxed ? "<dark_gray> ○ <white>Chi phí: <green>Đã đạt cấp tối đa</green>"
                                : "<dark_gray> ○ <white>Chi phí nâng: <gold>" + AutoFishingService.formatMoney(cost) + "</gold>"
                                + (discountPct > 0 ? " <green>(-" + discountPct + "% VIP)</green>" : ""),
                        " ",
                        "<dark_gray> ○ <white>Hiệu quả cấp bậc:",
                        "  <dark_gray> -<gray> Cấp 0 (Thường)</gray> <dark_gray>(<green>0.65s phản xạ<dark_gray>)",
                        "  <dark_gray> -<blue> Cấp 2 (Cao Cấp)</blue> <dark_gray>(<green>0.49s phản xạ<dark_gray>)",
                        "  <dark_gray> -<light_purple> Cấp 4 (Hiếm)</light_purple> <dark_gray>(<green>0.33s phản xạ<dark_gray>)",
                        "  <dark_gray> -<gold> Cấp 5 (Huyền Thoại)</gold> <dark_gray>(<green>0.25s phản xạ<dark_gray>)",
                        " ",
                        maxed ? "<#A8D8EA>Bạn đã đạt <white>cấp tối đa"
                                : (balance >= cost ? "<#C797FF>Nhấn chuột <white>để nâng cấp" : "<red>Chưa đủ tiền <white>để nâng cấp"),
                        "<white>tốc độ phản xạ khi cá cắn câu!"
                ),
                maxed
        );
    }

    @NotNull
    private ItemStack buildDurabilityUpgradeItem(@NotNull AutoFishProfile profile, double balance) {
        int level = profile.getDurabilitySaveLevel();
        int max = AutoFishProfile.MAX_DURABILITY_SAVE_LEVEL;
        boolean maxed = level >= max;
        double currentSave = AutoFishProfile.computeDurabilitySavePercent(level);
        double nextSave = AutoFishProfile.computeDurabilitySavePercent(Math.min(max, level + 1));
        int discountPct = profile.getUpgradeDiscountPercent();
        double cost = applyDiscount(computeDurabilityUpgradeCost(level), discountPct);

        return createPane(
                Material.ANVIL,
                "<gold>Nâng Cấp: Bền Bỉ (Giữ Độ Bền)</gold>",
                List.of(
                        " ",
                        "<dark_gray> ○ <white>Cấp độ: <yellow>" + level + "/" + max + "</yellow>",
                        "<dark_gray> ○ <white>Tỉ lệ không tốn độ bền: <green>" + DEC_FORMAT.format(currentSave) + "%</green>"
                                + (maxed ? " <gold>(TỐI ĐA)</gold>" : " <dark_gray>➔</dark_gray> <aqua>" + DEC_FORMAT.format(nextSave) + "%</aqua>"),
                        maxed ? "<dark_gray> ○ <white>Chi phí: <green>Đã đạt cấp tối đa</green>"
                                : "<dark_gray> ○ <white>Chi phí nâng: <gold>" + AutoFishingService.formatMoney(cost) + "</gold>"
                                + (discountPct > 0 ? " <green>(-" + discountPct + "% VIP)</green>" : ""),
                        " ",
                        "<dark_gray> ○ <white>Hiệu quả cấp bậc:",
                        "  <dark_gray> -<gray> Cấp 0 (Thường)</gray> <dark_gray>(<green>0% bảo toàn<dark_gray>)",
                        "  <dark_gray> -<blue> Cấp 2 (Cao Cấp)</blue> <dark_gray>(<green>20% bảo toàn<dark_gray>)",
                        "  <dark_gray> -<light_purple> Cấp 4 (Hiếm)</light_purple> <dark_gray>(<green>40% bảo toàn<dark_gray>)",
                        "  <dark_gray> -<gold> Cấp 5 (Huyền Thoại)</gold> <dark_gray>(<green>50% bảo toàn<dark_gray>)",
                        " ",
                        maxed ? "<#A8D8EA>Bạn đã đạt <white>cấp tối đa"
                                : (balance >= cost ? "<#C797FF>Nhấn chuột <white>để nâng cấp" : "<red>Chưa đủ tiền <white>để nâng cấp"),
                        "<white>khả năng bảo vệ độ bền cần câu!"
                ),
                maxed
        );
    }

    public static double applyDiscount(double baseCost, int discountPercent) {
        if (discountPercent <= 0) return baseCost;
        return Math.max(100D, Math.round(baseCost * (100.0D - discountPercent) / 100.0D));
    }

    public static double computeMissUpgradeCost(int currentLevel) {
        return 10_000D * (currentLevel + 1) * (1.0D + currentLevel * 0.25D);
    }

    public static double computeDelayUpgradeCost(int currentLevel) {
        return 10_000D * (currentLevel + 1) * (1.0D + currentLevel * 0.25D);
    }

    public static double computeReactionUpgradeCost(int currentLevel) {
        return 15_000D * (currentLevel + 1) * (1.0D + currentLevel * 0.35D);
    }

    public static double computeDurabilityUpgradeCost(int currentLevel) {
        return 15_000D * (currentLevel + 1) * (1.0D + currentLevel * 0.35D);
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AutoFishHolder holder)) {
            return;
        }
        event.setCancelled(true);
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        String action = holder.getAction(event.getRawSlot());
        if (action == null) {
            return;
        }

        AutoFishingService service = EvenMoreFish.getInstance().getAutoFishing();
        if (service == null) {
            player.closeInventory();
            return;
        }
        AutoFishProfile profile = service.getProfile(player.getUniqueId());

        if (action.startsWith("BUY_PKG:")) {
            String pkgId = action.substring("BUY_PKG:".length());
            for (TimePackage pkg : TIME_PACKAGES) {
                if (pkg.id().equalsIgnoreCase(pkgId)) {
                    if (!service.withdrawMoney(player, pkg.cost())) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.8f, 0.75f);
                        player.sendMessage(mm(
                                "<red>✗ Bạn không đủ <gold>" + AutoFishingService.formatMoney(pkg.cost())
                                        + "</gold> để mua " + pkg.nameMini() + "!</red>"
                        ));
                        return;
                    }
                    boolean wasFirstPurchase = !profile.isFirstPurchaseClaimed();
                    long grantedSeconds = computeGrantedPackageSeconds(pkg, profile);
                    if (wasFirstPurchase) {
                        profile.setFirstPurchaseClaimed(true);
                        if (profile.getMissReductionLevel() < AutoFishProfile.MAX_MISS_REDUCTION_LEVEL) {
                            profile.setMissReductionLevel(profile.getMissReductionLevel() + 1);
                        }
                    }
                    profile.applyPackagePurchaseTier(pkg.tier());
                    profile.addSeconds(grantedSeconds);
                    service.getDataStore().saveIfDirtyAsync();

                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.85f, 1.35f);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.55f, 1.45f);
                    player.sendMessage(mm(
                            "<green><bold>✔ MUA GÓI AUTO THÀNH CÔNG!</bold></green> <white>Đã cộng <yellow>+"
                                    + AutoFishProfile.formatDuration(grantedSeconds)
                                    + "</yellow> & kích hoạt đặc quyền " + profile.getActiveTierDisplayName()
                                    + " <gray>(Tổng: <aqua>" + AutoFishProfile.formatDuration(profile.getRemainingSeconds()) + "</aqua>)</gray>.</white>"
                    ));
                    if (wasFirstPurchase) {
                        player.sendMessage(mm(
                                "<gold><bold>🎁 ƯU ĐÃI MUA LẦN ĐẦU:</bold></gold> <white>Bạn đã được <yellow>NHÂN ĐÔI (x2) thời gian gốc</yellow> và tặng miễn phí <aqua>+1 Cấp Kỹ Năng Chuẩn Xác</aqua>!</white>"
                        ));
                    }
                    render(player, false);
                    return;
                }
            }
            return;
        }

        switch (action) {
            case "TOGGLE" -> {
                service.toggle(player);
                render(player, false);
            }
            case "UPG_MISS" -> {
                int level = profile.getMissReductionLevel();
                if (level >= AutoFishProfile.MAX_MISS_REDUCTION_LEVEL) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.7f, 0.9f);
                    return;
                }
                double cost = applyDiscount(computeMissUpgradeCost(level), profile.getUpgradeDiscountPercent());
                if (!service.withdrawMoney(player, cost)) {
                    sendInsufficientFunds(player, cost);
                    return;
                }
                profile.setMissReductionLevel(level + 1);
                service.getDataStore().saveIfDirtyAsync();
                player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.85f, 1.3f);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
                player.sendMessage(mm(
                        "<green><bold>⬆ NÂNG CẤP THÀNH CÔNG!</bold></green> <white>Chuẩn Xác đạt <yellow>Cấp "
                                + profile.getMissReductionLevel() + "</yellow> (Tỉ lệ câu hụt giảm còn <green>"
                                + DEC_FORMAT.format(profile.getMissChancePercent()) + "%</green>).</white>"
                ));
                render(player, false);
            }
            case "UPG_DELAY" -> {
                int level = profile.getCastDelayLevel();
                if (level >= AutoFishProfile.MAX_CAST_DELAY_LEVEL) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.7f, 0.9f);
                    return;
                }
                double cost = applyDiscount(computeDelayUpgradeCost(level), profile.getUpgradeDiscountPercent());
                if (!service.withdrawMoney(player, cost)) {
                    sendInsufficientFunds(player, cost);
                    return;
                }
                profile.setCastDelayLevel(level + 1);
                service.getDataStore().saveIfDirtyAsync();
                player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.85f, 1.3f);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
                player.sendMessage(mm(
                        "<green><bold>⬆ NÂNG CẤP THÀNH CÔNG!</bold></green> <white>Nhanh Nhẹn đạt <yellow>Cấp "
                                + profile.getCastDelayLevel() + "</yellow> (Delay mỗi đợt câu giảm còn <green>"
                                + DEC_FORMAT.format(profile.getCastDelaySeconds()) + "s</green>).</white>"
                ));
                render(player, false);
            }
            case "UPG_REACTION" -> {
                int level = profile.getReactionLevel();
                if (level >= AutoFishProfile.MAX_REACTION_LEVEL) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.7f, 0.9f);
                    return;
                }
                double cost = applyDiscount(computeReactionUpgradeCost(level), profile.getUpgradeDiscountPercent());
                if (!service.withdrawMoney(player, cost)) {
                    sendInsufficientFunds(player, cost);
                    return;
                }
                profile.setReactionLevel(level + 1);
                service.getDataStore().saveIfDirtyAsync();
                player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.85f, 1.3f);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
                player.sendMessage(mm(
                        "<green><bold>⬆ NÂNG CẤP THÀNH CÔNG!</bold></green> <white>Phản Xạ đạt <yellow>Cấp "
                                + profile.getReactionLevel() + "</yellow> (Phản xạ giật cần: <green>"
                                + DEC_FORMAT.format(profile.getReactionSeconds()) + "s</green>).</white>"
                ));
                render(player, false);
            }
            case "UPG_DURABILITY" -> {
                int level = profile.getDurabilitySaveLevel();
                if (level >= AutoFishProfile.MAX_DURABILITY_SAVE_LEVEL) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.7f, 0.9f);
                    return;
                }
                double cost = applyDiscount(computeDurabilityUpgradeCost(level), profile.getUpgradeDiscountPercent());
                if (!service.withdrawMoney(player, cost)) {
                    sendInsufficientFunds(player, cost);
                    return;
                }
                profile.setDurabilitySaveLevel(level + 1);
                service.getDataStore().saveIfDirtyAsync();
                player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.85f, 1.3f);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
                player.sendMessage(mm(
                        "<green><bold>⬆ NÂNG CẤP THÀNH CÔNG!</bold></green> <white>Bền Bỉ đạt <yellow>Cấp "
                                + profile.getDurabilitySaveLevel() + "</yellow> (Tỉ lệ giữ độ bền cần: <green>"
                                + DEC_FORMAT.format(profile.getDurabilitySavePercent()) + "%</green>).</white>"
                ));
                render(player, false);
            }
            case "TOGGLE_AUTO_SELL" -> {
                profile.setAutoSellOnFullInventory(!profile.isAutoSellOnFullInventory());
                service.getDataStore().saveIfDirtyAsync();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.7f, 1.2f);
                render(player, false);
            }
            case "TOGGLE_FULL_INV" -> {
                profile.setStopOnFullInventory(!profile.isStopOnFullInventory());
                service.getDataStore().saveIfDirtyAsync();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.7f, 1.2f);
                render(player, false);
            }
            case "TOGGLE_RECAST_MISS" -> {
                profile.setAutoRecastOnMiss(!profile.isAutoRecastOnMiss());
                service.getDataStore().saveIfDirtyAsync();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.7f, 1.2f);
                render(player, false);
            }
            case "TOGGLE_AUTO_ROD" -> {
                profile.setAutoSwitchRod(!profile.isAutoSwitchRod());
                service.getDataStore().saveIfDirtyAsync();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.7f, 1.2f);
                render(player, false);
            }
            case "OPEN_INDEX" -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.7f, 1.15f);
                FishIndexMenuManager.getInstance().open(player, null);
            }
            case "CLOSE" -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.7f, 0.9f);
                if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                    player.closeInventory();
                } else {
                    new MainMenuGui(player).open();
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AutoFishHolder) {
            event.setCancelled(true);
        }
    }

    private static void sendInsufficientFunds(@NotNull Player player, double cost) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.8f, 0.75f);
        player.sendMessage(mm(
                "<red>✗ Bạn cần <gold>" + AutoFishingService.formatMoney(cost) + "</gold> để thực hiện nâng cấp này!</red>"
        ));
    }

    @NotNull
    private static ItemStack createPane(
            @NotNull Material material,
            @NotNull String nameMini,
            @NotNull List<String> loreMini,
            boolean glint
    ) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(mm(nameMini));
        if (loreMini.isEmpty()) {
            meta.lore(null);
        } else {
            List<Component> lore = new ArrayList<>(loreMini.size());
            for (String line : loreMini) {
                lore.add(mm(line));
            }
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        com.oheers.fish.FishUtils.disableGlintOverride(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    @NotNull
    private static Component mm(@NotNull String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    private static final class AutoFishHolder implements InventoryHolder {
        private final UUID playerId;
        private final Component title;
        private final Map<Integer, String> slotActions = new HashMap<>();
        private Inventory inventory;

        private AutoFishHolder(@NotNull UUID playerId, @NotNull Component title) {
            this.playerId = playerId;
            this.title = title;
        }

        void bind(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @NotNull
        Component title() {
            return title;
        }

        void putAction(int slot, @NotNull String action) {
            slotActions.put(slot, action);
        }

        void replaceActionsFrom(@NotNull AutoFishHolder other) {
            slotActions.clear();
            slotActions.putAll(other.slotActions);
        }

        @Nullable
        String getAction(int slot) {
            return slotActions.get(slot);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
