package com.oheers.fish.baits;

import com.oheers.fish.baits.manager.BaitNBTManager;
import com.oheers.fish.utils.nbt.NbtKeys;
import com.oheers.fish.utils.nbt.NbtUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaitValidationBeforeMutationTest {
    @Test void malformedOrRepeatedEntryNeverChangesRodMetadataOnApplyOrClear() throws Exception {
        for (String blob : new String[]{"valid:2,bad:not-a-number", "valid:2,valid:3", "valid:2,", "valid:0"}) {
            var rod = mock(ItemStack.class); var meta = mock(ItemMeta.class); var bait = mock(BaitHandler.class);
            when(rod.getType()).thenReturn(Material.FISHING_ROD);
            when(rod.hasItemMeta()).thenReturn(true); when(rod.getItemMeta()).thenReturn(meta);
            when(bait.getId()).thenReturn("valid");
            try (var nbt = mockStatic(NbtUtils.class)) {
                nbt.when(() -> NbtUtils.getString(rod, NbtKeys.EMF_APPLIED_BAIT)).thenReturn(blob);
                assertNotNull(BaitNBTManager.applyBaitedRodNBT(rod, bait, 1));
                assertEquals(0, BaitNBTManager.deleteAllBaits(rod));
                assertFalse(BaitNBTManager.hasBaitApplied(rod, "valid"));
                assertNull(BaitNBTManager.randomBaitApplication(rod));
            }
            verify(rod, never()).editMeta(any());
            verifyNoInteractions(meta);
        }
    }
}
