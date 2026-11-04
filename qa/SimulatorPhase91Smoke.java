import com.oheers.fish.fishing.simulator.commit.SingleSlotDeliveryPlanner;
import com.oheers.fish.fishing.simulator.visual.PacketCleanupQueue;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Headless fault injection for one-slot insertion and orphan packet bookkeeping. */
public final class SimulatorPhase91Smoke {
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    private static void planning(){
        ItemStack reward=new ItemStack("FISH",2,16);
        ItemStack[] storage={new ItemStack("STONE",64,64),new ItemStack("FISH",13,16),null};
        var stacked=SingleSlotDeliveryPlanner.plan(storage,reward,64);
        check(stacked!=null && stacked.slot()==1 && stacked.contents().getAmount()==15,"merge into one slot");
        check(storage[1].getAmount()==13 && reward.getAmount()==2,"planner must not mutate source");
        storage[1].setAmount(15);
        var empty=SingleSlotDeliveryPlanner.plan(storage,reward,64);
        check(empty!=null && empty.slot()==2 && empty.contents().getAmount()==2,"prefer full empty slot");
        storage[2]=new ItemStack("DIRT",40,64);
        check(SingleSlotDeliveryPlanner.plan(storage,reward,64)==null,"full inventory => whole drop");
        check(SingleSlotDeliveryPlanner.plan(new ItemStack[]{null},new ItemStack("FISH",17,16),64)==null,
            "oversized reward cannot be inserted in one slot");
        check(SingleSlotDeliveryPlanner.plan(new ItemStack[]{null},reward,1)==null,
            "respect inventory stack limit");
        check(SingleSlotDeliveryPlanner.plan(new ItemStack[]{null},reward,16).slot()==0,
            "valid small reward should insert");
    }
    private static void cleanupLedger(){
        var queue=new PacketCleanupQueue(2);
        UUID viewer=UUID.randomUUID();UUID other=UUID.randomUUID();
        check(queue.record(viewer,100) && queue.record(viewer,100),"duplicate record idempotent");
        check(queue.pending()==1 && queue.pendingFor(viewer),"queue size per ID");
        check(queue.record(other,200) && !queue.record(viewer,300),"queue capacity never evicts");
        final Set<Integer> client=new HashSet<>(Set.of(100,200));
        final int[] calls={0};
        PacketTransport flaky=new PacketTransport(){
            public boolean available(){return true;}
            public boolean spawn(UUID id,FishAgent a){return false;}
            public boolean teleport(UUID id,FishAgent a){return false;}
            public boolean destroy(UUID id,int entityId){
                calls[0]++;
                if(entityId==100)throw new IllegalStateException("injected packet failure");
                client.remove(entityId);return true;
            }
        };
        var first=queue.retry(flaky,1);
        check(first.attempts()==1 && first.failed()==1 && queue.pending()==2,"failed cleanup stays queued");
        var second=queue.retry(flaky,2);
        check(second.attempts()==2 && second.cleared()==1 && queue.pending()==1,"other viewer cleanup survives thrown failure");
        PacketTransport healthy=new PacketTransport(){
            public boolean available(){return true;}
            public boolean spawn(UUID id,FishAgent a){return false;}
            public boolean teleport(UUID id,FishAgent a){return false;}
            public boolean destroy(UUID id,int entityId){client.remove(entityId);return true;}
        };
        var third=queue.retry(healthy,2);
        check(third.cleared()==1 && queue.pending()==0 && client.isEmpty(),"orphan retry converges");
        check(queue.record(viewer,400),"fresh record accepted after recovery");
        queue.forgetDisconnected(viewer);
        check(queue.pending()==0,"disconnect resets client-side entity state");
    }
    public static void main(String[] args){planning();cleanupLedger();
        System.out.println("PASS Phase 9.1: conservative single-slot plan + orphan retry ledger capacity, idempotency and recovery");}
}
