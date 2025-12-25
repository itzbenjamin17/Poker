package com.pokergame.service;

import com.pokergame.dto.internal.PlayerDecision;
import com.pokergame.dto.request.PlayerActionRequest;
import com.pokergame.model.Game;
import com.pokergame.enums.GamePhase;
import com.pokergame.model.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service class responsible for processing player actions and game progression.
 * Handles player decisions, betting rounds, and automatic game advancement.
 */
@Service
public class PlayerActionService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerActionService.class);

    @Autowired
    private GameLifecycleService gameLifecycleService;

    @Autowired
    private GameStateService gameStateService;

    // Betting round state tracking
    private final Map<String, Set<String>> playersWhoActedInInitialTurn = new HashMap<>();

    /**
     * Processes a player action request and advances the game state accordingly.
     * Validates the request, processes the decision, and handles game progression.
     *
     * @param gameId        the unique identifier of the game
     * @param actionRequest the action request containing action type and amount
     * @param playerName    the authenticated player name (from JWT Principal)
     * @throws SecurityException if the requesting player is not the current player
     */
    public void processPlayerAction(String gameId, PlayerActionRequest actionRequest, String playerName) {
        Game game = gameLifecycleService.getGame(gameId);
        if (game == null) {
            throw new com.pokergame.exception.ResourceNotFoundException("Game not found when processing player action in game: " + gameId);
        }

        // Synchronize on the game object to prevent concurrent modifications
        synchronized (game) {
            Player currentPlayer = game.getCurrentPlayer();

            logger.debug("Processing player action - Game: {}, Player: {}, Action: {}",
                    gameId, currentPlayer.getName(), actionRequest.action());
            logger.debug("Game state - Phase: {}, Current bet: {}",
                    game.getCurrentPhase(), game.getCurrentHighestBet());

            // Verify that the requesting player is the current player
            if (!currentPlayer.getName().equals(playerName)) {
                logger.warn("Player name mismatch: expected {}, got {}",
                        currentPlayer.getName(), playerName);
                throw new com.pokergame.exception.UnauthorizedActionException("It's not your turn. Current player is: " + currentPlayer.getName());
            }

            PlayerDecision decision = new PlayerDecision(
                    actionRequest.action(),
                    actionRequest.amount() != null ? actionRequest.amount() : 0,
                    currentPlayer.getPlayerId());

            logger.debug("Processing decision: {}", decision);

            // Process the decision first - this is the critical operation that must succeed
            String conversionMessage = game.processPlayerDecision(currentPlayer, decision);
            logger.debug("Decision processed successfully");

            // If there was a conversion, notify the player
            if (conversionMessage != null) {
                logger.info("Sending conversion message to player {}: {}", currentPlayer.getName(), conversionMessage);
                gameStateService.sendPlayerNotification(gameId, currentPlayer.getName(), conversionMessage);
            }

            // After successful processing, handle game progression and broadcasting
            // This is done in a try-catch to ensure that even if broadcasting fails,
            // the action itself was successful
            try {
                // Track who has acted in the initial turn
                Set<String> actedPlayers = playersWhoActedInInitialTurn.computeIfAbsent(gameId, k -> new HashSet<>());
                actedPlayers.add(currentPlayer.getPlayerId());

                // Check if everyone has had their initial turn
                List<Player> playersWhoShouldAct = game.getActivePlayers().stream()
                        .filter(p -> !p.getHasFolded() && !p.getIsAllIn())
                        .toList();

                boolean everyoneHasActed = playersWhoShouldAct.stream()
                        .allMatch(p -> actedPlayers.contains(p.getPlayerId()));

                if (everyoneHasActed && !game.isBettingRoundComplete()) {
                    game.setEveryoneHasHadInitialTurn(true);
                }

                // Broadcast game state after player action
                gameStateService.broadcastGameState(gameId, game);

                logger.debug("Checking if betting round is complete for game {}...", gameId);
                if (game.isBettingRoundComplete()) {
                    logger.info("Betting round complete for game {}, advancing game", gameId);
                    playersWhoActedInInitialTurn.remove(gameId);
                    advanceGame(gameId);
                } else {
                    logger.debug("Betting round not complete for game {}, moving to next player", gameId);
                    game.nextPlayer();
                    gameStateService.broadcastGameState(gameId, game);
                }
            } catch (Exception e) {
                logger.error("Error in post-processing for game {} (action was successful): {}", gameId, e.getMessage(),
                        e);
                // Re-broadcast to ensure clients have updated state
                try {
                    gameStateService.broadcastGameState(gameId, game);
                } catch (Exception broadcastError) {
                    logger.error("Failed to re-broadcast game state for game {}: {}", gameId,
                            broadcastError.getMessage());
                }
            }

            logger.debug("Player action processing complete for game {}", gameId);
        } // End synchronized block
    }

    /**
     * Advances the game to the next phase or conducts showdown if hand is over.
     * Handles progression through betting rounds (PRE_FLOP → FLOP → TURN → RIVER →
     * SHOWDOWN)
     * and manages game state transitions. Automatically advances when all players
     * are all-in.
     *
     * @param gameId the unique identifier of the game to advance
     */
    private void advanceGame(String gameId) {
        Game game = gameLifecycleService.getGame(gameId);
        logger.info("Advancing game {} from phase: {}", gameId, game.getCurrentPhase());

        if (game.isHandOver()) {
            logger.info("Hand is over for game {}, conducting showdown", gameId);
            int potBeforeDistribution = game.getPot();
            List<Player> winners = game.conductShowdown();
            logger.info("Showdown complete for game {} | Winners: {}",
                    gameId, winners.stream().map(Player::getName).toList());

            int winningsPerPlayer = winners.isEmpty() ? 0 : potBeforeDistribution / winners.size();
            gameStateService.broadcastShowdownResults(gameId, game, winners, winningsPerPlayer);

            // Delay before starting new hand to allow winner display
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // 5 seconds
                    logger.info("Starting new hand after showdown for game {}", gameId);
                    gameLifecycleService.startNewHand(gameId);
                } catch (InterruptedException e) {
                    logger.error("Error during showdown delay for game {}: {}", gameId, e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logger.error("Error starting new hand after showdown for game {}: {}", gameId, e.getMessage(), e);
                }
            }).start();
            return;
        }

        // Check if we need to auto-advance because of all-in situation
        long playersAbleToAct = game.getActivePlayers().stream()
                .filter(p -> !p.getHasFolded() && !p.getIsAllIn())
                .count();

        logger.debug("Game {} status | Players able to act: {} | Betting round complete: {}",
                gameId, playersAbleToAct, game.isBettingRoundComplete());

        // Auto-advance if betting round is complete AND most players are all-in
        if (game.isBettingRoundComplete() && playersAbleToAct <= 1) {
            logger.info("All-in situation detected for game {}, auto-advancing to showdown", gameId);
            gameStateService.broadcastAutoAdvanceNotification(gameId, game);
            autoAdvanceToShowdown(gameId);
            return;
        }

        // Normal advancement logic
        switch (game.getCurrentPhase()) {
            case PRE_FLOP:
                logger.info("Game {} advancing to FLOP phase", gameId);
                game.dealFlop();
                gameStateService.broadcastGameState(gameId, game);
                break;
            case FLOP:
                logger.info("Game {} advancing to TURN phase", gameId);
                game.dealTurn();
                gameStateService.broadcastGameState(gameId, game);
                break;
            case TURN:
                logger.info("Game {} advancing to RIVER phase", gameId);
                game.dealRiver();
                gameStateService.broadcastGameState(gameId, game);
                break;
            case RIVER:
                logger.info("RIVER betting complete for game {}, conducting showdown", gameId);
                int potBeforeDistribution = game.getPot();
                List<Player> winners = game.conductShowdown();
                logger.info("Showdown complete for game {} | Winners: {}",
                        gameId, winners.stream().map(Player::getName).toList());

                int winningsPerPlayer = winners.isEmpty() ? 0 : potBeforeDistribution / winners.size();
                gameStateService.broadcastShowdownResults(gameId, game, winners, winningsPerPlayer);

                // Delay before starting new hand
                new Thread(() -> {
                    try {
                        Thread.sleep(5000);
                        logger.info("Starting new hand after River showdown for game {}", gameId);
                        gameLifecycleService.startNewHand(gameId);
                    } catch (InterruptedException e) {
                        logger.error("Error during River showdown delay for game {}: {}", gameId, e.getMessage());
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        logger.error("Error starting new hand after River showdown for game {}: {}",
                                gameId, e.getMessage(), e);
                    }
                }).start();
                break;
            case SHOWDOWN:
                logger.warn("Game {} is already in SHOWDOWN phase", gameId);
                break;
        }
    }

    /**
     * Automatically advances the game to showdown when all active players are
     * all-in.
     * Deals remaining community cards with appropriate timing delays between each
     * phase
     * and conducts showdown. Runs in a separate thread to avoid blocking.
     *
     * @param gameId the unique identifier of the game to advance
     */
    private void autoAdvanceToShowdown(String gameId) {
        Game game = gameLifecycleService.getGame(gameId);
        if (game == null)
            return;

        logger.info("Starting auto-advance to showdown for game: {}", gameId);
        gameStateService.broadcastGameStateWithAutoAdvance(gameId, game, true,
                "All players are all-in. Auto-advancing to showdown...");

        new Thread(() -> {
            try {
                GamePhase currentPhase = game.getCurrentPhase();

                // Deal remaining cards with delays
                if (currentPhase == GamePhase.PRE_FLOP) {
                    Thread.sleep(2000);
                    game.dealFlop();
                    logger.info("Auto-dealt FLOP for game {}", gameId);
                    gameStateService.broadcastGameStateWithAutoAdvance(gameId, game, true,
                            "Dealing flop...");
                    currentPhase = GamePhase.FLOP;
                }

                if (currentPhase == GamePhase.FLOP) {
                    Thread.sleep(2000);
                    game.dealTurn();
                    logger.info("Auto-dealt TURN for game {}", gameId);
                    gameStateService.broadcastGameStateWithAutoAdvance(gameId, game, true,
                            "Dealing turn...");
                    currentPhase = GamePhase.TURN;
                }

                if (currentPhase == GamePhase.TURN) {
                    Thread.sleep(2000);
                    game.dealRiver();
                    logger.info("Auto-dealt RIVER for game {}", gameId);
                    gameStateService.broadcastGameStateWithAutoAdvance(gameId, game, true,
                            "Dealing river...");
                }

                // Conduct showdown
                Thread.sleep(2000);
                logger.info("Auto-advance showdown for game {}", gameId);
                int potBeforeDistribution = game.getPot();
                List<Player> winners = game.conductShowdown();

                int winningsPerPlayer = winners.isEmpty() ? 0 : potBeforeDistribution / winners.size();
                gameStateService.broadcastShowdownResults(gameId, game, winners, winningsPerPlayer);
                gameStateService.broadcastAutoAdvanceComplete(gameId, game);

                // Start new hand after delay
                Thread.sleep(5000);
                logger.info("Starting new hand after auto-advance for game {}", gameId);
                gameLifecycleService.startNewHand(gameId);

            } catch (InterruptedException e) {
                logger.error("Auto-advance interrupted for game {}: {}", gameId, e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Error during auto-advance for game {}: {}", gameId, e.getMessage(), e);
            }
        }).start();
    }
}
