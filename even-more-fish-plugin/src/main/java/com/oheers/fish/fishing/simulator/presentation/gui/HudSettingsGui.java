package com.oheers.fish.fishing.simulator.presentation.gui;

import com.oheers.fish.fishing.simulator.SimulatorMessages;
import com.oheers.fish.fishing.simulator.SimulatorModule;
import com.oheers.fish.fishing.simulator.presentation.UiProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One safe inventory session per player, all Bukkit API calls on the Paper primary thread.
 * Action map is server-owned; an item name never decides a button action.
 */
public final class HudSettingsGui implements Listener {
    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private final UUID sessionId=UUID.randomUUID();
        private Inventory inventory;
        private Holder(UUID owner){this.owner=owner;}
        @Override public @NotNull Inventory getInventory(){return inventory;}
    }
    private record Session(Holder holder, Inventory inventory, Map<Integer,UiProfile> actions) {}
    private final SimulatorModule module;
    private HudGuiSettings settings=HudGuiSettings.disabled();
    private final Map<UUID,Session> sessions=new HashMap<>();

    public HudSettingsGui(SimulatorModule module){this.module=module;}
    public void configure(HudGuiSettings settings){
        // Old inventory must be invalidated on reload; a stale event cannot click a new session.
        closeAll();this.settings=settings;
    }
    public boolean open(Player player){
        if(!settings.enabled() || !player.hasPermission("emf.simulator.preferences"))return false;
        UUID id=player.getUniqueId();
        module.prefetchHud(id);
        Holder holder=new Holder(id);
        Inventory inv=Bukkit.createInventory(holder,settings.size(),settings.title());
        holder.inventory=inv;
        var actions=new HashMap<Integer,UiProfile>();
        settings.buttons().forEach((profile,button)->actions.put(button.slot(),profile));
        Session session=new Session(holder,inv,Map.copyOf(actions));
        sessions.put(id,session);render(player,session);
        player.openInventory(inv);
        return true;
    }
    private void render(Player player,Session session){
        session.inventory().clear();
        ItemStack filler=new ItemStack(settings.filler());
        ItemMeta meta=filler.getItemMeta();
        meta.displayName(settings.fillerName().decoration(TextDecoration.ITALIC,false));
        filler.setItemMeta(meta);
        for(int slot=0;slot<session.inventory().getSize();slot++)session.inventory().setItem(slot,filler);
        UiProfile selected=module.ownHudProfile(player.getUniqueId());
        settings.buttons().forEach((profile,button)->{
            ItemStack item=new ItemStack(button.material());
            ItemMeta itemMeta=item.getItemMeta();
            itemMeta.displayName(button.name().decoration(TextDecoration.ITALIC,false));
            itemMeta.lore(button.lore().stream().map(v->v.decoration(TextDecoration.ITALIC,false)).toList());
            if(button.glow() || selected==profile){
                Enchantment glow=Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("unbreaking"));
                if(glow!=null)itemMeta.addEnchant(glow,1,true);
                itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(itemMeta);
            session.inventory().setItem(button.slot(),item);
        });
    }
    @EventHandler public void click(InventoryClickEvent event){
        Inventory top=event.getView().getTopInventory();
        if(!(top.getHolder() instanceof Holder holder))return;
        event.setCancelled(true); // cancel first, including bottom inventory/hotbar moves
        if(!(event.getWhoClicked() instanceof Player player))return;
        Session session=sessions.get(player.getUniqueId());
        if(session==null || session.holder()!=holder || session.inventory()!=top ||
            !holder.owner.equals(player.getUniqueId()))return;
        int slot=event.getRawSlot();
        if(slot<0 || slot>=top.getSize() ||
            (event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT))return;
        UiProfile profile=session.actions().get(slot);
        if(profile==null || !player.hasPermission("emf.simulator.preferences"))return;
        module.setOwnHudProfile(player.getUniqueId(),profile);
        player.playSound(player.getLocation(),settings.clickSound(),.65f,1.15f);
        render(player,session);
        SimulatorMessages.send(player,"presentation-changed",Map.of("profile",profile.name()));
    }
    @EventHandler public void drag(InventoryDragEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder))return;
        // Lock the full GUI while open; even bottom-inventory drag should not modify top slots.
        event.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent event){
        if(!(event.getInventory().getHolder() instanceof Holder holder))return;
        Session session=sessions.get(holder.owner);
        if(session!=null && session.holder()==holder && session.inventory()==event.getInventory())
            sessions.remove(holder.owner);
    }
    @EventHandler public void quit(PlayerQuitEvent event){sessions.remove(event.getPlayer().getUniqueId());}
    public void closeAll(){
        for(var entry:Map.copyOf(sessions).entrySet()){
            Player player=Bukkit.getPlayer(entry.getKey());
            if(player!=null && player.isOnline() && player.getOpenInventory().getTopInventory()==entry.getValue().inventory())
                player.closeInventory();
        }
        sessions.clear();
    }
    public int openSessions(){return sessions.size();}
}
