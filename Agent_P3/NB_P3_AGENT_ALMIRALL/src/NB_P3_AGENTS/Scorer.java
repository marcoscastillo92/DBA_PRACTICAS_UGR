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
    //Hay que modificar este método para calcular el coste
    //conforme nuestro criterio
    public double computeCost(Node from, Node to) {
        double R = 6372.8; // In kilometers

        double dX = Math.toRadians(to.getX() - from.getX());
        double dY = Math.toRadians(to.getY() - from.getY());
        double x1 = Math.toRadians(from.getX());
        double x2 = Math.toRadians(to.getX());

        double a = Math.pow(Math.sin(dX / 2),2) + Math.pow(Math.sin(dY / 2),2) * Math.cos(x1) * Math.cos(x2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return R * c;
    }
}
