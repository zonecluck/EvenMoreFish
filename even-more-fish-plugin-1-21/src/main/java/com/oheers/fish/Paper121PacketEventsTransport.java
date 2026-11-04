package com.oheers.fish;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemModel;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector3f;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.oheers.fish.fishing.simulator.model.FishRenderSpec;
import org.bukkit.Material;
import java.util.Map;
import java.util.HashMap;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.List;
import java.util.UUID;

/**
 * PacketEvents 2.14.0 integration for the Paper 1.21 variant only.
 * NO NMS, Bukkit entity spawn, client packet listener, or reflection.
 * Requires external PacketEvents plugin (compileOnly, not shaded).
 */
public final class Paper121PacketEventsTransport implements PacketTransport {
    private final EvenMoreFish plugin;
    private boolean tripped;
    private int failures;
    // Compiled ItemModel components cached; never reuse mutable Bukkit item for reward.
    private final Map<FishRenderSpec,com.github.retrooper.packetevents.protocol.item.ItemStack> itemTemplates = new HashMap<>();
    @Override public boolean supportsCustom(UUID viewerId) {
        if(!available())return false;
        Player viewer=Bukkit.getPlayer(viewerId);
        if(viewer==null || !viewer.isOnline())return false;
        try{return PacketEvents.getAPI().getPlayerManager().getClientVersion(viewer)
            .isNewerThanOrEquals(ClientVersion.V_1_21_4);}
        catch(LinkageError | RuntimeException failure){return false;}
    }

    public Paper121PacketEventsTransport(EvenMoreFish plugin){this.plugin=plugin;}
    @Override public boolean available() {
        return !tripped && backendReady();
    }
    /** Visual failures stop spawning/movement, but cleanup must still reach a healthy backend. */
    private boolean backendReady() {
        Plugin installed = Bukkit.getPluginManager().getPlugin("packetevents");
        if(installed == null || !installed.isEnabled())return false;
        try{return PacketEvents.getAPI()!=null && PacketEvents.getAPI().getPlayerManager()!=null;}
        catch(LinkageError | RuntimeException error){trip(error);return false;}
    }
    @Override public boolean spawn(UUID viewerId,FishAgent agent) {
        return spawn(viewerId,agent,FishRenderSpec.vanilla(agent.model()));
    }
    @Override public boolean spawn(UUID viewerId,FishAgent agent,FishRenderSpec render) {
        Player player=Bukkit.getPlayer(viewerId);
        if(player==null || !player.isOnline() || !available() || !player.getWorld().getUID().equals(agent.worldId())
            || (render.isCustom() && !supportsCustom(viewerId)))return false;
        try{
            var p=agent.position();
            EntityType type=render.isCustom()?EntityTypes.ITEM_DISPLAY:switch(agent.model()) {
                case COD -> EntityTypes.COD;
                case SALMON -> EntityTypes.SALMON;
                case TROPICAL_FISH -> EntityTypes.TROPICAL_FISH;
                case PUFFERFISH -> EntityTypes.PUFFERFISH;
            };
            var packet=new WrapperPlayServerSpawnEntity(agent.entityId(),agent.id(),type,
                new Location(p.x(),p.y(),p.z(),agent.yaw(),0f),agent.yaw(),0,new Vector3d(0,0,0));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,packet);
            // 1.21.4+ ItemDisplay metadata: #10 teleport interpolation, #12 scale,
            // #23 displayed stack, #24 FIXED transform context. Only one metadata packet.
            // No server Entity / NMS / invisible stand / extra entity ID.
            if(render.isCustom()) {
                var template=displayItem(render);
                PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerEntityMetadata(agent.entityId(),List.of(
                        new EntityData<>(10,EntityDataTypes.INT,render.interpolationTicks()),
                        new EntityData<>(12,EntityDataTypes.VECTOR3F,new Vector3f(render.scale(),render.scale(),render.scale())),
                        new EntityData<>(23,EntityDataTypes.ITEMSTACK,template.copy()),
                        new EntityData<>(24,EntityDataTypes.BYTE,(byte)8)
                    )));
            } else {
                // Native fish #5: no gravity. Old packet output unchanged.
                PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerEntityMetadata(agent.entityId(),List.of(new EntityData<>(5,EntityDataTypes.BOOLEAN,true))));
            }
            return true;
        }catch(LinkageError | RuntimeException error){
            // Spawn may have reached the client before metadata failed: best-effort destroy.
            try{PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerDestroyEntities(agent.entityId()));}
            catch(LinkageError | RuntimeException destroyFailure) {
                plugin.getLogger().warning("[Simulator] Failed to undo partial fish spawn: " + destroyFailure.getClass().getSimpleName());
            }
            trip(error);return false;
        }
    }
    private com.github.retrooper.packetevents.protocol.item.ItemStack displayItem(FishRenderSpec spec) {
        return itemTemplates.computeIfAbsent(spec,render -> {
            var bukkit=new org.bukkit.inventory.ItemStack(Material.valueOf(render.material()));
            var packet=SpigotConversionUtil.fromBukkitItemStack(bukkit);
            packet.setComponent(ComponentTypes.ITEM_MODEL,new ItemModel(new ResourceLocation(render.itemModel())));
            return packet;
        });
    }
    @Override public boolean teleport(UUID viewerId,FishAgent agent){
        Player player=Bukkit.getPlayer(viewerId);
        if(player==null || !player.isOnline() || !available())return false;
        try{
            var p=agent.position();
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerEntityTeleport(agent.entityId(),new Vector3d(p.x(),p.y(),p.z()),agent.yaw(),0f,false));
            return true;
        }catch(LinkageError | RuntimeException error){trip(error);return false;}
    }
    @Override public boolean destroy(UUID viewerId,int entityId){
        Player player=Bukkit.getPlayer(viewerId);
        if(player==null || !player.isOnline())return true; // disconnected clients already clear entity state
        if(!backendReady())return false;
        try{
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,new WrapperPlayServerDestroyEntities(entityId));
            return true;
        }catch(LinkageError | RuntimeException error){trip(error);return false;}
    }
    private void trip(Throwable error){
        if(!tripped){plugin.getLogger().severe("[Simulator] PacketEvents transport disabled (" + error.getClass().getSimpleName() + "): " + error.getMessage());}
        failures++;tripped=true; // fail closed; never repeatedly spam packets or logs
    }
}
