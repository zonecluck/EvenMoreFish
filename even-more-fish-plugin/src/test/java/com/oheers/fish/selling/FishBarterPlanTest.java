package com.oheers.fish.selling;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FishBarterPlanTest {
    private final Map<ItemStack,String> identities = new IdentityHashMap<>();
    private ItemStack stack(String identity, int amount, int max) {
        ItemStack item=mock(ItemStack.class);AtomicInteger count=new AtomicInteger(amount);
        identities.put(item,identity);
        when(item.getType()).thenReturn(identity.equals("fish")?Material.COD:Material.TRIPWIRE_HOOK);
        when(item.getAmount()).thenAnswer(call->count.get());
        doAnswer(call->{count.set(call.getArgument(0));return null;}).when(item).setAmount(anyInt());
        when(item.getMaxStackSize()).thenReturn(max);
        when(item.clone()).thenAnswer(call->stack(identity,count.get(),max));
        when(item.isSimilar(any())).thenAnswer(call->identity.equals(identities.get(call.getArgument(0))));
        return item;
    }
    private boolean fish(ItemStack item) {return "fish".equals(identities.get(item));}

    @Test void insufficientInputsLeaveOriginalInventoryUnchanged() {
        ItemStack input=stack("fish",3,64);
        assertTrue(FishBarterPlan.create(new ItemStack[]{input,null},this::fish,4,stack("key",1,1),1).isEmpty());
        assertEquals(3,input.getAmount());
    }
    @Test void partialConsumptionCannotDropARewardWhenInventoryIsFull() {
        ItemStack input=stack("fish",64,64),other=stack("other",64,64);
        assertTrue(FishBarterPlan.create(new ItemStack[]{input,other},this::fish,1,stack("key",1,1),1).isEmpty());
        assertEquals(64,input.getAmount());assertEquals(64,other.getAmount());
    }
    @Test void consumingWholeInputCanMakeRoomForItsReward() {
        ItemStack input=stack("fish",1,64);
        var result=FishBarterPlan.create(new ItemStack[]{input},this::fish,1,stack("material",1,64),48).orElseThrow();
        assertEquals("material",identities.get(result[0]));assertEquals(48,result[0].getAmount());assertEquals(1,input.getAmount());
    }
    @Test void splitInputStacksAndExistingOutputCapacityArePlannedTogether() {
        ItemStack a=stack("fish",2,64),b=stack("fish",6,64),material=stack("material",60,64);
        var result=FishBarterPlan.create(new ItemStack[]{a,b,material},this::fish,4,stack("material",1,64),8).orElseThrow();
        assertEquals(4,result[1].getAmount());assertEquals(64,result[2].getAmount());assertEquals(4,result[0].getAmount());
        assertEquals(2,a.getAmount());assertEquals(6,b.getAmount());assertEquals(60,material.getAmount());
    }
    @Test void nativeUnstackableKeysRequireEnoughSeparateSlots() {
        assertTrue(FishBarterPlan.create(new ItemStack[]{stack("fish",1,64)},this::fish,1,stack("key",1,1),2).isEmpty());
        var result=FishBarterPlan.create(new ItemStack[]{stack("fish",1,64),null},this::fish,1,stack("key",1,1),2).orElseThrow();
        assertEquals(1,result[0].getAmount());assertEquals(1,result[1].getAmount());
        assertEquals("key",identities.get(result[0]));assertNotSame(result[0],result[1]);
    }
    @Test void CounterfeitOrUnrelatedItemsNeverCountAsFish() {
        var fake=stack("named-fish-without-native-identity",64,64);
        assertTrue(FishBarterPlan.create(new ItemStack[]{fake,null},this::fish,1,stack("material",1,64),8).isEmpty());
        assertEquals(64,fake.getAmount());
    }
    @Test void invalidAmountsFailBeforePlanning() {
        assertThrows(IllegalArgumentException.class,()->FishBarterPlan.create(new ItemStack[2],this::fish,0,stack("key",1,1),1));
        assertThrows(IllegalArgumentException.class,()->FishBarterPlan.create(new ItemStack[2],this::fish,1,stack("key",1,1),0));
    }
}
