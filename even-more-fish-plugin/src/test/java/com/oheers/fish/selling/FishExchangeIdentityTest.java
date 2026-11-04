package com.oheers.fish.selling;

import com.oheers.fish.fishing.items.*;
import com.oheers.fish.utils.nbt.NbtUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FishExchangeIdentityTest {
    @Test void nativeNameAndRarityMustBothResolveToAnEnabledRegisteredFish() {
        ItemStack item=mock(ItemStack.class);when(item.getType()).thenReturn(Material.COD);when(item.getAmount()).thenReturn(8);
        FishManager manager=mock(FishManager.class);Rarity rarity=mock(Rarity.class);
        try(var nbt=mockStatic(NbtUtils.class);var fish=mockStatic(FishManager.class)) {
            fish.when(FishManager::getInstance).thenReturn(manager);
            assertFalse(FishExchangeService.accepts(item,"Common"));
            nbt.when(()->NbtUtils.getString(item,"emf-fish-name")).thenReturn("Registered Fish");
            assertFalse(FishExchangeService.accepts(item,"Common"));
            nbt.when(()->NbtUtils.getString(item,"emf-fish-rarity")).thenReturn("Rare");
            assertFalse(FishExchangeService.accepts(item,"Common"));
            nbt.when(()->NbtUtils.getString(item,"emf-fish-rarity")).thenReturn("Common");
            assertFalse(FishExchangeService.accepts(item,"Common"));
            when(manager.getRarity("Common")).thenReturn(rarity);
            assertFalse(FishExchangeService.accepts(item,"Common"));
            when(rarity.getFish("Registered Fish")).thenReturn(mock(Fish.class));
            assertTrue(FishExchangeService.accepts(item,"Common"));
            when(rarity.isDisabled()).thenReturn(true);
            assertFalse(FishExchangeService.accepts(item,"Common"));
            verify(item,never()).setAmount(anyInt());
        }
    }
    @Test void unavailableEconomyCannotAuthorizeFreeBaitPurchases() {
        var economy=mock(com.oheers.fish.api.economy.Economy.class);
        when(economy.isEmpty()).thenReturn(false);when(economy.isEnabled()).thenReturn(false);
        assertTrue(FishExchangeService.purchasingUnavailable(economy));
        when(economy.isEnabled()).thenReturn(true);
        assertFalse(FishExchangeService.purchasingUnavailable(economy));
    }
}
