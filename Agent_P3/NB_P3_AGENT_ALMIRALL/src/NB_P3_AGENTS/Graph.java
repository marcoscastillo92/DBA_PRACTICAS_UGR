/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package NB_P3_AGENTS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author Marcos
 * @param <T>
 */
public class Graph<T extends Node> {
    private final Set<T> nodes;
    private final Map<String, Set<String>> connections;
    
    /** Constructor del Graph
    * @param nodes nodos del grafo
    * @param connections conexiones entre nodos
    * @author Marcos
    */
    public Graph(Set<T> nodes, Map<String, Set<String>> connections) {
        this.nodes = nodes;
        this.connections = connections;
    }
    
    /**
     * Get de un nodo del grafo
     * @param id ID del nodo (y-x)
     * @return nodo con la ID
     * @author Marcos
     */
    public T getNode(String id) {
        T finded = null;
        for(T element : nodes){
            if(element.getId().equals(id)){
                finded = element;
                break;
            }
        }
        return finded;
//        return nodes.stream()
//                .filter(node -> node.getId().equals(id))
//                .findFirst()
//                .orElseThrow(() -> new IllegalArgumentException("No node found with ID"));
    }
    
    /**
     * Get de las conexiones de un nodo
     * @param node nodo a buscar
     * @return conexiones del nodo
     * @author Marcos Castillo
     */
    public Set<T> getConnections(T node) {
        String[] connectionsString = connections.get(node.getId()).toString().replace("[","").replace("]","").split(",");
        Set<T> result = new HashSet<T>();
        for(String id : connectionsString){
            T neighbour = getNode(id);
            if(neighbour != null)
                result.add(neighbour);
        }
        return result;
//        return connections.get(node.getId()).stream()
//                .map(this::getNode)
//                .collect(Collectors.toSet());
    }
}
