package com.houseman;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import shortestpath.pathfinder.VisitedTiles;
import shortestpath.WorldPointUtil;

public class HousemanRangeMapOverlay extends Overlay {
    private final Client client;
    private final HousemanModePlugin plugin;

    public BufferedImage image;
    public BufferedImage undergroundImage;

    private boolean imageIsDirty = false;
    private ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private Future<Images> imageFuture;
    private final Object imageMutex = new Object();

    private VisitedTiles currentTiles;

    @Inject
    private HousemanModeConfig config;

    private class Images{
        public BufferedImage image;
        public BufferedImage undergroundImage;
    }

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

        Rectangle worldMapRectangle = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW).getBounds();
        Area worldMapClipArea = getWorldMapClipArea(worldMapRectangle);
        graphics.setClip(worldMapClipArea);

        if (image != null){
            WorldMap worldMap = client.getWorldMap();

            float pixelsPerTile = worldMap.getWorldMapZoom();

            Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);
            if (map != null) {
                Rectangle worldMapRect = map.getBounds();

                int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);

                Point worldMapPosition = worldMap.getWorldMapPosition();

                

                int worldX = 1000;
                int worldY = 4149;
                int sizeX = 2966;
                int sizeY = 1650;

                int xTileOffset = worldX + widthInTiles / 2 - worldMapPosition.getX();

                int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
                //xGraphDiff += pixelsPerTile;
                xGraphDiff += (int) worldMapRect.getX();

                int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

                int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
                int yTileOffset = (yTileMax - worldY - 1) * -1;

                int yGraphDiff = (int) (yTileOffset * pixelsPerTile);
                //yGraphDiff -= pixelsPerTile;
                yGraphDiff = worldMapRect.height - yGraphDiff;
                yGraphDiff += (int) worldMapRect.getY();

                graphics.drawImage(image, xGraphDiff, yGraphDiff, (int)(sizeX * pixelsPerTile), (int)(sizeY * pixelsPerTile), null);
                graphics.drawImage(undergroundImage, xGraphDiff, yGraphDiff - (int)(6400 * pixelsPerTile), (int)(sizeX * pixelsPerTile), (int)(sizeY * pixelsPerTile), null);
            }


        }

        if (config.renderBordersInMap()) {
            List<Integer> borders = plugin.getCurrentBorder();

            if (borders != null) {
                for (Integer point : borders) {
                    drawOnMap(graphics, point);
                }
            }
        }

        return null;
    }

    public void onGameTick(GameTick tick) {
        synchronized (imageMutex) {
            if (imageExecutor == null) {
                ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("range-%d").build();
                imageExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
            }

            if (imageFuture != null && imageFuture.isDone()) {
                try {
                    Images p = imageFuture.get();
                    image = p.image;
                    undergroundImage = p.undergroundImage;
                    imageFuture = null;
                } catch (InterruptedException e) {

                } catch (ExecutionException e) {
                    imageFuture = null;
                } catch (CancellationException e) {
                    imageFuture = null;
                    imageIsDirty = true;
                }
            }

            if (imageFuture == null && imageIsDirty) {
                imageIsDirty = false;
                imageFuture = imageExecutor.submit(() -> {
                    Images images = new Images();
                    images.image = new BufferedImage(2966, 1650, BufferedImage.TYPE_INT_ARGB);
                    images.undergroundImage = new BufferedImage(2966, 1650, BufferedImage.TYPE_INT_ARGB);

                    int posX = 1000;
                    int posY = 2500;

                    for (int y = 0; y < 1650; y++) {
                        for (int x = 0; x < 2966; x++) {
                            WorldPoint point = new WorldPoint(posX + x, posY + 1650 - y - 1, 0);
                            images.image.setRGB(x, y, new Color(1, 1, 1, HousemanModePlugin.isVisible(currentTiles, point) ? 0 : 225).getRGB());
                            WorldPoint undergroundPoint = new WorldPoint(posX + x, 6400 + posY + 1650 - y - 1, 0);
                            images.undergroundImage.setRGB(x, y, new Color(1, 1, 1, HousemanModePlugin.isVisible(currentTiles, undergroundPoint) ? 0 : 225).getRGB());
                        }
                    }

                    return images;
                });
            }
        }
    }

    public void update(VisitedTiles visitedTiles) {
        currentTiles = visitedTiles;
        imageIsDirty = true;
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
