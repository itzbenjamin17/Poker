package com.pokergame.listener;

import com.pokergame.enums.GamePhase;
import com.pokergame.model.Game;
import com.pokergame.model.Player;
import com.pokergame.service.GameLifecycleService;
import com.pokergame.service.GameStateService;
import com.pokergame.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class GameAsyncEventListener {

    private static final Logger logger = LoggerFactory.getLogger(GameAsyncEventListener.class);

    private final GameLifecycleService gameLifecycleService;
    private final GameStateService gameStateService;
    private final TaskScheduler taskScheduler;

    public GameAsyncEventListener(GameLifecycleService gameLifecycleService,
                                  GameStateService gameStateService,
                                  @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.gameLifecycleService = gameLifecycleService;
        this.gameStateService = gameStateService;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Standard Non-Blocking Delay:
     * Schedules the job for the future and releases the current thread immediately.
     */
    @EventListener
    public void handleStartNewHandDelay(StartNewHandEvent event) {
        logger.info("Scheduling new hand for game {} in {}ms", event.gameId(), event.delay());

        taskScheduler.schedule(() -> {
            try {
                gameLifecycleService.startNewHand(event.gameId());
            } catch (Exception e) {
                logger.error("Error starting new hand for game {}: {}", event.gameId(), e.getMessage());
            }
        }, Instant.now().plusMillis(event.delay()));
    }

    @EventListener
    public void handleGameEndCleanup(GameCleanupEvent event) {
        logger.info("Scheduling cleanup for game {} in {}ms", event.gameId(), event.delay());

        taskScheduler.schedule(() -> {
            try {
                gameLifecycleService.performGameCleanup(event.gameId());
            } catch (Exception e) {
                logger.error("Error cleaning up game {}: {}", event.gameId(), e.getMessage());
            }
        }, Instant.now().plusMillis(event.delay()));
    }

    /**
     * Complex Sequential Delay:
     * For the "deal -> wait -> deal" sequence, we chain the schedules.
     * This avoids blocking a thread for 8+ seconds.
     */
    @EventListener
    public void handleAutoAdvanceToShowdown(AutoAdvanceEvent event) {
        // Start the chain immediately
        scheduleNextAutoAdvanceStep(event.gameId());
    }

    // Recursive scheduling method to handle the sequence steps
    private void scheduleNextAutoAdvanceStep(String gameId) {
        Game game = gameLifecycleService.getGame(gameId);
        if (game == null) return;

        // Determine delay based on what we are about to do
        // (You can tune these numbers)
        long delay = 2000;

        taskScheduler.schedule(() -> {
            try {
                // Re-fetch game state inside the scheduled thread
                Game currentGame = gameLifecycleService.getGame(gameId);
                if (currentGame == null) return;

                GamePhase currentPhase = currentGame.getCurrentPhase();
                boolean sequenceComplete = false;

                if (currentPhase == GamePhase.PRE_FLOP) {
                    currentGame.dealFlop();
                    gameStateService.broadcastGameStateWithAutoAdvance(gameId, currentGame, true, "Dealing flop...");
                } else if (currentPhase == GamePhase.FLOP) {
                    currentGame.dealTurn();
                    gameStateService.broadcastGameStateWithAutoAdvance(gameId, currentGame, true, "Dealing turn...");
                } else if (currentPhase == GamePhase.TURN) {
                    currentGame.dealRiver();
                    gameStateService.broadcastGameStateWithAutoAdvance(gameId, currentGame, true, "Dealing river...");
                } else {
                    // We are at the end (RIVER or SHOWDOWN)
                    int potBeforeDistribution = currentGame.getPot();
                    List<Player> winners = currentGame.conductShowdown();
                    int winningsPerPlayer = winners.isEmpty() ? 0 : potBeforeDistribution / winners.size();

                    gameStateService.broadcastShowdownResults(gameId, currentGame, winners, winningsPerPlayer);
                    gameStateService.broadcastAutoAdvanceComplete(gameId, currentGame);

                    // Schedule the final new hand start
                    taskScheduler.schedule(() -> gameLifecycleService.startNewHand(gameId),
                            Instant.now().plusMillis(5000));
                    sequenceComplete = true;
                }

                // If the sequence isn't done, schedule the next step recursively
                if (!sequenceComplete) {
                    scheduleNextAutoAdvanceStep(gameId);
                }

            } catch (Exception e) {
                logger.error("Error during auto-advance step for game {}: {}", gameId, e.getMessage());
            }
        }, Instant.now().plusMillis(delay));
    }
}