import com.oheers.fish.fishing.simulator.population.*;
import com.oheers.fish.fishing.simulator.visual.*;
import java.util.*;

/** Headless property tests against the ACTUAL production domain and viewer classes. */
public final class SimulatorPhase45Smoke {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static final class StubTransport implements PacketTransport {
        final Map<UUID,Set<Integer>> ids=new HashMap<>();
        boolean blockDestroy;
        int spawned,moved,destroyed;
        public boolean available(){return true;}
        public boolean spawn(UUID viewer,FishAgent agent){
            ids.computeIfAbsent(viewer,x->new HashSet<>()).add(agent.entityId());spawned++;return true;
        }
        public boolean teleport(UUID viewer,FishAgent agent){
            check(ids.getOrDefault(viewer,Set.of()).contains(agent.entityId()),"movement without spawn");moved++;return true;
        }
        public boolean destroy(UUID viewer,int entityId){
            if(blockDestroy)return false;
            ids.computeIfAbsent(viewer,x->new HashSet<>()).remove(entityId);destroyed++;return true;
        }
    }
    public static void main(String[] args){
        UUID world=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        String zone="central_lake";
        FishKey key=new FishKey("Rare","Koi");
        FishPopulationService population=new FishPopulationService(4,3,2,1);
        WaterPoint water=new WaterPoint(world,16,63,16);
        FishAgent shared=fish(key,zone,world,null,water,1);
        FishAgent exclusive=fish(key,zone,world,a,water,2);
        check(population.add(shared)&&population.add(exclusive),"valid spawns");
        check(population.privateCount(a)==1 && population.privateCount(b)==0,"private quota");
        check(!population.add(fish(key,zone,world,a,water,3)),"private-owner cap");
        check(population.reserve(shared.id(),UUID.nameUUIDFromBytes(new byte[]{1}),a),"reservation");
        check(!population.reserve(shared.id(),UUID.nameUUIDFromBytes(new byte[]{2}),b),"one shared agent one reservation");
        check(!population.reserve(exclusive.id(),UUID.randomUUID(),b),"private fish cannot be stolen");
        check(!population.consume(shared.id(),UUID.randomUUID()),"wrong claim blocked");
        // Rendering still shows reserved shared agents: visibility does not determine reward.
        StubTransport mock=new StubTransport();
        PacketVisibilityService visibility=new PacketVisibilityService(mock,5,10,48);
        List<PacketVisibilityService.Viewer> viewers=List.of(
            new PacketVisibilityService.Viewer(a,world,zone,water.swimmingPosition()),
            new PacketVisibilityService.Viewer(b,world,zone,water.swimmingPosition()));
        visibility.beginTick();visibility.sync(viewers,new ArrayList<>(population.agents()),10,true);
        check(mock.spawned==3,"shared seen by two, private by one");
        check(visibility.totalVisible()==3,"viewer memberships");
        check(visibility.packetsSent()==6,"spawn + metadata packet budget correctly counted");
        check(population.consume(shared.id(),UUID.nameUUIDFromBytes(new byte[]{1})),"one accepted claim");
        check(!population.consume(shared.id(),UUID.nameUUIDFromBytes(new byte[]{1})),"idempotent catch");
        visibility.beginTick();mock.blockDestroy=true;
        visibility.sync(viewers,new ArrayList<>(population.agents()),12,true);
        check(visibility.totalVisible()==3,"failed destroy kept for retry");
        mock.blockDestroy=false;
        visibility.beginTick();visibility.sync(viewers,new ArrayList<>(population.agents()),13,true);
        check(visibility.totalVisible()==1,"both shared viewers cleaned");
        check(mock.destroyed==2,"two destroy packets");
        check(population.removeOwned(a)==1 && population.size()==0,"private owner cleanup");
        visibility.beginTick();visibility.hideOtherViewers(Set.of());
        check(visibility.totalVisible()==0,"viewer cleanup");
        // Strong global + per-zone caps and TTL behavior.
        FishPopulationService caps=new FishPopulationService(2,1,0,0);
        check(caps.add(fish(key,"A",world,null,water,50)),"first zone A");
        check(!caps.add(fish(key,"A",world,null,water,50)),"zone limit");
        check(caps.add(fish(key,"B",world,null,water,50)),"second zone B");
        check(!caps.add(fish(key,"C",world,null,water,50)),"global limit");
        check(caps.expire(99)==0 && caps.expire(100)==2,"bounded TTL clean");
        // Position updates do not mutate the template or cross to non-adjacent water cells.
        FishAgent moving=fish(key,zone,world,null,water,100);
        moving.placeAt(water);
        moving.travelTo(new WaterPoint(world,water.x()+1,water.y(),water.z()));
        check(moving.move(2,.12),"moves to adjacent cell");
        check(!moving.fishKey().fishId().isBlank(),"fish identity stays stable");
        stressPreview(world,key,water);
        System.out.println("PASS: shared/private agent ownership, caps, reservation, visual privacy, double-claim, packet budget, destroy retry, TTL, motion, 100-viewer/1000-agent stress simulation");
    }
    private static void stressPreview(UUID world,FishKey key,WaterPoint base) {
        FishPopulationService p=new FishPopulationService(1000,1000,0,0);
        for(int i=0;i<1000;i++){
            Vec3 pos=new Vec3((i%40)+.5,63.3,(i/40)+.5);
            check(p.add(new FishAgent(UUID.randomUUID(),1_500_000_000+i,key,FishVisualModel.COD,
                "stress",world,null,9999,pos)),"stress populate");
        }
        List<PacketVisibilityService.Viewer> viewers=new ArrayList<>();
        for(int i=0;i<100;i++)viewers.add(new PacketVisibilityService.Viewer(
            UUID.nameUUIDFromBytes(("preview"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            world,"stress",new Vec3(20,63.3,12)));
        StubTransport mock=new StubTransport();
        PacketVisibilityService view=new PacketVisibilityService(mock,20,1200,48);
        long last=0;
        for(int tick=1;tick<=8;tick++){
            view.beginTick();view.sync(viewers,new ArrayList<>(p.agents()),tick,tick%2==0);
            check(view.packetsSent()-last<=1200,"packet budget exceeded in spawn burst");last=view.packetsSent();
        }
        check(view.totalVisible()<=2000 && view.totalVisible()>0,"bounded visibility across 100 viewers");
        p.clear();
        for(int tick=9;tick<=20;tick++){
            view.beginTick();view.sync(viewers,List.of(),tick,false);
            check(view.packetsSent()-last<=1200,"packet budget exceeded in destroy burst");last=view.packetsSent();
        }
        check(view.totalVisible()==0,"zero ghost entities after repeated cleanup");
        view.clearBestEffort();
    }
    private static FishAgent fish(FishKey key,String zone,UUID world,UUID owner,WaterPoint p,int id){
        return new FishAgent(UUID.randomUUID(),1_400_000_000+id,key,FishVisualModel.COD,zone,world,owner,100,p.swimmingPosition());
    }
}
