package NB_P3_AGENTS;

public class DroneInfo {
    private String name;
    private int money;
    private int xPosition, yPosition;
    private int droneHeight;
    private int energy;
    
    DroneInfo() {
        this.name = "";
        this.money = 0;
        this.xPosition = 0;
        this.yPosition = 0;
        this.droneHeight = 0;
        this.energy = 0;
    }
    
    DroneInfo(String n) {
        this.name = n;
        this.money = 0;
        this.xPosition = 0;
        this.yPosition = 0;
        this.droneHeight = 0;
        this.energy = 0;
    }
    
    DroneInfo(String n, int m, int x, int y, int h, int e) {
        this.name = n;
        this.money = m;
        this.xPosition = x;
        this.yPosition = y;
        this.droneHeight = h;
        this.energy = e;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public void setMoney(int money) {
        this.money = money;
    }

    public void setxPosition(int xPosition) {
        this.xPosition = xPosition;
    }

    public void setyPosition(int yPosition) {
        this.yPosition = yPosition;
    }

    public void setDroneHeight(int droneHeight) {
        this.droneHeight = droneHeight;
    }

    public void setEnergy(int energy) {
        this.energy = energy;
    }

    public String getName() {
        return name;
    }
    
    public int getMoney() {
        return money;
    }

    public int getxPosition() {
        return xPosition;
    }

    public int getyPosition() {
        return yPosition;
    }

    public int getDroneHeight() {
        return droneHeight;
    }

    public int getEnergy() {
        return energy;
    } 
}