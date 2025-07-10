package com.houseman;

import shortestpath.pathfinder.Node;

public class RangeNode implements Comparable<RangeNode> {

    public Node node;
    public int distance;

    public RangeNode(Node node, int distance){
        this.node = node;
        this.distance = distance;
    }

    @Override
    public int compareTo(RangeNode o) {
        return Integer.compare(distance, o.distance);
    }
}
