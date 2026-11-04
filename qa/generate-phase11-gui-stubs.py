from pathlib import Path
r=Path('qa/stubs11')
stubs={
'net/kyori/adventure/text/Component.java':'''package net.kyori.adventure.text;
import net.kyori.adventure.text.format.TextDecoration;
public class Component { public static Component empty(){return new Component();} public Component decoration(TextDecoration t,boolean b){return this;} }
''',
'net/kyori/adventure/text/format/TextDecoration.java':'package net.kyori.adventure.text.format; public enum TextDecoration {ITALIC}\n',
'org/jetbrains/annotations/NotNull.java':'package org.jetbrains.annotations; public @interface NotNull {}\n',
'org/bukkit/Material.java':'''package org.bukkit; public enum Material { GRAY_STAINED_GLASS_PANE,BARRIER,PAPER,COMPASS,NETHER_STAR,FEATHER,AIR; public static Material matchMaterial(String s){try{return valueOf(s);}catch(Exception x){return null;}} public boolean isItem(){return this!=AIR;} }''',
'org/bukkit/Sound.java':'package org.bukkit; public interface Sound { Sound UI_BUTTON_CLICK = new Sound(){}; }\n',
'org/bukkit/NamespacedKey.java':'package org.bukkit; public record NamespacedKey(String id) { public static NamespacedKey fromString(String s){return s.contains(":")?new NamespacedKey(s):null;} public static NamespacedKey minecraft(String s){return new NamespacedKey("minecraft:"+s);} }\n',
'org/bukkit/Registry.java':'package org.bukkit; public class Registry { public static final Registry SOUNDS = new Registry(); public Sound get(NamespacedKey k){return Sound.UI_BUTTON_CLICK;} }\n',
'org/bukkit/Location.java':'package org.bukkit; public class Location {}\n',
'org/bukkit/enchantments/Enchantment.java':'package org.bukkit.enchantments; public class Enchantment { public static final Enchantment UNBREAKING=new Enchantment(); public static Enchantment getByKey(org.bukkit.NamespacedKey key){return "minecraft:unbreaking".equals(key.id())?UNBREAKING:null;} }\n',
'org/bukkit/inventory/ItemFlag.java':'package org.bukkit.inventory; public enum ItemFlag {HIDE_ENCHANTS}\n',
'org/bukkit/inventory/meta/ItemMeta.java':'''package org.bukkit.inventory.meta;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.enchantments.Enchantment;
public class ItemMeta { public void displayName(Component c){} public void lore(List<Component> l){} public void addEnchant(Enchantment e,int n,boolean b){} public void addItemFlags(ItemFlag... f){} }
''',
'org/bukkit/inventory/ItemStack.java':'''package org.bukkit.inventory;
import org.bukkit.Material; import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack { public final Material material; ItemMeta meta=new ItemMeta(); public ItemStack(Material m){material=m;} public ItemMeta getItemMeta(){return meta;} public void setItemMeta(ItemMeta m){meta=m;} }
''',
'org/bukkit/inventory/InventoryHolder.java':'package org.bukkit.inventory; public interface InventoryHolder { Inventory getInventory(); }\n',
'org/bukkit/inventory/Inventory.java':'''package org.bukkit.inventory;
public class Inventory { private InventoryHolder holder; private ItemStack[] slots;
public Inventory(InventoryHolder h,int n){holder=h;slots=new ItemStack[n];} public void clear(){java.util.Arrays.fill(slots,null);} public void setItem(int n,ItemStack i){slots[n]=i;} public int getSize(){return slots.length;} public InventoryHolder getHolder(){return holder;} }
''',
'org/bukkit/inventory/InventoryView.java':'''package org.bukkit.inventory; public class InventoryView { private Inventory top; public InventoryView(Inventory i){top=i;} public Inventory getTopInventory(){return top;} }''',
'org/bukkit/entity/Player.java':'''package org.bukkit.entity;
import java.util.UUID;
import org.bukkit.*;import org.bukkit.inventory.*;
public class Player { private final UUID id; private InventoryView view;
public Player(UUID id){this.id=id;} public UUID getUniqueId(){return id;} public boolean hasPermission(String s){return true;} public boolean isOnline(){return true;}
public void openInventory(Inventory i){view=new InventoryView(i);} public InventoryView getOpenInventory(){return view;}
public void closeInventory(){view=new InventoryView(new Inventory(null,9));} public Location getLocation(){return new Location();} public void playSound(Location l,Sound s,float v,float p){} }
''',
'org/bukkit/Bukkit.java':'''package org.bukkit;
import java.util.*; import org.bukkit.entity.Player; import org.bukkit.inventory.*; import net.kyori.adventure.text.Component;
public class Bukkit { public static Map<UUID,Player> users=new HashMap<>(); public static Player getPlayer(UUID id){return users.get(id);} public static Inventory createInventory(InventoryHolder h,int size,Component title){return new Inventory(h,size);} }
''',
'org/bukkit/event/Listener.java':'package org.bukkit.event; public interface Listener {}\n',
'org/bukkit/event/EventHandler.java':'package org.bukkit.event; public @interface EventHandler {}\n',
'org/bukkit/event/inventory/ClickType.java':'package org.bukkit.event.inventory; public enum ClickType {LEFT, RIGHT, SHIFT_LEFT, NUMBER_KEY, DOUBLE_CLICK} \n',
'org/bukkit/event/inventory/InventoryClickEvent.java':'''package org.bukkit.event.inventory;
import org.bukkit.entity.Player;import org.bukkit.inventory.*;
public class InventoryClickEvent {private InventoryView view; private Player player;private int slot;private ClickType click;private boolean cancel;
public InventoryClickEvent(InventoryView v,Player p,int slot,ClickType c){view=v;player=p;this.slot=slot;click=c;}
public InventoryView getView(){return view;} public Player getWhoClicked(){return player;} public int getRawSlot(){return slot;} public ClickType getClick(){return click;} public void setCancelled(boolean b){cancel=b;} public boolean isCancelled(){return cancel;} }
''',
'org/bukkit/event/inventory/InventoryDragEvent.java':'''package org.bukkit.event.inventory; import org.bukkit.inventory.*;
public class InventoryDragEvent {private InventoryView v; private boolean cancelled; public InventoryDragEvent(InventoryView v){this.v=v;} public InventoryView getView(){return v;} public void setCancelled(boolean b){cancelled=b;} public boolean isCancelled(){return cancelled;} }
''',
'org/bukkit/event/inventory/InventoryCloseEvent.java':'''package org.bukkit.event.inventory; import org.bukkit.inventory.*;
public class InventoryCloseEvent {private Inventory inv; public InventoryCloseEvent(Inventory i){inv=i;} public Inventory getInventory(){return inv;} }
''',
'org/bukkit/event/player/PlayerQuitEvent.java':'package org.bukkit.event.player; import org.bukkit.entity.Player; public class PlayerQuitEvent {private Player p; public PlayerQuitEvent(Player p){this.p=p;} public Player getPlayer(){return p;}}\n',
'com/oheers/fish/fishing/simulator/SimulatorModule.java':'''package com.oheers.fish.fishing.simulator;
import java.util.*;import org.bukkit.entity.Player;import com.oheers.fish.fishing.simulator.presentation.UiProfile;
public class SimulatorModule { public UiProfile profile=UiProfile.BALANCED; public int changes; public void prefetchHud(UUID id){} public UiProfile ownHudProfile(UUID id){return profile;} public void setOwnHudProfile(UUID id,UiProfile p){profile=p;changes++;} }
''',
'com/oheers/fish/fishing/simulator/SimulatorMessages.java':'''package com.oheers.fish.fishing.simulator; import java.util.*; import org.bukkit.entity.Player;
public class SimulatorMessages { public static void send(Player p,String key,Map<String,String> vars){} }
'''
}
for path,content in stubs.items():
    file=r/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(content)
