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

import net.runelite.client.config.*;
import shortestpath.ShortestPathConfig;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@ConfigGroup("housemanMode")
public interface HousemanModeConfig extends ShortestPathConfig {
    @ConfigSection(
            name = "Settings",
            description = "Settings'",
            position = 2
    )
    String settingsSection = "settings";

    @Range(
            min = Integer.MIN_VALUE
    )
    @ConfigItem(
            keyName = "warningLimit",
            name = "Unspent tiles warning",
            section = settingsSection,
            description = "Highlights overlay when limit reached",
            position = 1
    )
    default int warningLimit() {
        return 20;
    }

    @ConfigItem(
            keyName = "enableTilesWarning",
            name = "Enable Tiles Warning",
            section = settingsSection,
            description = "Turns on tile warnings when you reach your set limit or 0.",
            position = 2
    )
    default boolean enableTileWarnings() {
        return false;
    }

    @ConfigItem(
            keyName = "overwriteSteps",
            name = "Reset step count",
            section = settingsSection,
            description = "Debug tool",
            position = 3
    )
    default int overwriteSteps() {
        return 20;
    }


}
