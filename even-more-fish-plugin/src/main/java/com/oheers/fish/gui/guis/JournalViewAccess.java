package com.oheers.fish.gui.guis;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** InventoryView changed from class to interface; resolve its public API at runtime. */
final class JournalViewAccess {
    private static final Method TOP = topMethod();

    private JournalViewAccess() { }

    private static Method topMethod() {
        try { return InventoryView.class.getMethod("getTopInventory"); }
        catch (NoSuchMethodException failure) { throw new ExceptionInInitializerError(failure); }
    }

    static Inventory top(Player player) {
        Object view = player.getOpenInventory();
        try { return (Inventory) TOP.invoke(view); }
        catch (IllegalAccessException | InvocationTargetException failure) { return null; }
    }
}
