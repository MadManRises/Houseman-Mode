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
import net.runelite.client.plugins.PluginDependency;
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
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.ShortestPathPlugin;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.ShortestPathConfig;
import shortestpath.pathfinder.VisitedTiles;

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
import java.util.*;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.chanceman.menus.ActionHandler.DISABLED;
import static com.chanceman.menus.ActionHandler.HOUSE_PORTALS;
import static net.runelite.api.ItemID.*;
import static net.runelite.client.RuneLite.RUNELITE_DIR;

@Slf4j
@PluginDescriptor(
        name = "Houseman Mode",
        description = "Support for houseman mode",
        tags = {"overlay", "tiles"}
)
@PluginDependency(ShortestPathPlugin.class)
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
    public ShortestPathConfig shortestPathConfig;

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

    private Pathfinder pathfinder;
    private Pathfinder cachedPathfinder;

    public Pathfinder getPathfinder() {
        if (pathfinder != null && pathfinder.isDone()){
            return pathfinder;
        }else{
            return cachedPathfinder;
        }
    }

    @Provides
    HousemanModeConfig provideConfig(ConfigManager configManager) {
        //configManager.setConfiguration(CONFIG_GROUP, "useTeleportationItems", TeleportationItem.INVENTORY_NON_CONSUMABLE);
        //configManager.setConfiguration(CONFIG_GROUP, "showTileCounter", TileCounter.REMAINING);
        //configManager.setConfiguration(CONFIG_GROUP, "pathStyle", TileStyle.TILES);
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
    private boolean homePathCalculated = false;

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
                if (remainingTiles < 0 && !homePathCalculated){
                    homePathCalculated = true;
                    Set<Integer> targets = new HashSet<>();
                    targets.addAll(houseLeavingPos.toWorldPointList().stream().map(point -> WorldPointUtil.packWorldPoint(point)).collect(Collectors.toList()));
                    Map<String, Object> data = new HashMap<>();
                    data.put("start", currentLocation);
                    data.put("target", targets);
                    eventBus.post(
                            new PluginMessage("shortestpath", "path", data)
                    );
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
            homePathCalculated = true;
            if (remainingTiles >= 0 || !hasItemsToDrop()) {
                remainingTiles = client.getTotalLevel() / 2;
                identifyItems();
                saveInfo();
            }
        }
    }

    private void identifyItems() {

        if (rolledItemsManager == null)
        {
            return;
        }

        Item[] items = client.getItemContainer(InventoryID.INVENTORY).getItems();

        if (items == null){
            return;
        }

        for (Item item : items){
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
        pathfinderConfig = new PathfinderConfig(client, shortestPathConfig);

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

        keyManager.registerKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (config.enableOverwriteSteps() && e.getKeyChar()=='r'){
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

        if (pathfindingExecutor != null) {
            pathfindingExecutor.shutdownNow();
            pathfindingExecutor = null;
        }
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

    /*public CollisionMap getMap() {
        return pathfinderConfig.getMap();
    }

    public Map<Integer, Set<Transport>> getTransports() {
        return pathfinderConfig.getTransports();
    }*/

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
        log.warn("lastTile was null");
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

        if (Math.abs(xDiff) > 2 || Math.abs(yDiff) > 2){
            log.warn("Illegal xDiff {}, yDiff {}", xDiff, yDiff);
        }

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

    private Widget getMinimapDrawWidget() {
        if (client.isResized()) {
            if (client.getVarbitValue(Varbits.SIDE_PANELS) == 1) {
                return client.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA);
            }
            return client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
        }
        return client.getWidget(ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
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
            if (remainingTiles >= 0 || !hasItemsToDrop()) {
                remainingTiles = client.getTotalLevel() / 2;
                identifyItems();
                saveInfo();
            }
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath actorDeath) {
        if (actorDeath.getActor() != client.getLocalPlayer())
            return;

        remainingTiles = -1;
        saveInfo();
    }

    public static boolean isVisible(VisitedTiles currentTiles, WorldPoint p){
        return currentTiles.get(p.getX(), p.getY(), p.getPlane())
                || currentTiles.get(p.getX()-1, p.getY(), p.getPlane())
                || currentTiles.get(p.getX()+1, p.getY(), p.getPlane())
                || currentTiles.get(p.getX(), p.getY()-1, p.getPlane())
                || currentTiles.get(p.getX(), p.getY()+1, p.getPlane());
    }


}
