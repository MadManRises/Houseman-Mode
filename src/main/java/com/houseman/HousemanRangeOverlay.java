package com.houseman;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.*;

import java.awt.*;
import java.util.List;

public class HousemanRangeOverlay extends Overlay {
    private final Client client;
    private final HousemanModePlugin plugin;

    @Inject
    public HousemanRangeOverlay(Client client, HousemanModePlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        List<Integer> borders = plugin.getCurrentBorder();

        if (borders != null){
            for (Integer point : borders){
                drawTile(graphics, point, Color.RED);
            }
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
