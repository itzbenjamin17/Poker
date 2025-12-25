package com.pokergame.service;

import com.pokergame.dto.response.ApiResponse;
import com.pokergame.model.Game;
import com.pokergame.model.Player;
import com.pokergame.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class responsible for game lifecycle management.
 * Handles game creation, starting new hands, and game termination.
 */
@Service
public class GameLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(GameLifecycleService.class);

    @Autowired
    private RoomService roomService;

    @Autowired
    private HandEvaluatorService handEvaluator;

    @Autowired
    private GameStateService gameStateService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Game state storage
    private final Map<String, Game> activeGames = new HashMap<>();

    /**
     * Creates and initializes an actual poker game from an existing room.
     * Requires at least 2 players in the room to start. Converts room players
     * to game players with buy-in chips and starts the first hand.
     *
     * @param roomId the unique identifier of the room to convert to a game
     * @return the game ID (same as room ID) of the newly created game
     * @throws IllegalArgumentException if room not found or has fewer than 2
     *                                  players
     */
    public String createGameFromRoom(String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }

        if (room.getPlayers().size() < 2) {
            throw new IllegalArgumentException("Need at least 2 players to start game");
        }

        // Create the actual poker game
        List<String> playerNames = new ArrayList<>(room.getPlayers());
        List<Player> players = playerNames.stream()
                .map(name -> new Player(name, UUID.randomUUID().toString(), room.getBuyIn()))
                .collect(Collectors.toList());

        Game game = new Game(roomId, players, room.getSmallBlind(), room.getBigBlind(), handEvaluator);
        activeGames.put(roomId, game);

        // Broadcast to all players in the room that the game has started
        Map<String, Object> gameStartMessage = new HashMap<>();
        gameStartMessage.put("gameId", roomId);
        gameStartMessage.put("message", "Game started! Redirecting to game...");

        messagingTemplate.convertAndSend("/rooms" + roomId, new ApiResponse<>(true, "GAME_STARTED", gameStartMessage));

        logger.info("Game created and started for room: {} with {} players", roomId, players.size());

        startNewHand(roomId);

        return roomId;
    }

    /**
     * Starts a new hand of poker for the specified game.
     * Resets game state, deals cards, posts blinds, and broadcasts initial state.
     *
     * @param gameId The unique identifier of the game
     */
    public void startNewHand(String gameId) {
        Game game = getGame(gameId);
        logger.info("Starting new hand for game: {}", gameId);

        if (game.isGameOver()) {
            logger.warn("Cannot start new hand - game {} is over", gameId);
            return;
        }

        game.resetForNewHand();

        if (game.isGameOver()) {
            logger.warn("Game {} became over after reset", gameId);
            return;
        }

        game.dealHoleCards();
        game.postBlinds();
        gameStateService.broadcastGameState(gameId, game);

        logger.info("New hand started successfully for game: {} | Current player: {} | Phase: {} | Pot: {}",
                gameId, game.getCurrentPlayer().getName(), game.getCurrentPhase(), game.getPot());
    }

    /**
     * Removes a player from an active game. If the last player leaves or only
     * one player remains, the game ends and associated resources are cleaned up.
     * If the leaving player is the current player, advances to the next player.
     *
     * @param gameId     the unique identifier of the game
     * @param playerName the name of the player leaving the game
     * @throws IllegalArgumentException if game or player not found
     */
    public void leaveGame(String gameId, String playerName) {
        Game game = getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        // Find and remove the player from the game
        Player playerToRemove = game.getPlayers().stream()
                .filter(p -> p.getName().equals(playerName))
                .findFirst()
                .orElse(null);

        if (playerToRemove == null) {
            throw new IllegalArgumentException("Player not found in game");
        }

        // Check if the leaving player was the current player
        boolean wasCurrentPlayer = game.getCurrentPlayer() != null && game.getCurrentPlayer().equals(playerToRemove);

        // Remove player from both lists
        game.getPlayers().remove(playerToRemove);
        game.getActivePlayers().remove(playerToRemove);

        logger.info("Player {} left game {} | Remaining players: {}",
                playerName, gameId, game.getPlayers().size());

        // Check if no players left in the game
        if (game.getPlayers().isEmpty()) {
            logger.info("All players left game {}, destroying game and room", gameId);

            // Clean up game and room data
            activeGames.remove(gameId);
            roomService.destroyRoom(gameId);
        } else {
            logger.info("Game {} continues with {} players", gameId, game.getPlayers().size());

            // If only one player remains, end the game immediately
            if (game.getPlayers().size() == 1) {
                logger.info("Only one player remaining in game {}, ending game", gameId);
                handleGameEnd(gameId);
                return;
            }

            // If the leaving player was the current player, advance to next player
            if (wasCurrentPlayer && !game.getActivePlayers().isEmpty()) {
                game.nextPlayer();
            }

            gameStateService.broadcastGameState(gameId, game);
        }
    }

    /**
     * Handles the end of a game when all players except one have been eliminated.
     * Broadcasts the game end event to all participants and cleans up game
     * resources.
     *
     * @param gameId the unique identifier of the game
     */
    public void handleGameEnd(String gameId) {
        Game game = getGame(gameId);
        if (game == null)
            return;

        // Find the winner (last remaining player)
        Player winner = game.getActivePlayers().stream()
                .findFirst()
                .orElse(null);

        if (winner == null) {
            // Edge case: no active players (should not happen)
            winner = game.getPlayers().stream()
                    .filter(p -> !p.getIsOut())
                    .findFirst()
                    .orElse(game.getPlayers().getFirst()); // Fallback to first player
        }

        gameStateService.broadcastGameEnd(gameId, winner);
        // TODO: spawning a separate thread for each game end might not be scalable, use a scheduled task executor instead
        // Wait a few seconds for players to see the result, then destroy the room
        new Thread(() -> {
            try {
                Thread.sleep(5000); // 5 seconds

                // Clean up game and room data
                activeGames.remove(gameId);
                roomService.destroyRoom(gameId);

                logger.info("Game {} and associated room cleaned up after game end", gameId);
            } catch (InterruptedException e) {
                logger.error("Error during game end cleanup for game {}: {}", gameId, e.getMessage());
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Retrieves a game by its unique identifier.
     *
     * @param gameId the unique identifier of the game to retrieve
     * @return the Game object if found, null otherwise
     */
    public Game getGame(String gameId) {
        return activeGames.get(gameId);
    }

    /**
     * Checks if a game with the specified ID exists in the active games.
     *
     * @param gameId the unique identifier of the game to check
     * @return true if the game exists in active games, false otherwise
     */
    public boolean gameExists(String gameId) {
        return activeGames.containsKey(gameId);
    }
}
