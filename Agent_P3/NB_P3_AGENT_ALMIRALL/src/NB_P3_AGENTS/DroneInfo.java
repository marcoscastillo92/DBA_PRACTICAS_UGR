package NB_P3_AGENTS;

/**
 *
 * @author Diego Garcia
 */
public class DroneInfo {
    private String name;
    private int money;
    private int xPosition, yPosition, nextXPosition, nextYPosition;
    private int droneHeight;
    private int energy;
    
    /** Constructor sin parametros de DroneInfo
    *
    * @author Diego Garcia
    */
    DroneInfo() {
        this.name = "";
        this.money = 0;
        this.xPosition = 0;
        this.yPosition = 0;
        this.droneHeight = 0;
        this.energy = 0;
    }
    
    /** Constructor con nombre de DronInfo
    * @param n nombre del dron
    * @author Diego Garcia
    */
    DroneInfo(String n) {
        this.name = n;
        this.money = 0;
        this.xPosition = 0;
        this.yPosition = 0;
        this.droneHeight = 0;
        this.energy = 0;
    }
    
    /** Constructor con parámetros de DronInfo
    * @param n nombre del dron
    * @param m dinero del dron
    * @param x coordenada x del dron
    * @param y coordenada y del dron
    * @param h altura del dron
    * @param e energía del dron
    * @author Diego Garcia
    */
    DroneInfo(String n, int m, int x, int y, int h, int e) {
        this.name = n;
        this.money = m;
        this.xPosition = x;
        this.yPosition = y;
        this.droneHeight = h;
        this.energy = e;
    }
    
    /**
     * Set para guardar el nombre del dron
     * @param name nombre del dron
     * @author Diego García
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Set para guardar el dinero del dron
     * @param money dinero del dron
     * @author Diego García
     */
    public void setMoney(int money) {
        this.money = money;
    }

    /**
     * Set para guardar la coordenada x del dron
     * @param xPosition coordenada x del dron
     * @author Diego García
     */
    public void setxPosition(int xPosition) {
        this.xPosition = xPosition;
    }

    /**
     * Set para guardar la coordenada y del dron
     * @param yPosition coordenada y del dron
     * @author Diego García
     */
    public void setyPosition(int yPosition) {
        this.yPosition = yPosition;
    }

    /**
     * Set para guardar la altura del dron
     * @param droneHeight altura del dron
     * @author Diego García
     */
    public void setDroneHeight(int droneHeight) {
        this.droneHeight = droneHeight;
    }

    /**
     * Set para guardar la energía del dron
     * @param energy energía del dron
     * @author Diego García
     */
    public void setEnergy(int energy) {
        this.energy = energy;
    }

    /**
     * Get del nombre del dron
     * @return nombre del dron
     * @author Diego García
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get del dinero del dron
     * @return dinero del dron
     * @author Diego García
     */
    public int getMoney() {
        return money;
    }

    /**
     * Get de la coordenada x del dron
     * @return coordenada x del dron
     * @author Diego García
     */
    public int getxPosition() {
        return xPosition;
    }

    /**
     * Get de la coordenada y del dron
     * @return coordenada y del dron
     * @author Diego García
     */
    public int getyPosition() {
        return yPosition;
    }

    /**
     * Get de la altura del dron
     * @return altura del dron
     * @author Diego García
     */
    public int getDroneHeight() {
        return droneHeight;
    }

    /**
     * Get de la energía del dron
     * @return energía del dron
     * @author Diego García
     */
    public int getEnergy() {
        return energy;
    }

    /**
     * Get de la siguiente coordenada x del dron
     * @return siguiente coordenada x del dron
     * @author Diego García
     */
    public int getNextXPosition() { return nextXPosition; }

    /**
     * Get de la siguiente coordenada y del dron
     * @return siguiente coordenada y del dron
     * @author Diego García
     */
    public int getNextYPosition() { return nextYPosition; }

    /**
     * Set para guardar la siguiente coordenada x del dron
     * @param x siguiente coordenada x del dron
     * @author Diego García
     */
    public void setNextXPosition(int x) {  nextXPosition = x; }

    /**
     * Set para guardar la siguiente coordenada y del dron
     * @param y siguiente coordenada y del dron
     * @author Diego García
     */
    public void setNextYPosition(int y) {  nextYPosition = y; }
}