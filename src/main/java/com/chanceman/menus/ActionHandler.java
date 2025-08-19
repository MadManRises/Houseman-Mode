package com.chanceman.menus;

import com.chanceman.managers.UnlockedItemsManager;
import com.houseman.HousemanModePlugin;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;
import shortestpath.ShortestPathPlugin;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Consumer;

@Singleton
public class ActionHandler {


	private static final Set<MenuAction> disabledActions = EnumSet.of(
			MenuAction.CC_OP,               // inventory “Use” on locked
			MenuAction.WIDGET_TARGET,       // “Use” on widgets
			MenuAction.WIDGET_TARGET_ON_WIDGET,  // “Use” on widget -> widget
			MenuAction.EXAMINE_ITEM_GROUND,
			MenuAction.CC_OP_LOW_PRIORITY
	);

	private static final Set<MenuAction> GROUND_ACTIONS = EnumSet.of(
			MenuAction.GROUND_ITEM_FIRST_OPTION,
			MenuAction.GROUND_ITEM_SECOND_OPTION,
			MenuAction.GROUND_ITEM_THIRD_OPTION,
			MenuAction.GROUND_ITEM_FOURTH_OPTION,
			MenuAction.GROUND_ITEM_FIFTH_OPTION,
			MenuAction.EXAMINE_ITEM_GROUND
	);

	public static final Set<Integer> HOUSE_PORTALS = Collections.unmodifiableSet(new HashSet<Integer>(Arrays.asList(
		ObjectID.PORTAL_15477,
			ObjectID.PORTAL_15478,
			ObjectID.PORTAL_15479,
			ObjectID.PORTAL_15480,
			ObjectID.PORTAL_15481,
			15482,
			ObjectID.PORTAL_28822,
			ObjectID.PORTAL_34947,
			ObjectID.PORTAL_55353
	)));

	/**
	 * Normalize a MenuEntryAdded into the base item ID.
	 */
	private int getItemId(MenuEntryAdded event, MenuEntry entry)
	{
		MenuAction type = entry.getType();
		boolean hasItemId = entry.getItemId() > 0 || event.getItemId() > 0;
		if (!GROUND_ACTIONS.contains(type) && !hasItemId) {return -1;}
		int raw = GROUND_ACTIONS.contains(type)
				? event.getIdentifier()
				: Math.max(event.getItemId(), entry.getItemId());
		return plugin.getItemManager().canonicalize(raw);
	}

	@Inject
	private Client client;
	@Inject
	private EventBus eventBus;
	@Inject
	private HousemanModePlugin plugin;
	@Inject
	private Restrictions restrictions;
	@Inject
	private UnlockedItemsManager unlockedItemsManager;

	// A no-op click handler that marks a menu entry as disabled.
	public static final Consumer<MenuEntry> DISABLED = e -> { };

	public void startUp() {
		eventBus.register(this);
		eventBus.register(restrictions);
	}

	public void shutDown() {
		eventBus.unregister(this);
		eventBus.unregister(restrictions);
	}

	private boolean inactive() {
		if (!unlockedItemsManager.ready()) return true;
		return client.getGameState().getState() < GameState.LOADING.getState();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (inactive()) return;

		MenuEntry entry = event.getMenuEntry();
		MenuAction action = entry.getType();

		String option = Text.removeTags(entry.getOption());
		String target = Text.removeTags(entry.getTarget());

		int id = getItemId(event, entry);
		boolean enabled;
		// Check if the entry looks like it's for a ground item.
		if (isGroundItem(entry)) {
			enabled = !isLockedGroundItem(id) || option.equalsIgnoreCase("take");
		} else {
			enabled = isEnabled(id, entry, action);
		}
		if (id != -1 && !unlockedItemsManager.isUnlocked(id) && plugin.isTradeable(id) && !target.isEmpty()) {
			entry.setTarget(entry.getTarget().replace(target, "Unidentified Item"));
			target = "Unidentified Item";

		}
		// If not enabled, grey out the text and set the click handler to DISABLED.
		if (!enabled) {
			entry.setOption("<col=808080>" + option);
			entry.setTarget("<col=808080>" + target);
			entry.onClick(DISABLED);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		// If the entry is disabled, consume the event.
		if (event.getMenuEntry().onClick() == DISABLED) {
			event.consume();
			return;
		}
		// Extra safeguard for ground items.
		handleGroundItems(plugin.getItemManager(), unlockedItemsManager, event, plugin);
	}


	@Subscribe
	public void onPostMenuSort(PostMenuSort postMenuSort)
	{
		// The menu is not rebuilt when it is open, so don't swap or else it will
		// repeatedly swap entries
		if (client.isMenuOpen() || plugin.getRemainingTiles() >= 0)
		{
			return;
		}

		Menu root = client.getMenu();
		MenuEntry[] menuEntries = root.getMenuEntries();


		// Perform swaps

		for (MenuEntry entry : menuEntries)
		{
			String target = Text.removeTags(entry.getTarget());

			if (!plugin.hasItemsToDrop() && ((plugin.getPathfinder() != null && plugin.getPathfinder().getObjectIDs() != null &&
					plugin.getPathfinder().getObjectIDs().contains(entry.getIdentifier())) || HOUSE_PORTALS.contains(entry.getIdentifier()) || target.endsWith("Home Teleport")))
				continue;


			String action = Text.removeTags(entry.getOption());
			if (!target.isEmpty()){

				if (!action.equalsIgnoreCase("drop")){
					root.removeMenuEntry(entry);
				}
			}else if (plugin.hasItemsToDrop()){
				if (action.equalsIgnoreCase("walk here")){
					root.removeMenuEntry(entry);
				}
			}
		}
	}


	/**
	 * Returns true if the entry appears to be for a ground item.
	 */
	private boolean isGroundItem(MenuEntry entry)
	{
		return GROUND_ACTIONS.contains(entry.getType());
	}

	/**
	 * @param itemId canonicalized item ID of a ground item
	 * @return true if it’s tradeable, tracked, and still locked
	 */
	private boolean isLockedGroundItem(int itemId)
	{
		return plugin.isTradeable(itemId)
				&& !plugin.isNotTracked(itemId)
				&& !unlockedItemsManager.isUnlocked(itemId);
	}

	/**
	 * This method handles non-ground items (or any other cases) by checking if the item is enabled.
	 * It returns true if the action should be allowed.
	 */
	private boolean isEnabled(int id, MenuEntry entry, MenuAction action) {
		String option = Text.removeTags(entry.getOption());
		String target = Text.removeTags(entry.getTarget());

		// Always allow "Drop"
		if (option.equalsIgnoreCase("drop") || (option.length() >= 3 && option.substring(0, 3).equalsIgnoreCase("buy")) || option.equalsIgnoreCase("value"))
			return true;
		if (option.equalsIgnoreCase("clean") || option.equalsIgnoreCase("rub"))
		{
			if (!plugin.isInPlay(id)) { return true; }
			return unlockedItemsManager.isUnlocked(id);
		}
		if (SkillOp.isSkillOp(option))
			return restrictions.isSkillOpEnabled(option);
		if (Spell.isSpell(option))
			return restrictions.isSpellOpEnabled(option);
		if (Spell.isSpell(target))
			return restrictions.isSpellOpEnabled(target);

		boolean enabled = !disabledActions.contains(action);

		if (enabled)
			return true;
		if (id == 0 || id == -1 || !plugin.isInPlay(id))
			return true;
		return unlockedItemsManager.isUnlocked(id);
	}

	/**
	 * A static helper to further safeguard ground item actions.
	 * If a ground item is locked, this method consumes the event.
	 */
	public static void handleGroundItems(ItemManager itemManager, UnlockedItemsManager unlockedItemsManager,
										 MenuOptionClicked event, HousemanModePlugin plugin)
	{
		if (event.getMenuAction() != null && GROUND_ACTIONS.contains(event.getMenuAction()))
		{
			int rawItemId = event.getId() != -1 ? event.getId() : event.getMenuEntry().getItemId();
			int canonicalGroundId = itemManager.canonicalize(rawItemId);
			if (plugin.isTradeable(canonicalGroundId)
					&& !plugin.isNotTracked(canonicalGroundId)
					&& unlockedItemsManager != null
					&& !unlockedItemsManager.isUnlocked(canonicalGroundId) && !Text.removeTags(event.getMenuEntry().getOption()).equalsIgnoreCase("take")) {
				event.consume();
			}
		}
	}
}
