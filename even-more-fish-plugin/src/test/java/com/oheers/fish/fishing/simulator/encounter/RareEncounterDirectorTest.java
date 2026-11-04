package com.oheers.fish.fishing.simulator.encounter;

import com.oheers.fish.fishing.simulator.population.*;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Paper-independent correctness gates; runtime visibility/Particle checks require staging. */
class RareEncounterDirectorTest {
    private final UUID world=UUID.randomUUID();
    private final UUID owner=UUID.randomUUID();
    private final UUID stranger=UUID.randomUUID();
    private RareEncounterSettings config(RareEncounterDirector.Kind only){
        var rules=new EnumMap<RareEncounterDirector.Kind,RareEncounterSettings.Profile>(RareEncounterDirector.Kind.class);
        for(var kind:RareEncounterDirector.Kind.values())rules.put(kind,
            new RareEncounterSettings.Profile(kind==only,kind==only?1:0,false,false,false,1));
        return new RareEncounterSettings(true,new RareEncounterSettings.Limits(20,16,4,2,100,200,100,12,8,4,16,1),
            Set.of("rare"),rules,Map.of());
    }
    private FishAgent fish(FishPopulationService pop,String rarity,UUID privateOwner,int x){
        var point=new WaterPoint(world,x,64,10);
        var fish=new FishAgent(UUID.randomUUID(),1500000000+x,new FishKey(rarity,"carp"),FishVisualModel.COD,
            "lake",world,privateOwner,400,point.swimmingPosition());
        fish.placeAt(point);
        assertTrue(pop.add(fish));
        return fish;
    }
    @Test void marksExistingFishKeyOnceAndDoesNotChangePopulationOrRewards(){
        var pop=new FishPopulationService(10,10,10,10);
        var rare=fish(pop,"Rare",null,12);
        fish(pop,"Common",null,13);
        var director=new RareEncounterDirector(config(RareEncounterDirector.Kind.GOLDEN),new Random(1));
        director.tick(20,pop,(f,t)->null,f->List.of());
        assertEquals(1,director.active());
        assertEquals(2,pop.size());
        assertEquals(rare.fishKey(),director.snapshots().getFirst().fishKey());
        director.tick(40,pop,(f,t)->null,f->List.of());
        assertEquals(1,director.spawned());
        assertTrue(pop.reserve(rare.id(),UUID.randomUUID(),owner));
        director.tick(41,pop,(f,t)->null,f->List.of());
        assertEquals(RareEncounterDirector.Phase.RESERVED,director.snapshots().getFirst().phase());
    }
    @Test void neverRevealsPrivateFishBeforeItIsVisibleToOwner(){
        var pop=new FishPopulationService(10,10,10,10);
        var fish=fish(pop,"Rare",owner,12);
        var director=new RareEncounterDirector(config(RareEncounterDirector.Kind.GOLDEN),new Random(1));
        director.tick(20,pop,(f,t)->null,f->List.of());
        var near=fish.position();
        var viewers=List.of(
            new RareEncounterDirector.Viewer(stranger,world,"lake",near,true),
            new RareEncounterDirector.Viewer(owner,world,"lake",near,true));
        AtomicInteger sent=new AtomicInteger();
        director.render(24,viewers,pop,(id,fishId)->false,(id,cue)->{sent.incrementAndGet();return true;});
        assertEquals(0,sent.get());
        director.render(36,viewers,pop,(id,fishId)->true,(id,cue)->{
            assertEquals(owner,id);
            assertEquals(fish.id(),cue.fishId());
            sent.incrementAndGet();return true;
        });
        assertEquals(1,sent.get());
        pop.remove(fish.id());
        director.tick(40,pop,(f,t)->null,f->List.of());
        assertEquals(0,director.active());
    }
}
