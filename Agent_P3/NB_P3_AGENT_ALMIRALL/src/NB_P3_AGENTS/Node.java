/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package NB_P3_AGENTS;

import java.util.StringJoiner;

/**
 *
 * @author Marcos
 */
public class Node {
    private final String id;
    private final int x;
    private final int y;
    private final int height;
    private boolean visited;
    private double distanceToRescuer;
    int MAX_HEIGHT = 256;

    public Node(String id, int x, int y, int height) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.height = height;
        this.visited = false;
    }

    public Node(String id, int x, int y, int height, double distanceToRescuer) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.height = height;
        this.visited = false;
        this.distanceToRescuer = distanceToRescuer;
    }

    public String getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
    
    public int getHeight() {
        return height;
    }

    public double getDistanceToRescuer() { return distanceToRescuer; }

    public void setDistanceToRescuer(double v) { distanceToRescuer = v; }
    
    public boolean isVisited() {
        return this.visited;
    }
    
    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Node.class.getSimpleName() + "[", "]").add("id='" + id + "'").add("x=" + x).add("y=" + y).toString();
    }
}
