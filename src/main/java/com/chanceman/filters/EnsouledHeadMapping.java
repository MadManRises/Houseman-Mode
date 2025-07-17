package com.chanceman.filters;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class EnsouledHeadMapping {
    public static final int DEFAULT_ENSOULED_HEAD_ID = -1;

    @Getter public static final Map<String, Integer> ENSOULED_HEAD_MAP;

    static {
        Map<String, Integer> ensouledMap = new HashMap<>();
        ensouledMap.put("ensouled abyssal head", 13508);
        ensouledMap.put("ensouled aviansie head", 13505);
        ensouledMap.put("ensouled bear head", 13463);
        ensouledMap.put("ensouled bloodveld head", 13496);
        ensouledMap.put("ensouled chaos druid head", 13472);
        ensouledMap.put("ensouled dagannoth head", 13493);
        ensouledMap.put("ensouled demon head", 13502);
        ensouledMap.put("ensouled dog head", 13469);
        ensouledMap.put("ensouled dragon head", 13511);
        ensouledMap.put("ensouled elf head", 13481);
        ensouledMap.put("ensouled giant head", 13475);
        ensouledMap.put("ensouled goblin head", 13448);
        ensouledMap.put("ensouled hellhound head", 26997);
        ensouledMap.put("ensouled horror head", 13487);
        ensouledMap.put("ensouled imp head", 13454);
        ensouledMap.put("ensouled kalphite head", 13490);
        ensouledMap.put("ensouled minotaur head", 13457);
        ensouledMap.put("ensouled monkey head", 13451);
        ensouledMap.put("ensouled ogre head", 13478);
        ensouledMap.put("ensouled scorpion head", 13460);
        ensouledMap.put("ensouled troll head", 13484);
        ensouledMap.put("ensouled tzhaar head", 13499);
        ensouledMap.put("ensouled unicorn head", 13466);
        ENSOULED_HEAD_MAP = Collections.unmodifiableMap(ensouledMap);
    }
}
