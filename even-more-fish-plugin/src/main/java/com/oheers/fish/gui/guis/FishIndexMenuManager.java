package com.oheers.fish.gui.guis;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.database.Database;
import com.oheers.fish.database.data.FishRarityKey;
import com.oheers.fish.database.data.UserFishRarityKey;
import com.oheers.fish.database.data.manager.DataManager;
import com.oheers.fish.database.model.fish.FishStats;
import com.oheers.fish.database.model.user.UserFishStats;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 54-slot Fish Index Collection GUI inspired by LeDatRNG's IndexMenuManager.
 * Displays rarity tabs, active tab indicator panes, 21 fish collection slots per page,
 * overall collection progress, and personal/server discovery records.
 */
public final class FishIndexMenuManager implements Listener {

    public enum FilterMode {
        ALL("<aqua>Tất cả</aqua>"),
        UNLOCKED("<green>Đã mở khóa</green>"),
        LOCKED("<gray>Chưa mở khóa</gray>");

        private final String displayMini;

        FilterMode(String displayMini) {
            this.displayMini = displayMini;
        }

        public String displayMini() {
            return displayMini;
        }

        public FilterMode next() {
            return switch (this) {
                case ALL -> UNLOCKED;
                case UNLOCKED -> LOCKED;
                case LOCKED -> ALL;
            };
        }
    }

    private static final FishIndexMenuManager INSTANCE = new FishIndexMenuManager();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final DecimalFormat LENGTH_FORMAT = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int INVENTORY_SIZE = 54;
    private static final int[] RARITY_SLOTS = {1, 2, 3, 4, 5, 6, 7};
    private static final int[] INDICATOR_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] CONTENT_SLOTS = {
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private static final int SLOT_RARITY_PREV = 0;
    private static final int SLOT_RARITY_NEXT = 8;
    private static final int SLOT_PAGE_PREV = 45;
    private static final int SLOT_HUB_INDEX = 46;
    private static final int SLOT_PROGRESS = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_CLOSE = 50;
    private static final int SLOT_PAGE_NEXT = 53;

    private static final int SUB_SLOT_FISH_PREVIEW = 4;
    private static final int[] SUB_TIER_PANE_SLOTS = {20, 21, 22, 23, 24};
    private static final int[] SUB_TIER_REWARD_SLOTS = {29, 30, 31, 32, 33};
    private static final Material[] SUB_TIER_REWARD_ICONS = {
        Material.EXPERIENCE_BOTTLE,
        Material.PRISMARINE_CRYSTALS,
        Material.AMETHYST_SHARD,
        Material.HEART_OF_THE_SEA,
        Material.NETHER_STAR
    };
    private static final int SUB_SLOT_BACK_TO_LIST = 45;
    private static final int SUB_SLOT_HUB_INDEX = 49;
    private static final int SUB_SLOT_CLOSE = 53;

    private static final int[] THRESHOLDS_COMMON_RARE = {1, 3, 7, 15, 30};
    private static final int[] THRESHOLDS_EPIC = {1, 2, 5, 10, 20};
    private static final int[] THRESHOLDS_HIGH_TIER = {1, 2, 3, 5, 8};
    private static final String[] ROMAN_LEVELS = {"0", "I", "II", "III", "IV", "V"};

    private static final AtomicInteger LOADER_THREAD_ID = new AtomicInteger(1);
    private static final ExecutorService LOADER_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "emf-index-loader-" + LOADER_THREAD_ID.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    private final Map<UUID, SessionState> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionSequence = new AtomicInteger(1);

    private FishIndexMenuManager() {
    }

    public static FishIndexMenuManager getInstance() {
        return INSTANCE;
    }

    public void open(@NotNull Player player, @Nullable Rarity initialRarity) {
        List<Rarity> rarities = getEligibleRarities();
        if (rarities.isEmpty()) {
            player.sendMessage(mm("<red>Hiện chưa có loại cá nào trong Bộ Sưu Tập!</red>"));
            return;
        }
        Rarity selected = resolveSelectedRarity(rarities, initialRarity);
        int rarityIndex = indexOfRarity(rarities, selected);
        int rarityPage = rarityIndex / RARITY_SLOTS.length;

        UUID playerId = player.getUniqueId();
        int sessionId = sessionSequence.incrementAndGet();
        SessionState session = new SessionState(
            sessionId,
            selected.getId(),
            0,
            rarityPage,
            FilterMode.ALL,
            new JournalDiscoverySnapshot(Map.of())
        );
        sessions.put(playerId, session);

        renderInventory(player, session, rarities, true);
        loadDiscoveryAsync(player, session);
    }

    public void closeAll() {
        for (UUID playerId : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof IndexHolder) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }

    private void loadDiscoveryAsync(@NotNull Player player, @NotNull SessionState state) {
        UUID playerId = player.getUniqueId();
        List<Rarity> rarities = getEligibleRarities();
        if (rarities.isEmpty()) {
            return;
        }

        Set<String> catalogRarities = new LinkedHashSet<>();
        Set<JournalDiscoverySnapshot.Key> selectedScope = new LinkedHashSet<>();
        for (Rarity rarity : rarities) {
            catalogRarities.add(rarity.getId());
            if (rarity.getId().equalsIgnoreCase(state.selectedRarityId())) {
                for (Fish fish : getEligibleFish(rarity)) {
                    selectedScope.add(new JournalDiscoverySnapshot.Key(rarity.getId(), fish.getName()));
                }
            }
        }
        String selectedRarityId = state.selectedRarityId();

        CompletableFuture.supplyAsync(() -> fetchCombinedSnapshot(playerId, selectedRarityId, selectedScope, catalogRarities, rarities), LOADER_EXECUTOR)
            .thenAccept(snapshot -> EvenMoreFish.getScheduler().runTask(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                SessionState current = sessions.get(playerId);
                if (current == null || current.sessionId() != state.sessionId()) {
                    return;
                }
                SessionState updated = current.withSnapshot(snapshot);
                sessions.put(playerId, updated);
                renderInventory(player, updated, getEligibleRarities(), false);
            }));
    }

    @NotNull
    private JournalDiscoverySnapshot fetchCombinedSnapshot(
        @NotNull UUID playerId,
        @NotNull String selectedRarityId,
        @NotNull Set<JournalDiscoverySnapshot.Key> selectedScope,
        @NotNull Set<String> catalogRarities,
        @NotNull List<Rarity> rarities
    ) {
        Map<JournalDiscoverySnapshot.Key, JournalDiscoverySnapshot.Entry> combined = new HashMap<>();
        int userId = resolveUserId(playerId);

        if (MainConfig.getInstance().databaseEnabled()) {
            Database database = EvenMoreFish.getInstance().getPluginDataManager().getDatabase();
            String prefix = MainConfig.getInstance().getPrefix();
            if (database != null && userId > 0 && !catalogRarities.isEmpty()) {
                try {
                    JournalDiscoverySnapshot allUserSnapshot = JournalDiscoverySnapshot.load(
                        database.getJdbi(), prefix, userId, null, Set.of(), catalogRarities
                    );
                    combined.putAll(allUserSnapshot.entries());
                } catch (RuntimeException ignored) {
                }
                if (!selectedScope.isEmpty()) {
                    try {
                        JournalDiscoverySnapshot raritySnapshot = JournalDiscoverySnapshot.load(
                            database.getJdbi(), prefix, userId, selectedRarityId, selectedScope, catalogRarities
                        );
                        combined.putAll(raritySnapshot.entries());
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }

        // Overlay live in-memory catches from PluginDataManager so newly caught fish appear unlocked immediately
        overlayInMemoryCache(userId, rarities, combined);
        return new JournalDiscoverySnapshot(combined);
    }

    private void overlayInMemoryCache(
        int userId,
        @NotNull List<Rarity> rarities,
        @NotNull Map<JournalDiscoverySnapshot.Key, JournalDiscoverySnapshot.Entry> entries
    ) {
        var dataManager = EvenMoreFish.getInstance().getPluginDataManager();
        if (dataManager == null) {
            return;
        }
        DataManager<UserFishStats> userStatsManager = dataManager.getUserFishStatsDataManager();
        DataManager<FishStats> fishStatsManager = dataManager.getFishStatsDataManager();

        for (Rarity rarity : rarities) {
            for (Fish fish : getEligibleFish(rarity)) {
                JournalDiscoverySnapshot.Key key = new JournalDiscoverySnapshot.Key(rarity.getId(), fish.getName());
                JournalDiscoverySnapshot.Entry existing = entries.get(key);

                UserFishStats liveUser = null;
                if (userId > 0 && userStatsManager != null) {
                    String userKey = UserFishRarityKey.of(userId, fish).toString();
                    liveUser = userStatsManager.peek(userKey);
                }
                FishStats liveServer = null;
                if (fishStatsManager != null) {
                    String serverKey = FishRarityKey.of(fish).toString();
                    liveServer = fishStatsManager.peek(serverKey);
                }

                if (liveUser == null && liveServer == null) {
                    continue;
                }

                boolean caught = (liveUser != null && liveUser.getQuantity() > 0)
                    || (existing != null && existing.caught());
                int userQty = liveUser != null ? Math.max(liveUser.getQuantity(), existing != null ? existing.quantity() : 0)
                    : (existing != null ? existing.quantity() : 0);
                String date = existing != null ? existing.date() : null;
                if (date == null && liveUser != null && liveUser.getFirstCatchTime() != null) {
                    date = DATE_FORMATTER.format(liveUser.getFirstCatchTime());
                }
                float userShortest = liveUser != null ? liveUser.getShortestLength()
                    : (existing != null ? existing.shortest() : 0.0F);
                float userLongest = liveUser != null ? liveUser.getLongestLength()
                    : (existing != null ? existing.longest() : 0.0F);

                int serverQty = liveServer != null ? Math.max(liveServer.getQuantity(), existing != null ? existing.serverQuantity() : 0)
                    : (existing != null ? existing.serverQuantity() : userQty);
                float serverShortest = liveServer != null ? liveServer.getShortestLength()
                    : (existing != null ? existing.serverShortest() : userShortest);
                float serverLongest = liveServer != null ? liveServer.getLongestLength()
                    : (existing != null ? existing.serverLongest() : userLongest);
                String discoverer = existing != null ? existing.discoverer() : null;
                if (liveServer != null) {
                    if (liveServer.getDiscovererName() != null && !liveServer.getDiscovererName().isBlank()) {
                        discoverer = liveServer.getDiscovererName();
                    } else if (discoverer == null) {
                        discoverer = liveServer.getDiscoverer().toString();
                    }
                }

                entries.put(key, new JournalDiscoverySnapshot.Entry(
                    caught,
                    userQty,
                    date,
                    userShortest,
                    userLongest,
                    serverQty,
                    serverShortest,
                    serverLongest,
                    discoverer
                ));
            }
        }
    }

    private int resolveUserId(@NotNull UUID playerId) {
        try {
            var dataManager = EvenMoreFish.getInstance().getPluginDataManager();
            if (dataManager == null || dataManager.getUserManager() == null) {
                return 0;
            }
            return dataManager.getUserManager().getUserId(playerId);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void renderInventory(
        @NotNull Player player,
        @NotNull SessionState session,
        @NotNull List<Rarity> rarities,
        boolean forceNewInventory
    ) {
        if (rarities.isEmpty()) {
            return;
        }
        Rarity selectedRarity = resolveSelectedRarity(rarities, FishManager.getInstance().getRarity(session.selectedRarityId()));
        List<Fish> allRarityFish = getSortedFish(selectedRarity);
        List<Fish> filteredFish = filterFishList(selectedRarity, allRarityFish, session.snapshot(), session.filterMode());
        int totalPages = Math.max(1, (int) Math.ceil(filteredFish.size() / (double) CONTENT_SLOTS.length));
        int currentPage = Math.max(0, Math.min(session.fishPage(), totalPages - 1));
        int totalRarityPages = Math.max(1, (int) Math.ceil(rarities.size() / (double) RARITY_SLOTS.length));
        int rarityPage = Math.max(0, Math.min(session.rarityPage(), totalRarityPages - 1));

        String rarityNameMini = formatRarityDisplayMini(selectedRarity);
        Component title = mm("<dark_gray>Bộ Sưu Tập Cá • " + rarityNameMini
            + " <gray>(" + (currentPage + 1) + "/" + totalPages + ")</gray></dark_gray>");

        IndexHolder holder = new IndexHolder(player.getUniqueId(), session.sessionId(), title);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title);
        holder.bind(inventory);

        // 1. Base filler panes
        ItemStack filler = createPane(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        // 2. Empty content slot placeholders
        ItemStack emptyContent = createPane(Material.STONE_BUTTON, "<dark_gray>Ô trống</dark_gray>", List.of());
        for (int slot : CONTENT_SLOTS) {
            inventory.setItem(slot, emptyContent);
        }

        // 3. Default unselected indicator row
        ItemStack inactiveIndicator = createPane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : INDICATOR_SLOTS) {
            inventory.setItem(slot, inactiveIndicator);
        }

        // 4. Rarity Tabs (Row 1: slots 1..7) & Active Indicator Panes (Row 2: slots 10..16)
        double totalRarityWeight = getTotalRarityWeight(rarities);
        int rarityStart = rarityPage * RARITY_SLOTS.length;
        for (int i = 0; i < RARITY_SLOTS.length; i++) {
            int index = rarityStart + i;
            if (index >= rarities.size()) {
                break;
            }
            Rarity rarity = rarities.get(index);
            boolean isSelected = rarity.getId().equalsIgnoreCase(selectedRarity.getId());
            int tabSlot = RARITY_SLOTS[i];
            int indicatorSlot = INDICATOR_SLOTS[i];

            inventory.setItem(tabSlot, buildRarityTabItem(rarity, isSelected, session.snapshot(), totalRarityWeight));
            holder.putAction(tabSlot, "RARITY:" + rarity.getId());

            if (isSelected) {
                inventory.setItem(indicatorSlot, createPane(
                    Material.LIME_STAINED_GLASS_PANE,
                    "<green><bold>▲ Đang chọn: " + formatRarityDisplayMini(rarity) + "</bold></green>",
                    List.of(
                        " ",
                        "<dark_gray> ○ <white>Đang hiển thị danh mục này"
                    )
                ));
            }
        }

        if (rarityPage > 0) {
            inventory.setItem(SLOT_RARITY_PREV, createPane(
                Material.ARROW,
                "<yellow>Nhóm độ hiếm trước</yellow>",
                List.of(
                    " ",
                    "<#C797FF>Nhấn <white>chuột",
                    "<white>để xem nhóm độ hiếm trước!"
                )
            ));
            holder.putAction(SLOT_RARITY_PREV, "RARITY_PAGE_PREV");
        }
        if (rarityPage + 1 < totalRarityPages) {
            inventory.setItem(SLOT_RARITY_NEXT, createPane(
                Material.ARROW,
                "<yellow>Nhóm độ hiếm tiếp theo</yellow>",
                List.of(
                    " ",
                    "<#C797FF>Nhấn <white>chuột",
                    "<white>để xem nhóm độ hiếm tiếp theo!"
                )
            ));
            holder.putAction(SLOT_RARITY_NEXT, "RARITY_PAGE_NEXT");
        }

        // 5. Rows 3-5: 21 Fish Collection Entries
        double totalFishWeightInRarity = getTotalFishWeight(allRarityFish);
        int startIndex = currentPage * CONTENT_SLOTS.length;
        int shownCount = 0;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int fishIndex = startIndex + i;
            if (fishIndex >= filteredFish.size()) {
                break;
            }
            Fish fish = filteredFish.get(fishIndex);
            int slot = CONTENT_SLOTS[i];
            JournalDiscoverySnapshot.Key key = new JournalDiscoverySnapshot.Key(selectedRarity.getId(), fish.getName());
            JournalDiscoverySnapshot.Entry entry = session.snapshot().entries().get(key);
            boolean unlocked = entry != null && entry.caught();

            long oneInOdds = computeOneInOdds(
                selectedRarity.getWeight(),
                totalRarityWeight,
                rarities.size(),
                fish.getWeight(),
                totalFishWeightInRarity,
                allRarityFish.size()
            );

            if (unlocked) {
                inventory.setItem(slot, buildUnlockedFishItem(player, fish, selectedRarity, entry, oneInOdds));
                holder.putAction(slot, "FISH:" + fish.getName());
            } else {
                inventory.setItem(slot, buildLockedFishItem(selectedRarity, oneInOdds));
                holder.putAction(slot, "LOCKED:" + fish.getName());
            }
            shownCount++;
        }

        // 7. Bottom Row Navigation & Progress (Slots 45, 48, 49, 50, 53)
        if (currentPage > 0) {
            inventory.setItem(SLOT_PAGE_PREV, createPane(
                Material.ARROW,
                "<yellow>Trang trước</yellow>",
                List.of(
                    " ",
                    "<dark_gray> ○ <white>Chuyển về trang <yellow>" + currentPage + "</yellow>",
                    " ",
                    "<#C797FF>Nhấn <white>chuột",
                    "<white>để lùi trang!"
                )
            ));
            holder.putAction(SLOT_PAGE_PREV, "PAGE_PREV");
        }
        if (currentPage + 1 < totalPages) {
            inventory.setItem(SLOT_PAGE_NEXT, createPane(
                Material.ARROW,
                "<yellow>Trang tiếp theo</yellow>",
                List.of(
                    " ",
                    "<dark_gray> ○ <white>Chuyển sang trang <yellow>" + (currentPage + 2) + "</yellow>",
                    " ",
                    "<#C797FF>Nhấn <white>chuột",
                    "<white>để sang trang tiếp!"
                )
            ));
            holder.putAction(SLOT_PAGE_NEXT, "PAGE_NEXT");
        }

        inventory.setItem(SLOT_HUB_INDEX, createPane(
            Material.COMPASS,
            "<aqua><bold>Về Trung Tâm /index</bold></aqua>",
            List.of(
                " ",
                "<dark_gray> ○ <white>Điểm đến: <yellow>Trung Tâm Bộ Sưu Tập</yellow>",
                "<dark_gray> ○ <white>Lệnh nhanh: <aqua>/index</aqua>",
                " ",
                "<#A8D8EA>Nhấn để quay về",
                "<white>sảnh chính Bộ Sưu Tập!"
            )
        ));
        holder.putAction(SLOT_HUB_INDEX, "OPEN_INDEX_HUB");

        inventory.setItem(SLOT_PROGRESS, buildOverallProgressItem(rarities, selectedRarity, session.snapshot()));
        inventory.setItem(SLOT_PAGE_INFO, createPane(
            Material.PAPER,
            "<white>Trang " + (currentPage + 1) + "/" + totalPages + "</white>",
            List.of(
                " ",
                "<dark_gray> ○ <white>Đang xem: " + rarityNameMini,
                "<dark_gray> ○ <white>Hiển thị: <yellow>" + shownCount + "/" + filteredFish.size() + "</yellow> loại cá",
                " ",
                "<#A8D8EA>Dùng mũi tên hai bên",
                "<white>để chuyển trang!"
            )
        ));

        inventory.setItem(SLOT_CLOSE, createPane(
            Material.BARRIER,
            "<red>Quay Lại / Đóng</red>",
            List.of(
                " ",
                "<dark_gray> ○ <white>Thao tác:",
                "  <dark_gray> -<aqua> Chuột trái</aqua> <dark_gray>(<white>Về Trung Tâm /index<dark_gray>)",
                "  <dark_gray> -<yellow> Shift + Trái</yellow> <dark_gray>(<white>Về Menu Cá /emf<dark_gray>)",
                "  <dark_gray> -<red> Chuột phải</red> <dark_gray>(<white>Đóng cửa sổ<dark_gray>)",
                " ",
                "<#C797FF>Nhấn <white>chuột",
                "<white>để thoát Bộ Sưu Tập!"
            )
        ));
        holder.putAction(SLOT_CLOSE, "CLOSE");

        // Update existing inventory in-place if the title is unchanged so the player's cursor never jumps
        if (!forceNewInventory
                && player.getOpenInventory().getTopInventory().getHolder() instanceof IndexHolder existingHolder
                && existingHolder.sessionId() == session.sessionId()
                && existingHolder.title().equals(title)) {
            existingHolder.getInventory().setContents(inventory.getContents());
            existingHolder.replaceActionsFrom(holder);
        } else {
            player.openInventory(inventory);
        }
    }

    @NotNull
    private List<Fish> filterFishList(
        @NotNull Rarity rarity,
        @NotNull List<Fish> sortedFish,
        @NotNull JournalDiscoverySnapshot snapshot,
        @NotNull FilterMode filterMode
    ) {
        if (filterMode == FilterMode.ALL) {
            return sortedFish;
        }
        List<Fish> result = new ArrayList<>(sortedFish.size());
        for (Fish fish : sortedFish) {
            boolean unlocked = snapshot.caught(rarity.getId(), fish.getName());
            if (filterMode == FilterMode.UNLOCKED && unlocked) {
                result.add(fish);
            } else if (filterMode == FilterMode.LOCKED && !unlocked) {
                result.add(fish);
            }
        }
        return result;
    }

    @NotNull
    private ItemStack buildRarityTabItem(
        @NotNull Rarity rarity,
        boolean selected,
        @NotNull JournalDiscoverySnapshot snapshot,
        double totalRarityWeight
    ) {
        ItemStack base = safeRarityIcon(rarity);
        List<Fish> fishList = getEligibleFish(rarity);
        int total = fishList.size();
        int discovered = countDiscoveredInRarity(rarity, fishList, snapshot);
        double percent = total > 0 ? (discovered * 100.0D) / total : 0.0D;
        long rarityOdds = computeRarityOneInOdds(rarity.getWeight(), totalRarityWeight);

        String displayMini = formatRarityDisplayMini(rarity);
        String title = selected ? "<green>▸ </green>" + displayMini : displayMini;
        List<String> lore = List.of(
            " ",
            "<dark_gray> ○ <white>Tiến độ: <yellow>" + discovered + "/" + total + " (" + PERCENT_FORMAT.format(percent) + "%)</yellow>",
            "<dark_gray> ○ <white>Thanh tiến độ: " + buildProgressBarMini(discovered, total, 15),
            "<dark_gray> ○ <white>Số loại cá: <aqua>" + total + "</aqua>",
            "<dark_gray> ○ <white>Tỉ lệ nhóm: <gold>1 in " + formatGroupedInt(rarityOdds) + "</gold>",
            " ",
            selected ? "<#A8D8EA>Đang xem danh mục này" : "<#C797FF>Nhấn <white>chuột trái",
            selected ? "<white>trong Bộ Sưu Tập!" : "<white>để xem danh sách cá!"
        );

        applyDisplayAndLore(base, title, lore, selected);
        return base;
    }

    @NotNull
    private ItemStack buildOverallProgressItem(
        @NotNull List<Rarity> rarities,
        @NotNull Rarity selectedRarity,
        @NotNull JournalDiscoverySnapshot snapshot
    ) {
        int totalFish = 0;
        int totalDiscovered = 0;
        for (Rarity rarity : rarities) {
            List<Fish> list = getEligibleFish(rarity);
            totalFish += list.size();
            totalDiscovered += countDiscoveredInRarity(rarity, list, snapshot);
        }
        double totalPercent = totalFish > 0 ? (totalDiscovered * 100.0D) / totalFish : 0.0D;

        List<Fish> currentList = getEligibleFish(selectedRarity);
        int currentTotal = currentList.size();
        int currentDiscovered = countDiscoveredInRarity(selectedRarity, currentList, snapshot);
        double currentPercent = currentTotal > 0 ? (currentDiscovered * 100.0D) / currentTotal : 0.0D;

        String status = (totalFish > 0 && totalDiscovered >= totalFish)
            ? "<green><bold>INDEX COMPLETE</bold></green>"
            : "<yellow>ĐANG SƯU TẦM</yellow>";

        return createPane(
            Material.NETHER_STAR,
            "<aqua><bold>Tiến Độ Bộ Sưu Tập Cá</bold></aqua>",
            List.of(
                " ",
                "<dark_gray> ○ <white>Tổng cá đã mở: <green>" + totalDiscovered + "/" + totalFish + "</green> <gray>(" + PERCENT_FORMAT.format(totalPercent) + "%)</gray>",
                "<dark_gray> ○ <white>Tiến độ tổng: " + buildProgressBarMini(totalDiscovered, totalFish, 20),
                "<dark_gray> ○ <white>Nhóm hiện tại: <yellow>" + currentDiscovered + "/" + currentTotal + "</yellow> <gray>(" + PERCENT_FORMAT.format(currentPercent) + "%)</gray>",
                "<dark_gray> ○ <white>Số bậc độ hiếm: <aqua>" + rarities.size() + "</aqua>",
                "<dark_gray> ○ <white>Trạng thái: " + status,
                " ",
                "<#A8D8EA>Câu đủ tất cả loại cá",
                "<white>để hoàn thành Bộ Sưu Tập!"
            )
        );
    }

    @NotNull
    private static String buildProgressBarMini(int current, int total, int segments) {
        if (total <= 0) {
            return "<dark_gray>" + "▏".repeat(segments) + "</dark_gray>";
        }
        int filled = Math.max(0, Math.min(segments, (int) Math.round((current / (double) total) * segments)));
        return "<green>" + "▏".repeat(filled) + "</green><dark_gray>" + "▏".repeat(segments - filled) + "</dark_gray>";
    }

    private static int[] getThresholdsForRarity(@NotNull Rarity rarity) {
        String id = rarity.getId().trim().toLowerCase(Locale.ROOT);
        if (id.contains("anomaly") || id.contains("divine") || id.contains("god") || id.contains("di_biet")
            || id.contains("mythic") || id.contains("than_thoai")
            || id.contains("legend") || id.contains("huyen_thoai")) {
            return THRESHOLDS_HIGH_TIER;
        }
        if (id.contains("epic") || id.contains("su_thi")) {
            return THRESHOLDS_EPIC;
        }
        return THRESHOLDS_COMMON_RARE;
    }

    private static int computeFishLevel(int copies, int[] thresholds) {
        int level = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (copies >= thresholds[i]) {
                level = i + 1;
            } else {
                break;
            }
        }
        return level;
    }

    @NotNull
    private static String formatFishLevelProgress(int copies, int[] thresholds) {
        int level = computeFishLevel(copies, thresholds);
        if (level <= 0) {
            return "<gray>Chưa mở khóa</gray> <dark_gray>(0/" + thresholds[0] + ")</dark_gray>";
        }
        if (level >= thresholds.length) {
            return "<gold><bold>Cấp V</bold></gold> <dark_gray>(</dark_gray><green>TỐI ĐA • " + copies + " lần</green><dark_gray>)</dark_gray>";
        }
        int nextReq = thresholds[level];
        return "<aqua>Cấp " + ROMAN_LEVELS[level] + "</aqua> <dark_gray>(</dark_gray><yellow>"
            + copies + "/" + nextReq + "</yellow> <gray>lên Cấp " + ROMAN_LEVELS[level + 1] + "</gray><dark_gray>)</dark_gray>";
    }

    @NotNull
    private ItemStack buildLockedFishItem(@NotNull Rarity rarity, long oneInOdds) {
        int[] thresholds = getThresholdsForRarity(rarity);
        ItemStack stack = createLockedCodexBaseItem();
        applyDisplayAndLore(
            stack,
            "<gray><bold>???</bold></gray>",
            List.of(
                " ",
                "<dark_gray> ○ <white>Độ hiếm: " + formatRarityDisplayMini(rarity),
                "<dark_gray> ○ <white>Cấp độ: " + formatFishLevelProgress(0, thresholds),
                "<dark_gray> ○ <white>Tỉ lệ câu: <aqua>1 in " + formatGroupedInt(oneInOdds) + "</aqua>",
                "<dark_gray> ○ <white>Trạng thái: <gray>Chưa mở khóa</gray>",
                " ",
                "<dark_gray> ○ <white>Phần thưởng cấp độ:",
                "  <dark_gray> -<yellow> Cấp I -> IV:</yellow> <dark_gray>Thưởng Xu & EXP Câu Cá",
                "  <dark_gray> -<gold> Cấp V (" + thresholds[4] + " lần):</gold> <green>+Điểm Tiềm Năng Kỹ Năng</green>",
                " ",
                "<#C797FF>Quăng cần <white>câu cá",
                "<white>để mở khóa loài cá này!"
            ),
            false
        );
        return stack;
    }

    @NotNull
    private ItemStack createLockedCodexBaseItem() {
        try {
            Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object builder = nexoItems.getMethod("itemFromId", String.class).invoke(null, "codex_item_no");
            if (builder != null) {
                Object built = builder.getClass().getMethod("build").invoke(builder);
                if (built instanceof ItemStack nexoStack && !nexoStack.getType().isAir()) {
                    return nexoStack.clone();
                }
            }
        } catch (Throwable ignored) {
        }

        ItemStack fallback = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = fallback.getItemMeta();
        if (meta != null) {
            try {
                meta.setCustomModelData(457);
            } catch (Throwable ignored) {
            }
            try {
                NamespacedKey modelKey = NamespacedKey.fromString("thanhviet:items/item/codex_item_no");
                if (modelKey != null) {
                    ItemMeta.class.getMethod("setItemModel", NamespacedKey.class).invoke(meta, modelKey);
                }
            } catch (Throwable ignored) {
            }
            fallback.setItemMeta(meta);
        }
        return fallback;
    }

    @NotNull
    private ItemStack buildUnlockedFishItem(
        @NotNull Player player,
        @NotNull Fish fish,
        @NotNull Rarity rarity,
        @NotNull JournalDiscoverySnapshot.Entry entry,
        long oneInOdds
    ) {
        ItemStack icon = createFishPreviewItem(player, fish);
        int copies = Math.max(1, entry.quantity());
        int[] thresholds = getThresholdsForRarity(rarity);
        int level = computeFishLevel(copies, thresholds);
        String levelBadge = level >= 5 ? " <gold>[Cấp V]</gold>" : " <aqua>[Cấp " + ROMAN_LEVELS[Math.max(1, level)] + "]</aqua>";
        String fishTitle = formatFishDisplayMini(fish, rarity) + levelBadge;
        String discovererText = resolveDiscovererDisplay(entry.discoverer(), player.getName());

        List<String> lore = List.of(
            " ",
            "<dark_gray> ○ <white>Độ hiếm: " + formatRarityDisplayMini(rarity),
            "<dark_gray> ○ <white>Cấp độ: " + formatFishLevelProgress(copies, thresholds),
            "<dark_gray> ○ <white>Đã câu: <yellow>" + formatGroupedInt(copies) + "</yellow> lần <dark_gray>(Kỷ lục: <green>"
                + LENGTH_FORMAT.format(Math.max(0.0F, entry.longest())) + "cm</green>)</dark_gray>",
            "<dark_gray> ○ <white>Phát hiện đầu: <gold>" + discovererText + "</gold>",
            " ",
            "<dark_gray> ○ <white>Phần thưởng cấp độ:",
            "  <dark_gray> -<yellow> Cấp I -> IV:</yellow> <dark_gray>Thưởng Xu & EXP Câu Cá",
            "  <dark_gray> -<gold> Cấp V (" + thresholds[4] + " lần):</gold> <green>+Điểm Tiềm Năng Kỹ Năng</green>",
            " ",
            "<#A8D8EA>Nhấn để xem hồ sơ chi tiết",
            "<white>hoặc câu thêm để lên Cấp V!"
        );

        applyDisplayAndLore(icon, fishTitle, lore, level >= 5);
        return icon;
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof IndexHolder holder)) {
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

        SessionState session = sessions.get(player.getUniqueId());
        if (session == null || session.sessionId() != holder.sessionId()) {
            return;
        }

        String action = holder.getAction(event.getRawSlot());
        if (action == null) {
            return;
        }

        List<Rarity> rarities = getEligibleRarities();
        if (rarities.isEmpty()) {
            player.closeInventory();
            return;
        }

        if (action.startsWith("RARITY:")) {
            String rarityId = action.substring("RARITY:".length());
            Rarity target = FishManager.getInstance().getRarity(rarityId);
            if (target != null) {
                int rIdx = indexOfRarity(rarities, target);
                SessionState next = session.withRarity(target.getId(), rIdx / RARITY_SLOTS.length);
                sessions.put(player.getUniqueId(), next);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
                renderInventory(player, next, rarities, false);
                loadDiscoveryAsync(player, next);
            }
            return;
        }

        switch (action) {
            case "RARITY_PAGE_PREV" -> {
                SessionState next = session.withRarityPage(Math.max(0, session.rarityPage() - 1));
                sessions.put(player.getUniqueId(), next);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.0F);
                renderInventory(player, next, rarities, false);
            }
            case "RARITY_PAGE_NEXT" -> {
                int maxRarityPages = Math.max(1, (int) Math.ceil(rarities.size() / (double) RARITY_SLOTS.length));
                SessionState next = session.withRarityPage(Math.min(maxRarityPages - 1, session.rarityPage() + 1));
                sessions.put(player.getUniqueId(), next);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.0F);
                renderInventory(player, next, rarities, false);
            }
            case "PAGE_PREV" -> {
                SessionState next = session.withFishPage(Math.max(0, session.fishPage() - 1));
                sessions.put(player.getUniqueId(), next);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.0F);
                renderInventory(player, next, rarities, false);
            }
            case "PAGE_NEXT" -> {
                Rarity currentRarity = resolveSelectedRarity(rarities, FishManager.getInstance().getRarity(session.selectedRarityId()));
                List<Fish> filtered = filterFishList(currentRarity, getSortedFish(currentRarity), session.snapshot(), session.filterMode());
                int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) CONTENT_SLOTS.length));
                SessionState next = session.withFishPage(Math.min(totalPages - 1, session.fishPage() + 1));
                sessions.put(player.getUniqueId(), next);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.15F);
                renderInventory(player, next, rarities, false);
            }
            case "BACK_TO_LIST" -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.75F, 1.05F);
                renderInventory(player, session, rarities, true);
            }
            case "OPEN_INDEX_HUB" -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.75F, 1.15F);
                sessions.remove(player.getUniqueId());
                player.closeInventory();
                player.performCommand("index");
            }
            case "CLOSE_SUB" -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 0.9F);
                sessions.remove(player.getUniqueId());
                player.closeInventory();
            }
            case "CLOSE" -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 0.9F);
                sessions.remove(player.getUniqueId());
                if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                    player.closeInventory();
                } else if (event.getClick() == ClickType.SHIFT_LEFT) {
                    new MainMenuGui(player).open();
                } else {
                    player.closeInventory();
                    player.performCommand("index");
                }
            }
            default -> {
                if (action.startsWith("FISH:")) {
                    String fishName = action.substring("FISH:".length());
                    Rarity currentRarity = resolveSelectedRarity(rarities, FishManager.getInstance().getRarity(session.selectedRarityId()));
                    Fish fish = currentRarity.getFish(fishName);
                    JournalDiscoverySnapshot.Key key = new JournalDiscoverySnapshot.Key(currentRarity.getId(), fishName);
                    JournalDiscoverySnapshot.Entry entry = session.snapshot().entries().get(key);
                    if (fish != null && entry != null && entry.caught()) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
                        openFishProgressionSubGui(player, session, currentRarity, fish, entry);
                    }
                } else if (action.startsWith("LOCKED:")) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.8F);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof IndexHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        // Keep session when re-rendering; clean up only on quit or explicit close
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void openFishProgressionSubGui(
        @NotNull Player player,
        @NotNull SessionState session,
        @NotNull Rarity rarity,
        @NotNull Fish fish,
        @NotNull JournalDiscoverySnapshot.Entry entry
    ) {
        String fishTitleMini = formatFishDisplayMini(fish, rarity);
        Component title = mm("<dark_gray>Lộ Trình Cấp Độ • </dark_gray>" + fishTitleMini);

        IndexHolder holder = new IndexHolder(player.getUniqueId(), session.sessionId(), title);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title);
        holder.bind(inventory);

        ItemStack filler = createPane(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        int copies = Math.max(1, entry.quantity());
        int[] thresholds = getThresholdsForRarity(rarity);
        int level = computeFishLevel(copies, thresholds);
        int maxReq = thresholds[thresholds.length - 1];
        String levelBadge = level >= 5 ? " <gold>[Cấp V]</gold>" : " <aqua>[Cấp " + ROMAN_LEVELS[Math.max(1, level)] + "]</aqua>";
        String discovererText = resolveDiscovererDisplay(entry.discoverer(), player.getName());

        // Slot 4: Fish Preview Item
        ItemStack fishPreview = createFishPreviewItem(player, fish);
        List<String> previewLore = List.of(
            " ",
            "<dark_gray> ○ <white>Độ hiếm: " + formatRarityDisplayMini(rarity),
            "<dark_gray> ○ <white>Cấp độ hiện tại: " + formatFishLevelProgress(copies, thresholds),
            "<dark_gray> ○ <white>Tổng đã câu: <yellow>" + formatGroupedInt(copies) + "</yellow> lần",
            "<dark_gray> ○ <white>Thanh cấp: " + buildProgressBarMini(Math.min(copies, maxReq), maxReq, 20),
            " ",
            "<dark_gray> ○ <white>Kỷ lục kích thước:",
            "  <dark_gray> -<aqua> Cá nhân:</aqua> <green>" + LENGTH_FORMAT.format(Math.max(0.0F, entry.longest()))
                + "cm</green> <dark_gray>(Ngắn nhất: <white>" + LENGTH_FORMAT.format(Math.max(0.0F, entry.shortest())) + "cm</white>)",
            "  <dark_gray> -<light_purple> Máy chủ:</light_purple> <green>" + LENGTH_FORMAT.format(Math.max(0.0F, entry.serverLongest()))
                + "cm</green> <dark_gray>(Ngắn nhất: <white>" + LENGTH_FORMAT.format(Math.max(0.0F, entry.serverShortest())) + "cm</white>)",
            "  <dark_gray> -<gold> Phát hiện đầu:</gold> <white>" + discovererText + "</white>",
            " ",
            "<#A8D8EA>Câu thêm loài cá này",
            "<white>để mở khóa toàn bộ mốc Cấp I → V!"
        );
        applyDisplayAndLore(fishPreview, fishTitleMini + levelBadge, previewLore, level >= 5);
        inventory.setItem(SUB_SLOT_FISH_PREVIEW, fishPreview);

        // Slots 20..24 (Tier Glass Panes) & Slots 29..33 (Reward Preview Icons)
        for (int i = 0; i < SUB_TIER_PANE_SLOTS.length; i++) {
            int tier = i + 1;
            int req = thresholds[i];
            String roman = ROMAN_LEVELS[tier];
            double pct = req > 0 ? Math.min(100.0D, (copies * 100.0D) / req) : 100.0D;
            String bar20 = buildProgressBarMini(Math.min(copies, req), req, 20);

            Material paneMaterial;
            String paneName;
            String statusLine;
            String footerLine1;
            String footerLine2;
            if (level >= tier) {
                paneMaterial = Material.LIME_STAINED_GLASS_PANE;
                paneName = "<green><bold>✔ Cấp " + roman + " — Đã Đạt</bold></green>";
                statusLine = "<green>Đã hoàn thành</green>";
                footerLine1 = "<#A8D8EA>Mốc cấp độ này";
                footerLine2 = "<white>đã được mở khóa!";
            } else if (level + 1 == tier) {
                paneMaterial = Material.YELLOW_STAINED_GLASS_PANE;
                paneName = "<yellow><bold>⏳ Cấp " + roman + " — Đang Tiến Độ</bold></yellow>";
                statusLine = "<yellow>Cần thêm " + formatGroupedInt(Math.max(0, req - copies)) + " lần câu</yellow>";
                footerLine1 = "<#C797FF>Tiếp tục câu cá";
                footerLine2 = "<white>để đạt Cấp " + roman + "!";
            } else {
                paneMaterial = Material.RED_STAINED_GLASS_PANE;
                paneName = "<red><bold>🔒 Cấp " + roman + " — Chưa Mở Khóa</bold></red>";
                statusLine = "<red>Chưa mở khóa</red>";
                footerLine1 = "<#C797FF>Hoàn thành cấp trước";
                footerLine2 = "<white>để tiến tới Cấp " + roman + "!";
            }

            List<String> rewardLines = getTierRewardLoreLines(tier);
            List<String> paneLore = new ArrayList<>();
            paneLore.add(" ");
            paneLore.add("<dark_gray> ○ <white>Tiến độ: <yellow>" + formatGroupedInt(copies) + "/" + formatGroupedInt(req)
                + "</yellow> <dark_gray>(" + PERCENT_FORMAT.format(pct) + "%)");
            paneLore.add("<dark_gray> ○ <white>Thanh cấp: " + bar20);
            paneLore.add("<dark_gray> ○ <white>Trạng thái: " + statusLine);
            paneLore.add(" ");
            paneLore.add("<dark_gray> ○ <white>Phần thưởng:");
            paneLore.addAll(rewardLines);
            paneLore.add(" ");
            paneLore.add(footerLine1);
            paneLore.add(footerLine2);

            inventory.setItem(SUB_TIER_PANE_SLOTS[i], createPane(paneMaterial, paneName, paneLore));

            // Reward preview icon below each tier (Slots 29..33)
            Material rewardMat = SUB_TIER_REWARD_ICONS[i];
            String rewardTitle = level >= tier
                ? "<green><bold>Quà Cấp " + roman + " (Đã Nhận)</bold></green>"
                : "<aqua><bold>Quà Cấp " + roman + "</bold></aqua>";
            List<String> rewardIconLore = new ArrayList<>();
            rewardIconLore.add(" ");
            rewardIconLore.add("<dark_gray> ○ <white>Yêu cầu: <yellow>" + formatGroupedInt(req) + " lần câu</yellow>");
            rewardIconLore.add("<dark_gray> ○ <white>Trạng thái: " + statusLine);
            rewardIconLore.add(" ");
            rewardIconLore.add("<dark_gray> ○ <white>Chi tiết phần thưởng:");
            rewardIconLore.addAll(rewardLines);
            rewardIconLore.add(" ");
            rewardIconLore.add(footerLine1);
            rewardIconLore.add(footerLine2);

            ItemStack rewardStack = createPane(rewardMat, rewardTitle, rewardIconLore);
            if (level >= tier) {
                applyDisplayAndLore(rewardStack, rewardTitle, rewardIconLore, true);
            }
            inventory.setItem(SUB_TIER_REWARD_SLOTS[i], rewardStack);
        }

        // Slot 45: Back to Fish List
        inventory.setItem(SUB_SLOT_BACK_TO_LIST, createPane(
            Material.ARROW,
            "<yellow>← Quay Lại Danh Sách Cá</yellow>",
            List.of(
                " ",
                "<dark_gray> ○ <white>Danh mục: " + formatRarityDisplayMini(rarity),
                "<dark_gray> ○ <white>Trang đang xem: <yellow>" + (session.fishPage() + 1) + "</yellow>",
                " ",
                "<#A8D8EA>Nhấn để quay lại",
                "<white>danh sách cá đang xem!"
            )
        ));
        holder.putAction(SUB_SLOT_BACK_TO_LIST, "BACK_TO_LIST");

        // Slot 49: Back to /index Hub
        inventory.setItem(SUB_SLOT_HUB_INDEX, createPane(
            Material.COMPASS,
            "<aqua>Về Trung Tâm /index</aqua>",
            List.of(
                " ",
                "<dark_gray> ○ <white>Điểm đến: <yellow>Trung Tâm Bộ Sưu Tập</yellow>",
                "<dark_gray> ○ <white>Lệnh nhanh: <aqua>/index</aqua>",
                " ",
                "<#A8D8EA>Nhấn để quay về",
                "<white>sảnh chính Bộ Sưu Tập!"
            )
        ));
        holder.putAction(SUB_SLOT_HUB_INDEX, "OPEN_INDEX_HUB");

        // Slot 53: Close
        inventory.setItem(SUB_SLOT_CLOSE, createPane(
            Material.BARRIER,
            "<red>Đóng</red>",
            List.of(
                " ",
                "<dark_gray> ○ <white>Chức năng: <red>Đóng cửa sổ</red>",
                " ",
                "<#C797FF>Nhấn <white>chuột",
                "<white>để đóng giao diện!"
            )
        ));
        holder.putAction(SUB_SLOT_CLOSE, "CLOSE_SUB");

        player.openInventory(inventory);
    }

    @NotNull
    private static List<String> getTierRewardLoreLines(int tier) {
        return switch (tier) {
            case 1 -> List.of(
                "  <dark_gray> -<white> Mở khóa Sổ Cá + Nội tại +0.15% Tốc Câu + 150 EXP Câu Cá</white>",
                "  <dark_gray> -<aqua> Mở khóa Sổ Cá</aqua>",
                "  <dark_gray> -<green> Nội tại +0.15% Tốc Câu</green>",
                "  <dark_gray> -<yellow> +150 EXP Câu Cá</yellow>"
            );
            case 2 -> List.of(
                "  <dark_gray> -<white> Nội tại +0.30% Tốc Câu + 350 EXP Câu Cá + 1 Điểm Kỹ Năng</white>",
                "  <dark_gray> -<green> Nội tại +0.30% Tốc Câu</green>",
                "  <dark_gray> -<yellow> +350 EXP Câu Cá</yellow>",
                "  <dark_gray> -<gold> +1 Điểm Kỹ Năng</gold>"
            );
            case 3 -> List.of(
                "  <dark_gray> -<white> Nội tại +0.50% Tốc Câu + 800 EXP Câu Cá + 2 Điểm Kỹ Năng + x1 Ngọc Nâng Cấp</white>",
                "  <dark_gray> -<green> Nội tại +0.50% Tốc Câu</green>",
                "  <dark_gray> -<yellow> +800 EXP Câu Cá</yellow>",
                "  <dark_gray> -<gold> +2 Điểm Kỹ Năng</gold>",
                "  <dark_gray> -<light_purple> x1 Ngọc Nâng Cấp</light_purple>"
            );
            case 4 -> List.of(
                "  <dark_gray> -<white> Nội tại +0.80% Tốc Câu + 1,800 EXP Câu Cá + 3 Điểm Kỹ Năng + x2 Ngọc Nâng Cấp</white>",
                "  <dark_gray> -<green> Nội tại +0.80% Tốc Câu</green>",
                "  <dark_gray> -<yellow> +1,800 EXP Câu Cá</yellow>",
                "  <dark_gray> -<gold> +3 Điểm Kỹ Năng</gold>",
                "  <dark_gray> -<light_purple> x2 Ngọc Nâng Cấp</light_purple>"
            );
            default -> List.of(
                "  <dark_gray> -<white> Nội tại +1.25% Tốc Câu + 4,000 EXP Câu Cá + 5 Điểm Kỹ Năng + x3 Ngọc Nâng Cấp ★</white>",
                "  <dark_gray> -<green> Nội tại +1.25% Tốc Câu</green>",
                "  <dark_gray> -<yellow> +4,000 EXP Câu Cá</yellow>",
                "  <dark_gray> -<gold> +5 Điểm Kỹ Năng</gold>",
                "  <dark_gray> -<light_purple> x3 Ngọc Nâng Cấp ★</light_purple>"
            );
        };
    }

    @NotNull
    private List<Rarity> getEligibleRarities() {
        List<Rarity> list = new ArrayList<>();
        for (Rarity rarity : FishManager.getInstance().getRarityMap().values()) {
            if (rarity == null || !rarity.getShowInJournal()) {
                continue;
            }
            if (!getEligibleFish(rarity).isEmpty()) {
                list.add(rarity);
            }
        }
        list.sort(Comparator.comparingInt(Rarity::getIndex).thenComparing(Rarity::getId, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @NotNull
    private List<Fish> getEligibleFish(@NotNull Rarity rarity) {
        List<Fish> list = new ArrayList<>();
        for (Fish fish : rarity.getOriginalFishList()) {
            if (fish != null && fish.getShowInJournal()) {
                list.add(fish);
            }
        }
        return list;
    }

    @NotNull
    private List<Fish> getSortedFish(@NotNull Rarity rarity) {
        List<Fish> list = getEligibleFish(rarity);
        // Sort from lowest "1 in X" (highest weight) to highest "1 in X" (lowest weight), matching LeDatRNG Index
        list.sort(Comparator.comparingDouble((Fish f) -> f.getWeight() > 0.0D ? f.getWeight() : 1.0D)
            .reversed()
            .thenComparing(Fish::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private int countDiscoveredInRarity(
        @NotNull Rarity rarity,
        @NotNull List<Fish> fishList,
        @NotNull JournalDiscoverySnapshot snapshot
    ) {
        int count = 0;
        for (Fish fish : fishList) {
            if (snapshot.caught(rarity.getId(), fish.getName())) {
                count++;
            }
        }
        return count;
    }

    @NotNull
    private Rarity resolveSelectedRarity(@NotNull List<Rarity> rarities, @Nullable Rarity requested) {
        if (requested != null) {
            for (Rarity rarity : rarities) {
                if (rarity.getId().equalsIgnoreCase(requested.getId())) {
                    return rarity;
                }
            }
        }
        return rarities.get(0);
    }

    private int indexOfRarity(@NotNull List<Rarity> rarities, @NotNull Rarity target) {
        for (int i = 0; i < rarities.size(); i++) {
            if (rarities.get(i).getId().equalsIgnoreCase(target.getId())) {
                return i;
            }
        }
        return 0;
    }

    @NotNull
    private ItemStack safeRarityIcon(@NotNull Rarity rarity) {
        try {
            ItemStack item = rarity.getJournalItem();
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        } catch (RuntimeException ignored) {
        }
        return new ItemStack(Material.COD);
    }

    @NotNull
    private ItemStack createFishPreviewItem(@NotNull Player player, @NotNull Fish fish) {
        try {
            ItemStack item = fish.getFactory().createItem(player.getUniqueId());
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        } catch (RuntimeException ignored) {
        }
        return new ItemStack(Material.COD);
    }

    @NotNull
    private ItemStack createPane(@NotNull Material material, @NotNull String nameMini, @NotNull List<String> loreMini) {
        ItemStack stack = new ItemStack(material);
        applyDisplayAndLore(stack, nameMini, loreMini, false);
        return stack;
    }

    private void applyDisplayAndLore(
        @NotNull ItemStack stack,
        @NotNull String nameMini,
        @NotNull List<String> loreMini,
        boolean glint
    ) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
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
        FishUtils.disableGlintOverride(meta);
        stack.setItemMeta(meta);
    }

    private double getTotalRarityWeight(@NotNull List<Rarity> rarities) {
        double sum = 0.0D;
        for (Rarity rarity : rarities) {
            sum += Math.max(0.0D, rarity.getWeight());
        }
        return sum;
    }

    private double getTotalFishWeight(@NotNull List<Fish> fishList) {
        double sum = 0.0D;
        for (Fish fish : fishList) {
            sum += Math.max(0.0D, fish.getWeight());
        }
        return sum;
    }

    private long computeRarityOneInOdds(double rarityWeight, double totalRarityWeight) {
        if (rarityWeight <= 0.0D || totalRarityWeight <= 0.0D) {
            return 1L;
        }
        return Math.max(1L, Math.round(totalRarityWeight / rarityWeight));
    }

    private long computeOneInOdds(
        double rarityWeight,
        double totalRarityWeight,
        int rarityCount,
        double fishWeight,
        double totalFishWeight,
        int fishCount
    ) {
        double rProb = (totalRarityWeight > 0.0D && rarityWeight > 0.0D)
            ? rarityWeight / totalRarityWeight
            : 1.0D / Math.max(1, rarityCount);
        double fProb = (totalFishWeight > 0.0D && fishWeight > 0.0D)
            ? fishWeight / totalFishWeight
            : 1.0D / Math.max(1, fishCount);
        double combined = rProb * fProb;
        if (combined <= 0.0D) {
            return 1L;
        }
        return Math.max(1L, Math.round(1.0D / combined));
    }

    @NotNull
    private String resolveDiscovererDisplay(@Nullable String rawDiscoverer, @NotNull String fallbackName) {
        if (rawDiscoverer == null || rawDiscoverer.isBlank()) {
            return fallbackName;
        }
        try {
            UUID uuid = UUID.fromString(rawDiscoverer.trim());
            String name = FishUtils.getPlayerName(uuid);
            return (name != null && !name.isBlank()) ? name : fallbackName;
        } catch (IllegalArgumentException ignored) {
            return rawDiscoverer;
        }
    }

    @NotNull
    private String formatRarityDisplayMini(@NotNull Rarity rarity) {
        return MINI_MESSAGE.serialize(rarity.getDisplayName().getComponentMessage());
    }

    @NotNull
    private String formatFishDisplayMini(@NotNull Fish fish, @NotNull Rarity rarity) {
        return MINI_MESSAGE.serialize(fish.getDisplayName().getComponentMessage());
    }

    @NotNull
    private static String formatGroupedInt(long value) {
        return String.format(Locale.US, "%,d", Math.max(0L, value));
    }

    @NotNull
    private static Component mm(@NotNull String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    private record SessionState(
        int sessionId,
        @NotNull String selectedRarityId,
        int fishPage,
        int rarityPage,
        @NotNull FilterMode filterMode,
        @NotNull JournalDiscoverySnapshot snapshot
    ) {
        SessionState withSnapshot(@NotNull JournalDiscoverySnapshot updatedSnapshot) {
            return new SessionState(sessionId, selectedRarityId, fishPage, rarityPage, filterMode, updatedSnapshot);
        }

        SessionState withRarity(@NotNull String nextRarityId, int nextRarityPage) {
            return new SessionState(sessionId, nextRarityId, 0, nextRarityPage, filterMode, snapshot);
        }

        SessionState withFishPage(int nextFishPage) {
            return new SessionState(sessionId, selectedRarityId, nextFishPage, rarityPage, filterMode, snapshot);
        }

        SessionState withRarityPage(int nextRarityPage) {
            return new SessionState(sessionId, selectedRarityId, fishPage, nextRarityPage, filterMode, snapshot);
        }

        SessionState withFilterMode(@NotNull FilterMode nextFilterMode) {
            return new SessionState(sessionId, selectedRarityId, 0, rarityPage, nextFilterMode, snapshot);
        }
    }

    private static final class IndexHolder implements InventoryHolder {
        private final UUID playerId;
        private final int sessionId;
        private final Component title;
        private final Map<Integer, String> slotActions = new HashMap<>();
        private Inventory inventory;

        private IndexHolder(@NotNull UUID playerId, int sessionId, @NotNull Component title) {
            this.playerId = playerId;
            this.sessionId = sessionId;
            this.title = title;
        }

        void bind(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        int sessionId() {
            return sessionId;
        }

        @NotNull
        Component title() {
            return title;
        }

        void putAction(int slot, @NotNull String action) {
            slotActions.put(slot, action);
        }

        void replaceActionsFrom(@NotNull IndexHolder other) {
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
