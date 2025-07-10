/*
 * Copyright (c) 2018, TheLonelyDev <https://github.com/TheLonelyDev>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2020, ConorLeckey <https://github.com/ConorLeckey>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.houseman;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import shortestpath.ShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderConfig;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Houseman Mode",
        description = "Support for houseman mode",
        tags = {"overlay", "tiles"}
)
public class HousemanModePlugin extends Plugin {
    public static final String CONFIG_GROUP = "housemanMode";
    public static final String PLUGIN_MESSAGE_UPDATE = "update";
    public static final String PLUGIN_MESSAGE_VISITED_TILES = "tiles";

    @Getter(AccessLevel.PACKAGE)
    private final List<WorldPoint> points = new ArrayList<>();

    @Inject
    private KeyManager keyManager;

    @Inject
    private Client client;

    @Inject
    private Gson gson;

    @Inject
    private HousemanModeConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private HousemanRangeOverlay rangeOverlay;

    @Inject
    private HousemanRangeMinimapOverlay rangeMinimapOverlay;

    @Inject
    private HousemanRangeMapOverlay rangeMapOverlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private HouseInfoOverlay infoOverlay;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    EventBus eventBus;

    @Provides
    HousemanModeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HousemanModeConfig.class);
    }

    private final MovementFlag[] fullBlock = new MovementFlag[]
            {MovementFlag.BLOCK_MOVEMENT_FLOOR,
                    MovementFlag.BLOCK_MOVEMENT_FLOOR_DECORATION,
                    MovementFlag.BLOCK_MOVEMENT_OBJECT,
                    MovementFlag.BLOCK_MOVEMENT_FULL};

    private final MovementFlag[] allDirections = new MovementFlag[]
            {
                    MovementFlag.BLOCK_MOVEMENT_NORTH_WEST,
                    MovementFlag.BLOCK_MOVEMENT_NORTH,
                    MovementFlag.BLOCK_MOVEMENT_NORTH_EAST,
                    MovementFlag.BLOCK_MOVEMENT_EAST,
                    MovementFlag.BLOCK_MOVEMENT_SOUTH_EAST,
                    MovementFlag.BLOCK_MOVEMENT_SOUTH,
                    MovementFlag.BLOCK_MOVEMENT_SOUTH_WEST,
                    MovementFlag.BLOCK_MOVEMENT_WEST
            };

    private final HashSet<Integer> tutorialIslandRegionIds = new HashSet<Integer>();

    private int remainingTiles = 1000;
    private LocalPoint lastTile;
    private int lastPlane;
    private boolean inHouse = false;

    private boolean rangeIsDirty = false;
    private ExecutorService rangeExecutor = Executors.newSingleThreadExecutor();
    private Future<RangeResult> rangeFuture;
    private final Object rangeMutex = new Object();

    private List<Integer> currentBorder;

    private PathfinderConfig pathfinderConfig;

    @Subscribe
    public void onGameTick(GameTick tick) {
        autoMark();

        int currentLocation = WorldPointUtil.fromLocalInstance(client, lastTile);

        synchronized (rangeMutex) {
            if (rangeExecutor == null) {
                ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("range-%d").build();
                rangeExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
            }

            if (rangeFuture != null && rangeFuture.isDone()){
                try {
                    RangeResult result = rangeFuture.get();
                    currentBorder = result.border;
                    Map<String, Object> data = new HashMap<>();
                    data.put(PLUGIN_MESSAGE_VISITED_TILES, result.visitedTiles);
                    eventBus.post(new PluginMessage(CONFIG_GROUP, PLUGIN_MESSAGE_UPDATE, data));
                    rangeFuture = null;
                }catch(InterruptedException e){

                }catch(ExecutionException e){
                    currentBorder = null;
                    rangeFuture = null;
                }catch(CancellationException e){
                    rangeFuture = null;
                    rangeIsDirty = true;
                }
            }

            if (rangeFuture == null && rangeIsDirty) {
                rangeIsDirty = false;
                pathfinderConfig.refresh();
                RangeTask rangeTask = new RangeTask(pathfinderConfig, currentLocation, remainingTiles);
                rangeFuture = rangeExecutor.submit(rangeTask);
            }
        }
    }

    public List<Integer> getCurrentBorder(){
        return currentBorder;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() != GameState.LOGGED_IN) {
            lastTile = null;
            return;
        }
        inHouse = false;
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();

        if (gameObject.getId() == 4525) {
            inHouse = true;
            remainingTiles = client.getTotalLevel() / 2;
        }
    }

    @Override
    protected void startUp() {
        pathfinderConfig = new PathfinderConfig(client, config);

        tutorialIslandRegionIds.add(12079);
        tutorialIslandRegionIds.add(12080);
        tutorialIslandRegionIds.add(12335);
        tutorialIslandRegionIds.add(12336);
        tutorialIslandRegionIds.add(12592);
        overlayManager.add(infoOverlay);
        overlayManager.add(rangeOverlay);
        overlayManager.add(rangeMinimapOverlay);
        overlayManager.add(rangeMapOverlay);
        log.debug("startup");

        keyManager.registerKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyChar()=='r'){
                    remainingTiles = config.overwriteSteps();
                    rangeIsDirty = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
    }

    @Override
    protected void shutDown() {
        tutorialIslandRegionIds.clear();
        overlayManager.remove(infoOverlay);
        overlayManager.remove(rangeOverlay);
        overlayManager.remove(rangeMinimapOverlay);
        overlayManager.remove(rangeMapOverlay);
        points.clear();
    }

    private void autoMark() {
        final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        if (playerPos == null) {
            return;
        }

        final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
        if (playerPosLocal == null) {
            return;
        }

        // If we have no last tile, we probably just spawned in, so make sure we walk on our current tile
        if ((lastTile == null
                || (lastTile.distanceTo(playerPosLocal) != 0 && lastPlane == playerPos.getPlane())
                || lastPlane != playerPos.getPlane()) && !regionIsOnTutorialIsland(playerPos.getRegionID())) {
            // Player moved
            remainingTiles -= handleWalkedToTile(playerPosLocal);
            lastTile = playerPosLocal;
            lastPlane = client.getPlane();
            log.debug("player moved");
            log.debug("last tile={}  distance={}", lastTile, lastTile == null ? "null" : lastTile.distanceTo(playerPosLocal));
            rangeIsDirty = true;
        }
    }

    int getRemainingTiles() {
        return remainingTiles;
    }

    private int handleWalkedToTile(LocalPoint currentPlayerPoint) {
        if (currentPlayerPoint == null ||
                inHouse) {
            return 0;
        }

        // If player moves 2 tiles in a straight line, fill in the middle tile
        // TODO Fill path between last point and current point. This will fix missing tiles that occur when you lag
        // TODO   and rendered frames are skipped. See if RL has an api that mimic's OSRS's pathing. If so, use that to
        // TODO   set all tiles between current tile and lastTile as marked
        if(lastTile != null){
            int xDiff = currentPlayerPoint.getX() - lastTile.getX();
            int yDiff = currentPlayerPoint.getY() - lastTile.getY();
            int yModifier = yDiff / 2;
            int xModifier = xDiff / 2;

            switch(lastTile.distanceTo(currentPlayerPoint)) {
                case 0: // Haven't moved
                case 128: // Moved 1 tile
                    return 1;
                case 181: // Moved 1 tile diagonally
                    return handleCornerMovement(xDiff, yDiff);
                case 256: // Moved 2 tiles straight
                case 362: // Moved 2 tiles diagonally
                    return 2;
                case 286: // Moved in an 'L' shape
                    return 2;
            }
        }
        return 1;
    }

    private int handleCornerMovement(int xDiff, int yDiff) {
        LocalPoint northPoint;
        LocalPoint southPoint;
        if(yDiff > 0) {
            northPoint = new LocalPoint(lastTile.getX(), lastTile.getY() + yDiff);
            southPoint = new LocalPoint(lastTile.getX() + xDiff, lastTile.getY());
        } else {
            northPoint = new LocalPoint(lastTile.getX() + xDiff, lastTile.getY());
            southPoint = new LocalPoint(lastTile.getX(), lastTile.getY() + yDiff);
        }

        MovementFlag[] northTile = getTileMovementFlags(northPoint);
        MovementFlag[] southTile = getTileMovementFlags(southPoint);

        if (xDiff + yDiff == 0) {
            // Diagonal tilts north west
            if(containsAnyOf(fullBlock, northTile)
                    || containsAnyOf(northTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_SOUTH, MovementFlag.BLOCK_MOVEMENT_WEST})){
                return 2;
            } else if (containsAnyOf(fullBlock, southTile)
                    || containsAnyOf(southTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_NORTH, MovementFlag.BLOCK_MOVEMENT_EAST})){
                return 2;
            }else{
                return 1;
            }
        } else {
            // Diagonal tilts north east
            if(containsAnyOf(fullBlock, northTile)
                    || containsAnyOf(northTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_SOUTH, MovementFlag.BLOCK_MOVEMENT_EAST})){
                return 2;
            } else if (containsAnyOf(fullBlock, southTile)
                    || containsAnyOf(southTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_NORTH, MovementFlag.BLOCK_MOVEMENT_WEST})){
                return 2;
            }else{
                return 1;
            }
        }
    }

    private MovementFlag[] getTileMovementFlags(int x, int y) {
        LocalPoint pointBeside = new LocalPoint(x, y);

        CollisionData[] collisionData = client.getCollisionMaps();
        assert collisionData != null;
        int[][] collisionDataFlags = collisionData[client.getPlane()].getFlags();

        Set<MovementFlag> tilesBesideFlagsSet = MovementFlag.getSetFlags(collisionDataFlags[pointBeside.getSceneX()][pointBeside.getSceneY()]);
        MovementFlag[] tileBesideFlagsArray = new MovementFlag[tilesBesideFlagsSet.size()];
        tilesBesideFlagsSet.toArray(tileBesideFlagsArray);

        return tileBesideFlagsArray;
    }

    private MovementFlag[] getTileMovementFlags(LocalPoint localPoint) {
        return  getTileMovementFlags(localPoint.getX(), localPoint.getY());
    }

    private boolean containsAnyOf(MovementFlag[] comparisonFlags, MovementFlag[] flagsToCompare) {
        if (comparisonFlags.length == 0 || flagsToCompare.length == 0) {
            return false;
        }
        for (MovementFlag flag : flagsToCompare) {
            if (Arrays.asList(comparisonFlags).contains(flag)) {
                return true;
            }
        }
        return false;
    }

    private boolean regionIsOnTutorialIsland(int regionId) {
        return tutorialIslandRegionIds.contains(regionId);
    }

    public int mapWorldPointToGraphicsPointX(int packedWorldPoint) {
        WorldMap worldMap = client.getWorldMap();

        float pixelsPerTile = worldMap.getWorldMapZoom();

        Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);
        if (map != null) {
            Rectangle worldMapRect = map.getBounds();

            int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);

            Point worldMapPosition = worldMap.getWorldMapPosition();

            int xTileOffset = WorldPointUtil.unpackWorldX(packedWorldPoint) + widthInTiles / 2 - worldMapPosition.getX();

            int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
            xGraphDiff += pixelsPerTile - Math.ceil(pixelsPerTile / 2);
            xGraphDiff += (int) worldMapRect.getX();

            return xGraphDiff;
        }
        return Integer.MIN_VALUE;
    }

    public int mapWorldPointToGraphicsPointY(int packedWorldPoint) {
        WorldMap worldMap = client.getWorldMap();

        float pixelsPerTile = worldMap.getWorldMapZoom();

        Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);
        if (map != null) {
            Rectangle worldMapRect = map.getBounds();

            int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

            Point worldMapPosition = worldMap.getWorldMapPosition();

            int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
            int yTileOffset = (yTileMax - WorldPointUtil.unpackWorldY(packedWorldPoint) - 1) * -1;

            int yGraphDiff = (int) (yTileOffset * pixelsPerTile);
            yGraphDiff -= pixelsPerTile - Math.ceil(pixelsPerTile / 2);
            yGraphDiff = worldMapRect.height - yGraphDiff;
            yGraphDiff += (int) worldMapRect.getY();

            return yGraphDiff;
        }
        return Integer.MIN_VALUE;
    }

    @AllArgsConstructor
    enum MovementFlag {
        BLOCK_MOVEMENT_NORTH_WEST(CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST),
        BLOCK_MOVEMENT_NORTH(CollisionDataFlag.BLOCK_MOVEMENT_NORTH),
        BLOCK_MOVEMENT_NORTH_EAST(CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST),
        BLOCK_MOVEMENT_EAST(CollisionDataFlag.BLOCK_MOVEMENT_EAST),
        BLOCK_MOVEMENT_SOUTH_EAST(CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST),
        BLOCK_MOVEMENT_SOUTH(CollisionDataFlag.BLOCK_MOVEMENT_SOUTH),
        BLOCK_MOVEMENT_SOUTH_WEST(CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST),
        BLOCK_MOVEMENT_WEST(CollisionDataFlag.BLOCK_MOVEMENT_WEST),

        BLOCK_MOVEMENT_OBJECT(CollisionDataFlag.BLOCK_MOVEMENT_OBJECT),
        BLOCK_MOVEMENT_FLOOR_DECORATION(CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION),
        BLOCK_MOVEMENT_FLOOR(CollisionDataFlag.BLOCK_MOVEMENT_FLOOR),
        BLOCK_MOVEMENT_FULL(CollisionDataFlag.BLOCK_MOVEMENT_FULL);

        @Getter
        private int flag;

        /**
         * @param collisionData The tile collision flags.
         * @return The set of {@link MovementFlag}s that have been set.
         */
        public static Set<MovementFlag> getSetFlags(int collisionData) {
            return Arrays.stream(values())
                    .filter(movementFlag -> (movementFlag.flag & collisionData) != 0)
                    .collect(Collectors.toSet());
        }
    }

}
