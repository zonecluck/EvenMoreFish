package com.oheers.fish.selling;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.utils.nbt.NbtKeys;
import com.oheers.fish.utils.nbt.NbtUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import vn.ledat.platform.api.LeDatPlatformProvider;
import vn.ledat.platform.api.item.CustomItemKey;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Optional native fish barter. Item preparation has no currency or command side effects. */
public final class FishExchangeService implements Listener {
    private static volatile FishExchangeService instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final EvenMoreFish plugin;
    private final YamlConfiguration config;
    private final Map<Integer, Trade> trades;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> processing = new HashSet<>();

    private record Trade(String id, int slot, String rarity, int required, String provider,
                         String outputId, int outputAmount, Material icon, String name, List<String> lore) {}

    private static final class Menu implements InventoryHolder {
        private final UUID owner;
        private final FishExchangeService service;
        private final long expires = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
        private Inventory inventory;
        private Menu(UUID owner, FishExchangeService service) { this.owner = owner; this.service = service; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public static void install(EvenMoreFish plugin) {
        FishExchangeService old = instance;
        if (old != null) old.close();
        instance = null;
        File file = new File(plugin.getDataFolder(), "fish-exchange.yml");
        if (!file.isFile()) return; // Existing installations retain their original shop.
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.getBoolean("enabled", false)) return;
        FishExchangeService next = new FishExchangeService(plugin, config);
        instance = next; // An invalid enabled catalog still intercepts the cash shop and fails closed.
        plugin.getServer().getPluginManager().registerEvents(next, plugin);
    }

    private FishExchangeService(EvenMoreFish plugin, YamlConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        Map<Integer, Trade> loaded = new LinkedHashMap<>();
        try {
            ConfigurationSection section = Objects.requireNonNull(config.getConfigurationSection("trades"), "Missing trades");
            if (section.getKeys(false).size() > 14) throw new IllegalArgumentException("At most 14 barter offers");
            for (String id : section.getKeys(false)) {
                ConfigurationSection row = Objects.requireNonNull(section.getConfigurationSection(id), id);
                int slot = row.getInt("slot", -1);
                String rarity = Objects.requireNonNull(row.getString("rarity"), "rarity");
                Rarity registered = FishManager.getInstance().getRarity(rarity);
                if (registered == null || registered.isDisabled()) throw new IllegalArgumentException("Unknown/disabled rarity " + rarity);
                int required = row.getInt("required", 0), amount = row.getInt("output.amount", 0);
                String provider = row.getString("output.provider", "").toUpperCase(Locale.ROOT);
                String outputId = row.getString("output.id", "");
                if (slot < 9 || slot > 25 || slot == 18 || slot == 26 || loaded.containsKey(slot)
                        || required < 1 || required > 2304 || amount < 1 || amount > 2304
                        || !(provider.equals("MMOITEMS") || provider.equals("CRATE_KEY")) || outputId.isBlank()) {
                    throw new IllegalArgumentException("Invalid barter offer " + id);
                }
                Material icon = Material.matchMaterial(row.getString("icon", "COD"));
                if (icon == null || icon.isAir()) throw new IllegalArgumentException("Invalid icon " + id);
                loaded.put(slot, new Trade(id, slot, rarity, required, provider, outputId, amount, icon,
                        row.getString("name", id), List.copyOf(row.getStringList("lore"))));
            }
        } catch (RuntimeException failure) {
            loaded.clear();
            plugin.getLogger().severe("Fish barter catalog unavailable: " + failure.getMessage());
        }
        trades = Collections.unmodifiableMap(loaded);
    }

    /** Returns true whenever the optional barter mode owns the shop, including provider failures. */
    public static boolean openIfEnabled(Player player) {
        FishExchangeService service = instance;
        if (service == null) return false;
        service.open(player);
        return true;
    }

    public static boolean isEnabled() { return instance != null; }

    public static boolean purchasingUnavailable(com.oheers.fish.api.economy.Economy economy) {
        return economy.isEmpty() || !economy.isEnabled();
    }

    private void open(Player player) {
        if (!player.hasPermission("emf.shop")) { message(player, "no-permission"); return; }
        if (trades.isEmpty()) { message(player, "unavailable"); return; }
        Menu menu = new Menu(player.getUniqueId(), this);
        menu.inventory = Bukkit.createInventory(menu, 36, MM.deserialize(config.getString("title", "<aqua>Đổi Cá")));
        for (Trade trade : trades.values()) {
            ItemStack item = new ItemStack(trade.icon());
            var meta = item.getItemMeta();
            meta.displayName(MM.deserialize(trade.name()).decoration(TextDecoration.ITALIC, false));
            meta.lore(trade.lore().stream().map(line -> MM.deserialize(line).decoration(TextDecoration.ITALIC, false)).toList());
            item.setItemMeta(meta);
            menu.inventory.setItem(trade.slot(), item);
        }
        ItemStack close = new ItemStack(Material.BARRIER);
        var meta = close.getItemMeta();
        meta.displayName(MM.deserialize(config.getString("close-name", "<red>Đóng")).decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(meta);menu.inventory.setItem(31, close);
        player.openInventory(menu.inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())
                || event.getRawSlot() < 0 || event.getRawSlot() >= menu.inventory.getSize()
                || event.getClick() != ClickType.LEFT) return;
        if (menu.service != this) { player.closeInventory(); message(player, "expired"); return; }
        if (event.getRawSlot() == 31) { player.closeInventory(); return; }
        if (System.nanoTime() > menu.expires) { player.closeInventory(); message(player, "expired"); return; }
        Trade trade = trades.get(event.getRawSlot());
        if (trade != null) exchange(player, trade);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());processing.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler public void disable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) close();
    }

    private void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu && menu.service == this) player.closeInventory();
        }
        cooldowns.clear();processing.clear();HandlerList.unregisterAll(this);
        if (instance == this) instance = null;
    }

    private void exchange(Player player, Trade trade) {
        if (!Bukkit.isPrimaryThread() || !player.isOnline() || !player.hasPermission("emf.shop")) return;
        UUID id = player.getUniqueId();long now = System.nanoTime();
        if (now < cooldowns.getOrDefault(id, 0L) || !processing.add(id)) { message(player, "cooldown"); return; }
        try {
            ItemStack reward = prepareReward(trade);
            ItemStack[] before = FishBarterPlan.copy(player.getInventory().getStorageContents());
            Optional<ItemStack[]> plan = FishBarterPlan.create(before, item -> accepts(item, trade.rarity()), trade.required(), reward, trade.outputAmount());
            if (plan.isEmpty()) { message(player, "missing-or-full"); return; }
            // Everything above is side-effect free. One owner-thread replacement commits input and output together.
            try { player.getInventory().setStorageContents(plan.get()); }
            catch (RuntimeException failedCommit) {
                player.getInventory().setStorageContents(before);
                throw failedCommit;
            }
            cooldowns.entrySet().removeIf(entry -> entry.getValue() < now);
            if (cooldowns.size() >= 4096) cooldowns.clear();
            cooldowns.put(id, now + TimeUnit.SECONDS.toNanos(1));
            message(player, "success");
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Fish barter rejected for offer " + trade.id() + ": " + failure.getClass().getSimpleName());
            message(player, "unavailable");
        } finally { processing.remove(id); }
    }

    static boolean accepts(ItemStack item, String rarityId) {
        if (item == null || item.getType().isAir() || item.getAmount() < 1) return false;
        String name = NbtUtils.getString(item, NbtKeys.EMF_FISH_NAME);
        String rarity = NbtUtils.getString(item, NbtKeys.EMF_FISH_RARITY);
        if (name == null || name.isBlank() || !rarityId.equals(rarity)) return false;
        Rarity registered = FishManager.getInstance().getRarity(rarity);
        return registered != null && !registered.isDisabled() && registered.getFish(name) != null;
    }

    private ItemStack prepareReward(Trade trade) {
        if (trade.provider().equals("MMOITEMS")) {
            var items = LeDatPlatformProvider.get().items();
            CustomItemKey key = new CustomItemKey("MMOITEMS", "MATERIAL", trade.outputId());
            ItemStack reward = items.create(key, 1).orElseThrow(() -> new IllegalStateException("MMOItems reward unavailable"));
            if (!items.identify(reward).filter(key::equals).isPresent()) throw new IllegalStateException("MMOItems identity mismatch");
            return reward;
        }
        try {
            Plugin provider = Bukkit.getPluginManager().getPlugin("LeDatExcellentCrates");
            if (provider == null || !provider.isEnabled()) throw new IllegalStateException("Crate provider unavailable");
            Class<?> api = provider.getClass().getClassLoader().loadClass("su.nightexpress.excellentcrates.CratesAPI");
            if (!Boolean.TRUE.equals(api.getMethod("isLoaded").invoke(null))) throw new IllegalStateException("Crate API unavailable");
            Object manager = api.getMethod("getKeyManager").invoke(null);
            Object key = manager.getClass().getMethod("getKeyById", String.class).invoke(manager, trade.outputId());
            if (key == null || Boolean.TRUE.equals(key.getClass().getMethod("isVirtual").invoke(key))) throw new IllegalStateException("Physical key unavailable");
            ItemStack reward = (ItemStack) key.getClass().getMethod("getItemStack").invoke(key);
            Object identified = manager.getClass().getMethod("getKeyByItem", ItemStack.class).invoke(manager, reward);
            if (identified == null || !trade.outputId().equals(identified.getClass().getMethod("getId").invoke(identified))) throw new IllegalStateException("Native key identity mismatch");
            return reward;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Crate API incompatible", error); }
    }

    private void message(Player player, String key) {
        player.sendMessage(MM.deserialize(config.getString("messages." + key, "<red>Không thể đổi cá lúc này.")));
    }
}
