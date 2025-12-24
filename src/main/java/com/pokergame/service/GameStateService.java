package com.pokergame.service;

import com.pokergame.dto.PrivatePlayerState;
import com.pokergame.dto.PublicPlayerState;
import com.pokergame.dto.response.PlayerNotificationResponse;
import com.pokergame.dto.response.PublicGameStateResponse;
import com.pokergame.model.Game;
import com.pokergame.model.Player;
import com.pokergame.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service class responsible for managing and broadcasting game state.
 * Handles all game state updates, notifications, and WebSocket broadcasts.
 */
@Service
public class GameStateService {

    private static final Logger logger = LoggerFactory.getLogger(GameStateService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RoomService roomService;

    /**
     * Broadcasts the current game state to all players in the game.
     * Each player receives a personalized view showing only their own hole cards.
     *
     * @param gameId the unique identifier of the game
     * @param game   the Game object containing the current state
     */
    public void broadcastGameState(String gameId, Game game) {
        if (game == null) {
            logger.warn("Cannot broadcast game state - game {} not found", gameId);
            return;
        }

        logger.debug("Broadcasting game state for game {}", gameId);

        messagingTemplate.convertAndSend("/game/" + gameId, buildPublicGameStateResponse(gameId, game));
        // Sending personalised game state to each player
        for (Player targetPlayer : game.getPlayers()) {
            messagingTemplate.convertAndSend(
                    "/game/" + gameId + "/player/" + targetPlayer.getPlayerId(), buildPrivatePlayerState(targetPlayer));
        }
    }

    /**
     * Broadcasts showdown results with winner information to all players.
     * Reveals hole cards, hand ranks, and best hands for winning players only.
     *
     * @param gameId            the unique identifier of the game
     * @param game              the Game object containing current state
     * @param winners           the list of Player objects who won the hand
     * @param winningsPerPlayer the amount of chips each winner receives
     */
    public void broadcastShowdownResults(String gameId, Game game, List<Player> winners, int winningsPerPlayer) {
        if (game == null) {
            logger.warn("Cannot broadcast showdown - game {} not found", gameId);
            return;
        }

        // Get room information
        Room room = roomService.getRoom(gameId);
        String roomName = room != null ? room.getRoomName() : "";
        int maxPlayers = room != null ? room.getMaxPlayers() : 0;

        // Get current player information
        Player currentPlayer = game.getCurrentPlayer();
        String currentPlayerName = currentPlayer != null ? currentPlayer.getName() : null;
        String currentPlayerId = currentPlayer != null ? currentPlayer.getPlayerId() : null;

        // Get winner names
        List<String> winnerNames = winners.stream().map(Player::getName).toList();

        // Convert players to PlayerState DTOs with showdown information
        List<PublicPlayerState> playersList = game.getPlayers().stream().map(player -> {
            boolean isWinner = winners.contains(player);
            String status = player.getHasFolded() ? "FOLDED"
                    : player.getIsOut() ? "OUT" : player.getIsAllIn() ? "ALL_IN" : "ACTIVE";
            return new PublicPlayerState(
                    player.getPlayerId(),
                    player.getName(),
                    player.getChips(),
                    player.getCurrentBet(),
                    status,
                    player.getIsAllIn(),
                    false, // isCurrentPlayer not relevant during showdown
                    player.getHasFolded(),
                    isWinner ? player.getHandRank() : null,
                    isWinner ? player.getBestHand() : List.of(),
                    isWinner,
                    isWinner ? winningsPerPlayer : 0);
        }).toList();

        // Create PublicGameStateResponse DTO with showdown information
        PublicGameStateResponse showdownResponse = new PublicGameStateResponse(
                maxPlayers,
                game.getPot(),
                game.getCurrentPhase(),
                game.getCurrentHighestBet(),
                game.getCommunityCards(),
                playersList,
                currentPlayerName,
                currentPlayerId,
                winnerNames,
                winningsPerPlayer);

        // Broadcast showdown results to all players
        messagingTemplate.convertAndSend("/game/" + gameId, showdownResponse);

        logger.info("Broadcasted showdown results for game {} with {} winner(s): {}",
                gameId, winnerNames.size(), winnerNames);
        logger.debug("Showdown game state - winners: {}, winnerCount: {}, winnings per player: {}",
                winnerNames, winnerNames.size(), winningsPerPlayer);
    }

    /**
     * Broadcasts game state with auto-advance information when all players are
     * all-in.
     * Includes special flags and messages to notify clients of automatic
     * progression.
     *
     * @param gameId          the unique identifier of the game
     * @param game            the Game object containing current state
     * @param isAutoAdvancing true if auto-advancing to showdown, false otherwise
     * @param message         the message to display to players about auto-advance
     *                        status
     */
    public void broadcastGameStateWithAutoAdvance(String gameId, Game game, boolean isAutoAdvancing, String message) {
        if (game == null) {
            logger.warn("Cannot broadcast auto-advance state - game {} not found", gameId);
            return;
        }

        // Get room information
        Room room = roomService.getRoom(gameId);
        String roomName = room != null ? room.getRoomName() : "";
        int maxPlayers = room != null ? room.getMaxPlayers() : 0;

        // Get current player information
        Player currentPlayer = game.getCurrentPlayer();
        String currentPlayerName = currentPlayer != null ? currentPlayer.getName() : null;
        String currentPlayerId = currentPlayer != null ? currentPlayer.getPlayerId() : null;

        // Convert players to PlayerState DTOs
        List<PublicPlayerState> playersList = game.getPlayers().stream().map(player -> {
            String status = player.getHasFolded() ? "FOLDED"
                    : player.getIsOut() ? "OUT" : player.getIsAllIn() ? "ALL_IN" : "ACTIVE";
            boolean isCurrentPlayer = player.equals(currentPlayer);
            return new PublicPlayerState(
                    player.getPlayerId(),
                    player.getName(),
                    player.getChips(),
                    player.getCurrentBet(),
                    status,
                    player.getIsAllIn(),
                    isCurrentPlayer,
                    player.getHasFolded(),
                    player.getHandRank(),
                    List.of(),
                    null,
                    null);
        }).toList();

        // Create PublicGameStateResponse DTO with auto-advance fields
        PublicGameStateResponse autoAdvanceResponse = new PublicGameStateResponse(
                maxPlayers,
                game.getPot(),
                game.getCurrentPhase(),
                game.getCurrentHighestBet(),
                game.getCommunityCards(),
                playersList,
                currentPlayerName,
                currentPlayerId,
                null, // winners
                null, // winningsPerPlayer
                isAutoAdvancing,
                message);

        messagingTemplate.convertAndSend("/game/" + gameId, autoAdvanceResponse);
    }

    /**
     * Broadcasts a notification that auto-advance to showdown is starting.
     * Sent when all active players are all-in.
     *
     * @param gameId the unique identifier of the game
     * @param game   the Game object containing current state
     */
    public void broadcastAutoAdvanceNotification(String gameId, Game game) {
        if (game == null) {
            logger.warn("Cannot broadcast auto-advance notification - game {} not found", gameId);
            return;
        }

        messagingTemplate.convertAndSend("/game/" + gameId,
                new PlayerNotificationResponse("AUTO_ADVANCE_START",
                        "All players are all-in. Auto-advancing to showdown...", null, gameId));
    }

    /**
     * Broadcasts a notification that auto-advance has completed.
     * Sent after all community cards have been dealt and showdown is ready.
     *
     * @param gameId the unique identifier of the game
     * @param game   the Game object containing current state
     */
    public void broadcastAutoAdvanceComplete(String gameId, Game game) {
        if (game == null) {
            logger.warn("Cannot broadcast auto-advance complete - game {} not found", gameId);
            return;
        }

        messagingTemplate.convertAndSend("/game/" + gameId,
                new PlayerNotificationResponse("AUTO_ADVANCE_COMPLETE", "", null, gameId));
    }

    /**
     * Sends a notification message to a specific player.
     * Used for action conversions and other player-specific alerts.
     *
     * @param gameId     the unique identifier of the game
     * @param playerName the name of the player to notify
     * @param message    the notification message content
     */
    public void sendPlayerNotification(String gameId, String playerName, String message) {
        PlayerNotificationResponse notification = new PlayerNotificationResponse(
                "PLAYER_NOTIFICATION",
                message,
                playerName,
                gameId);

        messagingTemplate.convertAndSend("/game/" + gameId, notification);
    }

    /**
     * Broadcasts a game end message when a winner is determined.
     * Sent when only one player remains with chips.
     *
     * @param gameId the unique identifier of the game
     * @param winner the Player object representing the game winner
     */
    public void broadcastGameEnd(String gameId, Player winner) {
        Map<String, Object> gameEndData = new HashMap<>();
        gameEndData.put("type", "GAME_END");
        gameEndData.put("winner", winner.getName());
        gameEndData.put("winnerChips", winner.getChips());
        gameEndData.put("gameId", gameId);
        gameEndData.put("message", "🏆 " + winner.getName() + " wins the game with " + winner.getChips() + " chips!");

        messagingTemplate.convertAndSend("/game/" + gameId, (Object) gameEndData);

        logger.info("Game {} completed - Winner: {} with {} chips",
                gameId, winner.getName(), winner.getChips());
    }

    /**
     * Builds a PublicGameStateResponse object to be shown to all players in a game.
     *
     * @param gameId the unique identifier of the game
     * @param game   the Game object containing current state
     * @return a PublicGameStateResponse
     */
    private PublicGameStateResponse buildPublicGameStateResponse(String gameId, Game game) {
        Room room = roomService.getRoom(gameId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }
        List<PublicPlayerState> playerStateList = new ArrayList<>();
        Player currentPlayer = game.getCurrentPlayer();
        for (Player player : game.getPlayers()) {
            String status = player.getHasFolded() ? "FOLDED"
                    : player.getIsOut() ? "OUT" : player.getIsAllIn() ? "ALL_IN" : "ACTIVE";
            playerStateList.add(new PublicPlayerState(
                    player.getPlayerId(),
                    player.getName(),
                    player.getChips(),
                    player.getCurrentBet(),
                    status,
                    player.getIsAllIn(),
                    player.equals(currentPlayer),
                    player.getHasFolded()));
        }

        return new PublicGameStateResponse(
                room.getMaxPlayers(),
                game.getPot(),
                game.getCurrentPhase(),
                game.getCurrentHighestBet(),
                game.getCommunityCards(),
                playerStateList,
                currentPlayer.getName(),
                currentPlayer.getPlayerId());

    }

    /**
     * Builds a PrivatePlayerState object to show to specific players.
     *
     * @param player object
     * @return a PrivatePlayerState
     */
    private PrivatePlayerState buildPrivatePlayerState(Player player) {
        return new PrivatePlayerState(
                player.getPlayerId(),
                player.getHoleCards());

    }

}
