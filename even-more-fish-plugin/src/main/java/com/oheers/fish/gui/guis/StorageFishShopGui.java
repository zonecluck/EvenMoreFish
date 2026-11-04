package com.oheers.fish.gui.guis;

import com.oheers.fish.api.economy.Economy;
import com.oheers.fish.autofish.AutoFishingService;
import com.oheers.fish.fishing.FishingProcessor;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.messages.ConfigMessage;
import com.oheers.fish.selling.SellHelper;
import com.oheers.fish.selling.TurtleStorageFishBridge;
import com.oheers.fish.selling.TurtleStorageFishBridge.StoredFishEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 54-slot GUI shop integrated with TurtleStorageUltra and Player Inventory.
 */
public final class StorageFishShopGui implements Listener {

    private static final StorageFishShopGui INSTANCE = new StorageFishShopGui();
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.##");

    private static final int GUI_SIZE = 54;
    private static final int[] RARITY_SLOTS = {1, 2, 3, 4, 5, 6};
    private static final int SLOT_INFO = 7;
    private static final int[] FISH_SLOTS = {
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_PREV = 45;
    private static final int SLOT_MANUAL_SHOP = 47;
    private static final int SLOT_SELL_ALL = 49;
    private static final int SLOT_CLOSE = 51;
    private static final int SLOT_NEXT = 53;

    private static final String[] RARITY_ORDER = {
        "Common", "Rare", "Epic", "Legendary", "Mythic", "Anomaly"
    };

    private StorageFishShopGui() {
    }

    public static @NotNull StorageFishShopGui getInstance() {
        return INSTANCE;
    }

    public static void open(@NotNull Player player) {
        INSTANCE.openMenu(player, 0);
    }

    public void closeAll() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof ShopHolder) {
                online.closeInventory();
            }
        }
    }

    public void openMenu(@NotNull Player player, int requestedPage) {
        List<StoredFishEntry> storedFish = TurtleStorageFishBridge.getStoredFish(player);
        int totalPages = Math.max(1, (int) Math.ceil(storedFish.size() / (double) FISH_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        ShopHolder holder = new ShopHolder(page);
        Component title = mm("<dark_gray>⚓</dark_gray> <gradient:#38ef7d:#11998e><bold>THƯƠNG ĐIẾM THỦY SẢN</bold></gradient> <dark_gray>("
            + (page + 1) + "/" + totalPages + ")</dark_gray>");
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.bind(inv);

        populate(player, inv, holder, storedFish, page, totalPages);
        player.openInventory(inv);
    }

    private void populate(
        @NotNull Player player,
        @NotNull Inventory inv,
        @NotNull ShopHolder holder,
        @NotNull List<StoredFishEntry> storedFish,
        int page,
        int totalPages
    ) {
        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = pane(Material.CYAN_STAINED_GLASS_PANE);
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, border);
        }
        inv.setItem(0, accent);
        inv.setItem(8, accent);
        for (int s = 9; s <= 17; s++) {
            inv.setItem(s, accent);
        }
        inv.setItem(45, accent);
        inv.setItem(53, accent);

        double vipBonusPct = AutoFishingService.getActiveSellBonusPercent(player);
        int comboStreak = FishingProcessor.getComboStreak(player.getUniqueId());
        int comboBonusPct = FishingProcessor.getComboBonusPercent(comboStreak);
        double totalBonusPct = vipBonusPct + comboBonusPct;
        double bonusMultiplier = 1.0D + (Math.max(0.0D, totalBonusPct) / 100.0D);

        // Group stored fish by canonical rarity bucket
        Map<String, List<StoredFishEntry>> byRarityBucket = new HashMap<>();
        for (String bucket : RARITY_ORDER) {
            byRarityBucket.put(bucket, new ArrayList<>());
        }
        for (StoredFishEntry entry : storedFish) {
            String bucket = resolveCanonicalBucket(entry.rarity().getId());
            byRarityBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(entry);
        }

        // Row 1 (Slots 1..6): 6 Rarity Summary Buttons
        for (int i = 0; i < RARITY_ORDER.length && i < RARITY_SLOTS.length; i++) {
            String bucket = RARITY_ORDER[i];
            int slot = RARITY_SLOTS[i];
            List<StoredFishEntry> entries = byRarityBucket.getOrDefault(bucket, Collections.emptyList());
            Rarity matchedRarity = findRarityForBucket(bucket, entries);
            String rarityId = matchedRarity != null ? matchedRarity.getId() : bucket;
            holder.slotRarityMap.put(slot, rarityId);
            inv.setItem(slot, buildRaritySummaryItem(bucket, entries, totalBonusPct, bonusMultiplier));
        }

        // Slot 7: Storage & Bonus Info Item
        long totalStorageCount = storedFish.stream().mapToLong(StoredFishEntry::quantity).sum();
        double totalStorageBase = storedFish.stream().mapToDouble(StoredFishEntry::totalBaseWorth).sum();
        int invCount = SellHelper.calculateInventoryCount(player.getInventory());
        double invBase = SellHelper.calculateInventoryWorth(player.getInventory());
        inv.setItem(SLOT_INFO, buildStorageInfoItem(
            player, totalStorageCount, totalStorageBase, invCount, invBase, vipBonusPct, comboStreak, comboBonusPct
        ));

        // Clear center fish area
        for (int slot : FISH_SLOTS) {
            inv.setItem(slot, null);
        }

        if (storedFish.isEmpty()) {
            inv.setItem(31, buildEmptyStorageItem(invCount, invBase));
        } else {
            int start = page * FISH_SLOTS.length;
            int end = Math.min(storedFish.size(), start + FISH_SLOTS.length);
            for (int i = start; i < end; i++) {
                int slot = FISH_SLOTS[i - start];
                StoredFishEntry entry = storedFish.get(i);
                holder.slotFishMap.put(slot, entry);
                inv.setItem(slot, buildStoredFishItem(entry, totalBonusPct, bonusMultiplier));
            }
        }

        // Row 6 Controls
        if (page > 0) {
            inv.setItem(SLOT_PREV, buildNavButton(Material.ARROW, "<aqua>« Trang Trước (" + page + "/" + totalPages + ")</aqua>",
                "<#A8D8EA>Nhấn để quay lại", "<white>trang cá trước đó!"));
        }
        if (page + 1 < totalPages) {
            inv.setItem(SLOT_NEXT, buildNavButton(Material.ARROW, "<aqua>Trang Tiếp (" + (page + 2) + "/" + totalPages + ") »</aqua>",
                "<#A8D8EA>Nhấn để chuyển sang", "<white>trang cá tiếp theo!"));
        }

        inv.setItem(SLOT_MANUAL_SHOP, buildManualShopButton(invCount, invBase));
        inv.setItem(SLOT_SELL_ALL, buildSellAllButton(
            totalStorageCount, totalStorageBase, invCount, invBase, totalBonusPct, bonusMultiplier
        ));
        inv.setItem(SLOT_CLOSE, buildCloseButton());
    }

    private @NotNull ItemStack buildRaritySummaryItem(
        @NotNull String bucket,
        @NotNull List<StoredFishEntry> entries,
        double bonusPct,
        double bonusMultiplier
    ) {
        long count = entries.stream().mapToLong(StoredFishEntry::quantity).sum();
        double baseWorth = entries.stream().mapToDouble(StoredFishEntry::totalBaseWorth).sum();
        double finalWorth = baseWorth * bonusMultiplier;

        Material icon = switch (bucket) {
            case "Common" -> Material.COD;
            case "Rare" -> Material.SALMON;
            case "Epic" -> Material.TROPICAL_FISH;
            case "Legendary" -> Material.PUFFERFISH;
            case "Mythic" -> Material.PRISMARINE_SHARD;
            case "Anomaly" -> Material.NETHER_STAR;
            default -> Material.COD;
        };
        String colorTag = rarityColorTag(bucket);
        String vnLabel = rarityVietnameseName(bucket);

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm(colorTag + "<bold>Bán Cá " + vnLabel + "</bold> <dark_gray>(" + bucket + ")</dark_gray>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Số loài trong kho: <yellow>" + entries.size() + "</yellow> loài"));
            lore.add(mm("<dark_gray> ○ <white>Tổng số lượng: <aqua>" + count + "</aqua> con"));
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Giá trị thu mua:"));
            lore.add(mm("  <dark_gray> -<gray> Giá gốc:</gray> <yellow>" + formatWorth(baseWorth) + "</yellow>"));
            lore.add(mm("  <dark_gray> -<green> Thưởng thêm:</green> <aqua>+" + (int) bonusPct + "%</aqua>"));
            lore.add(mm("  <dark_gray> -<gold> Thực nhận:</gold> <green>" + formatWorth(finalWorth) + "</green>"));
            lore.add(mm(" "));
            if (count > 0L) {
                lore.add(mm("<#A8D8EA>Nhấn để bán toàn bộ"));
                lore.add(mm("<white>cá bậc " + vnLabel + " trong kho!"));
            } else {
                lore.add(mm("<#A8D8EA>Chưa có cá " + vnLabel));
                lore.add(mm("<white>đang lưu trong Kho Chứa!"));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private @NotNull ItemStack buildStoredFishItem(
        @NotNull StoredFishEntry entry,
        double bonusPct,
        double bonusMultiplier
    ) {
        Fish fish = entry.fish();
        ItemStack item;
        try {
            item = fish.getFactory().createItem();
            if (item == null || item.isEmpty()) {
                item = new ItemStack(Material.COD);
            } else {
                item = item.clone();
            }
        } catch (Throwable ignored) {
            item = new ItemStack(Material.COD);
        }

        item.setAmount((int) Math.max(1L, Math.min(64L, entry.quantity())));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String fishNameMm = MM.serialize(fish.getDisplayName().getComponentMessage());
            String rarityMm = MM.serialize(entry.rarity().getDisplayName().getComponentMessage());
            double unitFinal = entry.unitWorth() * bonusMultiplier;
            double stackFinal = entry.totalBaseWorth() * bonusMultiplier;

            meta.displayName(mm(fishNameMm + " <dark_gray>x" + entry.quantity() + "</dark_gray>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Độ hiếm: " + rarityMm));
            lore.add(mm("<dark_gray> ○ <white>Đang lưu kho: <aqua>" + entry.quantity() + "</aqua> con"));
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Bảng giá thu mua:"));
            lore.add(mm("  <dark_gray> -<yellow> Bán 1 con:</yellow> <green>" + formatWorth(unitFinal) + "</green>"));
            lore.add(mm("  <dark_gray> -<gold> Bán tất cả (" + entry.quantity() + "):</gold> <green>" + formatWorth(stackFinal) + "</green> <dark_gray>(+" + (int) bonusPct + "%)</dark_gray>"));
            lore.add(mm(" "));
            lore.add(mm("<#A8D8EA>Chuột trái bán 1 con"));
            lore.add(mm("<white>Shift-Click bán hết loài này!"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private @NotNull ItemStack buildStorageInfoItem(
        @NotNull Player player,
        long storageCount,
        double storageBase,
        int invCount,
        double invBase,
        double vipBonusPct,
        int comboStreak,
        int comboBonusPct
    ) {
        boolean storageEnabled = TurtleStorageFishBridge.canAcceptCaughtFish(player);
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm("<aqua><bold>Thông Tin Kho Thủy Sản</bold></aqua>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Tự cất vào /storage: " + (storageEnabled ? "<green>Đang bật</green>" : "<red>Đang tắt / Đầy</red>")));
            lore.add(mm("<dark_gray> ○ <white>Cá trong Kho: <aqua>" + storageCount + "</aqua> con <dark_gray>(" + formatWorth(storageBase) + ")</dark_gray>"));
            lore.add(mm("<dark_gray> ○ <white>Cá trong Túi: <yellow>" + invCount + "</yellow> con <dark_gray>(" + formatWorth(invBase) + ")</dark_gray>"));
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Hệ số thưởng bán cá:"));
            lore.add(mm("  <dark_gray> -<aqua> Đặc quyền AutoFish:</aqua> <green>+" + (int) vipBonusPct + "%</green>"));
            lore.add(mm("  <dark_gray> -<gold> Chuỗi Combo (x" + comboStreak + "):</gold> <green>+" + comboBonusPct + "%</green>"));
            lore.add(mm(" "));
            lore.add(mm("<#A8D8EA>Cá câu được sẽ tự động"));
            lore.add(mm("<white>chuyển thẳng vào Kho Chứa!"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private @NotNull ItemStack buildEmptyStorageItem(int invCount, double invBase) {
        ItemStack item = new ItemStack(Material.TROPICAL_FISH_BUCKET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm("<yellow><bold>Kho Thủy Sản Đang Trống</bold></yellow>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Trạng thái: <gray>Chưa có cá trong /storage</gray>"));
            lore.add(mm("<dark_gray> ○ <white>Cá trong túi đồ: <yellow>" + invCount + "</yellow> con <dark_gray>(" + formatWorth(invBase) + ")</dark_gray>"));
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Hướng dẫn:"));
            lore.add(mm("  <dark_gray> -<aqua> Câu cá mới</aqua> <dark_gray>để tự động cất vào kho"));
            lore.add(mm("  <dark_gray> -<yellow> Nhấn nút Bán Tất Cả</yellow> <dark_gray>để bán cá trong túi"));
            lore.add(mm(" "));
            lore.add(mm("<#A8D8EA>Hãy ra hồ quăng cần"));
            lore.add(mm("<white>để lấp đầy kho thủy sản!"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private @NotNull ItemStack buildManualShopButton(int invCount, double invBase) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm("<aqua><bold>Tủ Bán Cá Túi Đồ</bold></aqua>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Cá trong túi: <yellow>" + invCount + "</yellow> con"));
            lore.add(mm("<dark_gray> ○ <white>Giá trị ước tính: <green>" + formatWorth(invBase) + "</green>"));
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Chức năng:"));
            lore.add(mm("  <dark_gray> -<gray> Bỏ từng con cá trong túi vào khay bán</gray>"));
            lore.add(mm(" "));
            lore.add(mm("<#A8D8EA>Nhấn để mở khay"));
            lore.add(mm("<white>bán cá thủ công từ túi đồ!"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private @NotNull ItemStack buildSellAllButton(
        long storageCount,
        double storageBase,
        int invCount,
        double invBase,
        double bonusPct,
        double bonusMultiplier
    ) {
        long totalCount = storageCount + invCount;
        double totalBase = storageBase + invBase;
        double totalFinal = totalBase * bonusMultiplier;

        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm("<gold><bold>✦ BÁN TẤT CẢ THỦY SẢN ✦</bold></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Tổng số cá: <yellow>" + totalCount + "</yellow> con"));
            lore.add(mm("<dark_gray> ○ <white>Nguồn: <aqua>" + storageCount + " Kho</aqua> + <yellow>" + invCount + " Túi đồ</yellow>"));
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Tổng kết thanh toán:"));
            lore.add(mm("  <dark_gray> -<gray> Giá gốc:</gray> <yellow>" + formatWorth(totalBase) + "</yellow>"));
            lore.add(mm("  <dark_gray> -<aqua> Thưởng Combo + VIP:</aqua> <green>+" + (int) bonusPct + "%</green>"));
            lore.add(mm("  <dark_gray> -<gold> Tổng nhận:</gold> <green><bold>" + formatWorth(totalFinal) + "</bold></green>"));
            lore.add(mm(" "));
            lore.add(mm("<#A8D8EA>Nhấn để bán toàn bộ cá"));
            lore.add(mm("<white>trong Kho Chứa và Túi Đồ!"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private @NotNull ItemStack buildCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm("<red><bold>Đóng Cửa Hàng</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Trạng thái: <green>Sẵn sàng</green>"));
            lore.add(mm(" "));
            lore.add(mm("<#A8D8EA>Nhấn để đóng"));
            lore.add(mm("<white>cửa hàng thủy sản!"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private @NotNull ItemStack buildNavButton(
        @NotNull Material mat,
        @NotNull String name,
        @NotNull String action1,
        @NotNull String action2
    ) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm(name));
            List<Component> lore = new ArrayList<>();
            lore.add(mm(" "));
            lore.add(mm("<dark_gray> ○ <white>Điều hướng danh sách cá trong kho"));
            lore.add(mm(" "));
            lore.add(mm(action1));
            lore.add(mm(action2));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= GUI_SIZE) {
            return;
        }

        if (rawSlot == SLOT_CLOSE) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.8f, 1.0f);
            player.closeInventory();
            return;
        }

        if (rawSlot == SLOT_PREV && holder.page > 0) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.9f, 1.15f);
            openMenu(player, holder.page - 1);
            return;
        }

        if (rawSlot == SLOT_NEXT) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.9f, 1.15f);
            openMenu(player, holder.page + 1);
            return;
        }

        if (rawSlot == SLOT_MANUAL_SHOP) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, SoundCategory.PLAYERS, 0.8f, 1.1f);
            new SellGui(player, SellGui.SellState.NORMAL, null).open();
            return;
        }

        if (rawSlot == SLOT_SELL_ALL) {
            new SellHelper(player.getInventory(), player).sell();
            openMenu(player, holder.page);
            return;
        }

        // Check Rarity Summary Click
        String rarityId = holder.slotRarityMap.get(rawSlot);
        if (rarityId != null) {
            TurtleStorageFishBridge.SellSummary summary = TurtleStorageFishBridge.sellStoredFish(
                player, rarityId, null, Long.MAX_VALUE, true
            );
            if (summary.soldCount() <= 0L) {
                ConfigMessage.NO_SELLABLE_FISH.getMessage().send(player);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.8f, 0.8f);
            }
            openMenu(player, holder.page);
            return;
        }

        // Check Individual Stored Fish Click
        StoredFishEntry fishEntry = holder.slotFishMap.get(rawSlot);
        if (fishEntry != null) {
            ClickType click = event.getClick();
            long maxToSell = (click.isShiftClick() || click.isRightClick()) ? Long.MAX_VALUE : 1L;
            TurtleStorageFishBridge.SellSummary summary = TurtleStorageFishBridge.sellStoredFish(
                player,
                fishEntry.rarity().getId(),
                fishEntry.fish().getName(),
                maxToSell,
                true
            );
            if (summary.soldCount() <= 0L) {
                ConfigMessage.NO_SELLABLE_FISH.getMessage().send(player);
            }
            openMenu(player, holder.page);
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    private static @NotNull String resolveCanonicalBucket(@Nullable String rarityId) {
        if (rarityId == null) {
            return "Common";
        }
        String lower = rarityId.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("anomaly") || lower.contains("divine") || lower.contains("god") || lower.contains("di_biet")) {
            return "Anomaly";
        }
        if (lower.contains("mythic") || lower.contains("than_thoai")) {
            return "Mythic";
        }
        if (lower.contains("legend") || lower.contains("huyen_thoai")) {
            return "Legendary";
        }
        if (lower.contains("epic") || lower.contains("su_thi")) {
            return "Epic";
        }
        if (lower.contains("rare") || lower.contains("hiem") || lower.contains("uncommon")) {
            return "Rare";
        }
        return "Common";
    }

    private static @Nullable Rarity findRarityForBucket(@NotNull String bucket, @NotNull List<StoredFishEntry> entries) {
        if (!entries.isEmpty()) {
            return entries.get(0).rarity();
        }
        Rarity direct = FishManager.getInstance().getRarity(bucket);
        if (direct != null) {
            return direct;
        }
        for (Rarity r : FishManager.getInstance().getRarityMap().values()) {
            if (resolveCanonicalBucket(r.getId()).equalsIgnoreCase(bucket)) {
                return r;
            }
        }
        return null;
    }

    private static @NotNull String rarityColorTag(@NotNull String bucket) {
        return switch (bucket) {
            case "Common" -> "<white>";
            case "Rare" -> "<aqua>";
            case "Epic" -> "<light_purple>";
            case "Legendary" -> "<gold>";
            case "Mythic" -> "<#FF5555>";
            case "Anomaly" -> "<#00E5FF>";
            default -> "<white>";
        };
    }

    private static @NotNull String rarityVietnameseName(@NotNull String bucket) {
        return switch (bucket) {
            case "Common" -> "Thường";
            case "Rare" -> "Hiếm";
            case "Epic" -> "Sử Thi";
            case "Legendary" -> "Huyền Thoại";
            case "Mythic" -> "Thần Thoại";
            case "Anomaly" -> "Dị Biệt";
            default -> bucket;
        };
    }

    private static @NotNull String formatWorth(double worth) {
        String formatted = AutoFishingService.formatMoney(Math.max(0.0D, worth));
        if (formatted != null && !formatted.isBlank()) {
            return formatted;
        }
        return MONEY_FMT.format(Math.max(0.0D, worth)) + " Xu";
    }

    private static @NotNull ItemStack pane(@NotNull Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static @NotNull Component mm(@NotNull String raw) {
        return MM.deserialize(raw).decoration(TextDecoration.ITALIC, false);
    }

    private static final class ShopHolder implements InventoryHolder {
        private final int page;
        private final Map<Integer, String> slotRarityMap = new HashMap<>();
        private final Map<Integer, StoredFishEntry> slotFishMap = new HashMap<>();
        private Inventory inventory;

        private ShopHolder(int page) {
            this.page = page;
        }

        private void bind(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
