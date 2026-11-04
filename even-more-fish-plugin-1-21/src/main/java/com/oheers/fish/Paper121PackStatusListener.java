package com.oheers.fish;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Pack-ID aware resource pack gate. No model is sent without a matching successful ACK. */
public final class Paper121PackStatusListener implements Listener {
    private final EMFModule owner;
    public Paper121PackStatusListener(EMFModule owner){this.owner=owner;}
    @EventHandler(priority=EventPriority.MONITOR)
    public void status(PlayerResourcePackStatusEvent event) {
        var module=owner.getSimulatorModule();
        if(module==null)return;
        module.modelPackAudience().status(event.getPlayer().getUniqueId(),event.getID(),
            event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        var module=owner.getSimulatorModule();
        if(module!=null)module.modelPackAudience().disconnected(event.getPlayer().getUniqueId());
    }
}
