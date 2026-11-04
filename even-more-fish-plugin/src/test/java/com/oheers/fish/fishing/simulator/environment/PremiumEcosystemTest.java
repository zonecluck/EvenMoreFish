package com.oheers.fish.fishing.simulator.environment;

import com.oheers.fish.fishing.simulator.behavior.FishPersonality;
import com.oheers.fish.fishing.simulator.behavior.FishPersonalityService;
import com.oheers.fish.fishing.simulator.population.*;
import com.oheers.fish.fishing.simulator.waterfx.SurfaceFxService;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Domain unit tests for Phase 13; integration/Paper verification is a separate release gate. */
class PremiumEcosystemTest {
    private final UUID world=UUID.randomUUID(),owner=UUID.randomUUID(),stranger=UUID.randomUUID();
    private FishAgent agent(String rarity, UUID privateOwner) {
        var cell=new WaterPoint(world,2,65,2);
        var fish=new FishAgent(UUID.randomUUID(),1450000000,
            new FishKey(rarity,"Test"),FishVisualModel.COD,"river",world,privateOwner,1000,
            cell.swimmingPosition());
        fish.placeAt(cell);return fish;
    }
    @Test void environmentCacheNeverBypassesPerTickSamplingBudget() {
        AtomicInteger readings=new AtomicInteger();
        var cache=new EnvironmentSampler(new EnvironmentSampler.Limits(1,16,40),(cell,tick)->{
            readings.incrementAndGet();return new EnvironmentSnapshot(cell,EnvironmentSnapshot.Depth.MEDIUM,
                EnvironmentSnapshot.TimeOfDay.DAY,EnvironmentSnapshot.Weather.CLEAR,"RIVER",4,tick);
        });
        var a=new WaterPoint(world,2,65,2);
        var b=new WaterPoint(world,30,65,2);
        assertNotNull(cache.observe(a,1));
        assertNotNull(cache.observe(a,1));
        assertNull(cache.observe(b,1));
        assertEquals(1,readings.get());
        assertNotNull(cache.observe(b,2));
        cache.invalidate(world,0,0);
        assertNotNull(cache.observe(a,3));
        assertEquals(3,readings.get());
    }
    @Test void personalityDoesNotOverrideFishAiReservation() {
        var behavior=new FishPersonalityService(Map.of("Rare",FishPersonality.SHY),Map.of(),5);
        var pop=new FishPopulationService(10,10,2,2);
        FishAgent fish=agent("Rare",owner);
        assertTrue(pop.add(fish));
        var nav=new FishPersonalityService.Navigation(){
            public List<WaterPoint> adjacent(WaterPoint c){return List.of(new WaterPoint(world,c.x()+1,c.y(),c.z()));}
            public boolean traversable(FishAgent a,WaterPoint c){return true;}
        };
        assertEquals(0,behavior.disturb(fish.position(),world,"river",stranger,pop,nav));
        var hook=UUID.randomUUID();
        assertTrue(pop.reserve(fish.id(),hook,owner));
        assertEquals(0,behavior.disturb(fish.position(),world,"river",owner,pop,nav));
        assertTrue(fish.reservedFor(hook));
        assertEquals(FishAgent.State.RESERVED,fish.state());
    }
    @Test void waterFxRespectViewerPrivacyAndParticleCaps() {
        var fx=new SurfaceFxService(new SurfaceFxService.Limits(2,6,4,8,20));
        var fish=agent("Legendary",owner);
        var behavior=new FishPersonalityService(Map.of("Legendary",FishPersonality.MYTHIC),Map.of(),5);
        var views=List.of(new SurfaceFxService.Viewer(stranger,world,"river",fish.position(),true),
            new SurfaceFxService.Viewer(owner,world,"river",fish.position(),true));
        List<UUID> receivers=new ArrayList<>();
        fx.tick(2,views,List.of(fish),behavior,(id,cue)->{receivers.add(id);return true;});
        assertEquals(List.of(owner),receivers);
        assertTrue(fx.particles()<=6);
        fx.signal(owner,world,"river",fish.position(),SurfaceFxService.Kind.BITE_DISTURBANCE,3);
        fx.tick(4,views,List.of(fish),behavior,(id,cue)->true);
        assertTrue(fx.particles()<=12);
    }
}
