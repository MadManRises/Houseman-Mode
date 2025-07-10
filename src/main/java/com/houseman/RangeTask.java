package com.houseman;

import shortestpath.pathfinder.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;


public class RangeTask implements Callable<RangeResult> {
    private int range;

    private PriorityQueue<RangeNode> queue = new PriorityQueue<>();
    private PathfinderConfig config;
    private CollisionMap map;

    public RangeTask(PathfinderConfig config, int start, int range) {
        queue.add(new RangeNode(new Node(start, null), 0));
        this.range = range;

        this.config = config;
        this.map = config.getMap();
    }

    @Override
    public RangeResult call() {
        RangeResult result = new RangeResult();
        result.border = new ArrayList<Integer>();
        result.visitedTiles = new VisitedTiles(map);

        if (range >= 0) {
            while (!queue.isEmpty()) {
                RangeNode node = queue.poll();
                if (result.visitedTiles.get(node.node.packedPosition))
                    continue;
                result.visitedTiles.set(node.node.packedPosition);

                if (node.distance == range) {
                    result.border.add(node.node.packedPosition);
                } else {

                    List<Node> nodes = map.getNeighbors(node.node, result.visitedTiles, config);
                    for (int i = 0; i < nodes.size(); ++i) {
                        Node neighbor = nodes.get(i);
                        queue.add(new RangeNode(neighbor, node.distance + 1));
                    }
                }
            }
        }

        return result;
    }

}
