/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package NB_P3_AGENTS;

/**
 *
 * @author Marcos
 */
public class Scorer<T extends Node> {

    /**
     * Método para calcular el coste de ir de X a Y (distancia entre 2 puntos + diferencia de altura)
     * @param from
     * @param to
     * @return
     * @author Marcos Castillo
     */
    public double computeCost(Node from, Node to) {
        double distance = 999999999.99;
        
        double dX = to.getX() - from.getX();
        double dY = to.getY() - from.getY();
        
        distance = Math.sqrt(Math.pow(dX, 2)+Math.pow(dY, 2)); //Distance between 2 points
        
        if(to.getHeight() >= to.MAX_HEIGHT){
            distance += 999999999.99;
        }else{
            distance += to.getHeight() - from.getHeight(); //Sumamos a la ponderación la diferencia de altura
        }
        
        return distance;
    }
}
