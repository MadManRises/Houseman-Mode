package com.houseman;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.WorldPointUtil;

import java.awt.*;
import java.awt.geom.Area;
import java.util.List;

public class HousemanRangeMapOverlay extends Overlay {
    private final Client client;
    private final HousemanModePlugin plugin;

    @Inject
    private HousemanRangeMapOverlay(Client client, HousemanModePlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getWidget(ComponentID.WORLD_MAP_MAPVIEW) == null) {
            return null;
        }

        List<Integer> borders = plugin.getCurrentBorder();

        if (borders != null){

            Rectangle worldMapRectangle = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW).getBounds();
            Area worldMapClipArea = getWorldMapClipArea(worldMapRectangle);
            graphics.setClip(worldMapClipArea);


            for (Integer point : borders){
                drawOnMap(graphics, point);
            }
        }

        return null;
    }

    private Area getWorldMapClipArea(Rectangle baseRectangle) {
        final Widget overview = client.getWidget(ComponentID.WORLD_MAP_OVERVIEW_MAP);
        final Widget surfaceSelector = client.getWidget(ComponentID.WORLD_MAP_SURFACE_SELECTOR);

        Area clipArea = new Area(baseRectangle);

        if (overview != null && !overview.isHidden()) {
            clipArea.subtract(new Area(overview.getBounds()));
        }

        if (surfaceSelector != null && !surfaceSelector.isHidden()) {
            clipArea.subtract(new Area(surfaceSelector.getBounds()));
        }

        return clipArea;
    }

    private void drawOnMap(Graphics2D graphics, int point) {
        drawOnMap(graphics, point, WorldPointUtil.dxdy(point, 1, -1));
    }

    private void drawOnMap(Graphics2D graphics, int point, int offsetPoint) {
        int startX = plugin.mapWorldPointToGraphicsPointX(point);
        int startY = plugin.mapWorldPointToGraphicsPointY(point);
        int endX = plugin.mapWorldPointToGraphicsPointX(offsetPoint);
        int endY = plugin.mapWorldPointToGraphicsPointY(offsetPoint);

        if (startX == Integer.MIN_VALUE || startY == Integer.MIN_VALUE ||
                endX == Integer.MIN_VALUE || endY == Integer.MIN_VALUE) {
            return;
        }

        int x = startX;
        int y = startY;
        final int width = endX - x;
        final int height = endY - y;
        x -= width / 2;
        y -= height / 2;

        graphics.fillRect(x, y, width, height);
    }

}
