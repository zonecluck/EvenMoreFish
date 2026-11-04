package org.bukkit.entity;
import java.util.UUID;
public final class Player {private final UUID uuid; public Player(UUID id){uuid=id;}
 public UUID getUniqueId(){return uuid;}public boolean isOnline(){return true;}}
