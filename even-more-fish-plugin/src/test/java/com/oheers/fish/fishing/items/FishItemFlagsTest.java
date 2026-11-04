package com.oheers.fish.fishing.items;

import com.oheers.fish.items.ItemFactory;
import com.oheers.fish.selling.WorthNBT;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class FishItemFlagsTest {
    @Test void fishItemDecorationSupportsTheRuntimeTooltipFlagAndRetainsFishNbt() {
        Section section = mock(Section.class);
        when(section.getNameAsString()).thenReturn("Nemo");
        ItemFactory factory = mock(ItemFactory.class, RETURNS_DEEP_STUBS);
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        doAnswer(invocation -> {
            Consumer<ItemMeta> edit = invocation.getArgument(0);
            edit.accept(meta);
            return true;
        }).when(item).editMeta(any());

        try (var factories = mockStatic(ItemFactory.class); var nbt = mockStatic(WorthNBT.class)) {
            factories.when(() -> ItemFactory.itemFactory(section)).thenReturn(factory);
            Fish fish = Fish.create(mock(Rarity.class), section);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<ItemStack>> finalizer = ArgumentCaptor.forClass(Consumer.class);
            verify(factory).setFinalChanges(finalizer.capture());

            assertDoesNotThrow(() -> finalizer.getValue().accept(item));
            ItemFlag specifics;
            try { specifics = ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"); }
            catch (IllegalArgumentException legacy) {
                // Paper 1.20.1 exposes HIDE_ITEM_SPECIFICS as an alias field, not an enum constant.
                specifics = ItemFlag.valueOf("HIDE_POTION_EFFECTS");
            }
            verify(meta).addItemFlags(specifics);
            verify(meta).addItemFlags(ItemFlag.HIDE_ENCHANTS);
            verify(meta).addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            nbt.verify(() -> WorthNBT.setNBT(item, fish));
        }
    }
}
