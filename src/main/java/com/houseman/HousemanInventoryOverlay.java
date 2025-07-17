package com.houseman;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import shortestpath.WorldPointUtil;

import java.awt.*;
import java.util.List;

public class HousemanInventoryOverlay extends WidgetItemOverlay {
    private final Client client;
    private final HousemanModePlugin plugin;
    private final HousemanModeConfig config;

    @Inject
    public HousemanInventoryOverlay(Client client, HousemanModePlugin plugin, HousemanModeConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        showOnInventory();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (plugin.getRemainingTiles() < 0 && plugin.itemNeedsToBeDropped(itemId)){
            graphics.setColor(Color.RED);
            Rectangle slotBounds = widgetItem.getCanvasBounds();
            graphics.drawRect(slotBounds.x-1, slotBounds.y-1, slotBounds.width+2, slotBounds.height+2);
        }
    }


}
