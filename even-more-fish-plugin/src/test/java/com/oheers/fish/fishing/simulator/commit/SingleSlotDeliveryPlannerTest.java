package com.oheers.fish.fishing.simulator.commit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingleSlotDeliveryPlannerTest {
    @Test void mergesIntoOneSlotWithoutMutatingInput() {
        ItemStack existing=new PlainStack(Material.COD,60);
        ItemStack reward=new PlainStack(Material.COD,2);
        ItemStack[] contents={new PlainStack(Material.STONE,64),existing,null};
        var result=SingleSlotDeliveryPlanner.plan(contents,reward,64);
        assertNotNull(result);
        assertEquals(1,result.slot());
        assertEquals(62,result.contents().getAmount());
        assertEquals(60,existing.getAmount());
        assertEquals(2,reward.getAmount());
    }

    @Test void choosesEmptySlotOrWholeDropNeverSplitsCatch() {
        ItemStack[] storage={new PlainStack(Material.STONE,64),new PlainStack(Material.COD,63),null};
        var result=SingleSlotDeliveryPlanner.plan(storage,new PlainStack(Material.COD,2),64);
        assertNotNull(result);
        assertEquals(2,result.slot());
        storage[2]=new PlainStack(Material.DIRT,64);
        assertNull(SingleSlotDeliveryPlanner.plan(storage,new PlainStack(Material.COD,2),64));
        assertNull(SingleSlotDeliveryPlanner.plan(new ItemStack[]{null},new PlainStack(Material.COD,2),1));
    }

    /** Material-only stacks need no server ItemFactory; clone/amount remain the real Bukkit implementation. */
    private static final class PlainStack extends ItemStack {
        PlainStack(Material material, int amount) { super(material, amount); }
        @Override public boolean isSimilar(ItemStack other) {
            return other instanceof PlainStack && getType() == other.getType();
        }
    }
}
