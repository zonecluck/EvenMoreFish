package com.oheers.fish.selling;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.api.EMFFishSellEvent;
import com.oheers.fish.api.economy.Economy;
import com.oheers.fish.api.reward.Reward;
import com.oheers.fish.autofish.AutoFishingService;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.fishing.FishingProcessor;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.messages.ConfigMessage;
import com.oheers.fish.messages.abstracted.EMFMessage;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection bridge connecting EvenMoreFish with TurtleStorageUltra and LeDatSkills.
 */
public final class TurtleStorageFishBridge {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<String, List<String>> FISH_KEY_CACHE = new ConcurrentHashMap<>();

    // Cached reflection handles for TurtleStorageUltra
    private static volatile boolean storageLookupAttempted = false;
    private static Method storageApiGetInstanceMethod;
    private static Method storageApiGetProviderMethod;
    private static Method storageApiGetUserMethod;
    private static Method userGetStorageMethod;
    private static Method itemUtilToMaterialKeyMethod;
    private static Method storageGetStatusMethod;
    private static Method storageIsMaxSpaceMethod;
    private static Method storageHasItemMethod;
    private static Method storageAddNewItemMethod;
    private static Method storageAddNewItemUnfilteredMethod;
    private static Method storageAddAndGetResultMethod;
    private static Method addResultGetAddedAmountMethod;
    private static Method storageAddMethod;
    private static Method storageGetQuantityMethod;
    private static Method storageSubtractMethod;
    private static Method storageDepositExternalMethod;

    // Cached reflection handles for LeDatSkills
    private static volatile boolean skillsLookupAttempted = false;
    private static Method leDatSkillsApiGetMethod;
    private static Method leDatSkillsAddExpMethod;
    private static Object fishingSkillTypeConstant;

    private TurtleStorageFishBridge() {
        throw new UnsupportedOperationException();
    }

    public record StoredFishEntry(
        @NotNull Fish fish,
        @NotNull Rarity rarity,
        @NotNull String storageKey,
        long quantity,
        double unitWorth
    ) {
        public double totalBaseWorth() {
            return unitWorth * quantity;
        }
    }

    public record SellSummary(
        long soldCount,
        double baseWorth,
        double bonusPercent,
        double finalWorth
    ) {
        public static final SellSummary EMPTY = new SellSummary(0L, 0.0D, 0.0D, 0.0D);
    }

    public static void clearCache() {
        FISH_KEY_CACHE.clear();
    }

    public static boolean isAvailable() {
        try {
            if (Bukkit.getServer() == null) {
                return false;
            }
            if (!Bukkit.getPluginManager().isPluginEnabled("TurtleStorage")
                && !Bukkit.getPluginManager().isPluginEnabled("TurtleStorageUltra")) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
        ensureStorageReflectionInitialized();
        if (storageApiGetInstanceMethod == null || storageApiGetUserMethod == null || itemUtilToMaterialKeyMethod == null) {
            return false;
        }
        try {
            Object api = storageApiGetInstanceMethod.invoke(null);
            if (api == null) {
                return false;
            }
            if (storageApiGetProviderMethod != null) {
                return storageApiGetProviderMethod.invoke(api) != null;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Checks whether the player's TurtleStorageUltra is enabled and has free space for caught fish.
     */
    public static boolean canAcceptCaughtFish(@NotNull Player player) {
        if (!isAvailable()) {
            return false;
        }
        Object storage = resolvePlayerStorage(player.getUniqueId());
        if (storage == null) {
            return false;
        }
        try {
            if (storageGetStatusMethod != null) {
                Object statusObj = storageGetStatusMethod.invoke(storage);
                if (statusObj instanceof Boolean status && !status) {
                    return false;
                }
            }
            if (storageIsMaxSpaceMethod != null) {
                Object maxObj = storageIsMaxSpaceMethod.invoke(storage);
                if (maxObj instanceof Boolean isMax && isMax) {
                    return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Deposits caught fish into TurtleStorageUltra first before giving any remainder to inventory.
     *
     * @return actual amount deposited into TurtleStorageUltra (0..amount)
     */
    public static int depositCaughtFish(
        @NotNull Player player,
        @Nullable Fish fish,
        @Nullable ItemStack fishItem,
        int amount
    ) {
        if (amount <= 0 || fishItem == null || fishItem.isEmpty() || !isAvailable()) {
            return 0;
        }
        Object storage = resolvePlayerStorage(player.getUniqueId());
        if (storage == null) {
            return 0;
        }
        try {
            if (storageGetStatusMethod != null) {
                Object statusObj = storageGetStatusMethod.invoke(storage);
                if (statusObj instanceof Boolean status && !status) {
                    return 0;
                }
            }

            // Try depositExternal(ItemStack, int) if available on Storage implementation
            if (storageDepositExternalMethod != null) {
                Object result = storageDepositExternalMethod.invoke(storage, fishItem, amount);
                if (result instanceof Number num) {
                    int deposited = Math.max(0, Math.min(amount, num.intValue()));
                    if (deposited > 0 && fish != null) {
                        String key = toMaterialKey(fishItem);
                        if (key != null) {
                            cacheFishKey(fish, key);
                        }
                    }
                    return deposited;
                }
            }

            String storageKey = toMaterialKey(fishItem);
            if ((storageKey == null || storageKey.isBlank() || "INVALID".equalsIgnoreCase(storageKey)) && fish != null) {
                List<String> candidates = resolveCandidateKeys(fish);
                if (!candidates.isEmpty()) {
                    storageKey = candidates.get(0);
                }
            }
            if (storageKey == null || storageKey.isBlank() || "INVALID".equalsIgnoreCase(storageKey)) {
                return 0;
            }

            if (fish != null) {
                cacheFishKey(fish, storageKey);
            }

            boolean hasItem = false;
            if (storageHasItemMethod != null) {
                Object hasObj = storageHasItemMethod.invoke(storage, storageKey);
                hasItem = hasObj instanceof Boolean b && b;
            }
            if (!hasItem) {
                if (storageAddNewItemMethod != null) {
                    storageAddNewItemMethod.invoke(storage, storageKey);
                }
                if (storageHasItemMethod != null) {
                    Object hasObj = storageHasItemMethod.invoke(storage, storageKey);
                    hasItem = hasObj instanceof Boolean b && b;
                }
                if (!hasItem && storageAddNewItemUnfilteredMethod != null && storageAddNewItemUnfilteredMethod.getDeclaringClass().isInstance(storage)) {
                    storageAddNewItemUnfilteredMethod.invoke(storage, storageKey);
                    if (storageHasItemMethod != null) {
                        Object hasObj = storageHasItemMethod.invoke(storage, storageKey);
                        hasItem = hasObj instanceof Boolean b && b;
                    }
                }
            }

            if (!hasItem) {
                return 0;
            }

            if (storageAddAndGetResultMethod != null && storageAddAndGetResultMethod.getDeclaringClass().isInstance(storage)) {
                Object addResult = storageAddAndGetResultMethod.invoke(storage, storageKey, (long) amount);
                if (addResult != null) {
                    if (addResultGetAddedAmountMethod == null) {
                        addResultGetAddedAmountMethod = addResult.getClass().getMethod("getAddedAmount");
                    }
                    Object addedObj = addResultGetAddedAmountMethod.invoke(addResult);
                    if (addedObj instanceof Number num) {
                        return (int) Math.max(0L, Math.min(amount, num.longValue()));
                    }
                }
            }

            if (storageAddMethod != null && storageGetQuantityMethod != null) {
                long before = getQuantity(storage, storageKey);
                storageAddMethod.invoke(storage, storageKey, (long) amount);
                long after = getQuantity(storage, storageKey);
                return (int) Math.max(0L, Math.min(amount, after - before));
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /**
     * Grants LeDatSkills FISHING experience scaled by fish rarity.
     */
    public static void grantFishingSkillXp(@NotNull Player player, @Nullable Fish fish) {
        try {
            if (fish == null || Bukkit.getServer() == null || !Bukkit.getPluginManager().isPluginEnabled("LeDatSkills")) {
                return;
            }
        } catch (Throwable ignored) {
            return;
        }
        ensureSkillsReflectionInitialized();
        if (leDatSkillsApiGetMethod == null || leDatSkillsAddExpMethod == null || fishingSkillTypeConstant == null) {
            return;
        }
        double xp = getSkillXpForRarity(fish.getRarity());
        if (xp <= 0.0D) {
            return;
        }
        try {
            Object api = leDatSkillsApiGetMethod.invoke(null);
            if (api != null) {
                leDatSkillsAddExpMethod.invoke(api, player.getUniqueId(), fishingSkillTypeConstant, xp);
            }
        } catch (Throwable ignored) {
        }
    }

    public static double getSkillXpForRarity(@Nullable Rarity rarity) {
        if (rarity == null) {
            return 15.0D;
        }
        String id = rarity.getId().trim().toLowerCase(Locale.ROOT);
        if (id.contains("anomaly") || id.contains("divine") || id.contains("god") || id.contains("di_biet")) {
            return 950.0D;
        }
        if (id.contains("mythic") || id.contains("than_thoai")) {
            return 420.0D;
        }
        if (id.contains("legend") || id.contains("huyen_thoai")) {
            return 180.0D;
        }
        if (id.contains("epic") || id.contains("su_thi")) {
            return 80.0D;
        }
        if (id.contains("rare") || id.contains("hiem")) {
            return 35.0D;
        }
        return 15.0D;
    }

    public static double resolveUnitWorth(@NotNull Fish fish) {
        double setWorth = fish.getSetWorth();
        if (setWorth > 0.0D) {
            return setWorth;
        }
        return WorthNBT.getValue(fish).orElse(25.0D);
    }

    /**
     * Scans all EvenMoreFish species and returns entries with quantity > 0 in the player's TurtleStorageUltra.
     */
    public static @NotNull List<StoredFishEntry> getStoredFish(@NotNull Player player) {
        if (!isAvailable()) {
            return Collections.emptyList();
        }
        Object storage = resolvePlayerStorage(player.getUniqueId());
        if (storage == null) {
            return Collections.emptyList();
        }

        TreeMap<String, Rarity> rarityMap = FishManager.getInstance().getRarityMap();
        if (rarityMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<StoredFishEntry> result = new ArrayList<>();
        Set<String> claimedStorageKeys = new HashSet<>();

        for (Rarity rarity : rarityMap.values()) {
            for (Fish fish : rarity.getOriginalFishList()) {
                List<String> candidateKeys = resolveCandidateKeys(fish);
                for (String key : candidateKeys) {
                    if (key == null || key.isBlank() || claimedStorageKeys.contains(key)) {
                        continue;
                    }
                    long qty = getQuantity(storage, key);
                    if (qty > 0L) {
                        claimedStorageKeys.add(key);
                        double unitWorth = resolveUnitWorth(fish);
                        result.add(new StoredFishEntry(fish, rarity, key, qty, unitWorth));
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Sells fish stored in TurtleStorageUltra matching optional rarity / fishName filters.
     */
    public static @NotNull SellSummary sellStoredFish(
        @NotNull Player player,
        @Nullable String rarityFilter,
        @Nullable String fishNameFilter,
        long maxAmountToSell,
        boolean sendFeedback
    ) {
        if (!Economy.getInstance().isEnabled() || !isAvailable() || maxAmountToSell <= 0L) {
            return SellSummary.EMPTY;
        }
        Object storage = resolvePlayerStorage(player.getUniqueId());
        if (storage == null || storageSubtractMethod == null) {
            return SellSummary.EMPTY;
        }

        List<StoredFishEntry> storedEntries = getStoredFish(player);
        if (storedEntries.isEmpty()) {
            return SellSummary.EMPTY;
        }

        long remainingAllowed = maxAmountToSell;
        List<SoldFish> soldFishList = new ArrayList<>();
        List<StoredSellAction> pendingActions = new ArrayList<>();

        for (StoredFishEntry entry : storedEntries) {
            if (remainingAllowed <= 0L) {
                break;
            }
            if (rarityFilter != null && !entry.rarity().getId().equalsIgnoreCase(rarityFilter)) {
                continue;
            }
            if (fishNameFilter != null && !entry.fish().getName().equalsIgnoreCase(fishNameFilter)) {
                continue;
            }
            long currentQty = getQuantity(storage, entry.storageKey());
            if (currentQty <= 0L) {
                continue;
            }
            long sellQty = Math.min(currentQty, remainingAllowed);
            if (sellQty <= 0L) {
                continue;
            }
            int boundedQty = (int) Math.min(Integer.MAX_VALUE, sellQty);
            SoldFish soldFish = new SoldFish(entry.fish(), boundedQty, entry.unitWorth());
            soldFishList.add(soldFish);
            pendingActions.add(new StoredSellAction(entry, boundedQty));
            remainingAllowed -= boundedQty;
        }

        if (pendingActions.isEmpty()) {
            return SellSummary.EMPTY;
        }

        double plannedBaseWorth = soldFishList.stream().mapToDouble(SoldFish::getTotalValue).sum();
        EMFFishSellEvent fishSellEvent = new EMFFishSellEvent(soldFishList, player, plannedBaseWorth, LocalDateTime.now());
        if (!fishSellEvent.callEvent()) {
            return SellSummary.EMPTY;
        }

        long totalSoldCount = 0L;
        double baseWorth = 0.0D;

        for (StoredSellAction action : pendingActions) {
            String key = action.entry.storageKey();
            long before = getQuantity(storage, key);
            long toDeduct = Math.min(before, action.amount);
            if (toDeduct <= 0L) {
                continue;
            }
            try {
                storageSubtractMethod.invoke(storage, key, toDeduct);
            } catch (Throwable ignored) {
                continue;
            }
            totalSoldCount += toDeduct;
            double itemTotal = action.entry.unitWorth() * toDeduct;
            baseWorth += itemTotal;

            for (Reward reward : action.entry.fish().getSellRewards()) {
                reward.rewardPlayer(player, player.getLocation());
            }
        }

        if (totalSoldCount <= 0L || baseWorth <= 0.0D) {
            return SellSummary.EMPTY;
        }

        double bonusPercent = AutoFishingService.getActiveSellBonusPercent(player)
            + FishingProcessor.getComboBonusPercent(FishingProcessor.getComboStreak(player.getUniqueId()));
        double finalWorth = baseWorth * (1.0D + (Math.max(0.0D, bonusPercent) / 100.0D));

        Economy.getInstance().deposit(player, finalWorth, true);

        try {
            SellHelper.logSoldFish(player, soldFishList, finalWorth, (int) Math.min(Integer.MAX_VALUE, totalSoldCount));
        } catch (Throwable ignored) {
        }

        if (sendFeedback) {
            EMFMessage message = ConfigMessage.FISH_SALE.getMessage();
            message.setAmount((int) Math.min(Integer.MAX_VALUE, totalSoldCount));
            message.setSellPrice(Economy.getInstance().getWorthFormat(finalWorth, true));
            message.setPlayer(player);
            message.send(player);

            if (bonusPercent > 0.0D) {
                double bonusAmount = Math.max(0.0D, finalWorth - baseWorth);
                player.sendMessage(MINI_MESSAGE.deserialize(
                    "<dark_gray>[<gold>★ Thưởng Câu Cá</gold>]</dark_gray> <green>Cộng thêm <gold>+"
                        + AutoFishingService.formatMoney(bonusAmount)
                        + "</gold> <yellow>(+" + (int) bonusPercent + "% Ưu đãi VIP & Combo)</yellow> khi bán cá từ kho!</green>"
                ));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.05f);
        }

        return new SellSummary(totalSoldCount, baseWorth, bonusPercent, finalWorth);
    }

    private record StoredSellAction(@NotNull StoredFishEntry entry, int amount) {}

    private static void cacheFishKey(@NotNull Fish fish, @NotNull String storageKey) {
        String id = fishCacheId(fish);
        FISH_KEY_CACHE.compute(id, (k, existing) -> {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            set.add(storageKey);
            if (existing != null) {
                set.addAll(existing);
            }
            return List.copyOf(set);
        });
    }

    private static @NotNull String fishCacheId(@NotNull Fish fish) {
        return fish.getRarity().getId().toLowerCase(Locale.ROOT) + ":" + fish.getName().toLowerCase(Locale.ROOT);
    }

    public static @NotNull List<String> resolveCandidateKeys(@NotNull Fish fish) {
        String cacheId = fishCacheId(fish);
        List<String> cached = FISH_KEY_CACHE.get(cacheId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        try {
            Section config = fish.getConfig();
            if (config != null) {
                addMaterialConfigCandidate(keys, config.getString("item.raw-material"));
                addMaterialConfigCandidate(keys, config.getString("item.material"));
            }
        } catch (Throwable ignored) {
        }

        try {
            ItemStack sample = fish.getFactory().createItem();
            if (sample != null && !sample.isEmpty()) {
                String sampleKey = toMaterialKey(sample);
                if (sampleKey != null && !sampleKey.isBlank() && !"INVALID".equalsIgnoreCase(sampleKey)) {
                    keys.add(sampleKey);
                    addMmoItemsSeparatorVariants(keys, sampleKey);
                }
            }
        } catch (Throwable ignored) {
        }

        List<String> resolved = keys.isEmpty() ? Collections.emptyList() : List.copyOf(keys);
        if (!resolved.isEmpty()) {
            FISH_KEY_CACHE.put(cacheId, resolved);
        }
        return resolved;
    }

    private static void addMaterialConfigCandidate(@NotNull Set<String> keys, @Nullable String rawMaterial) {
        if (rawMaterial == null || rawMaterial.isBlank()) {
            return;
        }
        String trimmed = rawMaterial.trim();
        if (trimmed.regionMatches(true, 0, "mmoitems:", 0, "mmoitems:".length())) {
            String rest = trimmed.substring("mmoitems:".length()).trim().toUpperCase(Locale.ROOT);
            int sep = rest.indexOf(':');
            if (sep < 0) {
                sep = rest.indexOf(';');
            }
            if (sep > 0 && sep + 1 < rest.length()) {
                String type = rest.substring(0, sep).trim();
                String id = rest.substring(sep + 1).trim();
                String semiVariant = "MMOITEMS:" + type + ";" + id;
                String colonVariant = "MMOITEMS:" + type + ":" + id;
                keys.add(semiVariant);
                keys.add(colonVariant);
                String normSemi = toMaterialKey(semiVariant);
                if (normSemi != null && !"INVALID".equalsIgnoreCase(normSemi)) {
                    keys.add(normSemi);
                }
                String normColon = toMaterialKey(colonVariant);
                if (normColon != null && !"INVALID".equalsIgnoreCase(normColon)) {
                    keys.add(normColon);
                }
            }
        }
    }

    private static void addMmoItemsSeparatorVariants(@NotNull Set<String> keys, @NotNull String key) {
        if (!key.regionMatches(true, 0, "MMOITEMS:", 0, "MMOITEMS:".length())) {
            return;
        }
        String rest = key.substring("MMOITEMS:".length());
        int colonIdx = rest.indexOf(':');
        if (colonIdx > 0) {
            keys.add("MMOITEMS:" + rest.substring(0, colonIdx) + ";" + rest.substring(colonIdx + 1));
        }
        int semiIdx = rest.indexOf(';');
        if (semiIdx > 0) {
            keys.add("MMOITEMS:" + rest.substring(0, semiIdx) + ":" + rest.substring(semiIdx + 1));
        }
    }

    private static @Nullable Object resolvePlayerStorage(@NotNull UUID playerId) {
        ensureStorageReflectionInitialized();
        if (storageApiGetInstanceMethod == null || storageApiGetUserMethod == null || userGetStorageMethod == null) {
            return null;
        }
        try {
            Object api = storageApiGetInstanceMethod.invoke(null);
            if (api == null) {
                return null;
            }
            Object user = storageApiGetUserMethod.invoke(api, playerId);
            if (user == null) {
                return null;
            }
            return userGetStorageMethod.invoke(user);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable String toMaterialKey(@NotNull Object itemOrKey) {
        ensureStorageReflectionInitialized();
        if (itemUtilToMaterialKeyMethod == null) {
            return null;
        }
        try {
            Object res = itemUtilToMaterialKeyMethod.invoke(null, itemOrKey);
            return res instanceof String str ? str : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long getQuantity(@NotNull Object storage, @NotNull String key) {
        if (storageGetQuantityMethod == null) {
            return 0L;
        }
        try {
            Object res = storageGetQuantityMethod.invoke(storage, key);
            return res instanceof Number num ? Math.max(0L, num.longValue()) : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static void ensureStorageReflectionInitialized() {
        if (storageLookupAttempted && storageApiGetInstanceMethod != null) {
            return;
        }
        synchronized (TurtleStorageFishBridge.class) {
            if (storageLookupAttempted && storageApiGetInstanceMethod != null) {
                return;
            }
            storageLookupAttempted = true;
            try {
                Class<?> storageApiClass = Class.forName("vn.turtle.turtlestorage.api.StorageAPI");
                storageApiGetInstanceMethod = storageApiClass.getMethod("getInstance");
                try {
                    storageApiGetProviderMethod = storageApiClass.getMethod("getProvider");
                } catch (NoSuchMethodException ignored) {
                }
                storageApiGetUserMethod = storageApiClass.getMethod("getUser", UUID.class);

                Class<?> userClass = Class.forName("vn.turtle.turtlestorage.api.user.User");
                userGetStorageMethod = userClass.getMethod("getStorage");

                Class<?> itemUtilClass = Class.forName("vn.turtle.turtlestorage.util.ItemUtil");
                itemUtilToMaterialKeyMethod = itemUtilClass.getMethod("toMaterialKey", Object.class);

                Class<?> storageClass = Class.forName("vn.turtle.turtlestorage.api.storage.Storage");
                storageGetStatusMethod = storageClass.getMethod("getStatus");
                try {
                    storageIsMaxSpaceMethod = storageClass.getMethod("isMaxSpace");
                } catch (NoSuchMethodException ignored) {
                }
                storageHasItemMethod = storageClass.getMethod("hasItem", Object.class);
                storageAddNewItemMethod = storageClass.getMethod("addNewItem", Object.class);
                storageAddMethod = storageClass.getMethod("add", Object.class, long.class);
                storageGetQuantityMethod = storageClass.getMethod("getQuantity", Object.class);
                storageSubtractMethod = storageClass.getMethod("subtract", Object.class, long.class);

                try {
                    storageDepositExternalMethod = storageClass.getMethod("depositExternal", ItemStack.class, int.class);
                } catch (NoSuchMethodException ignored) {
                }

                try {
                    Class<?> stubStorageClass = Class.forName("vn.turtle.turtlestorage.data.stub.StubStorage");
                    storageAddNewItemUnfilteredMethod = stubStorageClass.getMethod("addNewItemUnfiltered", String.class);
                    storageAddAndGetResultMethod = stubStorageClass.getMethod("addAndGetResult", Object.class, long.class);
                    if (storageDepositExternalMethod == null) {
                        try {
                            storageDepositExternalMethod = stubStorageClass.getMethod("depositExternal", ItemStack.class, int.class);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void ensureSkillsReflectionInitialized() {
        if (skillsLookupAttempted && leDatSkillsApiGetMethod != null) {
            return;
        }
        synchronized (TurtleStorageFishBridge.class) {
            if (skillsLookupAttempted && leDatSkillsApiGetMethod != null) {
                return;
            }
            skillsLookupAttempted = true;
            try {
                Class<?> apiClass = Class.forName("org.ledat.leDatSkills.api.LeDatSkillsApi");
                Class<?> skillTypeClass = Class.forName("org.ledat.leDatSkills.models.SkillType");
                leDatSkillsApiGetMethod = apiClass.getMethod("get");
                leDatSkillsAddExpMethod = apiClass.getMethod("addExperience", UUID.class, skillTypeClass, double.class);
                for (Object constant : skillTypeClass.getEnumConstants()) {
                    if ("FISHING".equalsIgnoreCase(constant.toString())) {
                        fishingSkillTypeConstant = constant;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
