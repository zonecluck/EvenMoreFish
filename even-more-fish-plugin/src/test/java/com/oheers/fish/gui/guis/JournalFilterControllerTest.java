package com.oheers.fish.gui.guis;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.utils.sort.SortType;
import de.themoep.inventorygui.InventoryGui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JournalFilterControllerTest {
    @Test void repeatedReplacementCannotReleaseOutstandingBlockedReads() throws Exception {
        var workers = Executors.newFixedThreadPool(4);
        CountDownLatch started = new CountDownLatch(4);
        CountDownLatch finish = new CountDownLatch(1);
        List<JournalFilterController.ReadPermit> permits = new ArrayList<>();
        List<Future<?>> results = new ArrayList<>();
        try (Fixture f = new Fixture()) {
            for (int index = 0; index < 4; index++) {
                var permit = JournalFilterController.reserveRead(); assertNotNull(permit); permits.add(permit);
                results.add(workers.submit(() -> {
                    assertTrue(permit.begin()); started.countDown();
                    try { assertTrue(finish.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                    finally { permit.close(); }
                }));
            }
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Object previous = f.request;
            for (int index = 0; index < 100; index++) {
                Object replacement = f.newRequest(); f.sessions.put(f.id, replacement);
                f.call("discard", new Class[]{UUID.class, f.requestType}, f.id, previous);
                previous = replacement;
                var excess = JournalFilterController.reserveRead();
                if (excess != null) excess.close();
                assertNull(excess, "Replacing UI must not free a running SQL permit");
            }
        } finally {
            finish.countDown(); workers.shutdown(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            for (var result : results) result.get();
            for (var permit : permits) permit.close();
        }
        try (var available = JournalFilterController.reserveRead()) { assertNotNull(available); }
    }

    @Test void disablingCanReleaseOnlyQueriesThatNeverStarted() {
        var queued = JournalFilterController.reserveRead(); assertNotNull(queued);
        queued.cancelWaiting(); assertFalse(queued.begin()); queued.close();
        var running = JournalFilterController.reserveRead(); assertNotNull(running); assertTrue(running.begin());
        running.cancelWaiting();
        running.close(); running.close();
    }

    @Test void replacementForeignUiAndGenerationBlockOldOwnership() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.owns());
            f.sessions.remove(f.id); assertFalse(f.owns());
            f.sessions.put(f.id, f.request); assertTrue(f.owns());
            when(f.inventory.getType()).thenReturn(InventoryType.CHEST); assertFalse(f.owns());
            when(f.inventory.getType()).thenReturn(InventoryType.CRAFTING);
            var bridge = mock(JournalFilterController.Bridge.class);
            Fixture.field(f.controller, "bridge").set(f.controller, bridge);
            when(bridge.foreignUi(eq(f.player), any())).thenReturn(true); assertFalse(f.owns());
            when(bridge.foreignUi(eq(f.player), any())).thenReturn(false); assertTrue(f.owns());
            Fixture.field(f.controller, "generation").setLong(f.controller, 1); assertFalse(f.owns());
        }
    }

    @Test void offlineDisabledAndExpiredRequestsCannotApply() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.player.isOnline()).thenReturn(false); assertFalse(f.owns());
            when(f.player.isOnline()).thenReturn(true);
            when(f.plugin.isEnabled()).thenReturn(false); assertFalse(f.owns());
            when(f.plugin.isEnabled()).thenReturn(true);
            Fixture.field(f.request, "created").setLong(f.request, System.nanoTime() - TimeUnit.SECONDS.toNanos(61));
            assertFalse(f.owns());
            // Expiration can still return the original view when no newer UI owns the player.
            assertTrue((boolean) f.call("ownsWindow", new Class[]{Player.class, f.requestType}, f.player, f.request));
        }
    }

    @Test void cleanupOfAnOldRequestCannotRemoveItsReplacement() throws Exception {
        try (Fixture f = new Fixture()) {
            Object replacement = f.newRequest(); f.sessions.put(f.id, replacement);
            var bridge = mock(JournalFilterController.Bridge.class);
            Fixture.field(f.controller, "bridge").set(f.controller, bridge);
            f.call("discard", new Class[]{UUID.class, f.requestType}, f.id, f.request);
            assertSame(replacement, f.sessions.get(f.id));
            verifyNoInteractions(bridge);
            f.call("discard", new Class[]{UUID.class, f.requestType}, f.id, replacement);
            assertFalse(f.sessions.containsKey(f.id));
            verify(bridge).cleanup(eq(f.id), any());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        MockedStatic<?> registries;
        final EvenMoreFish plugin = mock(EvenMoreFish.class);
        final Player player = mock(Player.class);
        final FishJournalGui journal = mock(FishJournalGui.class);
        final Inventory inventory = mock(Inventory.class);
        final InventoryView view = mock(InventoryView.class);
        final UUID id = UUID.randomUUID();
        final JournalFilterController controller;
        final Class<?> requestType = Class.forName(JournalFilterController.class.getName() + "$Pending");
        final Object request;
        final Map<UUID,Object> sessions;

        @SuppressWarnings("unchecked") Fixture() throws Exception {
            try {
            registries = registrySupport();
            // Resolve registry-backed enum constants before Mockito starts a stubbing call.
            InventoryType crafting = InventoryType.CRAFTING;
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            when(plugin.isEnabled()).thenReturn(true); when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true); when(player.hasPermission("emf.journal")).thenReturn(true);
            when(player.getOpenInventory()).thenReturn(view); when(view.getTopInventory()).thenReturn(inventory);
            when(inventory.getType()).thenReturn(crafting);
            when(journal.viewer()).thenReturn(player); when(journal.getGui()).thenReturn(mock(InventoryGui.class));
            when(journal.filterState()).thenReturn(new JournalFilterState("", SortType.INDEX, false));
            var constructor = JournalFilterController.class.getDeclaredConstructor(EvenMoreFish.class);
            constructor.setAccessible(true); controller = constructor.newInstance(plugin);
            sessions = (Map<UUID,Object>) field(controller, "pending").get(controller);
            request = newRequest(); sessions.put(id, request);
            } catch (Exception | Error failure) {
                if (registries != null) registries.close();
                bukkit.close();
                throw failure;
            }
        }

        Object newRequest() throws Exception {
            var constructor = requestType.getDeclaredConstructor(FishJournalGui.class, long.class);
            constructor.setAccessible(true); return constructor.newInstance(journal, 0L);
        }
        boolean owns() throws Exception { return (boolean) call("owns", new Class[]{Player.class, requestType}, player, request); }
        Object call(String name, Class<?>[] types, Object... args) throws Exception {
            var method = controller.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(controller, args);
        }
        static Field field(Object value, String name) throws Exception {
            Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field;
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        private static MockedStatic<?> registrySupport() throws Exception {
            Class<?> type;
            try { type = Class.forName("io.papermc.paper.registry.RegistryAccess"); }
            catch (ClassNotFoundException olderPaper) { return null; }
            MockedStatic registry = mockStatic(type);
            try {
                Object access = mock(type, call -> mock(org.bukkit.Registry.class));
                var method = type.getMethod("registryAccess");
                registry.when(() -> method.invoke(null)).thenReturn(access);
                return registry;
            } catch (Exception | Error failure) { registry.close(); throw failure; }
        }
        @Override public void close() { if (registries != null) registries.close(); bukkit.close(); }
    }
}
