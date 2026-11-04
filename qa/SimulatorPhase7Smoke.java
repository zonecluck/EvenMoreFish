import com.oheers.fish.fishing.simulator.ai.FishAiDirector;
import com.oheers.fish.fishing.simulator.population.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Full synchronous transaction state-machine tests against actual Phase-7 core classes. */
public final class SimulatorPhase7Smoke {
    static final FishKey KEY=new FishKey("Epic","GoldenCarp");
    static final UUID WORLD=UUID.randomUUID();
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    static final class Water implements FishAiDirector.WaterNavigation {
        public boolean valid(WaterPoint cell){return cell.worldId().equals(WORLD) && cell.z()==0 && cell.x()>=0 && cell.x()<=4;}
        public List<WaterPoint> adjacent(WaterPoint cell){
            List<WaterPoint> result=new ArrayList<>();
            for(int x:List.of(cell.x()-1,cell.x()+1)){
                WaterPoint next=new WaterPoint(WORLD,x,64,0);if(valid(next))result.add(next);
            }
            return result;
        }
    }
    static final class Events implements FishAiDirector.Listener {
        int finishes;
        FishAiDirector.Outcome last;
        FishKey lastKey;
        public void phase(UUID user,UUID hook,FishAiDirector.Phase p,FishKey key){}
        public void finished(UUID user,UUID hook,FishAiDirector.Outcome result,FishKey key){
            finishes++;last=result;lastKey=key;
        }
    }
    static final class Setup {
        final UUID player=UUID.randomUUID(),hook=UUID.randomUUID();
        final FishPopulationService population=new FishPopulationService(6,6,0,0);
        final FishAgent fish;
        final FishAiDirector director;
        final Events events=new Events();
        final FishAiDirector.View ready;
        Setup() {
            WaterPoint start=new WaterPoint(WORLD,0,64,0);
            fish=new FishAgent(UUID.randomUUID(),1900000000,KEY,FishVisualModel.COD,
                "pond",WORLD,null,9000,start.swimmingPosition());
            fish.placeAt(start);check(population.add(fish),"fish spawned");
            var cfg=new FishAiDirector.Tuning(5,8,64,1,2,26,3,0,0,300,12,.75,.7);
            director=new FishAiDirector(population,new Water(),cfg);
            check(director.begin(player,hook,"pond",WORLD,1),"cast");
            check(director.setGoal(player,hook,new WaterPoint(WORLD,2,64,0)),"goal");
            for(int t=2;t<90 && director.view(player).phase()!=FishAiDirector.Phase.BITE_READY;t++){
                director.tick(player,t,a->1,events);
            }
            ready=director.view(player);
            check(ready.phase()==FishAiDirector.Phase.BITE_READY,"must bite");
            check(fish.reservedFor(hook),"owner reservation");
        }
        long reelTick(){return ready.biteTick()+1;}
    }
    static void cancelledEmfEvent(){
        Setup s=new Setup();AtomicInteger events=new AtomicInteger(),grants=new AtomicInteger();
        var out=s.director.reelWithCommit(s.player,s.hook,s.reelTick(),s.events,(view,fish)->{
            events.incrementAndGet();check(view.fishKey().equals(KEY),"identity before event");
            return null; // EMFFishCaughtEvent was cancelled
        });
        check(out==FishAiDirector.Outcome.COMMIT_REJECTED,"must reject cancellation");
        check(events.get()==1 && grants.get()==0,"one event, zero grants");
        check(s.population.size()==1 && !s.fish.reserved(),"cancelled event releases fish");
        check(s.director.reel(s.player,s.hook,s.reelTick(),s.events)==FishAiDirector.Outcome.NONE,"cannot retry");
        check(s.events.finishes==1,"one finish");
    }
    static void successfulCatchExactlyOnce(){
        Setup s=new Setup();AtomicInteger preparations=new AtomicInteger(),grants=new AtomicInteger();
        var out=s.director.reelWithCommit(s.player,s.hook,s.reelTick(),s.events,(view,fish)->{
            preparations.incrementAndGet();
            check(view.fishId().equals(fish.id()) && view.fishKey().equals(fish.fishKey()),"FishKey locks fish identity");
            return new FishAiDirector.PreparedCatch(){
                public boolean ready(){return fish.reservedFor(s.hook);}
                public boolean deliver(){
                    check(s.population.size()==0,"fish consumed before item delivery");
                    grants.incrementAndGet();return true;
                }
            };
        });
        check(out==FishAiDirector.Outcome.SUCCESS_COMMITTED,"committed");
        check(preparations.get()==1 && grants.get()==1 && s.events.finishes==1,"exactly one event/item/finish");
        check(s.events.lastKey.equals(KEY),"reported key matches visible fish");
        check(s.population.size()==0 && s.director.active()==0,"agent removed");
        check(s.director.reelWithCommit(s.player,s.hook,s.reelTick(),s.events,(v,f)->{throw new AssertionError("duplicate");})
            ==FishAiDirector.Outcome.NONE,"second reel cannot deliver");
    }
    static void readinessRevokedAfterEvent(){
        Setup s=new Setup();AtomicInteger grants=new AtomicInteger();
        var out=s.director.reelWithCommit(s.player,s.hook,s.reelTick(),s.events,(view,fish)->
            new FishAiDirector.PreparedCatch(){
                public boolean ready(){return false;} // permission/zone/rod changed after EMF event
                public boolean deliver(){grants.incrementAndGet();return true;}
            });
        check(out==FishAiDirector.Outcome.COMMIT_REJECTED,"late validation rejected");
        check(s.population.size()==1 && grants.get()==0 && !s.fish.reserved(),"no consume/reward");
    }
    static void missingReservationAfterEvent(){
        Setup s=new Setup();AtomicInteger grants=new AtomicInteger();
        var out=s.director.reelWithCommit(s.player,s.hook,s.reelTick(),s.events,(view,fish)->{
            check(s.population.release(fish.id(),s.hook),"simulate external invalidation");
            return new FishAiDirector.PreparedCatch(){
                public boolean ready(){return true;}
                public boolean deliver(){grants.incrementAndGet();return true;}
            };
        });
        check(out==FishAiDirector.Outcome.LOST_FISH && grants.get()==0,"cannot deliver unreserved fish");
    }
    static void reentrantReload(){
        Setup s=new Setup();AtomicInteger grants=new AtomicInteger();
        var out=s.director.reelWithCommit(s.player,s.hook,s.reelTick(),s.events,(view,fish)->{
            check(s.director.reelWithCommit(s.player,s.hook,s.reelTick(),s.events,(v,f)->null)
                ==FishAiDirector.Outcome.NONE,"recursive reel during commit is ignored");
            check(s.director.abort(s.player),"reload cleanup");
            return new FishAiDirector.PreparedCatch(){
                public boolean ready(){return true;}
                public boolean deliver(){grants.incrementAndGet();return true;}
            };
        });
        check(out==FishAiDirector.Outcome.NONE && grants.get()==0,"cannot award after reload");
        check(s.population.size()==1 && !s.fish.reserved(),"fish retained after reload");
    }
    static void deliveryFailureIsTerminal(){
        Setup s=new Setup();AtomicInteger attempts=new AtomicInteger();
        var out=s.director.reelWithCommit(s.player,s.hook,s.reelTick(),s.events,(view,fish)->
            new FishAiDirector.PreparedCatch(){
                public boolean ready(){return true;}
                public boolean deliver(){attempts.incrementAndGet();return false;}
            });
        check(out==FishAiDirector.Outcome.COMMIT_FAILED && s.population.size()==0,"fail closed");
        check(attempts.get()==1 && s.director.active()==0,"cannot replay after partial failure");
        check(s.director.reel(s.player,s.hook,s.reelTick(),s.events)==FishAiDirector.Outcome.NONE,"replay rejected");
    }
    static void deliveryExceptionIsTerminal(){
        Setup s=new Setup();
        var out=s.director.reelWithCommit(s.player,s.hook,s.reelTick(),s.events,(view,fish)->
            new FishAiDirector.PreparedCatch(){
                public boolean ready(){return true;}
                public boolean deliver(){throw new IllegalStateException("inventory provider unavailable");}
            });
        check(out==FishAiDirector.Outcome.COMMIT_FAILED && s.population.size()==0,"exception closed");
        check(s.events.finishes==1,"exception does not re-award or leak session");
    }
    public static void main(String[] args){
        cancelledEmfEvent();successfulCatchExactlyOnce();readinessRevokedAfterEvent();
        missingReservationAfterEvent();reentrantReload();deliveryFailureIsTerminal();deliveryExceptionIsTerminal();
        System.out.println("PASS: Phase 7 duplicate reel, identity, event cancellation, late validation, " +
            "reservation, reentrant reload, delivery failure & no replay");
    }
}
