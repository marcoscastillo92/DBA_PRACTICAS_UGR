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
    private final double x;
    private final double y;
    private final int height;
    private boolean visited;
    int MAX_HEIGHT = 255;

    public Node(String id, double x, double y, int height) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.height = height;
        this.visited = false;
    }

    public String getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
    
    public int getHeight() {
        return height;
    }
    
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
