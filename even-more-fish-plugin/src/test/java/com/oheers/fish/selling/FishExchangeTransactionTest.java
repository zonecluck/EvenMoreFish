package com.oheers.fish.selling;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.utils.nbt.NbtUtils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import vn.ledat.platform.api.*;
import vn.ledat.platform.api.item.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FishExchangeTransactionTest {
    @Test void providerFailureAndIdentityMismatchNeverReadOrConsumeInventory() throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.items.create(f.key,1)).thenReturn(Optional.empty());f.exchange();verifyNoInteractions(f.inventory);
            when(f.items.create(f.key,1)).thenReturn(Optional.of(f.reward));when(f.items.identify(f.reward)).thenReturn(Optional.empty());
            f.exchange();verifyNoInteractions(f.inventory);
        }
    }
    @Test void nativeOutputAndFishInputCommitOnceAndRapidDuplicateIsDenied() throws Exception {
        try(Fixture f=new Fixture()) {
            f.exchange();var contents=org.mockito.ArgumentCaptor.forClass(ItemStack[].class);
            verify(f.inventory).setStorageContents(contents.capture());assertSame(f.output,contents.getValue()[0]);
            verify(f.output).setAmount(8);verify(f.input,never()).setAmount(anyInt());
            f.exchange();verify(f.inventory,times(1)).setStorageContents(any());verify(f.items,times(1)).create(f.key,1);
        }
    }
    @Test void failedCommitRestoresBeforeContentsAndAllowsAValidRetry() throws Exception {
        try(Fixture f=new Fixture()) {
            doThrow(new IllegalStateException("setter failure")).doNothing().doNothing().when(f.inventory).setStorageContents(any());
            f.exchange();var contents=org.mockito.ArgumentCaptor.forClass(ItemStack[].class);verify(f.inventory,times(2)).setStorageContents(contents.capture());
            assertSame(f.input,contents.getAllValues().get(1)[0]);assertNull(contents.getAllValues().get(1)[1]);
            f.exchange();verify(f.inventory,times(3)).setStorageContents(any());verify(f.items,times(2)).create(f.key,1);
        }
    }
    @Test void permissionRevocationRejectsBeforePreparingAnyOutput() throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.player.hasPermission("emf.shop")).thenReturn(false);f.exchange();verifyNoInteractions(f.items,f.inventory);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final org.mockito.MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final org.mockito.MockedStatic<FishManager> fish=mockStatic(FishManager.class);
        final org.mockito.MockedStatic<NbtUtils> nbt=mockStatic(NbtUtils.class);
        final org.mockito.MockedStatic<LeDatPlatformProvider> platform=mockStatic(LeDatPlatformProvider.class);
        final Player player=mock(Player.class);final PlayerInventory inventory=mock(PlayerInventory.class);
        final CustomItemService items=mock(CustomItemService.class);
        final CustomItemKey key=new CustomItemKey("MMOITEMS","MATERIAL","BP_TRADE_1");
        final ItemStack input=mock(ItemStack.class),reward=mock(ItemStack.class),output=mock(ItemStack.class);
        final FishExchangeService service;final Object trade;final Method exchange;
        Fixture() throws Exception {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            FishManager manager=mock(FishManager.class);fish.when(FishManager::getInstance).thenReturn(manager);
            Rarity rarity=mock(Rarity.class);when(manager.getRarity("Common")).thenReturn(rarity);when(rarity.getFish("Cod")).thenReturn(mock(Fish.class));
            LeDatPlatformApi api=mock(LeDatPlatformApi.class);platform.when(LeDatPlatformProvider::get).thenReturn(api);when(api.items()).thenReturn(items);
            when(items.create(key,1)).thenReturn(Optional.of(reward));when(items.identify(reward)).thenReturn(Optional.of(key));
            when(input.getType()).thenReturn(Material.COD);when(input.getAmount()).thenReturn(1);when(input.clone()).thenReturn(input);
            nbt.when(()->NbtUtils.getString(input,"emf-fish-name")).thenReturn("Cod");nbt.when(()->NbtUtils.getString(input,"emf-fish-rarity")).thenReturn("Common");
            when(reward.getType()).thenReturn(Material.STONE);when(reward.getMaxStackSize()).thenReturn(64);when(reward.clone()).thenReturn(output);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);when(player.hasPermission("emf.shop")).thenReturn(true);when(player.getInventory()).thenReturn(inventory);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{input,null});
            EvenMoreFish plugin=mock(EvenMoreFish.class);when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            YamlConfiguration config=new YamlConfiguration();String path="trades.common.";
            config.set(path+"slot",10);config.set(path+"rarity","Common");config.set(path+"required",1);config.set(path+"output.provider","MMOITEMS");config.set(path+"output.id","BP_TRADE_1");config.set(path+"output.amount",8);
            Constructor<FishExchangeService> ctor=FishExchangeService.class.getDeclaredConstructor(EvenMoreFish.class,YamlConfiguration.class);ctor.setAccessible(true);service=ctor.newInstance(plugin,config);
            Field field=FishExchangeService.class.getDeclaredField("trades");field.setAccessible(true);trade=((Map<?,?>)field.get(service)).get(10);assertNotNull(trade);
            exchange=FishExchangeService.class.getDeclaredMethod("exchange",Player.class,trade.getClass());exchange.setAccessible(true);
        }
        void exchange() throws Exception {exchange.invoke(service,player,trade);}
        @Override public void close() {platform.close();nbt.close();fish.close();bukkit.close();}
    }
}
