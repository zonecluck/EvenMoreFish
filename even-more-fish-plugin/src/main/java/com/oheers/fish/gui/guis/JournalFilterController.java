package com.oheers.fish.gui.guis;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.api.events.EMFPluginReloadEvent;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.database.Database;
import com.oheers.fish.database.DatabaseUtil;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import de.themoep.inventorygui.InventoryGui;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Owns one short-lived journal editor per player, including the classic fallback. */
final class JournalFilterController implements Listener {
    interface Bridge {
        boolean open(Player player, UUID token, JournalFilterState initial, boolean rarityMenu,
                     Consumer<JournalFilterState> save, Runnable cancel, Consumer<String> invalid);
        boolean foreignUi(Player player, UUID token);
        boolean confirmed(Player player, UUID token);
        void cleanup(UUID player, UUID token);
    }

    private enum Phase { OPENING, TYPED, CLASSIC, CHAT, LOADING }
    private static final long TTL_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final Semaphore READ_LIMIT = new Semaphore(4);
    private static volatile JournalFilterController instance;
    private final EvenMoreFish plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Set<ReadPermit> reads = ConcurrentHashMap.newKeySet();
    private volatile boolean active = true;
    private volatile long generation;
    private Bridge bridge;

    private JournalFilterController(EvenMoreFish plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    static synchronized JournalFilterController instance() {
        if (instance == null) instance = new JournalFilterController(EvenMoreFish.getInstance());
        return instance;
    }

    void open(FishJournalGui journal) {
        Player player = journal.viewer();
        if (!active || !player.hasPermission("emf.journal") || !DatabaseUtil.isDatabaseOnline()
                || InventoryGui.getOpen(player) != journal.getGui()) return;
        if (pending.size() >= 32 && !pending.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Nhật ký đang bận. Hãy thử lại sau."));
            return;
        }
        discard(player.getUniqueId());
        Pending request = new Pending(journal, generation);
        pending.put(player.getUniqueId(), request);
        journal.getGui().close(player, false);
        player.getScheduler().runDelayed(plugin, task -> {
            if (!owns(player, request)) { discard(player.getUniqueId(), request); return; }
            request.phase = Phase.TYPED;
            Bridge adapter = adapter();
            boolean opened = false;
            if (adapter != null) {
                try {
                    opened = adapter.open(player, request.token, request.draft, journal.selectedRarity() == null,
                            values -> at(player.getUniqueId(), current -> apply(current, request, values)),
                            () -> at(player.getUniqueId(), current -> cancel(current, request)),
                            message -> at(player.getUniqueId(), current -> fail(current, request, message)));
                } catch (LinkageError | RuntimeException unavailable) {
                    try { adapter.cleanup(player.getUniqueId(), request.token); }
                    catch (LinkageError | RuntimeException ignored) { }
                    bridge = null;
                }
            }
            if (!opened && owns(player, request)) classic(player, request);
            else if (opened) {
                player.getScheduler().runDelayed(plugin, later -> {
                    if (pending.get(player.getUniqueId()) != request || request.phase != Phase.TYPED) return;
                    if (!owns(player, request)) { discard(player.getUniqueId(), request); return; }
                    if (!adapter.confirmed(player, request.token)) classic(player, request);
                }, () -> discard(player.getUniqueId(), request), 2L);
            }
        }, () -> discard(player.getUniqueId(), request), 1L);
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, task ->
                at(player.getUniqueId(), current -> {
                    if (pending.get(current.getUniqueId()) == request) cancel(current, request);
                }), 60, TimeUnit.SECONDS);
    }

    private Bridge adapter() {
        Plugin provider = Bukkit.getPluginManager().getPlugin("LeDatPlatform");
        if (provider == null || !provider.isEnabled()) return null;
        if (bridge != null) return bridge;
        try {
            bridge = (Bridge) Class.forName("com.oheers.fish.gui.guis.PlatformJournalFilterAdapter", true, getClass().getClassLoader())
                    .getDeclaredConstructor(Plugin.class).newInstance(plugin);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
        return bridge;
    }

    private void apply(Player player, Pending request, JournalFilterState values) {
        if (!owns(player, request) || (request.phase != Phase.TYPED && request.phase != Phase.CLASSIC)) return;
        if (!player.hasPermission("emf.journal") || !DatabaseUtil.isDatabaseOnline()) {
            fail(player, request, "Nhật ký hiện không khả dụng."); return;
        }
        request.phase = Phase.LOADING;
        request.draft = values;
        Database database = plugin.getPluginDataManager().getDatabase();
        String prefix = MainConfig.getInstance().getPrefix();
        Map<String, Rarity> catalog = new HashMap<>();
        Set<JournalDiscoverySnapshot.Key> scope = new HashSet<>();
        for (Rarity rarity : FishManager.getInstance().getRarityMap().values()) {
            if (!rarity.getShowInJournal() || (request.original.selectedRarity() != null && rarity != request.original.selectedRarity())) continue;
            catalog.put(rarity.getId(), rarity);
            for (var fish : rarity.getFishList()) {
                if (request.original.selectedRarity() == null || fish.getShowInJournal()) {
                    scope.add(new JournalDiscoverySnapshot.Key(rarity.getId(), fish.getName()));
                }
            }
        }
        Set<JournalDiscoverySnapshot.Key> copiedScope = Set.copyOf(scope);
        Map<String, Rarity> copiedCatalog = Map.copyOf(catalog);
        int user = request.original.userId();
        String rarityId = request.original.selectedRarity() == null ? null : request.original.selectedRarity().getId();
        UUID playerId = player.getUniqueId();
        ReadPermit permit = reserveRead();
        if (permit == null) { fail(player, request, "Nhật ký đang bận. Hãy thử lại sau."); return; }
        reads.add(permit);
        player.closeInventory();
        try {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            if (!permit.begin()) { reads.remove(permit); return; }
            try {
                JournalDiscoverySnapshot snapshot = copiedCatalog.isEmpty() || (rarityId != null && copiedScope.isEmpty())
                        ? new JournalDiscoverySnapshot(Map.of())
                        : JournalDiscoverySnapshot.load(database.getJdbi(), prefix, user, rarityId, copiedScope, copiedCatalog.keySet());
                at(playerId, current -> {
                    if (!owns(current, request)) { discard(current.getUniqueId(), request); return; }
                    if (!current.hasPermission("emf.journal") || !DatabaseUtil.isDatabaseOnline()
                            || plugin.getPluginDataManager().getDatabase() != database
                            || !prefix.equals(MainConfig.getInstance().getPrefix()) || !sameCatalog(copiedCatalog)) {
                        discard(current.getUniqueId(), request); return;
                    }
                    discard(current.getUniqueId(), request);
                    new FishJournalGui(current, request.original.selectedRarity(), values, snapshot, user).open();
                });
            } catch (RuntimeException failure) {
                at(playerId, current -> fail(current, request, "Không thể đọc nhật ký. Hãy thử lại."));
            } finally {
                reads.remove(permit);
                permit.close();
            }
            });
        } catch (RuntimeException rejected) {
            reads.remove(permit); permit.close();
            fail(player, request, "Không thể đọc nhật ký. Hãy thử lại.");
        }
    }

    private boolean sameCatalog(Map<String, Rarity> expected) {
        var current = FishManager.getInstance().getRarityMap();
        return expected.entrySet().stream().allMatch(entry -> current.get(entry.getKey()) == entry.getValue());
    }

    private boolean owns(Player player, Pending request) {
        return System.nanoTime() - request.created < TTL_NANOS && ownsWindow(player, request);
    }

    private boolean ownsWindow(Player player, Pending request) {
        if (!active || !plugin.isEnabled() || !player.isOnline() || pending.get(player.getUniqueId()) != request
                || request.generation != generation) return false;
        Inventory top = JournalViewAccess.top(player);
        if (top == null) return false;
        InventoryHolder holder = top.getHolder();
        boolean ownClassic = holder instanceof FilterHolder filter && filter.token.equals(request.token);
        if (!ownClassic && top.getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) return false;
        return bridge == null || !bridge.foreignUi(player, request.token);
    }

    private void cancel(Player player, Pending request) {
        if (!ownsWindow(player, request)) { discard(player.getUniqueId(), request); return; }
        discard(player.getUniqueId(), request);
        if (!player.hasPermission("emf.journal") || !DatabaseUtil.isDatabaseOnline()) {
            Inventory top = JournalViewAccess.top(player);
            if (top != null && top.getHolder() instanceof FilterHolder) player.closeInventory();
            return;
        }
        request.original.open();
        request.original.getGui().setPageNumber(player, request.page);
        request.original.getGui().draw(player);
    }

    private void fail(Player player, Pending request, String message) {
        if (!owns(player, request)) { discard(player.getUniqueId(), request); return; }
        player.sendMessage(Component.text(message));
        cancel(player, request);
    }

    private void classic(Player player, Pending request) {
        request.phase = Phase.OPENING;
        FilterHolder holder = new FilterHolder(player.getUniqueId(), request.token);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Bộ lọc nhật ký cá"));
        holder.inventory = inventory;
        inventory.setItem(10, item(Material.NAME_TAG, "Tìm tên: " + (request.draft.query().isEmpty() ? "Tất cả" : request.draft.query()), "Nhấp để nhập tên trong chat."));
        inventory.setItem(12, item(Material.COMPASS, "Sắp xếp: " + JournalFilterState.SORT_LABELS.get(request.draft.sortIndex()), "Nhấp để đổi cách sắp xếp."));
        inventory.setItem(14, item(Material.LEVER, "Chỉ mục đã câu: " + (request.draft.caughtOnly() ? "Bật" : "Tắt"), "Nhấp để thay đổi."));
        inventory.setItem(18, item(Material.ARROW, "Quay lại", "Giữ bộ lọc đang dùng."));
        inventory.setItem(22, item(Material.LIME_DYE, "Áp dụng", "Xem nhật ký với bộ lọc này."));
        player.openInventory(inventory);
        request.phase = Phase.CLASSIC;
    }

    private static ItemStack item(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name, net.kyori.adventure.text.format.NamedTextColor.AQUA)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(Component.text(" "),
                    Component.text(" ○ ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                            .append(Component.text(lore, net.kyori.adventure.text.format.NamedTextColor.WHITE))
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false), Component.text(" ")));
        });
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof FilterHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.player.equals(player.getUniqueId())) return;
        Pending request = pending.get(holder.player);
        if (request == null || !request.token.equals(holder.token) || request.phase != Phase.CLASSIC
                || !owns(player, request) || event.isShiftClick()
                || (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT)) return;
        switch (event.getRawSlot()) {
            case 10 -> {
                request.phase = Phase.CHAT;
                player.closeInventory();
                player.sendMessage(Component.text("Nhập tên cần tìm, '-' để xem tất cả, hoặc 'hủy' để quay lại."));
            }
            case 12 -> { request.draft = request.draft.nextSort(); classic(player, request); }
            case 14 -> { request.draft = request.draft.toggleCaught(); classic(player, request); }
            case 18 -> cancel(player, request);
            case 22 -> apply(player, request, request.draft);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void drag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof FilterHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void chat(AsyncChatEvent event) {
        Pending request = pending.get(event.getPlayer().getUniqueId());
        if (request == null || request.phase != Phase.CHAT) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        at(event.getPlayer().getUniqueId(), player -> {
            if (!owns(player, request) || request.phase != Phase.CHAT) return;
            if (text.equalsIgnoreCase("hủy") || text.equalsIgnoreCase("cancel")) { cancel(player, request); return; }
            try { request.draft = request.draft.withQuery(text.equals("-") ? "" : text); }
            catch (IllegalArgumentException invalid) { player.sendMessage(Component.text(invalid.getMessage())); }
            classic(player, request);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void opened(InventoryOpenEvent event) {
        Pending request = pending.get(event.getPlayer().getUniqueId());
        if (request == null) return;
        if (event.getInventory().getHolder() instanceof FilterHolder holder && holder.token.equals(request.token)) return;
        discard(event.getPlayer().getUniqueId(), request);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void closed(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof FilterHolder holder)) return;
        Pending request = pending.get(holder.player);
        if (request != null && request.token.equals(holder.token) && request.phase == Phase.CLASSIC) {
            Player player = (Player) event.getPlayer();
            player.getScheduler().runDelayed(plugin, task -> cancel(player, request), () -> discard(holder.player, request), 1L);
        }
    }

    @EventHandler public void quit(PlayerQuitEvent event) { discard(event.getPlayer().getUniqueId()); }
    @EventHandler public void reload(EMFPluginReloadEvent event) { generation++; clear(); }
    @EventHandler public void disabled(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            active = false; generation++; clear(); instance = null;
            // A queued task may be cancelled by the server without entering its finally block.
            for (ReadPermit permit : reads) permit.cancelWaiting();
        }
    }

    private void at(UUID playerId, Consumer<Player> task) {
        if (!active || !plugin.isEnabled()) { discard(playerId); return; }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) { discard(playerId); return; }
        player.getScheduler().run(plugin, ignored -> task.accept(player), () -> discard(playerId));
    }

    private void clear() { for (UUID player : Set.copyOf(pending.keySet())) discard(player); }
    private void discard(UUID player) { Pending request = pending.remove(player); cleanup(player, request); }
    private void discard(UUID player, Pending request) { if (pending.remove(player, request)) cleanup(player, request); }
    private void cleanup(UUID player, Pending request) {
        if (request != null && bridge != null) bridge.cleanup(player, request.token);
    }

    static ReadPermit reserveRead() { return READ_LIMIT.tryAcquire() ? new ReadPermit() : null; }

    /** A replaced editor never releases a query that is still running. */
    static final class ReadPermit implements AutoCloseable {
        private boolean started;
        private boolean closed;
        synchronized boolean begin() {
            if (closed) return false;
            started = true;
            return true;
        }
        synchronized void cancelWaiting() { if (!started) close(); }
        @Override public synchronized void close() {
            if (!closed) { closed = true; READ_LIMIT.release(); }
        }
    }

    private static final class Pending {
        final UUID token = UUID.randomUUID();
        final long created = System.nanoTime();
        final FishJournalGui original;
        final long generation;
        final int page;
        volatile Phase phase = Phase.OPENING;
        JournalFilterState draft;
        Pending(FishJournalGui original, long generation) {
            this.original = original; this.generation = generation;
            this.draft = original.filterState();
            this.page = original.getGui().getPageNumber(original.viewer());
        }
    }

    private static final class FilterHolder implements InventoryHolder {
        final UUID player;
        final UUID token;
        Inventory inventory;
        FilterHolder(UUID player, UUID token) { this.player = player; this.token = token; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
