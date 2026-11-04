package com.oheers.fish.fishing.simulator.population;

import java.util.Objects;
import java.util.UUID;

/** Mutable only on the Paper owner ticker. Never an actual Bukkit entity or a catch reward. */
public final class FishAgent {
    public enum State { WANDERING, RESERVED, INTERESTED, APPROACHING, BITE_READY, FLEEING, CAUGHT, DESPAWNED }
    private final UUID id;
    private final int entityId;
    private final FishKey fishKey;
    private final FishVisualModel model;
    private final String zoneId;
    private final UUID worldId;
    private final UUID privateOwner; // null = shared, otherwise PRIVATE
    private final long expiresAtTick;
    private Vec3 position;
    private Vec3 waypoint;
    private WaterPoint currentCell;
    private WaterPoint nextCell;
    private State state = State.WANDERING;
    private UUID reservedBy;
    private float yaw;
    private long lastMovedTick;

    public FishAgent(UUID id, int entityId, FishKey fishKey, FishVisualModel model, String zoneId,
                     UUID worldId, UUID privateOwner, long expiresAtTick, Vec3 position) {
        this.id=Objects.requireNonNull(id);
        this.entityId=entityId;
        this.fishKey=Objects.requireNonNull(fishKey);
        this.model=Objects.requireNonNull(model);
        this.zoneId=Objects.requireNonNull(zoneId);
        this.worldId=Objects.requireNonNull(worldId);
        this.privateOwner=privateOwner;
        this.expiresAtTick=expiresAtTick;
        this.position=Objects.requireNonNull(position);
        this.waypoint=position;
    }
    public UUID id(){return id;}
    public int entityId(){return entityId;}
    public FishKey fishKey(){return fishKey;}
    public FishVisualModel model(){return model;}
    public String zoneId(){return zoneId;}
    public UUID worldId(){return worldId;}
    public UUID privateOwner(){return privateOwner;}
    public long expiresAtTick(){return expiresAtTick;}
    public Vec3 position(){return position;}
    public float yaw(){return yaw;}
    public long lastMovedTick(){return lastMovedTick;}
    public State state(){return state;}
    public boolean reserved(){return reservedBy!=null;}
    /** Read-only proof of reservation ownership for the reward bridge. */
    public boolean reservedFor(UUID session){return session!=null && session.equals(reservedBy);}
    public Vec3 waypoint(){return waypoint;}
    public WaterPoint cell(){return currentCell;}
    public WaterPoint nextCell(){return nextCell;}
    public void placeAt(WaterPoint cell) {
        if (!worldId.equals(cell.worldId())) throw new IllegalArgumentException("wrong world");
        currentCell=cell;nextCell=null;position=cell.swimmingPosition();waypoint=position;
    }
    public void travelTo(WaterPoint next) {
        if(state!=State.WANDERING || currentCell==null || !worldId.equals(next.worldId()))return;
        if(Math.abs(currentCell.x()-next.x())>1 || Math.abs(currentCell.z()-next.z())>1 || Math.abs(currentCell.y()-next.y())>1)return;
        nextCell=next;waypoint=next.swimmingPosition();
    }
    public boolean visibleTo(UUID viewer){return privateOwner == null || privateOwner.equals(viewer);}
    public boolean isExpired(long tick) {return tick >= expiresAtTick;}
    public void waypoint(Vec3 target) {if(state==State.WANDERING) waypoint=Objects.requireNonNull(target);}
    public boolean move(long tick, double distance) {
        if((state != State.WANDERING && state != State.APPROACHING && state != State.FLEEING)
            || position.distanceSquared(waypoint) < 0.0025) return false;
        Vec3 next=position.stepTowards(waypoint, distance);
        double dx=next.x()-position.x(), dz=next.z()-position.z();
        if (dx*dx+dz*dz > 0.000001) {
            float desired=(float)Math.toDegrees(Math.atan2(-dx,dz));
            float diff=(desired-yaw+540f)%360f-180f;
            // Packet yaw eases around the shortest turn: positions remain server-authoritative.
            yaw+=Math.max(-28f,Math.min(28f,diff));
        }
        position=next;
        if(position.distanceSquared(waypoint) < 0.0025) {
            if(nextCell!=null){currentCell=nextCell;nextCell=null;}
            if(state==State.FLEEING)state=State.WANDERING;
        }
        lastMovedTick=tick;
        return true;
    }
    public boolean reserve(UUID session, UUID owner) {
        if(state!=State.WANDERING || !visibleTo(owner)) return false;
        reservedBy=Objects.requireNonNull(session);
        nextCell=null;waypoint=position;
        state=State.RESERVED;
        return true;
    }
    public boolean interested(UUID session) {
        if(state!=State.RESERVED || !Objects.equals(reservedBy, session))return false;
        state=State.INTERESTED;return true;
    }
    public boolean approaching(UUID session) {
        if(state!=State.INTERESTED || !Objects.equals(reservedBy,session))return false;
        state=State.APPROACHING;return true;
    }
    public boolean approachTo(UUID session,WaterPoint next) {
        if(state!=State.APPROACHING || !Objects.equals(reservedBy,session)
            || currentCell==null || nextCell!=null || !adjacent(currentCell,next))return false;
        nextCell=next;waypoint=next.swimmingPosition();return true;
    }
    /** A reserved fish may have been half-way between two cells when targeted. */
    public boolean recenterAtGoal(UUID session,WaterPoint goal) {
        if(state!=State.APPROACHING || !Objects.equals(reservedBy,session) || nextCell!=null
            || currentCell==null || !currentCell.equals(goal))return false;
        waypoint=goal.swimmingPosition();return true;
    }
    public boolean readyToBite(UUID session) {
        if(state!=State.APPROACHING || !Objects.equals(reservedBy,session) || nextCell!=null)return false;
        waypoint=position;state=State.BITE_READY;return true;
    }
    /** Release never grants a reward; it also cancels any pending waypoint. */
    public boolean release(UUID session) {
        if(reservedBy==null || !Objects.equals(reservedBy,session))return false;
        reservedBy=null;nextCell=null;waypoint=position;state=State.WANDERING;return true;
    }
    /** Optional one-cell flee animation after a failed bite; never crosses dry cells. */
    public boolean fleeTo(WaterPoint next) {
        if(state!=State.WANDERING || currentCell==null || !adjacent(currentCell,next))return false;
        nextCell=next;waypoint=next.swimmingPosition();state=State.FLEEING;return true;
    }
    private boolean adjacent(WaterPoint first,WaterPoint second) {
        return worldId.equals(second.worldId()) &&
            Math.abs(first.x()-second.x())+Math.abs(first.z()-second.z())==1 &&
            Math.abs(first.y()-second.y())<=1;
    }
    public boolean consume(UUID session) {
        if(reservedBy==null || !Objects.equals(reservedBy,session))return false;
        reservedBy=null;nextCell=null;state=State.CAUGHT;return true;
    }
    public void despawn() { state=State.DESPAWNED; reservedBy=null; }
}
