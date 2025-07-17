package com.chanceman.menus;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

import java.util.HashSet;
import java.util.HashMap;

@Getter
public enum SkillItem
{

	BRONZE_AXE(1351, SkillOp.CHOP_DOWN),
	IRON_AXE(1349, SkillOp.CHOP_DOWN),
	STEEL_AXE(1353, SkillOp.CHOP_DOWN),
	BLACK_AXE(1361, SkillOp.CHOP_DOWN),
	MITHRIL_AXE(1355, SkillOp.CHOP_DOWN),
	ADAMANT_AXE(1357, SkillOp.CHOP_DOWN),
	RUNE_AXE(1359, SkillOp.CHOP_DOWN),
	DRAGON_AXE(6739, SkillOp.CHOP_DOWN),
	GILDED_AXE(23279, SkillOp.CHOP_DOWN),
	THIRD_AGE_AXE(20011, SkillOp.CHOP_DOWN),

	BRONZE_FELLING_AXE(28196, SkillOp.CHOP_DOWN),
	IRON_FELLING_AXE(28199, SkillOp.CHOP_DOWN),
	STEEL_FELLING_AXE(28202, SkillOp.CHOP_DOWN),
	BLACK_FELLING_AXE(28205, SkillOp.CHOP_DOWN),
	MITHRIL_FELLING_AXE(28208, SkillOp.CHOP_DOWN),
	ADAMANT_FELLING_AXE(28211, SkillOp.CHOP_DOWN),
	RUNE_FELLING_AXE(28214, SkillOp.CHOP_DOWN),
	DRAGON_FELLING_AXE(28217, SkillOp.CHOP_DOWN),
	THIRD_AGE_FELLING_AXE(28226, SkillOp.CHOP_DOWN),

	BRONZE_PICKAXE(1265, SkillOp.MINE),
	IRON_PICKAXE(1267, SkillOp.MINE),
	STEEL_PICKAXE(1269, SkillOp.MINE),
	BLACK_PICKAXE(12297, SkillOp.MINE),
	MITHRIL_PICKAXE(1273, SkillOp.MINE),
	ADAMANT_PICKAXE(1271, SkillOp.MINE),
	RUNE_PICKAXE(1275, SkillOp.MINE),
	DRAGON_PICKAXE(12797,SkillOp.MINE),
	GILDED_PICKAXE(23276, SkillOp.MINE),
	THIRD_AGE_PICKAXE(20014, SkillOp.MINE),

	SMALL_FISHING_NET(303, SkillOp.NET),
	BIG_FISHING_NET(305, SkillOp.NET),
	LOBSTER_POT(301, SkillOp.CAGE),
	FISHING_BAIT(313, SkillOp.BAIT),
	FLY_FISHING_ROD(309, SkillOp.LURE),
	RAKE(5341, SkillOp.RAKE),
	SHEARS(1735, SkillOp.SHEAR),
	SECATEURS(5329, SkillOp.PRUNE),

	BRONZE_BAR(2349, SkillOp.SMITH),
	IRON_BAR(2351, SkillOp.SMITH),
	STEEL_BAR(2353, SkillOp.SMITH),
	MITHRIL_BAR(2359, SkillOp.SMITH),
	ADAMANTITE_BAR(2361, SkillOp.SMITH),
	RUNITE_BAR(2363, SkillOp.SMITH),

	TIN_ORE(438, SkillOp.SMELT),
	COPPER_ORE(436, SkillOp.SMELT),
	IRON_ORE(440, SkillOp.SMELT),
	COAL(453, SkillOp.SMELT),
	MITHRIL_ORE(447, SkillOp.SMELT),
	RUNITE_ORE(451, SkillOp.SMELT),
	SILVER_ORE(442, SkillOp.SMELT),
	SILVER_BAR(2355,SkillOp.SMELT),
	GOLD_ORE(444, SkillOp.SMELT),
	GOLD_BAR(2357, SkillOp.SMELT),

	GRIMY_GUAM_LEAF(199, SkillOp.CLEAN),
	GRIMY_MARRENTILL(201, SkillOp.CLEAN),
	GRIMY_TARROMIN(203, SkillOp.CLEAN),
	GRIMY_HARRALANDER(205, SkillOp.CLEAN),
	GRIMY_RANARR_WEED(207, SkillOp.CLEAN),
	GRIMY_IRIT_LEAF(209, SkillOp.CLEAN),
	GRIMY_AVANTOE(211, SkillOp.CLEAN),
	GRIMY_KWUARM(213, SkillOp.CLEAN),
	GRIMY_CADANTINE(215, SkillOp.CLEAN),
	GRIMY_DWARF_WEED(217, SkillOp.CLEAN),
	GRIMY_TORSTOL(219, SkillOp.CLEAN),
	GRIMY_LANTADYME(2485, SkillOp.CLEAN),
	GRIMY_TOADFLAX(3049, SkillOp.CLEAN),
	GRIMY_SNAPDRAGON(3051, SkillOp.CLEAN),

	UNFIRED_BOWL(1791, SkillOp.FIRE),
	UNFIRED_CUP(28193, SkillOp.FIRE),
	UNFIRED_PIE_DISH(1789, SkillOp.FIRE),
	UNFIRED_PLANT_POT(5352, SkillOp.FIRE),
	UNFIRED_POT(1787, SkillOp.FIRE),
	UNFIRED_POT_LID(4438, SkillOp.FIRE),

	RUNE_ESSENCE(1436, SkillOp.CRAFT_RUNE),
	PURE_ESSENCE(7936, SkillOp.CRAFT_RUNE),

	//Blackjacks
	OAK_BLACKJACK_O(6408, SkillOp.LURE),
	OAK_BLACKJACK_D(6410, SkillOp.LURE),
	WILLOW_BLACKJACK(4600, SkillOp.LURE),
	WILLOW_BLACKJACK_O(6412, SkillOp.LURE),
	WILLOW_BLACKJACK_D(6414, SkillOp.LURE),
	MAPLE_BLACKJACK(6416, SkillOp.LURE),
	MAPLE_BLACKJACK_O(6418, SkillOp.LURE),
	MAPLE_BLACKJACK_D(6420, SkillOp.LURE),

	// Untradeables that conflict
	OAK_BLACKJACK(4599, SkillOp.LURE, false),
	RED_VINE_WORMS(25, SkillOp.BAIT, false),
	BOBS_NET(6209, SkillOp.NET, false);

	private final int id;
	private final SkillOp option;
	private final boolean requiresUnlock;

	SkillItem(int id, SkillOp option)
	{
		this(id, option, true);
	}

	SkillItem(int id, SkillOp option, boolean requiresUnlock)
	{
		this.id = id;
		this.option = option;
		this.requiresUnlock = requiresUnlock;
	}

	public SkillOp getSkillOp()
	{
		return option;
	}

	private static final HashSet<Integer> ALL_SKILL_ITEMS = new HashSet<>();
	private static final HashMap<Integer, SkillItem> ID_TO_ITEM = new HashMap<>();

	static
	{
		for (SkillItem skillItem : SkillItem.values())
		{
			ALL_SKILL_ITEMS.add(skillItem.getId());
			ID_TO_ITEM.put(skillItem.getId(), skillItem);
		}
	}

	public static boolean isSkillItem(int id)
	{
		return ALL_SKILL_ITEMS.contains(id);
	}

	public static SkillItem fromId(int id)
	{
		return ID_TO_ITEM.get(id);
	}
}
