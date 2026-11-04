package com.oheers.fish.api.events;
import com.oheers.fish.fishing.items.Fish;
import org.bukkit.entity.Player;
public final class EMFFishCaughtEvent {
    private final Fish fish;private final Player player;
    public EMFFishCaughtEvent(Fish f,Player p){fish=f;player=p;}
    public Fish getFish(){return fish;}
    public Player getPlayer(){return player;}
}
