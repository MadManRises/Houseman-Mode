package com.houseman;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import com.housemanGPU.HousemanGpuPlugin;
import shortestpath.ShortestPathPlugin;

public class HousemanModePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ShortestPathPlugin.class, HousemanModePlugin.class, HousemanGpuPlugin.class);
		RuneLite.main(args);
	}
}