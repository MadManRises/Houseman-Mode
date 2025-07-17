package com.chanceman;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("housemanMode")
public interface ChanceManConfig extends Config
{
    @ConfigSection(
            name = "Chance Man - Settings",
            description = "Chance Man - Settings'",
            position = 2
    )
    String settingsSection = "chancemansettings";


    @ConfigItem(
            keyName = "freeToPlay",
            name = "Free To Play Mode",
            description = "Only allow free-to-play items",
            position = 1,
            section = settingsSection
    )
    default boolean freeToPlay()
    {
        return false;
    }

    @ConfigItem(
            keyName = "includeF2PTradeOnlyItems",
            name = "Include F2P trade-only items",
            description = "When Free-to-Play mode is enabled, also roll items that can only " +
                    "be obtained via trading or the Grand Exchange.",
            position = 2,
            section = settingsSection
    )
    default boolean includeF2PTradeOnlyItems() { return false; }

    @ConfigItem(
            keyName = "enableItemSets",
            name = "Roll Item Sets",
            description = "Include item set items in the rollable items list. Disabling this will exclude any" +
                    " item set items from random rolls.",
            position = 3,
            section = settingsSection
    )
    default boolean enableItemSets() { return true; }

    @ConfigItem(
            keyName = "enableFlatpacks",
            name = "Roll Flatpacks",
            description = "Include flatpacks in the rollable items list. Disabling this will prevent" +
                    " flatpacks from being rolled.",
            position = 4,
            section = settingsSection
    )
    default boolean enableFlatpacks() { return true; }

    @ConfigItem(
            keyName = "requireWeaponPoison",
            name = "Weapon Poison Unlock Requirements",
            description = "Force poison variants to roll only if both the base weapon and the corresponding" +
                    " weapon poison are unlocked. (Disabling this will allow poisoned variants to roll even if " +
                    "the poison is locked.)",
            position = 5,
            section = settingsSection
    )
    default boolean requireWeaponPoison() { return true; }

    @ConfigItem(
            keyName = "enableRollSounds",
            name = "Enable Roll Sounds",
            description = "Toggle Roll Sound",
            position = 6,
            section = settingsSection
    )
    default boolean enableRollSounds() { return true; }

    @ConfigItem(
            keyName = "requireRolledUnlockedForGe",
            name = "GE Requires Rolled and Unlocked",
            description = "Only allow Grand Exchange purchases once items are both rolled and unlocked.",
            position = 7,
            section = settingsSection
    )
    default boolean requireRolledUnlockedForGe() { return true; }
}
