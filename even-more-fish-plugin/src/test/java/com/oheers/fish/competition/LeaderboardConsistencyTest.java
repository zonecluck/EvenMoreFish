package com.oheers.fish.competition;

import com.oheers.fish.competition.leaderboard.Leaderboard;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaderboardConsistencyTest {
    @Test void equalScoreAndTimeRetainBothPlayersForBothSortDirections() {
        for (var type : List.of(CompetitionType.MOST_FISH, CompetitionType.SHORTEST_FISH)) {
            Leaderboard board = new Leaderboard(type);
            CompetitionEntry first = mock(CompetitionEntry.class), second = mock(CompetitionEntry.class);
            when(first.getPlayer()).thenReturn(new UUID(0, 1)); when(second.getPlayer()).thenReturn(new UUID(0, 2));
            when(first.getValue()).thenReturn(5f); when(second.getValue()).thenReturn(5f);
            when(first.getTime()).thenReturn(100L); when(second.getTime()).thenReturn(100L);
            board.addEntry(first); board.addEntry(second);
            assertEquals(2, board.getSize()); assertEquals(List.of(first, second), board.getEntries());
            board.removeEntry(second); assertEquals(List.of(first), board.getEntries());
        }
    }
    @Test void missingOrShortColourListDoesNotSuppressRows() {
        assertEquals("<white>{name}</white>", Leaderboard.rowColour(List.of(), 5));
        assertEquals("<gold>{name}", Leaderboard.rowColour(List.of("<gold>{name}"), 10));
        assertEquals("<white>{name}</white>", Leaderboard.rowColour(List.of(" "), 1));
    }
}
