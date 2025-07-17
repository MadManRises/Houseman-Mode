package com.chanceman;

import com.houseman.HousemanModeConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Overlay for displaying the roll animation.
 * It renders a scrolling set of item icons and highlights the final item.
 */
@Singleton @Slf4j
public class ChanceManOverlay extends Overlay
{
    @Inject private AudioPlayer audioPlayer;
    @Inject private HousemanModeConfig config;
    private final Client client;
    private final ItemManager itemManager;

    // Animation state
    private volatile boolean isAnimating = false;
    private long rollDuration;             // spin duration (ms)
    private long highlightDuration = 2000; // highlight duration (ms)
    private long rollStartTime = 0;

    // Spin parameters
    private float rollOffset = 0f;
    private float currentSpeed;
    private final float initialSpeed = 975f;   // start speed (px/sec)
    private final float deceleration = 425f;    // px/sec^2
    private final float minSpeed = 120f;        // never go below this speed

    // Icon layout
    private final int iconCount = 5;
    private final int iconWidth = 32;
    private final int iconHeight = 32;
    private final int spacing = 5;

    // Extra width so icons don't spill out mid-scroll
    private final int extraWidthBuffer = 17;

    // Overall bounding box padding (internal margin)
    private final int outerPad = 5;

    // Vertical offset from the top of the 3D viewport
    private final int offsetFromTop = 20;

    // Shift the entire background box left by 20 pixels
    private final int boxShift = -20;

    // For drawing the background
    private final int cornerRadius = 10;
    private final float borderStrokeWidth = 2f;

    // List of rolling items (synchronized for thread safety)
    private final List<Integer> rollingItems = Collections.synchronizedList(new ArrayList<>());
    private Supplier<Integer> randomLockedItemSupplier;

    @Inject
    public ChanceManOverlay(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    /**
     * Starts the roll animation.
     *
     * @param dummy Unused parameter.
     * @param rollDurationMs The duration of the roll phase in milliseconds.
     * @param randomLockedItemSupplier Supplier for obtaining random locked items.
     */
    public void startRollAnimation(int dummy, int rollDurationMs, Supplier<Integer> randomLockedItemSupplier)
    {
        if (config.enableRollSounds())
        {
            try
            {
                audioPlayer.play(ChanceManOverlay.class,
                        "/com/chanceman/tick.wav",
                        0.0f);
            }
            catch (IOException | UnsupportedAudioFileException | LineUnavailableException ex)
            {
                log.warn("ChanceMan: failed to play tick.wav", ex);
            }
        }
        this.rollDuration = rollDurationMs;
        this.rollStartTime = System.currentTimeMillis();
        this.rollOffset = 0f;
        this.currentSpeed = initialSpeed;
        this.randomLockedItemSupplier = randomLockedItemSupplier;
        this.isAnimating = true;

        synchronized (rollingItems) {
            rollingItems.clear();
            for (int i = 0; i < iconCount; i++)
            {
                rollingItems.add(randomLockedItemSupplier.get());
            }
        }
    }

    /**
     * Retrieves the final item ID based on the center icon after the roll animation finishes.
     *
     * @return The final item ID.
     */
    public int getFinalItem()
    {
        synchronized (rollingItems) {
            int centerIndex = iconCount / 2;
            if (rollingItems.size() > centerIndex)
            {
                return rollingItems.get(centerIndex);
            }
        }
        return 0;
    }

    /**
     * Renders the roll animation overlay.
     *
     * @param g The graphics context.
     * @return null (no preferred size).
     */
    @Override
    public Dimension render(Graphics2D g)
    {
        if (!isAnimating)
        {
            return null;
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        long now = System.currentTimeMillis();
        long elapsed = now - rollStartTime;
        boolean inHighlightPhase = (elapsed > rollDuration);

        if (elapsed > rollDuration + highlightDuration)
        {
            isAnimating = false;
            return null;
        }

        int vpX = client.getViewportXOffset();
        int vpY = client.getViewportYOffset();
        int vpWidth = client.getViewportWidth();
        int centerX = vpX + (vpWidth / 2);
        int boxTopY = vpY + offsetFromTop;

        int totalIconsWidth = iconCount * iconWidth + (iconCount - 1) * spacing;
        int totalWidthWithBuffer = totalIconsWidth + extraWidthBuffer;
        int boxWidth = totalWidthWithBuffer + outerPad * 2;
        int boxHeight = iconHeight + outerPad * 2;
        int boxLeftX = centerX - (boxWidth / 2) + boxShift;

        Shape backgroundRect = new RoundRectangle2D.Float(
                boxLeftX, boxTopY, boxWidth, boxHeight,
                cornerRadius, cornerRadius
        );
        g.setColor(new Color(0, 0, 0, 180));
        g.fill(backgroundRect);
        g.setColor(new Color(0, 0, 0, 255));
        g.setStroke(new BasicStroke(borderStrokeWidth));
        g.draw(backgroundRect);

        synchronized (rollingItems) {
            if (!inHighlightPhase)
            {
                float dt = 1f / 60f;
                rollOffset += currentSpeed * dt;
                currentSpeed = Math.max(currentSpeed - deceleration * dt, minSpeed);

                if (rollOffset >= (iconWidth + spacing))
                {
                    rollOffset -= (iconWidth + spacing);
                    if (!rollingItems.isEmpty())
                    {
                        rollingItems.remove(0);
                    }
                    rollingItems.add(randomLockedItemSupplier.get());
                }
            }

            int iconsLeftX = centerX - (totalIconsWidth / 2);
            int iconsY = boxTopY + outerPad;
            for (int i = 0; i < rollingItems.size(); i++)
            {
                int itemId = rollingItems.get(i);
                BufferedImage image = itemManager.getImage(itemId, 1, false);
                if (image != null)
                {
                    int drawX = (int) (iconsLeftX + i * (iconWidth + spacing) - rollOffset);
                    g.drawImage(image, drawX, iconsY, iconWidth, iconHeight, null);
                }
            }

            if (inHighlightPhase)
            {
                int centerIndex = iconCount / 2;
                int highlightX = (int) (iconsLeftX + centerIndex * (iconWidth + spacing) - rollOffset);
                g.setColor(Color.YELLOW);
                g.setStroke(new BasicStroke(3f));
                g.drawRect(highlightX, iconsY, iconWidth, iconHeight);
            }
        }
        return null;
    }
}
