package com.oheers.fish.selling;

import com.oheers.fish.api.economy.Economy;
import com.oheers.fish.autofish.AutoFishingService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FishLegacyCashSafetyTest {
    @Test void disabledCashCannotConsumeFishFromAnInventoryBeforeSellIsRejected() throws Exception {
        Inventory inventory=mock(Inventory.class);ItemStack item=mock(ItemStack.class);
        when(inventory.getSize()).thenReturn(1);when(inventory.getItem(0)).thenReturn(item);
        Economy disabled=mock(Economy.class);
        try(var economy=mockStatic(Economy.class);var sold=mockConstruction(SoldFish.class)) {
            economy.when(Economy::getInstance).thenReturn(disabled);
            Method method=SellHelper.class.getDeclaredMethod("fetchFish",Inventory.class,boolean.class);method.setAccessible(true);method.invoke(null,inventory,true);
            assertEquals(1,sold.constructed().size());verify(inventory,never()).setItem(anyInt(),any());
        }
    }
    @Test void disabledCashCannotZeroAStackBeforeSellIsRejected() throws Exception {
        ItemStack item=mock(ItemStack.class);Economy disabled=mock(Economy.class);
        try(var economy=mockStatic(Economy.class);var sold=mockConstruction(SoldFish.class)) {
            economy.when(Economy::getInstance).thenReturn(disabled);
            Method method=SellHelper.class.getDeclaredMethod("fetchFish",ItemStack[].class,boolean.class);method.setAccessible(true);method.invoke(null,new ItemStack[]{item},true);
            assertEquals(1,sold.constructed().size());verify(item,never()).setAmount(anyInt());
        }
    }
    @Test void barterModeCannotReadOrWithdrawVaultThroughAutoFishing() {
        AutoFishingService service=mock(AutoFishingService.class,CALLS_REAL_METHODS);Player player=mock(Player.class);
        try(var barter=mockStatic(FishExchangeService.class)) {
            barter.when(FishExchangeService::isEnabled).thenReturn(true);
            assertEquals(0,service.getBalance(player));assertFalse(service.withdrawMoney(player,10));assertFalse(service.withdrawMoney(player,0));
            verifyNoInteractions(player);
        }
    }
}
