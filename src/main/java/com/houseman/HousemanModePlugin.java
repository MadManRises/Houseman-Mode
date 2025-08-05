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

import com.chanceman.ChanceManOverlay;
import com.chanceman.ChanceManPanel;
import com.chanceman.account.AccountChanged;
import com.chanceman.account.AccountManager;
import com.chanceman.drops.DropFetcher;
import com.chanceman.filters.EnsouledHeadMapping;
import com.chanceman.filters.ItemsFilter;
import com.chanceman.managers.RollAnimationManager;
import com.chanceman.managers.RolledItemsManager;
import com.chanceman.managers.UnlockedItemsManager;
import com.chanceman.menus.ActionHandler;
import com.chanceman.ui.DropsTabUI;
import com.chanceman.ui.DropsTooltipOverlay;
import com.chanceman.ui.MusicWidgetController;
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
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapRegion;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import shortestpath.*;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.chanceman.menus.ActionHandler.DISABLED;
import static com.chanceman.menus.ActionHandler.HOUSE_PORTALS;
import static net.runelite.api.ItemID.*;
import static net.runelite.api.ScriptID.SPLITPM_CHANGED;
import static net.runelite.client.RuneLite.RUNELITE_DIR;

@Slf4j
@PluginDescriptor(
        name = "Houseman Mode",
        description = "Support for houseman mode",
        tags = {"overlay", "tiles"},
        conflicts = "Shortest Path"
)
public class HousemanModePlugin extends Plugin {
    final int COLLECTION_LOG_POPUP_WIDGET = 660;

    private final Deque<Integer> pendingPopups = new ArrayDeque<>();

    public static final String CONFIG_GROUP = "housemanMode";
    public static final String PLUGIN_MESSAGE_UPDATE = "update";
    public static final String PLUGIN_MESSAGE_VISITED_TILES = "tiles";
    public static final String PLUGIN_MESSAGE_REMAINING_TILES = "remaining";

    private Shape minimapClipFixed;
    private Shape minimapClipResizeable;

    private WorldMapPoint marker;

    @Inject
    private SpriteManager spriteManager;

    private static final String CLEAR = "Clear";
    private static final String PATH = ColorUtil.wrapWithColorTag("Path", JagexColors.MENU_TARGET);
    private static final String SET = "Set";
    private static final String FIND_CLOSEST = "Find closest";
    private static final String FLASH_ICONS = "Flash icons";
    private static final String START = ColorUtil.wrapWithColorTag("Start", JagexColors.MENU_TARGET);
    private static final String TARGET = ColorUtil.wrapWithColorTag("Target", JagexColors.MENU_TARGET);
    private static final BufferedImage MARKER_IMAGE = ImageUtil.loadImageResource(ShortestPathPlugin.class, "/shortestpath/marker.png");

    private static final Set<String> equippableActions = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("wield", "wear")));

    private static final Map<Integer, Integer> tileRestorationSources = Map.ofEntries(
            Map.entry(ENERGY_POTION1, 1),
            Map.entry(ENERGY_POTION2, 1),
            Map.entry(ENERGY_POTION3, 1),
            Map.entry(ENERGY_POTION4, 1)
    );

    private Rectangle minimapRectangle = new Rectangle();

    private Point lastMenuOpenedPoint;

    @Getter(AccessLevel.PACKAGE)
    private final List<WorldPoint> points = new ArrayList<>();

    @Inject
    private KeyManager keyManager;

    @Inject
    Client client;

    @Inject
    private Gson gson;

    @Inject
    public HousemanModeConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private HousemanRangeOverlay rangeOverlay;

    @Inject
    private HousemanRangeMinimapOverlay rangeMinimapOverlay;

    @Inject
    public HousemanRangeMapOverlay rangeMapOverlay;

    @Inject
    private HousemanInventoryOverlay inventoryOverlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private HouseInfoOverlay infoOverlay;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ChanceManOverlay chanceManOverlay;

    @Inject
    private DropsTooltipOverlay dropsTooltipOverlay;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ItemManager itemManager;

    @Inject
    private PathTileOverlay pathOverlay;

    @Inject
    private PathMinimapOverlay pathMinimapOverlay;

    @Inject
    private PathMapOverlay pathMapOverlay;

    @Inject
    private PathMapTooltipOverlay pathMapTooltipOverlay;

    @Inject private DropsTabUI dropsTabUI;
    @Inject private DropFetcher dropFetcher;
    @Inject private MusicWidgetController musicWidgetController;

    @Getter
    private boolean startPointSet = false;

    @Inject
    private WorldMapPointManager worldMapPointManager;

    @Inject
    EventBus eventBus;

    @Inject
    private AccountManager accountManager;
    @Inject
    private UnlockedItemsManager unlockedItemsManager;
    @Inject
    private RolledItemsManager rolledItemsManager;
    @Inject
    private RollAnimationManager rollAnimationManager;

    private ChanceManPanel chanceManPanel;
    private NavigationButton navButton;
    private ExecutorService fileExecutor;
    @Getter private final HashSet<Integer> allTradeableItems = new LinkedHashSet<>();
    private static final int GE_SEARCH_BUILD_SCRIPT = 751;
    private boolean tradeableItemsInitialized = false;
    private boolean featuresActive = false;
    private boolean showingPopup = false;
    private WorldArea houseLeavingPos;

    private ExecutorService pathfindingExecutor = Executors.newSingleThreadExecutor();
    private Future<?> pathfinderFuture;
    private final Object pathfinderMutex = new Object();

    @Getter
    private Pathfinder pathfinder;

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
    private WorldPoint lastTile;
    private int lastPlane;
    private boolean inHouse = false;

    private boolean rangeIsDirty = true;
    private ExecutorService rangeExecutor = Executors.newSingleThreadExecutor();
    private Future<RangeResult> rangeFuture;
    private final Object rangeMutex = new Object();

    private BufferedImage minimapSpriteFixed;
    private BufferedImage minimapSpriteResizeable;

    private List<Integer> currentBorder;

    private PathfinderConfig pathfinderConfig;

    @Subscribe
    public void onGameTick(GameTick tick) {
        autoMark();

        int currentLocation = WorldPointUtil.packWorldPoint(lastTile);

        synchronized (rangeMutex) {
            if (rangeExecutor == null) {
                ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("range-%d").build();
                rangeExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
            }

            if (rangeFuture != null && rangeFuture.isDone()){
                try {
                    RangeResult result = rangeFuture.get();
                    currentBorder = result.border;
                    rangeMapOverlay.update(result.visitedTiles);
                    rangeMinimapOverlay.update(result.visitedTiles);
                    Map<String, Object> data = new HashMap<>();
                    data.put(PLUGIN_MESSAGE_VISITED_TILES, result.visitedTiles);
                    data.put(PLUGIN_MESSAGE_REMAINING_TILES, remainingTiles);
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
                RangeTask rangeTask = new RangeTask(pathfinderConfig, currentLocation, remainingTiles, inHouse);
                rangeFuture = rangeExecutor.submit(rangeTask);
                if (remainingTiles < 0){
                    Set<Integer> targets = new HashSet<>();
                    targets.addAll(houseLeavingPos.toWorldPointList().stream().map(point -> WorldPointUtil.packWorldPoint(point)).collect(Collectors.toList()));
                    restartPathfinding(currentLocation, targets);
                }
            }
        }

        if (!featuresActive) return;
        if (!tradeableItemsInitialized && client.getGameState() == GameState.LOGGED_IN)
        {
            refreshTradeableItems();
            tradeableItemsInitialized = true;
        }

        rollAnimationManager.process();

        if (!pendingPopups.isEmpty() && !showingPopup){
            int itemId = pendingPopups.poll();
            showPopup(itemId);
        }

        rangeMapOverlay.onGameTick(tick);
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
        if (inHouse) {
            inHouse = false;
            for (Tile[][] planes : client.getScene().getTiles()){
                for (Tile[] rows : planes){
                    for (Tile tile : rows){
                        if (tile != null) {
                            for (GameObject object : tile.getGameObjects()) {
                                if (object != null && HOUSE_PORTALS.contains(object.getId())) {
                                    int x = object.getWorldLocation().getX() - (object.sizeX() - 1) / 2;
                                    int y = object.getWorldLocation().getY() - (object.sizeY() - 1) / 2;
                                    WorldArea area = new WorldArea(x, y, object.sizeX(), object.sizeY(), object.getPlane());
                                    if (area.distanceTo(client.getLocalPlayer().getWorldLocation()) <= 1) {
                                        houseLeavingPos = area;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            saveInfo();
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();

        if (gameObject.getId() == 4525) {
            inHouse = true;
            remainingTiles = client.getTotalLevel() / 2;
            identifyItems();
            saveInfo();
        }
    }

    private void identifyItems() {

        if (rolledItemsManager == null)
        {
            return;
        }

        for (Item item : client.getItemContainer(InventoryID.INVENTORY).getItems()){
            int itemId = item.getId();
            ItemComposition comp = itemManager.getItemComposition(itemId);
            String name = (comp != null && comp.getName() != null) ? comp.getName() : item.toString();
            if (name.toLowerCase().contains("ensouled")) {
                int mappedId = ItemsFilter.getEnsouledHeadId(name);
                if (mappedId != EnsouledHeadMapping.DEFAULT_ENSOULED_HEAD_ID) { itemId = mappedId; }
            }
            int canonicalItemId = itemManager.canonicalize(itemId);
            if (!isTradeable(canonicalItemId) || isNotTracked(canonicalItemId))
            {
                continue;
            }

            if (!unlockedItemsManager.isUnlocked(canonicalItemId)){
                unlockedItemsManager.unlockItem(canonicalItemId);
                pendingPopups.add(canonicalItemId);
                if (chanceManPanel != null) {
                    SwingUtilities.invokeLater(() -> chanceManPanel.updatePanel());
                }
            }
        }
    }

    private void showPopup(int itemId) {
        showingPopup = true;
        clientThread.invokeLater(() -> {
            // Handles both resizable and fixed modes
            int componentId = (client.getTopLevelInterfaceId() << 16) | (client.isResized() ? 13 : 43);
            WidgetNode widgetNode = client.openInterface(
                    componentId,
                    COLLECTION_LOG_POPUP_WIDGET,
                    WidgetModalMode.MODAL_CLICKTHROUGH
            );
            String title = ColorUtil.wrapWithColorTag("Item Identified", ColorUtil.fromHex("aaff00"));
            String name = itemManager.getItemComposition(itemId).getName();
            itemManager.getImage(itemId);
            String description = String.format(
                    "<col=aaff00>%s</col>",
                    name
            );
            client.runScript(3343, title, description, -1);

            clientThread.invokeLater(() -> {
                Widget w = client.getWidget(COLLECTION_LOG_POPUP_WIDGET, 1);
                if (w == null || w.getWidth() > 0) {
                    return false;
                }
                try {
                    showingPopup = false;
                    client.closeInterface(widgetNode, true);
                } catch (IllegalArgumentException e) {
                    log.debug("Interface attempted to close, but was no longer valid.");
                }
                return true;
            });
        });
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
        overlayManager.add(inventoryOverlay);
        log.debug("startup");

        overlayManager.add(pathOverlay);
        overlayManager.add(pathMinimapOverlay);
        overlayManager.add(pathMapOverlay);
        overlayManager.add(pathMapTooltipOverlay);

        keyManager.registerKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyChar()=='r'){
                    remainingTiles = config.overwriteSteps();
                    rangeIsDirty = true;
                    saveInfo();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });

        //eventBus.register(this);
        if (isNormalWorld()) enableFeatures();
    }

    @Override
    protected void shutDown() {
        if (featuresActive) disableFeatures();
       // eventBus.unregister(this);

        tutorialIslandRegionIds.clear();
        overlayManager.remove(infoOverlay);
        overlayManager.remove(rangeOverlay);
        overlayManager.remove(rangeMinimapOverlay);
        overlayManager.remove(rangeMapOverlay);
        overlayManager.remove(inventoryOverlay);
        points.clear();

        overlayManager.remove(pathOverlay);
        overlayManager.remove(pathMinimapOverlay);
        overlayManager.remove(pathMapOverlay);
        overlayManager.remove(pathMapTooltipOverlay);

        if (pathfindingExecutor != null) {
            pathfindingExecutor.shutdownNow();
            pathfindingExecutor = null;
        }
    }

    public void restartPathfinding(int start, Set<Integer> ends, boolean canReviveFiltered) {
        synchronized (pathfinderMutex) {
            if (pathfinder != null) {
                pathfinder.cancel();
                pathfinderFuture.cancel(true);
            }

            if (pathfindingExecutor == null) {
                ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("shortest-path-%d").build();
                pathfindingExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
            }
        }

        clientThread.invokeLater(() -> {
            pathfinderConfig.refresh();
            pathfinderConfig.filterLocations(ends, canReviveFiltered);
            synchronized (pathfinderMutex) {
                if (ends.isEmpty()) {
                    setTarget(WorldPointUtil.UNDEFINED);
                } else {
                    pathfinder = new Pathfinder(pathfinderConfig, start, ends);
                    pathfinderFuture = pathfindingExecutor.submit(pathfinder);
                }
            }
        });
    }

    public void restartPathfinding(int start, Set<Integer> ends) {
        restartPathfinding(start, ends, true);
    }


    private void autoMark() {
        final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        if (playerPos == null) {
            return;
        }

        // If we have no last tile, we probably just spawned in, so make sure we walk on our current tile
        if ((lastTile == null
                || (lastTile.distanceTo(playerPos) != 0 && lastPlane == playerPos.getPlane())
                || lastPlane != playerPos.getPlane()) && !regionIsOnTutorialIsland(playerPos.getRegionID())) {
            // Player moved
            remainingTiles -= handleWalkedToTile(playerPos);
            saveInfo();
            lastTile = playerPos;
            lastPlane = client.getPlane();
            log.debug("player moved");
            log.debug("last tile={}  distance={}", lastTile, lastTile == null ? "null" : lastTile.distanceTo(playerPos));
            rangeIsDirty = true;
        }
    }

    public int getRemainingTiles() {
        return remainingTiles;
    }

    public CollisionMap getMap() {
        return pathfinderConfig.getMap();
    }

    public Map<Integer, Set<Transport>> getTransports() {
        return pathfinderConfig.getTransports();
    }

    private int handleWalkedToTile(WorldPoint currentPlayerPoint) {
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

            int dist = lastTile.distanceTo(currentPlayerPoint);
            if (dist == 1 && xDiff != 0 && yDiff != 0){
                return handleCornerMovement(xDiff, yDiff);
            } else if (dist == 2) {
                return 2;
            }else{
                return 1;
            }
        }
        return 1;
    }

    private int handleCornerMovement(int xDiff, int yDiff) {
        WorldPoint northPoint;
        WorldPoint southPoint;
        if(yDiff > 0) {
            northPoint = new WorldPoint(lastTile.getX(), lastTile.getY() + yDiff, client.getPlane());
            southPoint = new WorldPoint(lastTile.getX() + xDiff, lastTile.getY(), client.getPlane());
        } else {
            northPoint = new WorldPoint(lastTile.getX() + xDiff, lastTile.getY(), client.getPlane());
            southPoint = new WorldPoint(lastTile.getX(), lastTile.getY() + yDiff, client.getPlane());
        }

        MovementFlag[] northTile = getTileMovementFlags(LocalPoint.fromWorld(client, northPoint));
        MovementFlag[] southTile = getTileMovementFlags(LocalPoint.fromWorld(client, southPoint));

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

    public boolean itemNeedsToBeDropped(int id){

        if (!unlockedItemsManager.isUnlocked(id) && isTradeable(id))
            return true;

        boolean canDrop = false;
        for (String name : getItemManager().getItemComposition(id).getInventoryActions()){
            if (new String("drop").equalsIgnoreCase(name)) {
                canDrop = true;
                break;
            }
        }
        if (!canDrop)
            return false;

        return !equippableActions.stream().anyMatch((action) -> {
            for (String name : getItemManager().getItemComposition(id).getInventoryActions()){
                if (action.equalsIgnoreCase(name))
                    return true;
            }
            return false;
        });
    }

    public boolean hasItemsToDrop() {
        ItemContainer inventoryItems = client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
        if (inventoryItems != null) {
            for (Item item : inventoryItems.getItems()) {
                int id = item.getId();
                if (id != -1 && itemNeedsToBeDropped(id))
                    return true;
            }
        }
        return false;
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

    private void enableFeatures()
    {
        if (featuresActive) return;
        featuresActive = true;

        getInjector().getInstance(ActionHandler.class).startUp();
        eventBus.register(accountManager);
        overlayManager.add(chanceManOverlay);
        overlayManager.add(dropsTooltipOverlay);
        fileExecutor = Executors.newSingleThreadExecutor();
        unlockedItemsManager.setExecutor(fileExecutor);
        rolledItemsManager.setExecutor(fileExecutor);
        rollAnimationManager.startUp();
        dropsTabUI.startUp();

        chanceManPanel = new ChanceManPanel(
                unlockedItemsManager,
                rolledItemsManager,
                itemManager,
                allTradeableItems,
                clientThread,
                rollAnimationManager
        );
        rollAnimationManager.setChanceManPanel(chanceManPanel);

        BufferedImage icon = ImageUtil.loadImageResource(
                getClass(), "/net/runelite/client/plugins/chanceman/icon.png"
        );
        navButton = NavigationButton.builder()
                .tooltip("ChanceMan")
                .icon(icon)
                .priority(5)
                .panel(chanceManPanel)
                .build();
        clientToolbar.addNavigation(navButton);

        accountManager.init();
    }

    private void disableFeatures()
    {
        if (!featuresActive) return;
        featuresActive = false;
        dropsTabUI.shutDown();
        eventBus.unregister(accountManager);
        getInjector().getInstance(ActionHandler.class).shutDown();
        musicWidgetController.restore();

        if (clientToolbar != null && navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        if (overlayManager != null)
        {
            overlayManager.remove(chanceManOverlay);
            overlayManager.remove(dropsTooltipOverlay);
        }
        if (rollAnimationManager != null)
        {
            rollAnimationManager.shutdown();
        }
        if (fileExecutor != null)
        {
            fileExecutor.shutdownNow();
            fileExecutor = null;
        }
        dropFetcher.shutdown();

        // reset panel/tradeable state
        chanceManPanel = null;
        allTradeableItems.clear();
        tradeableItemsInitialized = false;
        accountManager.reset();
    }

    @Subscribe
    public void onWorldChanged(WorldChanged event)
    {
        if (isNormalWorld()) enableFeatures();
        else disableFeatures();
    }

    public void refreshTradeableItems() {
        clientThread.invokeLater(() -> {
            allTradeableItems.clear();
            for (int i = 0; i < 40000; i++) {
                ItemComposition comp = itemManager.getItemComposition(i);
                if (comp != null && comp.isTradeable() && !isNotTracked(i)
                        && !ItemsFilter.isBlocked(i, config)) {
                    if (config.freeToPlay() && comp.isMembers()) {
                        continue;
                    }
                    if (!ItemsFilter.isPoisonEligible(i, config.requireWeaponPoison(),
                            unlockedItemsManager.getUnlockedItems())) {
                        continue;
                    }
                    allTradeableItems.add(i);
                }
            }
            rollAnimationManager.setAllTradeableItems(allTradeableItems);
            if (chanceManPanel != null) {
                SwingUtilities.invokeLater(() -> chanceManPanel.updatePanel());
            }
        });
    }

    @Subscribe
    private void onAccountChanged(AccountChanged event)
    {
        if (!featuresActive) return;
        unlockedItemsManager.loadUnlockedItems();
        rolledItemsManager.loadRolledItems();
        if (chanceManPanel != null)
        {
            SwingUtilities.invokeLater(() -> chanceManPanel.updatePanel());
        }

        loadInfo();
    }

    private class Info{
        int remainingTiles;
        WorldArea houseLeavingPosition;
        WorldPoint lastTile;
    }

    public void loadInfo()
    {
        if (accountManager.getPlayerName() == null)
        {
            return;
        }

        Path file;
        try
        {
            file = getFilePath();
        }
        catch (IOException ioe)
        {
            return;
        }

        if (!Files.exists(file))
        {
            // first run: write an empty file
            saveInfo();
            return;
        }

        try (Reader r = Files.newBufferedReader(file))
        {
            Info info = gson.fromJson(r,
                    new com.google.gson.reflect.TypeToken<Info>() {}.getType());
            remainingTiles = info.remainingTiles;
            houseLeavingPos = info.houseLeavingPosition;
            lastTile = info.lastTile;
        }
        catch (IOException e)
        {
            log.error("Error loading rolled items", e);
        }
    }

    /**
     * Saves the current set of rolled items to disk.
     * Uses a temporary file and backups for atomicity and data safety.
     */
    public void saveInfo()
    {
        fileExecutor.submit(() ->
        {
            Path file;
            try
            {
                file = getFilePath();
            }
            catch (IOException ioe)
            {
                log.error("Could not resolve info path", ioe);
                return;
            }

            try
            {
                // 2) write new JSON to .tmp
                try (BufferedWriter w = Files.newBufferedWriter(file))
                {
                    Info info = new Info();
                    info.remainingTiles = remainingTiles;
                    info.houseLeavingPosition = houseLeavingPos;
                    info.lastTile = lastTile;
                    gson.toJson(info, w);
                }
            }
            catch (IOException e)
            {
                log.error("Error saving info", e);
            }
        });
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (!featuresActive) return;
        if (event.getScriptId() == GE_SEARCH_BUILD_SCRIPT) { killSearchResults(); }
    }

    private void killSearchResults() {
        Widget geSearchResults = client.getWidget(162, 51);
        if (geSearchResults == null) {
            return;
        }
        Widget[] children = geSearchResults.getDynamicChildren();
        if (children == null || children.length < 2 || children.length % 3 != 0) {
            return;
        }
        Set<Integer> unlocked = unlockedItemsManager.getUnlockedItems();
        Set<Integer> rolled = rolledItemsManager.getRolledItems();
        boolean requireRolled = config.requireRolledUnlockedForGe();
        for (int i = 0; i < children.length; i += 3) {
            int offerItemId = children[i + 2].getItemId();
            boolean isUnlocked = unlocked.contains(offerItemId);
            boolean isRolled = rolled.contains(offerItemId);
            boolean hide = requireRolled ? !(isUnlocked && isRolled) : !isUnlocked;
            if (hide) {
                children[i].setHidden(true);
                children[i + 1].setOpacity(70);
                children[i + 2].setOpacity(70);
            }
        }
    }

    public boolean isNormalWorld()
    {
        EnumSet<WorldType> worldTypes = client.getWorldType();
        return !(worldTypes.contains(WorldType.DEADMAN)
                || worldTypes.contains(WorldType.SEASONAL)
                || worldTypes.contains(WorldType.BETA_WORLD)
                || worldTypes.contains(WorldType.PVP_ARENA)
                || worldTypes.contains(WorldType.QUEST_SPEEDRUNNING)
                || worldTypes.contains(WorldType.TOURNAMENT_WORLD));
    }

    public boolean isTradeable(int itemId)
    {
        ItemComposition comp = itemManager.getItemComposition(itemId);
        return comp != null && comp.isTradeable();
    }

    public boolean isNotTracked(int itemId)
    {
        return itemId == 995 || itemId == 13191 || itemId == 13190 ||
                itemId == 7587 || itemId == 7588 || itemId == 7589 || itemId == 7590 || itemId == 7591;
    }

    public boolean isInPlay(int itemId)
    {
        return allTradeableItems.contains(itemId);
    }

    public ItemManager getItemManager() { return itemManager; }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (client.isKeyPressed(KeyCode.KC_SHIFT)
                && event.getType() == MenuAction.WALK.getId()) {
            addMenuEntry(event, SET, TARGET, 1);
            if (pathfinder != null) {
                if (pathfinder.getTargets().size() >= 1) {
                    addMenuEntry(event, SET, TARGET + ColorUtil.wrapWithColorTag(" " +
                            (pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET), 1);
                }
                for (int target : pathfinder.getTargets()) {
                    if (target != WorldPointUtil.UNDEFINED) {
                        addMenuEntry(event, SET, START, 1);
                        break;
                    }
                }
                int selectedTile = getSelectedWorldPoint();
                if (pathfinder.getPath() != null) {
                    for (int tile : pathfinder.getPath()) {
                        if (tile == selectedTile) {
                            addMenuEntry(event, CLEAR, PATH, 1);
                            break;
                        }
                    }
                }
            }
        }

        final Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);

        if (map != null) {
            if (map.getBounds().contains(
                    client.getMouseCanvasPosition().getX(),
                    client.getMouseCanvasPosition().getY())) {
                addMenuEntry(event, SET, TARGET, 0);
                if (pathfinder != null) {
                    if (pathfinder.getTargets().size() >= 1) {
                        addMenuEntry(event, SET, TARGET + ColorUtil.wrapWithColorTag(" " +
                                (pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET), 0);
                    }
                    for (int target : pathfinder.getTargets()) {
                        if (target != WorldPointUtil.UNDEFINED) {
                            addMenuEntry(event, SET, START, 0);
                            addMenuEntry(event, CLEAR, PATH, 0);
                        }
                    }
                }
            }
            if (event.getOption().equals(FLASH_ICONS) && pathfinderConfig.hasDestination(simplify(event.getTarget()))) {
                addMenuEntry(event, FIND_CLOSEST, event.getTarget(), 1);
            }
        }

        final Shape minimap = getMinimapClipArea();

        if (minimap != null && pathfinder != null
                && minimap.contains(
                client.getMouseCanvasPosition().getX(),
                client.getMouseCanvasPosition().getY())) {
            addMenuEntry(event, CLEAR, PATH, 0);
        }

        if (minimap != null && pathfinder != null
                && ("Floating World Map".equals(Text.removeTags(event.getOption()))
                || "Close Floating panel".equals(Text.removeTags(event.getOption())))) {
            addMenuEntry(event, CLEAR, PATH, 1);
        }
    }

    private Widget getMinimapDrawWidget() {
        if (client.isResized()) {
            if (client.getVarbitValue(Varbits.SIDE_PANELS) == 1) {
                return client.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA);
            }
            return client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
        }
        return client.getWidget(ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
    }

    public Shape getMinimapClipArea() {
        Widget minimapWidget = getMinimapDrawWidget();

        if (minimapWidget == null || minimapWidget.isHidden() || !minimapRectangle.equals(minimapRectangle = minimapWidget.getBounds())) {
            minimapClipFixed = null;
            minimapClipResizeable = null;
            minimapSpriteFixed = null;
            minimapSpriteResizeable = null;
        }

        if (minimapWidget == null || minimapWidget.isHidden()) {
            return null;
        }

        if (client.isResized()) {
            if (minimapClipResizeable != null) {
                return minimapClipResizeable;
            }
            if (minimapSpriteResizeable == null) {
                minimapSpriteResizeable = spriteManager.getSprite(SpriteID.RESIZEABLE_MODE_MINIMAP_ALPHA_MASK, 0);
            }
            if (minimapSpriteResizeable != null) {
                minimapClipResizeable = bufferedImageToPolygon(minimapSpriteResizeable);
                return minimapClipResizeable;
            }
            return getMinimapClipAreaSimple();
        }
        if (minimapClipFixed != null) {
            return minimapClipFixed;
        }
        if (minimapSpriteFixed == null) {
            minimapSpriteFixed = spriteManager.getSprite(SpriteID.FIXED_MODE_MINIMAP_ALPHA_MASK, 0);
        }
        if (minimapSpriteFixed != null) {
            minimapClipFixed = bufferedImageToPolygon(minimapSpriteFixed);
            return minimapClipFixed;
        }
        return getMinimapClipAreaSimple();
    }

    private Polygon bufferedImageToPolygon(BufferedImage image) {
        Color outsideColour = null;
        Color previousColour;
        final int width = image.getWidth();
        final int height = image.getHeight();
        List<java.awt.Point> points = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            previousColour = outsideColour;
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int a = (rgb & 0xff000000) >>> 24;
                int r = (rgb & 0x00ff0000) >> 16;
                int g = (rgb & 0x0000ff00) >> 8;
                int b = (rgb & 0x000000ff) >> 0;
                Color colour = new Color(r, g, b, a);
                if (x == 0 && y == 0) {
                    outsideColour = colour;
                    previousColour = colour;
                }
                if (!colour.equals(outsideColour) && previousColour.equals(outsideColour)) {
                    points.add(new java.awt.Point(x, y));
                }
                if ((colour.equals(outsideColour) || x == (width - 1)) && !previousColour.equals(outsideColour)) {
                    points.add(0, new java.awt.Point(x, y));
                }
                previousColour = colour;
            }
        }
        int offsetX = minimapRectangle.x;
        int offsetY = minimapRectangle.y;
        Polygon polygon = new Polygon();
        for (java.awt.Point point : points) {
            polygon.addPoint(point.x + offsetX, point.y + offsetY);
        }
        return polygon;
    }

    private void addMenuEntry(MenuEntryAdded event, String option, String target, int position) {
        List<MenuEntry> entries = new LinkedList<>(Arrays.asList(client.getMenuEntries()));

        if (entries.stream().anyMatch(e -> e.getOption().equals(option) && e.getTarget().equals(target))) {
            return;
        }

        client.createMenuEntry(position)
                .setOption(option)
                .setTarget(target)
                .setParam0(event.getActionParam0())
                .setParam1(event.getActionParam1())
                .setIdentifier(event.getIdentifier())
                .setType(MenuAction.RUNELITE)
                .onClick(this::onMenuOptionClicked);
    }

    private Shape getMinimapClipAreaSimple() {
        Widget minimapDrawArea = getMinimapDrawWidget();

        if (minimapDrawArea == null || minimapDrawArea.isHidden()) {
            return null;
        }

        Rectangle bounds = minimapDrawArea.getBounds();

        return new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
    }

    private void onMenuOptionClicked(MenuEntry entry) {
        if (entry.getOption().equals(SET) && entry.getTarget().equals(TARGET)) {
            setTarget(getSelectedWorldPoint());
        } else if (entry.getOption().equals(SET) && pathfinder != null && entry.getTarget().equals(TARGET +
                ColorUtil.wrapWithColorTag(" " + (pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET))) {
            setTarget(getSelectedWorldPoint(), true);
        } else if (entry.getOption().equals(SET) && entry.getTarget().equals(START)) {
            setStart(getSelectedWorldPoint());
        } else if (entry.getOption().equals(CLEAR) && entry.getTarget().equals(PATH)) {
            setTarget(WorldPointUtil.UNDEFINED);
        } else if (entry.getOption().equals(FIND_CLOSEST)) {
            setTargets(pathfinderConfig.getDestinations(simplify(entry.getTarget())), true);
        }
    }

    private String simplify(String text) {
        return Text.removeTags(text).toLowerCase()
                .replaceAll("[^a-zA-Z ]", "")
                .replaceAll("[ ]", "_")
                .replace("__", "_");
    }

    private void setTargets(Set<Integer> targets, boolean append) {
        if (targets == null || targets.isEmpty()) {
            synchronized (pathfinderMutex) {
                if (pathfinder != null) {
                    pathfinder.cancel();
                }
                pathfinder = null;
            }

            worldMapPointManager.removeIf(x -> x == marker);
            marker = null;
            startPointSet = false;
        } else {
            Player localPlayer = client.getLocalPlayer();
            if (!startPointSet && localPlayer == null) {
                return;
            }
            worldMapPointManager.removeIf(x -> x == marker);
            if (targets.size() == 1) {
                marker = new WorldMapPoint(WorldPointUtil.unpackWorldPoint(targets.iterator().next()), MARKER_IMAGE);
                marker.setName("Target");
                marker.setTarget(marker.getWorldPoint());
                marker.setJumpOnClick(true);
                worldMapPointManager.add(marker);
            }

            int start = WorldPointUtil.fromLocalInstance(client, localPlayer.getLocalLocation());
            if (startPointSet && pathfinder != null) {
                start = pathfinder.getStart();
            }
            Set<Integer> destinations = new HashSet<>(targets);
            if (pathfinder != null && append) {
                destinations.addAll(pathfinder.getTargets());
            }
            restartPathfinding(start, destinations, append);
        }
    }

    private int getSelectedWorldPoint() {
        if (client.getWidget(ComponentID.WORLD_MAP_MAPVIEW) == null) {
            if (client.getSelectedSceneTile() != null) {
                return WorldPointUtil.fromLocalInstance(client, client.getSelectedSceneTile().getLocalLocation());
            }
        } else {
            return client.isMenuOpen()
                    ? calculateMapPoint(lastMenuOpenedPoint.getX(), lastMenuOpenedPoint.getY())
                    : calculateMapPoint(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());
        }
        return WorldPointUtil.UNDEFINED;
    }

    public int calculateMapPoint(int pointX, int pointY) {
        WorldMap worldMap = client.getWorldMap();
        float zoom = worldMap.getWorldMapZoom();
        int mapPoint = WorldPointUtil.packWorldPoint(worldMap.getWorldMapPosition().getX(), worldMap.getWorldMapPosition().getY(), 0);
        int middleX = mapWorldPointToGraphicsPointX(mapPoint);
        int middleY = mapWorldPointToGraphicsPointY(mapPoint);

        if (pointX == Integer.MIN_VALUE || pointY == Integer.MIN_VALUE ||
                middleX == Integer.MIN_VALUE || middleY == Integer.MIN_VALUE) {
            return WorldPointUtil.UNDEFINED;
        }

        final int dx = (int) ((pointX - middleX) / zoom);
        final int dy = (int) ((-(pointY - middleY)) / zoom);

        return WorldPointUtil.dxdy(mapPoint, dx, dy);
    }

    private void setTarget(int target) {
        setTarget(target, false);
    }

    private void setTarget(int target, boolean append) {
        Set<Integer> targets = new HashSet<>();
        if (target != WorldPointUtil.UNDEFINED) {
            targets.add(target);
        }
        setTargets(targets, append);
    }

    private void setStart(int start) {
        if (pathfinder == null) {
            return;
        }
        startPointSet = true;
        restartPathfinding(start, pathfinder.getTargets());
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        lastMenuOpenedPoint = client.getMouseCanvasPosition();
    }

    private Path getFilePath() throws IOException
    {
        String name = accountManager.getPlayerName();
        if (name == null)
        {
            throw new IOException("Player name is null");
        }
        Path dir = RUNELITE_DIR.toPath()
                .resolve("houseman")
                .resolve(name);
        Files.createDirectories(dir);
        return dir.resolve("houseman.json");
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuEntry().onClick() == DISABLED) {
            return;
        }
        int item = event.getItemId();
        if (item != 0){
            Integer restoration = tileRestorationSources.get(item);
            if (restoration != null) {
                remainingTiles += restoration;
                saveInfo();
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(final ItemContainerChanged event)
    {
        final int id = event.getContainerId();
        if (id == InventoryID.INVENTORY.getId() && inHouse)
        {
            identifyItems();
        }
    }

}
