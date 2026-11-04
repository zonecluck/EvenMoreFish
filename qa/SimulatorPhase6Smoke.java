import com.oheers.fish.fishing.simulator.ai.FishAiDirector;
import com.oheers.fish.fishing.simulator.population.*;
import java.util.*;

/** Tests actual pure-Java production FishAiDirector with no Bukkit mocks or external jars. */
public final class SimulatorPhase6Smoke {
    static final FishKey KEY=new FishKey("Rare","Koi");
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    static final class Waters implements FishAiDirector.WaterNavigation {
        final Set<WaterPoint> cells=new HashSet<>();
        final Set<WaterPoint> deniedZone=new HashSet<>();
        public boolean valid(WaterPoint p){return cells.contains(p);}
        public boolean traversable(WaterPoint p,String zone){return valid(p) && !deniedZone.contains(p);}
        public List<WaterPoint> adjacent(WaterPoint p){
            return cells.stream().filter(n->n.worldId().equals(p.worldId()) &&
                Math.abs(n.x()-p.x())+Math.abs(n.z()-p.z())==1 && Math.abs(n.y()-p.y())<=1).toList();
        }
    }
    static final class Events implements FishAiDirector.Listener {
        final Map<UUID,List<FishAiDirector.Phase>> phases=new HashMap<>();
        final Map<UUID,FishAiDirector.Outcome> outcomes=new HashMap<>();
        public void phase(UUID user,UUID hook,FishAiDirector.Phase phase,FishKey key){
            phases.computeIfAbsent(user,ignored->new ArrayList<>()).add(phase);
        }
        public void finished(UUID user,UUID hook,FishAiDirector.Outcome result,FishKey key){
            check(outcomes.putIfAbsent(hook,result)==null,"finished callback fired twice");
        }
    }
    static FishAgent fish(UUID world,UUID privateOwner,int x,int id){
        WaterPoint p=new WaterPoint(world,x,63,0);
        FishAgent agent=new FishAgent(UUID.randomUUID(),1_800_000_000+id,KEY,FishVisualModel.COD,"lake",world,
            privateOwner,10000,p.swimmingPosition());agent.placeAt(p);return agent;
    }
    static FishAiDirector.Tuning settings(){
        return new FishAiDirector.Tuning(8,12,96,2,2,26,3,2,8,400,10.0,.5,.7);
    }
    static void tickUntil(FishAiDirector d,UUID user,Events events,long from,long to){
        for(long t=from;t<=to && d.view(user)!=null;t++) d.tick(user,t,a->1.0,events);
    }
    static void tickToBite(FishAiDirector d,UUID user,Events events,long from,long to){
        for(long t=from;t<=to;t++){
            var view=d.view(user);
            if(view==null || view.phase()==FishAiDirector.Phase.BITE_READY)return;
            d.tick(user,t,a->1.0,events);
        }
    }
    public static void main(String[] args){
        UUID world=UUID.randomUUID(),alice=UUID.randomUUID(),bob=UUID.randomUUID(),hook=UUID.randomUUID();
        Waters water=new Waters();for(int x=0;x<=10;x++)water.cells.add(new WaterPoint(world,x,63,0));
        FishPopulationService population=new FishPopulationService(16,16,6,3);
        FishAgent shared=fish(world,null,0,1);check(population.add(shared),"add shared fish");
        FishAiDirector director=new FishAiDirector(population,water,settings());Events events=new Events();
        check(director.begin(alice,hook,"lake",world,1),"begin cast");
        check(director.setGoal(alice,hook,new WaterPoint(world,3,63,0)),"set goal");
        check(!director.setGoal(alice,hook,new WaterPoint(world,9,63,0)),"target is immutable");
        tickToBite(director,alice,events,1,60);
        FishAiDirector.View active=director.view(alice);
        check(active!=null && active.phase()==FishAiDirector.Phase.BITE_READY,"must reach actual bite");
        check(events.phases.get(alice).containsAll(List.of(FishAiDirector.Phase.INTERESTED,
            FishAiDirector.Phase.APPROACHING,FishAiDirector.Phase.BITE_READY)),"phase transitions");
        check(shared.fishKey().equals(KEY),"visible fish identity preserved");
        check(director.reel(alice,hook,active.biteTick()+15,events)==FishAiDirector.Outcome.SUCCESS_PREVIEW_ONLY,
            "inside 26-tick reel window");
        check(population.size()==0 && director.active()==0 && director.successes()==1,"consumed preview fish exactly once");
        check(director.reel(alice,hook,active.biteTick()+15,events)==FishAiDirector.Outcome.NONE,"duplicate reel no-op");
        check(!director.begin(alice,UUID.randomUUID(),"lake",world,active.biteTick()+16),"cast cooldown");

        // Two owners compete for one shared fish: second owner cannot reserve or catch it.
        FishAgent competitive=fish(world,null,4,2);check(population.add(competitive),"add competitive fish");
        UUID ha=UUID.randomUUID(),hb=UUID.randomUUID();
        check(director.begin(alice,ha,"lake",world,200),"alice reopened");
        check(director.begin(bob,hb,"lake",world,200),"bob opened");
        check(director.setGoal(alice,ha,new WaterPoint(world,6,63,0)),"alice target");
        check(director.setGoal(bob,hb,new WaterPoint(world,6,63,0)),"bob target");
        director.tick(alice,201,a->1,events);director.tick(bob,201,a->1,events);
        check(Objects.equals(director.view(alice).fishId(),competitive.id()),"first fisher reserves shared fish");
        check(director.view(bob).fishId()==null,"second fisher cannot reserve it");
        check(director.reel(alice,ha,202,events)==FishAiDirector.Outcome.EARLY_REEL,"early reel fails");
        check(population.size()==1 && competitive.state()==FishAgent.State.FLEEING,
            "failed reel releases fish with safe flee state");
        for(long t=203;t<=210;t++)competitive.move(t,.5);
        check(competitive.state()==FishAgent.State.WANDERING,"flee returns to wander");
        tickUntil(director,bob,events,202,220);
        check(director.view(bob)!=null && director.view(bob).fishId()!=null,"second fisher can target released fish");
        director.abort(bob);
        check(competitive.state()==FishAgent.State.WANDERING,"abort frees reservation");

        // Private fish is strictly restricted by owner, even if nearer to the hook.
        population.clear();FishAgent privateAlice=fish(world,alice,0,3);population.add(privateAlice);
        UUID hBob=UUID.randomUUID();check(director.begin(bob,hBob,"lake",world,301),"bob restart");
        director.setGoal(bob,hBob,new WaterPoint(world,2,63,0));
        tickUntil(director,bob,events,302,320);
        check(director.view(bob).fishId()==null,"private fish never visible as target to other owner");
        director.abort(bob);

        // No impossible swim across land: BFS cannot jump missing water even at short direct distance.
        population.clear();FishAgent isolated=fish(world,null,0,4);population.add(isolated);
        water.cells.remove(new WaterPoint(world,1,63,0));
        UUID hc=UUID.randomUUID();check(director.begin(alice,hc,"lake",world,400),"isolated cast");
        director.setGoal(alice,hc,new WaterPoint(world,2,63,0));
        tickUntil(director,alice,events,401,450);
        check(director.view(alice).fishId()==null,"no path across land");
        director.abort(alice);water.cells.add(new WaterPoint(world,1,63,0));

        // Water may be indexed but still DENIED by WorldGuard/CUBOID region routing.
        water.deniedZone.add(new WaterPoint(world,1,63,0));
        UUID hz=UUID.randomUUID();check(director.begin(alice,hz,"lake",world,460),"zone cast");
        check(director.setGoal(alice,hz,new WaterPoint(world,2,63,0)),"zone goal inside");
        tickUntil(director,alice,events,461,478);
        check(director.view(alice).fishId()==null,"AI cannot cross denied zone even if water is present");
        director.abort(alice);water.deniedZone.clear();

        // Missed reaction window releases a fish instead of issuing any reward.
        population.clear();FishAgent late=fish(world,null,8,5);population.add(late);
        UUID hd=UUID.randomUUID();check(director.begin(alice,hd,"lake",world,500),"late cast");
        director.setGoal(alice,hd,new WaterPoint(world,9,63,0));
        tickToBite(director,alice,events,501,535);
        FishAiDirector.View beforeMiss=director.view(alice);
        check(beforeMiss!=null && beforeMiss.biteTick()>=0,"late fish bites");
        tickUntil(director,alice,events,536,beforeMiss.biteTick()+40);
        check(director.view(alice)==null && director.misses()==1,"late bite expires automatically");
        check(population.size()==1 && late.state()!=FishAgent.State.BITE_READY,"missed fish released");
        director.clear();check(director.active()==0,"all sessions cleared");

        // Reservation survives TTL while a fish is approaching; it expires after release.
        FishPopulationService ttl=new FishPopulationService(2,2,0,0);
        FishAgent protectedAgent=new FishAgent(UUID.randomUUID(),1_900_000_000,KEY,FishVisualModel.COD,
            "lake",world,null,5,new WaterPoint(world,0,63,0).swimmingPosition());
        protectedAgent.placeAt(new WaterPoint(world,0,63,0));
        check(ttl.add(protectedAgent),"TTL fish created");
        UUID reservation=UUID.randomUUID();
        check(ttl.reserve(protectedAgent.id(),reservation,alice),"TTL fish reserved");
        check(protectedAgent.interested(reservation) && protectedAgent.approaching(reservation),
            "TTL fish moves through reserved AI states");
        check(ttl.expire(100)==0 && ttl.size()==1,"TTL may not delete reserved AI fish");
        check(ttl.release(protectedAgent.id(),reservation),"TTL release");
        check(ttl.expire(101)==1 && ttl.size()==0,"released expired fish cleaned");

        System.out.println("PASS: indexed-water BFS, server-only bite window, shared competition, private owner, " +
            "early reel, missed reel, idempotent success, cooldown, cleanup and fish identity");
    }
}
