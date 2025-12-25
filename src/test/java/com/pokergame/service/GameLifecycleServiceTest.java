package com.pokergame.service;

import com.pokergame.enums.GamePhase;
import com.pokergame.model.Game;
import com.pokergame.model.Player;
import com.pokergame.model.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the GameLifecycleService class.
 */
@ExtendWith(MockitoExtension.class)
class GameLifecycleServiceTest {

    @Mock
    private RoomService roomService;

    @Mock
    private HandEvaluatorService handEvaluator;

    @Mock
    private GameStateService gameStateService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GameLifecycleService gameLifecycleService;

    private Room testRoom;
    private static final String ROOM_ID = "test-room-id";

    @BeforeEach
    void setUp() {
        testRoom = new Room(
                ROOM_ID,
                "Test Room",
                "Host",
                6,
                5,
                10,
                100,
                null);
        testRoom.addPlayer("Host");
        testRoom.addPlayer("Player2");
    }

    // ==================== createGameFromRoom Tests ====================

    @Test
    void createGameFromRoom_WithValidRoom_ShouldCreateGame() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);

        String gameId = gameLifecycleService.createGameFromRoom(ROOM_ID);

        assertEquals(ROOM_ID, gameId);
        assertNotNull(gameLifecycleService.getGame(ROOM_ID));
        assertTrue(gameLifecycleService.gameExists(ROOM_ID));

        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        verify(gameStateService).broadcastGameState(eq(ROOM_ID), any(Game.class));
    }

    @Test
    void createGameFromRoom_WithThreePlayers_ShouldCreateGameWithAllPlayers() {
        testRoom.addPlayer("Player3");
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);

        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        assertNotNull(game);
        assertEquals(3, game.getPlayers().size());
    }

    @Test
    void createGameFromRoom_WhenRoomNotFound_ShouldThrowException() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> gameLifecycleService.createGameFromRoom(ROOM_ID));

        assertEquals("Room not found", exception.getMessage());
    }

    @Test
    void createGameFromRoom_WithOnePlayer_ShouldThrowException() {
        Room onePlayerRoom = new Room(
                ROOM_ID,
                "Single Player Room",
                "Host",
                6,
                5,
                10,
                100,
                null);
        onePlayerRoom.addPlayer("Host");

        when(roomService.getRoom(ROOM_ID)).thenReturn(onePlayerRoom);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> gameLifecycleService.createGameFromRoom(ROOM_ID));

        assertEquals("Need at least 2 players to start game", exception.getMessage());
    }

    @Test
    void createGameFromRoom_ShouldSetCorrectBlinds() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);

        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        assertNotNull(game);
        // The game should have the blinds from the room (5 small, 10 big)
        // We can verify this indirectly through the pot after blinds are posted
        assertTrue(game.getPot() > 0);
    }

    @Test
    void createGameFromRoom_ShouldGivePlayersCorrectBuyIn() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);

        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        // Each player should start with buyIn minus any blinds they posted
        for (Player player : game.getPlayers()) {
            assertTrue(player.getChips() <= 100 && player.getChips() >= 85);
        }
    }

    // ==================== startNewHand Tests ====================

    @Test
    void startNewHand_WithValidGame_ShouldResetAndDeal() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);
        reset(gameStateService);

        gameLifecycleService.startNewHand(ROOM_ID);

        verify(gameStateService).broadcastGameState(eq(ROOM_ID), any(Game.class));
    }

    @Test
    void startNewHand_WhenGameOver_ShouldNotProceed() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        // Simulate game over by removing all but one player
        game.getPlayers().subList(1, game.getPlayers().size()).clear();
        game.getActivePlayers().clear();
        game.getActivePlayers().add(game.getPlayers().get(0));

        reset(gameStateService);
        gameLifecycleService.startNewHand(ROOM_ID);

        // Should not broadcast because game became over after reset
        verify(gameStateService, never()).broadcastGameState(anyString(), any(Game.class));
    }

    @Test
    void startNewHand_ShouldDealHoleCardsToPlayers() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        for (Player player : game.getPlayers()) {
            assertEquals(2, player.getHoleCards().size());
        }
    }

    @Test
    void startNewHand_ShouldPostBlinds() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        assertTrue(game.getPot() > 0);
        assertEquals(10, game.getCurrentHighestBet()); // Big blind is 10
    }

    // ==================== leaveGame Tests ====================

    @Test
    void leaveGame_WhenGameNotFound_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> gameLifecycleService.leaveGame("nonexistent-id", "Player"));

        assertEquals("Game not found", exception.getMessage());
    }

    @Test
    void leaveGame_WhenPlayerNotFound_ShouldThrowException() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> gameLifecycleService.leaveGame(ROOM_ID, "NonexistentPlayer"));

        assertEquals("Player not found in game", exception.getMessage());
    }

    @Test
    void leaveGame_OnePlayerRemains_ShouldEndGame() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        String playerToLeave = game.getPlayers().get(0).getName();

        gameLifecycleService.leaveGame(ROOM_ID, playerToLeave);

        verify(gameStateService).broadcastGameEnd(eq(ROOM_ID), any(Player.class));
    }

    @Test
    void leaveGame_WithMultiplePlayers_ShouldContinueGame() {
        testRoom.addPlayer("Player3");
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);
        reset(gameStateService);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        // Find a player who is not the current player
        Player currentPlayer = game.getCurrentPlayer();
        Player playerToLeave = game.getPlayers().stream()
                .filter(p -> !p.equals(currentPlayer))
                .findFirst()
                .orElseThrow();

        gameLifecycleService.leaveGame(ROOM_ID, playerToLeave.getName());

        assertEquals(2, gameLifecycleService.getGame(ROOM_ID).getPlayers().size());
        verify(gameStateService).broadcastGameState(eq(ROOM_ID), any(Game.class));
    }

    // ==================== getGame Tests ====================

    @Test
    void getGame_WithValidId_ShouldReturnGame() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);

        assertNotNull(game);
    }

    @Test
    void getGame_WithInvalidId_ShouldReturnNull() {
        Game game = gameLifecycleService.getGame("nonexistent-id");
        assertNull(game);
    }

    // ==================== gameExists Tests ====================

    @Test
    void gameExists_WhenGameExists_ShouldReturnTrue() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);

        assertTrue(gameLifecycleService.gameExists(ROOM_ID));
    }

    @Test
    void gameExists_WhenGameDoesNotExist_ShouldReturnFalse() {
        assertFalse(gameLifecycleService.gameExists("nonexistent-id"));
    }

    // ==================== handleGameEnd Tests ====================

    @Test
    void handleGameEnd_WithWinner_ShouldBroadcastEnd() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);
        reset(gameStateService);

        gameLifecycleService.handleGameEnd(ROOM_ID);

        verify(gameStateService).broadcastGameEnd(eq(ROOM_ID), any(Player.class));
    }

    @Test
    void handleGameEnd_WithNullGame_ShouldNotBroadcast() {
        gameLifecycleService.handleGameEnd("nonexistent-id");

        verify(gameStateService, never()).broadcastGameEnd(anyString(), any(Player.class));
    }

    // ==================== Integration-like Tests ====================

    @Test
    void createGame_ShouldInitializeCorrectGamePhase() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        assertEquals(GamePhase.PRE_FLOP, game.getCurrentPhase());
    }

    @Test
    void createGame_ShouldHaveEmptyCommunityCards() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        assertTrue(game.getCommunityCards().isEmpty());
    }

    @Test
    void createGame_ShouldSetCurrentPlayer() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(testRoom);
        gameLifecycleService.createGameFromRoom(ROOM_ID);

        Game game = gameLifecycleService.getGame(ROOM_ID);
        assertNotNull(game.getCurrentPlayer());
    }
}
