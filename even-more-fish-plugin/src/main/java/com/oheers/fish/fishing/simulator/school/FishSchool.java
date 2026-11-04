package com.oheers.fish.fishing.simulator.school;

import com.oheers.fish.fishing.simulator.population.Vec3;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Owner-thread only; school is a logical group of existing packet fish, not an extra entity. */
public final class FishSchool {
    public enum Behavior { CRUISING, TURNING, SCATTERED, REFORMING }
    private final UUID id;
    private final UUID worldId;
    private final String zoneId;
    private final UUID privateOwner;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    private final Vec3 anchor;
    private UUID leaderId;
    private Behavior behavior = Behavior.CRUISING;
    private double headingX=1, headingZ=0;
    private Vec3 disturbance;
    private long behaviorUntil;
    private long lastDecisionTick=Long.MIN_VALUE;

    FishSchool(UUID id, UUID worldId, String zoneId, UUID owner, UUID leader, Vec3 anchor) {
        this.id=Objects.requireNonNull(id);
        this.worldId=Objects.requireNonNull(worldId);
        this.zoneId=Objects.requireNonNull(zoneId);
        this.privateOwner=owner;
        this.leaderId=Objects.requireNonNull(leader);
        this.anchor=Objects.requireNonNull(anchor);
        this.members.add(leader);
    }
    public UUID id(){return id;}
    public UUID worldId(){return worldId;}
    public String zoneId(){return zoneId;}
    public UUID privateOwner(){return privateOwner;}
    public UUID leaderId(){return leaderId;}
    public Set<UUID> memberIds(){return Set.copyOf(members);}
    public int size(){return members.size();}
    public Behavior behavior(){return behavior;}
    public Vec3 anchor(){return anchor;}
    public double headingX(){return headingX;}
    public double headingZ(){return headingZ;}
    public long lastDecisionTick(){return lastDecisionTick;}

    boolean add(UUID fishId,int cap){return members.size()<cap && members.add(fishId);}
    boolean remove(UUID fishId) {
        if(!members.remove(fishId))return false;
        if(fishId.equals(leaderId))leaderId=members.isEmpty()?null:members.iterator().next();
        return true;
    }
    Set<UUID> orderedMembers(){return members;}
    Vec3 disturbance(){return disturbance;}
    void heading(double dx,double dz,long tick) {
        double norm=Math.hypot(dx,dz);
        if(norm<.000001)return;
        double nx=dx/norm,nz=dz/norm;
        if(headingX*nx+headingZ*nz < .15 && behavior==Behavior.CRUISING)behavior=Behavior.TURNING;
        headingX=nx;headingZ=nz;lastDecisionTick=tick;
        if(behavior==Behavior.TURNING)behaviorUntil=tick+5;
    }
    void decide(long tick){lastDecisionTick=tick;}
    void scatter(Vec3 source,long now,long duration) {
        disturbance=source;
        behavior=Behavior.SCATTERED;
        behaviorUntil=now+duration;
    }
    void update(long tick,int reformTicks) {
        if(behavior==Behavior.SCATTERED && tick>=behaviorUntil){behavior=Behavior.REFORMING;behaviorUntil=tick+reformTicks;}
        else if((behavior==Behavior.REFORMING || behavior==Behavior.TURNING) && tick>=behaviorUntil){
            behavior=Behavior.CRUISING;disturbance=null;
        }
    }
}
