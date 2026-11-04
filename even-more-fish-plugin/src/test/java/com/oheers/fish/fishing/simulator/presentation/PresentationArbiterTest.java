package com.oheers.fish.fishing.simulator.presentation;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PresentationArbiterTest {
    @Test void bitePreemptsInterestedAndOldSessionCannotOverwriteCatch() {
        var arbiter=new PresentationArbiter();
        UUID player=UUID.randomUUID(), old=UUID.randomUUID(), fresh=UUID.randomUUID();
        arbiter.begin(player,old);
        assertTrue(arbiter.offer(player,old,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.APPROACHING,"approach",Map.of(),10,30));
        assertEquals(1,arbiter.drain(10,5).size());
        assertTrue(arbiter.offer(player,old,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.BITE_READY,"bite",Map.of(),11,10));
        assertEquals("bite",arbiter.drain(11,5).getFirst().messageKey());
        assertFalse(arbiter.offer(player,old,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.APPROACHING,"old",Map.of(),12,10));
        arbiter.offer(player,old,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.CATCH_RESULT,"caught",Map.of(),13,35);
        arbiter.close(player,old);
        assertEquals("caught",arbiter.drain(13,5).getFirst().messageKey());
        arbiter.begin(player,fresh);
        assertFalse(arbiter.offer(player,old,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.CATCH_RESULT,"ghost",Map.of(),14,35));
        assertTrue(arbiter.offer(player,fresh,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.BITE_READY,"bite-new",Map.of(),14,10));
        assertEquals("bite-new",arbiter.drain(14,5).getFirst().messageKey());
    }
}
