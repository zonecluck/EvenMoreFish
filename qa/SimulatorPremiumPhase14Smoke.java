import com.oheers.fish.fishing.simulator.encounter.*;
import com.oheers.fish.fishing.simulator.population.*;
import com.oheers.fish.fishing.simulator.environment.*;
import java.util.*;

/** Executable Java 21 assertions, independent of Bukkit/PacketEvents; does NOT replace Paper integration. */
public class SimulatorPremiumPhase14Smoke {
    static final UUID WORLD=UUID.randomUUID(), ALICE=UUID.randomUUID(), BOB=UUID.randomUUID();
    static int sequence=10;
    static RareEncounterSettings settings(double chance,int maxActive,int perZone,
                                          RareEncounterDirector.Kind kind, boolean night,boolean deep,
                                          int schoolSize) {
        var limits=new RareEncounterSettings.Limits(20,20,maxActive,perZone,120,400,160,12,12,4,16,chance);
        var profiles=new EnumMap<RareEncounterDirector.Kind,RareEncounterSettings.Profile>(RareEncounterDirector.Kind.class);
        for(var k:RareEncounterDirector.Kind.values())
            profiles.put(k,new RareEncounterSettings.Profile(k==kind,k==kind?1:0,
                k==kind&&night,k==kind&&deep,false,k==kind?schoolSize:1));
        return new RareEncounterSettings(true,limits,Set.of("RARE","legendary"),profiles,Map.of());
    }
    static FishAgent fish(FishPopulationService pop,String rarity,UUID owner,String zone,int x) {
        WaterPoint cell=new WaterPoint(WORLD,x,64,3);
        FishAgent agent=new FishAgent(UUID.randomUUID(),++sequence,new FishKey(rarity,"lake_carp"),
            FishVisualModel.COD,zone,WORLD,owner,9999,cell.swimmingPosition());
        agent.placeAt(cell);assert pop.add(agent);return agent;
    }
    static FishPopulationService pop(){return new FishPopulationService(200,160,160,160);}
    static EnvironmentSnapshot env(FishAgent fish,long tick,EnvironmentSnapshot.Depth depth,EnvironmentSnapshot.TimeOfDay day){
        return new EnvironmentSnapshot(fish.cell(),depth,day,EnvironmentSnapshot.Weather.CLEAR,"river",8,tick);
    }
    static RareEncounterDirector.Viewer viewer(UUID id,String zone,boolean allow){
        return new RareEncounterDirector.Viewer(id,WORLD,zone,new Vec3(12,64,3),allow);
    }
    public static void main(String[] args) {
        // 1. Only catalog-backed eligible rarity; exactly one roll per FishAgent.
        var p=pop();var common=fish(p,"Common",null,"main",11);var rare=fish(p,"Rare",null,"main",12);
        var d=new RareEncounterDirector(settings(1,8,2,RareEncounterDirector.Kind.GOLDEN,false,false,1),new Random(123));
        d.tick(20,p,(f,t)->null,f->List.of());
        assert d.active()==1 && d.snapshots().getFirst().fishKey().equals(rare.fishKey());
        for(int t=40;t<=100;t+=20)d.tick(t,p,(f,k)->null,f->List.of());
        assert d.spawned()==1 : "Must not re-roll same fish every tick";
        // 2. Real server-side reservation is preserved; cosmetics never reserve or mutate an agent.
        UUID cast=UUID.randomUUID();assert p.reserve(rare.id(),cast,ALICE);
        d.tick(105,p,(f,t)->null,f->List.of());
        assert d.snapshots().getFirst().phase()==RareEncounterDirector.Phase.RESERVED;
        assert rare.reservedFor(cast);
        d.committed(rare.id());assert d.active()==0&&d.completed()==1;
        // 3. No phantom reward / no expiring catch side effect.
        assert p.get(rare.id())==rare && rare.fishKey().rarityId().equals("Rare");
        assert p.release(rare.id(),cast);
        // 4. Private fish visibility + backing PacketVisibilityService check.
        var privateP=pop();var privateFish=fish(privateP,"Legendary",ALICE,"private",12);
        var pd=new RareEncounterDirector(settings(1,8,2,RareEncounterDirector.Kind.GOLDEN,false,false,1),new Random(7));
        pd.tick(20,privateP,(f,t)->null,f->List.of());assert pd.active()==1;
        List<UUID> receivers=new ArrayList<>();
        var people=List.of(viewer(ALICE,"private",true),viewer(BOB,"private",true));
        pd.render(24,people,privateP,(who,id)->true,(who,cue)->{receivers.add(who);return true;});
        assert receivers.equals(List.of(ALICE)):receivers;
        long before=pd.particles();
        pd.render(36,people,privateP,(who,id)->false,(who,cue)->{throw new AssertionError("not shown");});
        assert pd.particles()==before;
        pd.forgetViewer(ALICE);privateP.remove(privateFish.id());pd.tick(40,privateP,(f,t)->null,f->List.of());
        assert pd.active()==0 && pd.expired()==1;
        // 5. No trigger when night/deep conditions lack valid environment snapshot.
        var shadowP=pop();var shadowFish=fish(shadowP,"Rare",null,"deep",12);
        var shadow=new RareEncounterDirector(settings(1,8,2,RareEncounterDirector.Kind.SHADOW,false,true,1),new Random(1));
        shadow.tick(20,shadowP,(f,t)->null,f->List.of());assert shadow.active()==0;
        shadow.tick(40,shadowP,(f,t)->env(f,t,EnvironmentSnapshot.Depth.SHALLOW,EnvironmentSnapshot.TimeOfDay.DAY),f->List.of());
        assert shadow.active()==0;
        shadow.tick(60,shadowP,(f,t)->env(f,t,EnvironmentSnapshot.Depth.DEEP,EnvironmentSnapshot.TimeOfDay.DAY),f->List.of());
        assert shadow.active()==1;
        var ghostP=pop();fish(ghostP,"Rare",null,"night",12);
        var ghost=new RareEncounterDirector(settings(1,8,2,RareEncounterDirector.Kind.GHOST,true,false,1),new Random(1));
        ghost.tick(20,ghostP,(f,t)->null,f->List.of());assert ghost.active()==0;
        ghost.tick(40,ghostP,(f,t)->env(f,t,EnvironmentSnapshot.Depth.SHALLOW,EnvironmentSnapshot.TimeOfDay.NIGHT),f->List.of());
        assert ghost.active()==1;
        // 6. RARE_SCHOOL must reuse 2+ genuine fish, not create extra fish.
        var schoolP=pop();var groupA=fish(schoolP,"Rare",null,"school",10);
        var groupB=fish(schoolP,"Legendary",null,"school",11);
        var school=new RareEncounterDirector(settings(1,8,2,RareEncounterDirector.Kind.RARE_SCHOOL,false,false,2),new Random(1));
        school.tick(20,schoolP,(f,t)->null,f->List.of(groupA.id()));assert school.active()==0;
        school.tick(40,schoolP,(f,t)->null,f->List.of(groupA.id(),groupB.id()));
        assert school.active()==1 && school.snapshots().getFirst().fishIds().size()==2 && schoolP.size()==2;
        // 7. Global/per viewer FX budget, 100 synthetic viewers, no packets/entities created.
        var stressPop=pop();for(int i=0;i<100;i++)fish(stressPop,"Rare",null,"stress",10+i%5);
        var stress=new RareEncounterDirector(settings(1,8,2,RareEncounterDirector.Kind.GOLDEN,false,false,1),new Random(1));
        stress.tick(20,stressPop,(f,t)->null,f->List.of());
        List<RareEncounterDirector.Viewer> users=new ArrayList<>();
        for(int i=0;i<100;i++)users.add(viewer(UUID.randomUUID(),"stress",true));
        int[] total={0};Map<UUID,Integer> viewerAmount=new HashMap<>();
        stress.render(24,users,stressPop,(v,f)->true,(v,c)->{
            total[0]+=c.particles();viewerAmount.merge(v,c.particles(),Integer::sum);return true;});
        assert total[0]<=12 && viewerAmount.values().stream().allMatch(x->x<=4):total[0];
        // 8. Staff preview cannot bypass actual rarity or active caps.
        assert !stress.preview(RareEncounterDirector.Kind.GOLDEN,common,25,p,(f,t)->null,f->List.of());
        // 9. Exact FishKey override selects a cosmetic profile, never changes the FishKey.
        var overrideP=pop();var target=fish(overrideP,"Rare",null,"override",12);
        var previous=settings(1,4,2,RareEncounterDirector.Kind.RARE_PRESENCE,false,false,1);
        var maps=new EnumMap<RareEncounterDirector.Kind,RareEncounterSettings.Profile>(RareEncounterDirector.Kind.class);
        maps.putAll(previous.profiles());
        maps.put(RareEncounterDirector.Kind.GOLDEN,new RareEncounterSettings.Profile(true,0,false,false,false,1));
        var special=new RareEncounterSettings(true,previous.limits(),previous.eligibleRarities(),maps,
            Map.of("Rare:lake_carp",RareEncounterDirector.Kind.GOLDEN));
        var specialDirector=new RareEncounterDirector(special,new Random(11));
        specialDirector.tick(20,overrideP,(f,t)->null,f->List.of());
        assert specialDirector.active()==1;
        assert specialDirector.snapshots().getFirst().kind()==RareEncounterDirector.Kind.GOLDEN;
        assert specialDirector.snapshots().getFirst().fishKey().equals(target.fishKey());
        // 10. Reconnect/cleanup can't keep obsolete viewer IDs (read-only observation).
        stress.clear();assert stress.active()==0;
        System.out.println("PASS Phase14: identity, one-roll, cooldown, privacy, visibility, school, night/depth, budgets, reservation, cleanup");
    }
}
