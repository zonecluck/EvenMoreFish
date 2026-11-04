package org.bukkit.entity;
import org.bukkit.*;
import java.util.UUID;
import net.kyori.adventure.sound.Sound;
public class Player {
 private final UUID id;
 private Location location;
 private boolean online=true;
 public Player(UUID id,Location loc){this.id=id;this.location=loc;}
 public UUID getUniqueId(){return id;}
 public World getWorld(){return location.getWorld();}
 public Location getLocation(){return location;}
 public Location getEyeLocation(){return location.clone().add(0,1.62,0);}
 public boolean isOnline(){return online;}
 public void setOnline(boolean value){online=value;}
 public void teleport(Location loc){location=loc;}
 public void playSound(Sound sound, Sound.Emitter emitter){}
 public void spawnParticle(Particle particle, Location loc, int count,double ox,double oy,double oz,double extra){}
 public <T> void spawnParticle(Particle particle, Location loc, int count,double ox,double oy,double oz,double extra,T data){}
}
