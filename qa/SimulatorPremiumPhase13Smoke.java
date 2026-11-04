import com.oheers.fish.fishing.simulator.behavior.*;
import com.oheers.fish.fishing.simulator.environment.*;
import com.oheers.fish.fishing.simulator.population.*;
import com.oheers.fish.fishing.simulator.waterfx.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real Java 21 domain smoke tests (no Paper or fake visual pass claims). */
public final class SimulatorPremiumPhase13Smoke {
    private static final UUID WORLD=UUID.randomUUID(), OWNER=UUID.randomUUID(), OTHER=UUID.randomUUID();
    private static void check(boolean success,String msg){if(!success)throw new AssertionError(msg);}
    private static FishAgent fish(String rarity,UUID viewer,int x,int y) {
        WaterPoint cell=new WaterPoint(WORLD,x,y,0);
        FishAgent fish=new FishAgent(UUID.randomUUID(),123456789,
            new FishKey(rarity,"Fish"),FishVisualModel.COD,"zone",WORLD,viewer,1000,cell.swimmingPosition());
        fish.placeAt(cell);return fish;
    }
    private static void environmentBudgetAndInvalidation() {
        AtomicInteger reads=new AtomicInteger();
        EnvironmentSampler source=new EnvironmentSampler(new EnvironmentSampler.Limits(2,16,20),
            (cell,tick)->{
                reads.incrementAndGet();
                return new EnvironmentSnapshot(cell,EnvironmentSnapshot.Depth.MEDIUM,
                    EnvironmentSnapshot.TimeOfDay.NIGHT,EnvironmentSnapshot.Weather.RAIN,"RIVER",4,tick);
            });
        WaterPoint a=new WaterPoint(WORLD,0,62,0),b=new WaterPoint(WORLD,17,62,0),c=new WaterPoint(WORLD,33,62,0);
        check(source.observe(a,1)!=null,"first sample");
        check(source.observe(a,1)!=null,"cached same cell");
        check(source.observe(b,1)!=null,"second sample");
        check(source.observe(c,1)==null,"must obey two reads per tick");
        check(reads.get()==2 && source.budgetDrops()==1,"strict rate limits");
        check(source.observe(c,2)!=null,"budget reset");
        source.invalidate(WORLD,0,0);
        check(source.observe(a,2)!=null,"invalidate chunk must force resample");
        check(reads.get()==4,"count reads");
        source.clear();check(source.cached()==0,"clear cache");
        var broken=new EnvironmentSampler(new EnvironmentSampler.Limits(2,16,20),
            (cell,tick)->{throw new IllegalStateException("optional biome read failed");});
        check(broken.observe(a,9)==null && broken.failedReads()==1,
            "optional environment read failure must not crash catch ticker");
        check(EnvironmentSnapshot.depthFor(1)==EnvironmentSnapshot.Depth.SHALLOW,"depth shallow");
        check(EnvironmentSnapshot.depthFor(4)==EnvironmentSnapshot.Depth.MEDIUM,"depth medium");
        check(EnvironmentSnapshot.depthFor(7)==EnvironmentSnapshot.Depth.DEEP,"depth deep");
    }
    private static void personasOnlySteerUnreservedAgents() {
        var profiles=Map.of("Rare",FishPersonality.SHY,"Common",FishPersonality.CALM,
            "Epic",FishPersonality.AGGRESSIVE,"Deep",FishPersonality.DEEP_DWELLER,
            "Night",FishPersonality.NOCTURNAL);
        var behavior=new FishPersonalityService(profiles,Map.of("Common:Fish",FishPersonality.CURIOUS),5);
        FishAgent calm=fish("Common",null,1,63),aggro=fish("Epic",null,2,63);
        check(behavior.of(calm)==FishPersonality.CURIOUS,"exact FishKey override");
        check(behavior.movementFactor(aggro,null)>behavior.movementFactor(calm,null),"aggressive fast");
        FishAgent nocturnal=fish("Night",null,3,63);
        EnvironmentSnapshot night=new EnvironmentSnapshot(nocturnal.cell(),EnvironmentSnapshot.Depth.DEEP,
            EnvironmentSnapshot.TimeOfDay.NIGHT,EnvironmentSnapshot.Weather.CLEAR,"OCEAN",8,1);
        EnvironmentSnapshot day=new EnvironmentSnapshot(nocturnal.cell(),EnvironmentSnapshot.Depth.DEEP,
            EnvironmentSnapshot.TimeOfDay.DAY,EnvironmentSnapshot.Weather.CLEAR,"OCEAN",8,1);
        check(behavior.movementFactor(nocturnal,night)>behavior.movementFactor(nocturnal,day),"night activation");
        var nav=new FishPersonalityService.Navigation() {
            @Override public List<WaterPoint> adjacent(WaterPoint cell) {
                return List.of(new WaterPoint(WORLD,cell.x()+1,cell.y()-1,0),
                    new WaterPoint(WORLD,cell.x()-1,cell.y(),0));
            }
            @Override public boolean traversable(FishAgent f,WaterPoint p){return p.x()>=0;}
        };
        FishAgent deep=fish("Deep",null,6,63);
        check(behavior.preferNext(deep,nav.adjacent(deep.cell()),nav,1).y()==62,"deep fish prefer low water");
        FishPopulationService pop=new FishPopulationService(20,20,10,10);
        FishAgent shy=fish("Rare",null,2,63),privateShy=fish("Rare",OWNER,2,63);
        check(pop.add(shy)&&pop.add(privateShy),"adding fish");
        int changed=behavior.disturb(shy.position(),WORLD,"zone",OTHER,pop,nav);
        check(changed==1 && shy.state()==FishAgent.State.FLEEING,"shared shy flee");
        check(privateShy.state()==FishAgent.State.WANDERING,"private fish cannot react to other player");
        check(pop.reserve(privateShy.id(),UUID.randomUUID(),OWNER),"reserve private fish");
        check(behavior.disturb(privateShy.position(),WORLD,"zone",OWNER,pop,nav)==0,
            "reserved fish immune to ambient personality");
    }
    private static void fxBudgetsAndPrivacy() {
        var fx=new SurfaceFxService(new SurfaceFxService.Limits(2,6,4,8,18));
        FishAgent privateMythic=fish("Mythical",OWNER,2,63);
        var personality=new FishPersonalityService(Map.of("Mythical",FishPersonality.MYTHIC),Map.of(),5);
        var ownerView=new SurfaceFxService.Viewer(OWNER,WORLD,"zone",privateMythic.position(),true);
        var otherView=new SurfaceFxService.Viewer(OTHER,WORLD,"zone",privateMythic.position(),true);
        List<UUID> sends=new ArrayList<>();
        SurfaceFxService.Emitter emitter=(id,cue)->{sends.add(id);return true;};
        fx.tick(1,List.of(otherView,ownerView),List.of(privateMythic),personality,emitter);
        check(sends.isEmpty(),"interval cap");
        fx.tick(2,List.of(otherView,ownerView),List.of(privateMythic),personality,emitter);
        check(sends.size()==1 && sends.getFirst().equals(OWNER),"private FX are viewer-only");
        check(fx.particles()<=6,"global particle cap");
        fx.signal(OWNER,WORLD,"zone",privateMythic.position(),SurfaceFxService.Kind.LURE_PULSE,3);
        fx.tick(4,List.of(otherView,ownerView),List.of(privateMythic),personality,emitter);
        check(fx.particles()<=12,"per tick hard cap");
        check(sends.stream().allMatch(OWNER::equals),"private FX may not leak");
        int beforeInvisible=sends.size();
        fx.tick(6,List.of(ownerView),List.of(privateMythic),personality,(viewer,id)->false,emitter);
        check(sends.size()==beforeInvisible,"fish FX must wait until client entity spawn");
        fx.forgetViewer(OWNER);fx.clear();
        long before=fx.particles();
        fx.tick(6,List.of(new SurfaceFxService.Viewer(OWNER,WORLD,"zone",privateMythic.position(),false)),
            List.of(privateMythic),personality,emitter);
        check(fx.particles()==before,"profile OFF cannot send particles");
        // Test hard budget with 100 agents and 100 viewers; no entity creation or reward calls.
        var all=new ArrayList<FishAgent>();for(int i=0;i<100;i++)all.add(fish("Mythical",null,i%8,63));
        var viewers=new ArrayList<SurfaceFxService.Viewer>();
        for(int i=0;i<100;i++)viewers.add(new SurfaceFxService.Viewer(UUID.randomUUID(),WORLD,"zone",all.getFirst().position(),true));
        long baseline=fx.particles();
        fx.tick(8,viewers,all,personality,(id,cue)->true);
        check(fx.particles()-baseline<=6,"100 viewers cannot exceed 6 particles/tick");
    }
    public static void main(String[] args) {
        environmentBudgetAndInvalidation();
        personasOnlySteerUnreservedAgents();
        fxBudgetsAndPrivacy();
        System.out.println("PASS: Phase13 environment budgets, personalities, owner privacy, water FX, 100-viewer cap");
    }
}
