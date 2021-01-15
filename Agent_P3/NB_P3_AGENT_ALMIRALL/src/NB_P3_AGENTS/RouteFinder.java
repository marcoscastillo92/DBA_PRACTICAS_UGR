/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package NB_P3_AGENTS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 *
 * @author Marcos Castillo
 */
public class RouteFinder<T extends Node> {
    private final Graph<T> graph;
    private final Scorer<T> nextNodeScorer;
    private final Scorer<T> targetScorer;
    
    public RouteFinder(Graph<T> graph, Scorer<T> nextNodeScorer, Scorer<T> targetScorer) {
        this.graph = graph;
        this.nextNodeScorer = nextNodeScorer;
        this.targetScorer = targetScorer;
    }

    /**
     * Método que devuelve una ruta de nodo X a nodo Y
     * @param from
     * @param to
     * @return
     * @author Marcos Castillo
     */
    public List<T> findRoute(T from, T to) {
        Queue<RouteNode> openSet = new PriorityQueue<>();
        Map<T, RouteNode<T>> allNodes = new HashMap<>();
        
        RouteNode<T> start = new RouteNode<>(from, null, 0d, targetScorer.computeCost(from, to));
        openSet.add(start);
        allNodes.put(from, start);
        
        while(!openSet.isEmpty()){
            RouteNode<T> next = openSet.poll();
            if(next.getCurrent().equals(to)){
                List<T> route = new ArrayList<>();
                RouteNode<T> current = next;
                do {
                    route.add(0, current.getCurrent());
                    current = allNodes.get(current.getPrevious());
                } while (current != null);
                return route;
            }
            
            graph.getConnections(next.getCurrent()).forEach(connection -> {
                RouteNode<T> nextNode = allNodes.getOrDefault(connection, new RouteNode<>(connection));
                allNodes.put(connection, nextNode);
                
                double newScore = next.getRouteScore() + nextNodeScorer.computeCost(next.getCurrent(), connection);
                if(newScore < nextNode.getRouteScore()) {
                    nextNode.setPrevious(next.getCurrent());
                    nextNode.setRouteScore(newScore);
                    nextNode.setEstimatedScore(newScore + targetScorer.computeCost(connection, to));
                    openSet.add(nextNode);
                }
            });
        }
        
        throw new IllegalStateException("No route found");
    }
}
