package com.oheers.fish.fishing.simulator.school;

import com.oheers.fish.fishing.simulator.population.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Domain-level JUnit coverage; no Paper or PacketEvents connection required. */
class FishSchoolServiceTest {
    private final UUID world=UUID.randomUUID(), owner=UUID.randomUUID();
    private final FishKey key=new FishKey("common","cod");
    private final SchoolSettings limits=new SchoolSettings(true,4,4,3,2,8,2,3,10,10,6,10,1.2,.12,.14);
    private FishAgent fish(int id,int x,int z,UUID privateOwner){
        var cell=new WaterPoint(world,x,63,z);
        var fish=new FishAgent(UUID.randomUUID(),1_300_000_000+id,key,FishVisualModel.COD,
            "lake",world,privateOwner,1000,cell.swimmingPosition());
        fish.placeAt(cell);return fish;
    }
    private final FishSchoolService.Navigation nav=new FishSchoolService.Navigation(){
        @Override public List<WaterPoint> adjacent(WaterPoint cell){
            return List.of(new WaterPoint(world,cell.x()+1,cell.y(),cell.z()),
                new WaterPoint(world,cell.x()-1,cell.y(),cell.z()));
        }
        @Override public boolean traversable(FishAgent fish,WaterPoint cell){
            return cell.x()>=0 && cell.x()<=8 && cell.z()>=0 && cell.z()<=8;
        }
    };
    @Test void groupingRespectsOwnershipAndMaxMembers(){
        var pop=new FishPopulationService(20,20,10,10);var service=new FishSchoolService(limits);
        var a=fish(1,2,2,null);var b=fish(2,3,2,null);var c=fish(3,4,2,null);
        for(var f:List.of(a,b,c)){assertTrue(pop.add(f));assertTrue(service.onSpawn(f,pop,1));}
        assertEquals(service.schoolId(a.id()),service.schoolId(c.id()));
        assertEquals(3,service.school(service.schoolId(a.id())).size());
        var privateFish=fish(4,2,2,owner);assertTrue(pop.add(privateFish));
        assertTrue(service.onSpawn(privateFish,pop,1));
        assertNotEquals(service.schoolId(a.id()),service.schoolId(privateFish.id()));
        assertNull(service.preferredSpawnCell(world,"lake",null,FishVisualModel.COD,pop,nav,2));
    }
    @Test void reservationWinsAndSchoolElectsNewLeader(){
        var pop=new FishPopulationService(20,20,10,10);var service=new FishSchoolService(limits);
        var a=fish(1,3,3,null);var b=fish(2,4,3,null);
        assertTrue(pop.add(a));assertTrue(pop.add(b));
        service.onSpawn(a,pop,1);service.onSpawn(b,pop,1);
        UUID group=service.schoolId(a.id()), hook=UUID.randomUUID();
        assertTrue(pop.reserve(a.id(),hook,owner));
        service.steer(2,pop,nav);
        assertNull(service.schoolId(a.id()));
        assertTrue(a.reservedFor(hook));
        assertEquals(b.id(),service.school(group).leaderId());
        assertEquals(1,service.members());
    }
    @Test void steeringBudgetsAndScatterLifecycle(){
        var pop=new FishPopulationService(20,20,10,10);var service=new FishSchoolService(limits);
        var a=fish(1,3,3,null);var b=fish(2,4,3,null);
        assertTrue(pop.add(a));assertTrue(pop.add(b));
        service.onSpawn(a,pop,1);service.onSpawn(b,pop,1);
        UUID school=service.schoolId(a.id());
        service.steer(2,pop,nav);
        assertTrue(service.decisions()>0 && service.decisions()<=2);
        assertTrue(service.neighborChecks()<=8);
        service.scatter(a.position(),world,"lake",null,pop,5);
        assertEquals(FishSchool.Behavior.SCATTERED,service.school(school).behavior());
        service.steer(16,pop,nav);
        assertEquals(FishSchool.Behavior.REFORMING,service.school(school).behavior());
        service.steer(26,pop,nav);
        assertEquals(FishSchool.Behavior.CRUISING,service.school(school).behavior());
    }
}
