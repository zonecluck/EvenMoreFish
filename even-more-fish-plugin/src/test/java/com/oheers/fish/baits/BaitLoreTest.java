package com.oheers.fish.baits;

import com.oheers.fish.baits.manager.BaitNBTManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaitLoreTest {

    @Test
    void preservesOriginalLoreWithoutBait() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);

        List<Component> originalLore = List.of(
            Component.text("Rarity: Legendary"),
            Component.text("Power: +25%"),
            Component.text("Unbreakable")
        );

        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasLore()).thenReturn(true);
        when(meta.lore()).thenReturn(originalLore);

        List<Component> cleaned = BaitNBTManager.deleteOldLore(item);

        assertEquals(3, cleaned.size());
        assertEquals("Rarity: Legendary", PlainTextComponentSerializer.plainText().serialize(cleaned.get(0)));
        assertEquals("Power: +25%", PlainTextComponentSerializer.plainText().serialize(cleaned.get(1)));
        assertEquals("Unbreakable", PlainTextComponentSerializer.plainText().serialize(cleaned.get(2)));
    }

    @Test
    void removesOnlyBaitLinesAndPreservesOriginalLore() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);

        List<Component> fullLore = List.of(
            Component.text("Trade - Can Cau Ngan Trieu V"),
            Component.text("Rarity: Epic"),
            Component.text("Luck: +8%"),
            Component.text(BaitNBTManager.LINE_IDENTIFIER).append(Component.text("Bait Slots: (1/7)")),
            Component.text(BaitNBTManager.LINE_IDENTIFIER).append(Component.text("► 5 Blood Bait")),
            Component.text(BaitNBTManager.LINE_IDENTIFIER).append(Component.text("► ? Available Slot"))
        );

        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasLore()).thenReturn(true);
        when(meta.lore()).thenReturn(fullLore);

        List<Component> cleaned = BaitNBTManager.deleteOldLore(item);

        assertEquals(3, cleaned.size());
        assertEquals("Trade - Can Cau Ngan Trieu V", PlainTextComponentSerializer.plainText().serialize(cleaned.get(0)));
        assertEquals("Rarity: Epic", PlainTextComponentSerializer.plainText().serialize(cleaned.get(1)));
        assertEquals("Luck: +8%", PlainTextComponentSerializer.plainText().serialize(cleaned.get(2)));
    }

    @Test
    void handlesEmptyOrNullLoreSafely() {
        ItemStack nullMetaItem = mock(ItemStack.class);
        when(nullMetaItem.hasItemMeta()).thenReturn(false);
        assertTrue(BaitNBTManager.deleteOldLore(nullMetaItem).isEmpty());

        ItemStack noLoreItem = mock(ItemStack.class);
        ItemMeta emptyMeta = mock(ItemMeta.class);
        when(noLoreItem.hasItemMeta()).thenReturn(true);
        when(noLoreItem.getItemMeta()).thenReturn(emptyMeta);
        when(emptyMeta.hasLore()).thenReturn(false);
        when(emptyMeta.lore()).thenReturn(null);
        assertTrue(BaitNBTManager.deleteOldLore(noLoreItem).isEmpty());
    }

    @Test
    void removesAllLinesWhenOnlyBaitLinesPresent() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);

        List<Component> allBaitLore = List.of(
            Component.text(BaitNBTManager.LINE_IDENTIFIER).append(Component.text("Bait Slots: (1/7)")),
            Component.text(BaitNBTManager.LINE_IDENTIFIER).append(Component.text("► 5 Blood Bait"))
        );

        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasLore()).thenReturn(true);
        when(meta.lore()).thenReturn(allBaitLore);

        List<Component> cleaned = BaitNBTManager.deleteOldLore(item);
        assertTrue(cleaned.isEmpty());
    }

    @Test
    void deleteAllBaitsReturnsZeroForUnbaitedRod() {
        ItemStack item = mock(ItemStack.class);
        assertEquals(0, BaitNBTManager.deleteAllBaits(item));
    }
}