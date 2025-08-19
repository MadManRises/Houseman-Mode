package com.houseman;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.ShortestPathPlugin;
import shortestpath.pathfinder.VisitedTiles;
import shortestpath.WorldPointUtil;

import java.awt.*;
import java.util.List;

import static net.runelite.api.Perspective.LOCAL_COORD_BITS;

public class HousemanRangeMinimapOverlay extends Overlay {
    private final Client client;
    private final HousemanModePlugin plugin;

    @Inject
    private HousemanModeConfig config;

    private VisitedTiles currentTiles;

    @Inject
    private HousemanRangeMinimapOverlay(Client client, HousemanModePlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        if (currentTiles != null) {
            Widget minimapDrawWidget;
            if (client.isResized()) {
                if (client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1) {
                    minimapDrawWidget = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA);
                } else {
                    minimapDrawWidget = client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_STONES_DRAW_AREA);
                }
            } else {
                minimapDrawWidget = client.getWidget(WidgetInfo.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
            }

            if (minimapDrawWidget != null && !minimapDrawWidget.isHidden()) {
                Rectangle miniMapRect = minimapDrawWidget.getBounds();

                float pixelsPerTile = (float) client.getMinimapZoom();

                int widthInTiles = (int) Math.ceil(miniMapRect.getWidth() / pixelsPerTile);

                WorldPoint player = client.getLocalPlayer().getWorldLocation();

                for (int x = 0; x < widthInTiles+1; x++) {
                    for (int y = 0; y < widthInTiles+1; y++) {
                        WorldPoint p = new WorldPoint(player.getX() - widthInTiles / 2 + x, player.getY() - widthInTiles / 2 + y, player.getPlane());
                        if (!HousemanModePlugin.isVisible(currentTiles, p))
                            drawTile(graphics, WorldPointUtil.packWorldPoint(p), new Color(1, 1, 1, 255));
                    }
                }
            }
        }

        if (config.renderBordersInMinimap()) {

            List<Integer> borders = plugin.getCurrentBorder();

            if (borders != null) {
                for (Integer point : borders) {
                    drawTile(graphics, point, Color.RED);
                }
            }
        }

        return null;
    }

    public void update(VisitedTiles visitedTiles) {
        currentTiles = visitedTiles;
    }

    private void drawTile(Graphics2D graphics, int location, Color color) {
        for (int point : WorldPointUtil.toLocalInstance(client, location)) {
            LocalPoint lp = WorldPointUtil.toLocalPoint(client, point);

            if (lp == null) {
                continue;
            }

            Point posOnMinimap = Perspective.localToMinimap(client, lp);

            if (posOnMinimap == null) {
                continue;
            }

            renderMinimapRect(client, graphics, posOnMinimap, color);
        }
    }

    public static void renderMinimapRect(Client client, Graphics2D graphics, Point center, Color color) {
        double angle = client.getCameraYawTarget() * Perspective.UNIT;
        double tileSize = client.getMinimapZoom();
        int x = (int) Math.round(center.getX() - tileSize / 2);
        int y = (int) Math.round(center.getY() - tileSize / 2);
        int width = (int) Math.ceil(tileSize) + 1;
        int height = (int) Math.ceil(tileSize) + 1;
        graphics.setColor(color);
        graphics.rotate(angle, center.getX(), center.getY());
        graphics.fillRect(x, y, width, height);
        graphics.rotate(-angle, center.getX(), center.getY());
    }
}
