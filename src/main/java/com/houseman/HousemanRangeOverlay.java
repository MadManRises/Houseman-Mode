package com.houseman;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.*;

import java.awt.*;
import java.util.List;

public class HousemanRangeOverlay extends Overlay {
    private final Client client;
    private final HousemanModePlugin plugin;
    private final HousemanModeConfig config;

    @Inject
    public HousemanRangeOverlay(Client client, HousemanModePlugin plugin, HousemanModeConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        if (config.renderBordersInGame()) {

            List<Integer> borders = plugin.getCurrentBorder();

            if (borders != null) {
                for (Integer point : borders) {
                    drawTile(graphics, point, Color.RED);
                }
            }
        }

        if (plugin.getRemainingTiles() < 0 && plugin.hasItemsToDrop()){
            graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 100.0f));
            graphics.setColor(Color.RED);
            Dimension bounds = graphics.getClipBounds().getSize();
            float width = (float)(graphics.getFont().getStringBounds("Your items are", graphics.getFontRenderContext()).getWidth());
            graphics.drawString("Your items are", (bounds.width - width) / 2, bounds.height / 2 - 150.0f);
            width = (float)( graphics.getFont().getStringBounds("holding you down", graphics.getFontRenderContext()).getWidth());

            graphics.drawString("holding you down", (bounds.width - width) / 2, bounds.height / 2 + 150.0f);
        }

        return null;
    }

    private void drawTile(Graphics2D graphics, int location, Color color) {
        if (client == null) {
            return;
        }

        for (int point : WorldPointUtil.toLocalInstance(client, location)) {
            if (point == WorldPointUtil.UNDEFINED) {
                continue;
            }

            LocalPoint lp = WorldPointUtil.toLocalPoint(client, point);
            if (lp == null) {
                continue;
            }

            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly == null) {
                continue;
            }

            graphics.setColor(color);
            graphics.fill(poly);
        }
    }
}
