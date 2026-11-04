package com.oheers.fish.baits;

import com.oheers.fish.baits.manager.BaitNBTManager;
import com.oheers.fish.baits.model.BaitData;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.utils.nbt.NbtKeys;
import com.oheers.fish.utils.nbt.NbtUtils;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaitForeignMetadataTest {
    @SuppressWarnings("unchecked")
    @Test void repeatedApplicationAndClearWriteOnlyOwnedNbtAndKeepForeignItemMeta() throws Exception {
        var owner = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(owner.getName()).thenReturn("EvenMoreFish");
        try (var provider = mockStatic(org.bukkit.plugin.java.JavaPlugin.class)) {
            provider.when(() -> org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(NbtUtils.class)).thenReturn(owner);
            Class.forName(NbtKeys.class.getName());
        }
        var rod = mock(ItemStack.class); var meta = mock(ItemMeta.class);
        var nbtRoot = mock(ReadWriteItemNBT.class); var owned = mock(ReadWriteNBT.class);
        var config = mock(MainConfig.class); var bait = mock(BaitHandler.class); var data = mock(BaitData.class);
        var values = new HashMap<String, String>();
        List<Component> foreignLore = List.of(Component.text("MMOItems identity and stats"), Component.text("Custom lore"));
        when(rod.getType()).thenReturn(Material.FISHING_ROD); when(rod.hasItemMeta()).thenReturn(true);
        when(rod.getItemMeta()).thenReturn(meta); when(meta.hasLore()).thenReturn(true); when(meta.lore()).thenReturn(foreignLore);
        when(rod.editMeta(any())).thenAnswer(invocation -> {
            ((Consumer<ItemMeta>) invocation.getArgument(0)).accept(meta); return true;
        });
        when(nbtRoot.getOrCreateCompound(NbtKeys.EMF_COMPOUND)).thenReturn(owned);
        doAnswer(invocation -> { values.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                .when(owned).setString(anyString(), anyString());
        doAnswer(invocation -> { values.remove(invocation.getArgument(0)); return null; }).when(owned).removeKey(anyString());
        when(config.getBaitsPerRod()).thenReturn(3); when(config.getBaitAddToLore()).thenReturn(false);
        when(bait.getId()).thenReturn("blood"); when(bait.getBaitData()).thenReturn(data); when(data.maxApplications()).thenReturn(20);
        try (var configStatic = mockStatic(MainConfig.class); var reads = mockStatic(NbtUtils.class); var writes = mockStatic(NBT.class)) {
            configStatic.when(MainConfig::getInstance).thenReturn(config);
            reads.when(() -> NbtUtils.getString(rod, NbtKeys.EMF_APPLIED_BAIT)).thenAnswer(ignored -> values.get(NbtKeys.EMF_APPLIED_BAIT));
            reads.when(() -> NbtUtils.getBaitArray(rod)).thenAnswer(ignored -> {
                String blob = values.get(NbtKeys.EMF_APPLIED_BAIT); return blob == null ? new String[0] : blob.split(",");
            });
            writes.when(() -> NBT.modify(eq(rod), org.mockito.ArgumentMatchers.<Consumer<ReadWriteItemNBT>>any()))
                    .thenAnswer(invocation -> { ((Consumer<ReadWriteItemNBT>) invocation.getArgument(1)).accept(nbtRoot); return null; });
            assertSame(rod, BaitNBTManager.applyBaitedRodNBT(rod, bait, 2).fishingRod());
            assertEquals("blood:2", values.get(NbtKeys.EMF_APPLIED_BAIT));
            assertEquals(-3, BaitNBTManager.applyBaitedRodNBT(rod, bait, 3).cursorItemModifier());
            assertEquals("blood:5", values.get(NbtKeys.EMF_APPLIED_BAIT));
            var absent = mock(BaitHandler.class); when(absent.getId()).thenReturn("absent");
            when(absent.getBaitData()).thenReturn(data);
            BaitNBTManager.applyBaitedRodNBT(rod, absent, -1);
            assertEquals("blood:5", values.get(NbtKeys.EMF_APPLIED_BAIT));
            assertEquals(5, BaitNBTManager.deleteAllBaits(rod));
            assertNull(values.get(NbtKeys.EMF_APPLIED_BAIT));
        }
        verify(nbtRoot, times(4)).getOrCreateCompound(NbtKeys.EMF_COMPOUND); verifyNoMoreInteractions(nbtRoot);
        verify(owned, times(3)).setString(eq(NbtKeys.EMF_APPLIED_BAIT), anyString());
        verify(owned, times(3)).setBoolean(NbtKeys.EMF_BAIT_REFORMATTED, true);
        verify(owned).removeKey(NbtKeys.EMF_APPLIED_BAIT); verifyNoMoreInteractions(owned);
        verify(meta, times(3)).hasLore(); verify(meta, times(3)).lore(); verify(meta, times(3)).lore(foreignLore);
        // No setters for enchants, PDC, attributes, display name or MMOItems identity are called.
        verifyNoMoreInteractions(meta);
    }
}
