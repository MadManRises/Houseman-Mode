package com.chanceman.managers;

import com.chanceman.ChanceManOverlay;
import com.chanceman.ChanceManPanel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the roll animation for unlocking items.
 * It processes roll requests asynchronously and handles the roll animation through the overlay.
 */
@Singleton
public class RollAnimationManager
{
    @Inject private ItemManager itemManager;
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private UnlockedItemsManager unlockedManager;
    @Inject private ChanceManOverlay overlay;
    @Setter private ChanceManPanel chanceManPanel;

    @Setter private HashSet<Integer> allTradeableItems;
    private final Queue<Integer> rollQueue = new ConcurrentLinkedQueue<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isRolling = false;
    private final int rollDuration = 3000; // Continuous phase duration (ms)
    private final int highlightDuration = 1500; // Highlight phase (ms)
    private final Random random = new Random();

    @Getter
    @Setter
    private volatile boolean manualRoll = false;

    /**
     * Enqueues an item ID for the roll animation.
     *
     * @param itemId The item ID to be rolled.
     */
    public void enqueueRoll(int itemId)
    {
        rollQueue.offer(itemId);
    }

    /**
     * Processes the roll queue by initiating a roll animation if not already rolling.
     */
    public void process()
    {
        if (!isRolling && !rollQueue.isEmpty())
        {
            int queuedItemId = rollQueue.poll();
            isRolling = true;
            executor.submit(() -> performRoll(queuedItemId));
        }
    }

    /**
     * Performs the roll animation, unlocking the final item and sending a chat message.
     */
    private void performRoll(int queuedItemId)
    {
        overlay.startRollAnimation(0, rollDuration, this::getRandomLockedItem);
        try {
            Thread.sleep(rollDuration + highlightDuration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int finalRolledItem = overlay.getFinalItem();
        unlockedManager.unlockItem(finalRolledItem);
        final boolean wasManualRoll = isManualRoll();
        clientThread.invoke(() -> {
            String message;
            if (wasManualRoll)
            {
                message = "Unlocked " + "<col=267567>" + getItemName(finalRolledItem) + "</col>" +
                      " by" + "<col=ff0000> pressing a button</col>";
            }
            else
            {
                message = "Unlocked " + "<col=267567>" + getItemName(finalRolledItem) + "</col>"
                        + " by rolling " + "<col=ff0000>" + getItemName(queuedItemId) + "</col>";
            }
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
            if (chanceManPanel != null) {
                SwingUtilities.invokeLater(() -> chanceManPanel.updatePanel());
            }
        });
        setManualRoll(false);
        isRolling = false;
    }

    /**
     * Checks if a roll animation is currently in progress.
     *
     * @return true if a roll is in progress, false otherwise.
     */
    public boolean isRolling() {
        return isRolling;
    }

    /**
     * Retrieves a random locked item from the list of tradeable items.
     *
     * @return A random locked item ID, or a fallback if all items are unlocked.
     */
    public int getRandomLockedItem()
    {
        List<Integer> locked = new ArrayList<>();
        for (int id : allTradeableItems)
        {
            if (!unlockedManager.isUnlocked(id))
            {
                locked.add(id);
            }
        }
        if (locked.isEmpty())
        {
            int fallback = overlay.getFinalItem();
            return fallback;
        }
        int selected = locked.get(random.nextInt(locked.size()));
        return selected;
    }

    public String getItemName(int itemId)
    {
        ItemComposition comp = itemManager.getItemComposition(itemId);
        return comp != null ? comp.getName() : "Unknown";
    }

    public void startUp() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newSingleThreadExecutor();
        }
    }

    /**
     * Shuts down the roll animation executor service.
     */
    public void shutdown()
    {
        executor.shutdownNow();
    }
}
