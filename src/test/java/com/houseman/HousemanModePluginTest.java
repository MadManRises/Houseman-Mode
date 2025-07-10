package com.houseman;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import com.housemanGPU.HousemanGpuPlugin;

public class HousemanModePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HousemanModePlugin.class, HousemanGpuPlugin.class);
		RuneLite.main(args);
	}
}