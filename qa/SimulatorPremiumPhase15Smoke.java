import com.oheers.fish.fishing.simulator.model.*;
import com.oheers.fish.fishing.simulator.population.*;
import com.oheers.fish.fishing.simulator.visual.*;
import java.util.*;

public final class SimulatorPremiumPhase15Smoke {
    private static void ok(boolean cond,String detail){if(!cond)throw new AssertionError(detail);}
    private static final class FakeTransport implements PacketTransport {
        final List<String> events=new ArrayList<>();
        boolean denyDestroy;
        @Override public boolean available(){return true;}
        @Override public boolean supportsCustom(UUID viewer){return true;}
        @Override public boolean spawn(UUID v,FishAgent fish) {events.add(v+":vanilla:"+fish.entityId());return true;}
        @Override public boolean spawn(UUID v,FishAgent fish,FishRenderSpec visual) {
            events.add(v+":"+(visual.isCustom()?"custom":"vanilla")+":"+fish.entityId());return true;
        }
        @Override public boolean teleport(UUID v,FishAgent fish){return true;}
        @Override public boolean destroy(UUID v,int id){events.add(v+":destroy:"+id);return !denyDestroy;}
    }
    private static FishAgent fish(UUID owner){
        return new FishAgent(UUID.randomUUID(),99,new FishKey("Legendary","Golden Koi"),
            FishVisualModel.SALMON,"lake",UUID.fromString("00000000-0000-0000-0000-000000000123"),owner,99999,
            new Vec3(1,62,1));
    }
    private static PacketVisibilityService.Viewer viewer(UUID viewer,FishAgent fish){
        return new PacketVisibilityService.Viewer(viewer,fish.worldId(),fish.zoneId(),new Vec3(1,62,1));
    }
    public static void main(String[] args){
        UUID pack=UUID.randomUUID(), otherPack=UUID.randomUUID(), a=UUID.randomUUID(), b=UUID.randomUUID();
        var audience=new FishPackAudience();
        var policy=new FishRenderPolicy(audience,v->true);
        var profile=new FishModelSettings.Profile("PAPER","ledat:fish/golden_koi",.8f,2);
        policy.configure(new FishModelSettings(true,pack,Map.of("legendary:golden koi",profile),Map.of()));
        var fish=fish(null);
        ok(!policy.choose(a,fish).isCustom(),"unknown pack must fallback");
        audience.status(a,otherPack,true);
        ok(!policy.choose(a,fish).isCustom(),"other pack must not grant access");
        audience.status(a,pack,true);
        ok(policy.choose(a,fish).isCustom(),"exact pack grants model");
        ok(!policy.choose(b,fish).isCustom(),"viewer pack isolation");
        ok(!new FishRenderPolicy(audience).choose(a,fish).isCustom(),"unimplemented adapter fails closed");
        var t=new FakeTransport();
        var vis=new PacketVisibilityService(t,12,100,25,policy);
        var viewers=List.of(viewer(a,fish),viewer(b,fish));
        vis.beginTick();vis.sync(viewers,List.of(fish),1,false);
        ok(t.events.stream().anyMatch(s->s.equals(a+":custom:99")),"ready viewer gets ItemDisplay");
        ok(t.events.stream().anyMatch(s->s.equals(b+":vanilla:99")),"other viewer gets vanilla");
        ok(vis.totalVisible()==2,"two viewers shown");
        t.events.clear();audience.status(a,pack,false);
        vis.beginTick();vis.sync(viewers,List.of(fish),2,false);
        ok(t.events.indexOf(a+":destroy:99")>=0,"old custom must be destroyed");
        ok(t.events.indexOf(a+":vanilla:99")>t.events.indexOf(a+":destroy:99"),"no double visible entities");
        ok(vis.modelReplacements()>=1,"model replacement counter");
        // Failed destroy must not resummon fish over a stale model; retry on next tick.
        t.events.clear();audience.status(a,pack,true);t.denyDestroy=true;
        vis.beginTick();vis.sync(viewers,List.of(fish),3,false);
        ok(t.events.contains(a+":destroy:99"),"destroy attempted");
        ok(t.events.stream().noneMatch(s->s.equals(a+":custom:99")),"no replacement until destroy ACK");
        t.denyDestroy=false;t.events.clear();
        vis.beginTick();vis.sync(viewers,List.of(fish),4,false);
        ok(t.events.indexOf(a+":custom:99")>t.events.indexOf(a+":destroy:99"),"retries before spawn");
        // Private fish cannot leak to a viewer even if they have pack loaded.
        var own=fish(a);
        vis.beginTick();vis.sync(viewers,List.of(own),5,false);
        ok(!vis.shownTo(b,own.id()),"private not visible");
        policy.configure(new FishModelSettings(true,otherPack,Map.of("legendary:golden koi",profile),Map.of()));
        ok(!policy.choose(a,fish).isCustom(),"changed pack id invalidates loaded sessions");
        policy.configure(FishModelSettings.disabled());
        ok(!policy.choose(a,fish).isCustom(),"disabled always vanilla");
        try {new FishRenderSpec(FishVisualModel.COD,"PAPER","evil:../../path",1,0);
            throw new AssertionError("invalid path allowed");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: Phase15 exact pack gating, shared/private per-viewer models, hot downgrade, destroy-first retry, fallback, invalid key");
    }
}
