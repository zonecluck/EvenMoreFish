import com.oheers.fish.fishing.simulator.population.*;
import com.oheers.fish.fishing.simulator.school.*;
import com.oheers.fish.fishing.simulator.visual.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dependency-free executable integration assertions for Phase 12 (NOT a Paper client test). */
public final class SimulatorPremiumPhase12Smoke {
    static final UUID WORLD=UUID.nameUUIDFromBytes("world".getBytes(StandardCharsets.UTF_8));
    static final UUID A=UUID.nameUUIDFromBytes("player-A".getBytes(StandardCharsets.UTF_8));
    static final UUID B=UUID.nameUUIDFromBytes("player-B".getBytes(StandardCharsets.UTF_8));
    static final FishKey KEY=new FishKey("common","river-cod");
    static int ids;
    static WaterPoint cell(int x,int z){return new WaterPoint(WORLD,x,62,z);}
    static FishAgent fish(int x,int z,String zone,UUID owner,FishVisualModel model){
        WaterPoint p=cell(x,z);
        FishAgent f=new FishAgent(UUID.randomUUID(),1_200_000_000+ids++,KEY,model,zone,WORLD,owner,9999,p.swimmingPosition());
        f.placeAt(p);return f;
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static final class Grid implements FishSchoolService.Navigation {
        int allowedChecks;
        @Override public List<WaterPoint> adjacent(WaterPoint p){
            return List.of(cell(p.x()+1,p.z()),cell(p.x()-1,p.z()),cell(p.x(),p.z()+1),cell(p.x(),p.z()-1));
        }
        @Override public boolean traversable(FishAgent f,WaterPoint p){
            allowedChecks++;
            return f.worldId().equals(p.worldId()) && p.x()>=0 && p.x()<=25 && p.z()>=0 && p.z()<=25
                && !(p.x()==10 && p.z()==10); // simulated dry/invalid chunk
        }
    }
    static final class Mock implements PacketTransport {
        public boolean available(){return true;}
        public boolean spawn(UUID viewer,FishAgent fish){return true;}
        public boolean teleport(UUID viewer,FishAgent fish){return true;}
        public boolean destroy(UUID viewer,int id){return true;}
    }
    public static void main(String[] args){
        SchoolSettings settings=new SchoolSettings(true,10,4,4,7,19,4,3,12,10,6,12,1.25,.12,.14);
        FishPopulationService pop=new FishPopulationService(160,80,20,12);
        FishSchoolService school=new FishSchoolService(settings);Grid nav=new Grid();
        FishAgent leader=fish(8,8,"lake",null,FishVisualModel.COD);
        FishAgent follower=fish(8,9,"lake",null,FishVisualModel.COD);
        FishAgent third=fish(9,8,"lake",null,FishVisualModel.COD);
        FishAgent fourth=fish(9,9,"lake",null,FishVisualModel.COD);
        for(FishAgent f:List.of(leader,follower,third,fourth)){
            check(pop.add(f) && school.onSpawn(f,pop,1),"attach shared school");
        }
        UUID id=school.schoolId(leader.id());
        check(id!=null && id.equals(school.schoolId(follower.id())) &&
            school.school(id).size()==4 && school.schools()==1,"four fish in one shared group");
        FishAgent differentModel=fish(8,10,"lake",null,FishVisualModel.SALMON);
        check(pop.add(differentModel) && school.onSpawn(differentModel,pop,1),"new species forms own group");
        check(!id.equals(school.schoolId(differentModel.id())),"species visual model boundary");
        FishAgent otherZone=fish(8,8,"other-zone",null,FishVisualModel.COD);
        check(pop.add(otherZone) && school.onSpawn(otherZone,pop,1),"other zone new group");
        check(!id.equals(school.schoolId(otherZone.id())),"different zone never joined");
        UUID alternateWorld=UUID.nameUUIDFromBytes("other-world".getBytes(StandardCharsets.UTF_8));
        WaterPoint foreignCell=new WaterPoint(alternateWorld,8,62,8);
        FishAgent foreign=new FishAgent(UUID.randomUUID(),1_200_000_000+ids++,KEY,FishVisualModel.COD,
            "lake",alternateWorld,null,9999,foreignCell.swimmingPosition());
        foreign.placeAt(foreignCell);
        check(pop.add(foreign) && school.onSpawn(foreign,pop,1),"another world has own school capacity");
        check(!id.equals(school.schoolId(foreign.id())),"same zone id in different worlds must not mix");
        FishAgent privateA=fish(8,8,"lake",A,FishVisualModel.COD);
        FishAgent privateB=fish(9,8,"lake",B,FishVisualModel.COD);
        check(pop.add(privateA) && pop.add(privateB),"private population cap");
        check(school.onSpawn(privateA,pop,1) && school.onSpawn(privateB,pop,1),"private groups created");
        check(!school.schoolId(privateA.id()).equals(school.schoolId(privateB.id())),"private owners isolated");
        check(!id.equals(school.schoolId(privateA.id())),"shared != private");
        check(!privateA.visibleTo(B) && privateA.visibleTo(A),"private viewer policy preserved");
        WaterPoint prefer=school.preferredSpawnCell(WORLD,"lake",null,FishVisualModel.SALMON,pop,nav,2);
        check(prefer!=null && nav.traversable(differentModel,prefer),"nearby valid spawn point");
        check(school.preferredSpawnCell(WORLD,"lake",null,FishVisualModel.COD,pop,nav,2)==null,
            "full school must not attract more agents");
        check(school.preferredSpawnCell(WORLD,"lake",A,FishVisualModel.SALMON,pop,nav,2)==null,
            "do not silently join incompatible owner/species");

        Vec3 old=leader.position();long checkedTotal=0,decidedTotal=0;
        for(int tick=4;tick<=150;tick+=2){
            long beforeCheck=school.neighborChecks(), beforeDec=school.decisions();
            school.steer(tick,pop,nav);
            check(school.neighborChecks()-beforeCheck<=settings.neighborChecksPerMove(),"nav budget violated");
            check(school.decisions()-beforeDec<=settings.decisionsPerMove(),"direction budget violated");
            checkedTotal+=school.neighborChecks()-beforeCheck;
            decidedTotal+=school.decisions()-beforeDec;
            for(FishAgent agent:pop.agents())if(agent.state()==FishAgent.State.WANDERING){
                agent.move(tick,school.assigned(agent.id())?school.speed(agent):.12);
                check(agent.position().x()>=-0.01 && agent.position().x()<=26 &&
                    agent.position().z()>=-0.01 && agent.position().z()<=26,"out-of-water movement");
                check(!(agent.cell().x()==10 && agent.cell().z()==10),"agent traversed invalid water cell");
            }
        }
        check(old.distanceSquared(leader.position())>.02,"leader must move along cached water");
        check(decidedTotal>0 && checkedTotal>0,"steering budget actually exercised");
        FishAgent turning=fish(2,2,"lake",null,FishVisualModel.COD);
        turning.travelTo(cell(3,2));turning.move(1,.2);
        float beforeYaw=turning.yaw();
        turning.placeAt(cell(2,2));turning.travelTo(cell(1,2));turning.move(2,.2);
        float turnDelta=Math.abs((turning.yaw()-beforeYaw+540f)%360f-180f);
        check(turnDelta<=28.01f && turnDelta>=0,"packet yaw rotation must be eased");
        // A reserved fish must hand authority to the AI; schooling cannot alter its route.
        UUID hook=UUID.randomUUID();
        check(pop.reserve(follower.id(),hook,A),"reserve shared fish");
        check(follower.interested(hook) && follower.approaching(hook),"AI state transition");
        Vec3 before=follower.position();
        school.steer(152,pop,nav);
        check(!school.assigned(follower.id()),"reserved agent detached from schooling");
        check(follower.reservedFor(hook) && follower.state()==FishAgent.State.APPROACHING &&
            follower.position().equals(before),"AI target left untouched");
        check(pop.release(follower.id(),hook),"AI reservation release");
        // Detach / leader election after fish is caught or water chunk unloads.
        check(pop.remove(leader.id()),"despawn leader");
        school.steer(154,pop,nav);
        check(school.school(id)!=null && !school.school(id).leaderId().equals(leader.id()),"leader election");
        FishAgent elected=pop.get(school.school(id).leaderId());
        school.scatter(elected.position(),WORLD,"lake",null,pop,160);
        check(school.school(id).behavior()==FishSchool.Behavior.SCATTERED,"scatter after interaction");
        school.steer(160+settings.scatterTicks(),pop,nav);
        check(school.school(id).behavior()==FishSchool.Behavior.REFORMING,"scatter must transition into regrouping");
        school.steer(160+settings.scatterTicks()+settings.reformTicks(),pop,nav);
        check(school.school(id).behavior()==FishSchool.Behavior.CRUISING,"school must finish reforming");
        check(school.scatters()>0,"scatter metrics");
        check(school.members()<=pop.size(),"no ghost school members");
        // Large headless scenario: groups never increase fish population or packet budget.
        syntheticLoad();
        FishSchoolService off=new FishSchoolService(SchoolSettings.disabled());
        FishAgent noSchool=fish(3,3,"disabled",null,FishVisualModel.COD);
        check(pop.add(noSchool) && !off.onSpawn(noSchool,pop,1) && off.schools()==0,"feature flag preserves solo roaming");
        int original=pop.size();school.clear();
        check(school.schools()==0 && school.members()==0 && pop.size()==original,"reload resets schools, not fish rewards");
        System.out.println("PASS: schooling 4-member grouping, private/world/zone/species isolation, " +
            "reservation handoff, path limits, yaw, scatter/reform, leader re-election, LOD, " +
            "160-fish/100-viewer packet-budget simulation");
    }
    static void syntheticLoad(){
        FishPopulationService pop=new FishPopulationService(160,160,0,0);
        FishSchoolService school=new FishSchoolService(new SchoolSettings(true,32,8,6,36,144,8,6,32,28,6,12,1.3,.12,.14));
        Grid nav=new Grid();
        for(int i=0;i<160;i++){
            FishAgent f=fish(i%20, i/20,"arena",null,FishVisualModel.COD);
            check(pop.add(f),"stress population add");school.onSpawn(f,pop,1);
        }
        check(pop.size()==160 && school.schools()<=32,"hard group cap");
        List<PacketVisibilityService.Viewer> viewers=new ArrayList<>();
        for(int i=0;i<100;i++)viewers.add(new PacketVisibilityService.Viewer(
            UUID.nameUUIDFromBytes(("viewer"+i).getBytes(StandardCharsets.UTF_8)),WORLD,"arena",new Vec3(10,62.3,10)));
        PacketVisibilityService vis=new PacketVisibilityService(new Mock(),20,800,40);
        long oldSent=0;
        for(int tick=2;tick<=50;tick+=2){
            long checks=school.neighborChecks(),decisions=school.decisions();
            school.steer(tick,pop,nav);
            check(school.neighborChecks()-checks<=144 && school.decisions()-decisions<=36,"synthetic steering budget");
            for(FishAgent agent:pop.agents())if(agent.state()==FishAgent.State.WANDERING)
                agent.move(tick,school.assigned(agent.id())?school.speed(agent):.12);
            vis.beginTick();vis.sync(viewers,new ArrayList<>(pop.agents()),tick,true);
            check(vis.usedThisTick()<=800 && vis.packetsSent()-oldSent<=800,"packet LOD budget");
            oldSent=vis.packetsSent();
        }
        check(vis.totalVisible()>0 && vis.totalVisible()<=2000,"viewer LOD selection cap");
        pop.clear();
        for(int tick=52;tick<=80;tick++){
            vis.beginTick();vis.sync(viewers,List.of(),tick,false);
            check(vis.usedThisTick()<=800,"destroy budget");
        }
        check(vis.totalVisible()==0,"no dangling visual fish after despawn");
        school.reconcile(pop);
        check(school.members()==0,"removed fish pruned from groups");
    }
}
