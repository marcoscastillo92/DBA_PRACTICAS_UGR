/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package NB_P3_AGENTS;



/**
 *
 * @author diego
 */
public class Sensor {
    private String shop;
    private String name;
    private int price;
    
    Sensor(String s, String n, int p) {
        this.shop = s;
        this.name = n;
        this.price = p;
    }

    public String getShop() {
        return shop;
    }

    public void setShop(String shop) {
        this.shop = shop;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }
}