package nb_hw_adv;

import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import ControlPanel.TTYControlPanel;
import static java.lang.Math.abs;
import java.util.ArrayList;

enum Status {
    LOGIN, NEEDS_INFO, HAS_ACTIONS, PLANNING, LOGOUT
}

public class AgentP2 extends IntegratedAgent {
    String receiver;
    TTYControlPanel myControlPanel;
    // Login variables
    String key;
    int width, height, maxflight;
    JsonArray options;
    JsonArray sensors;
    String world = "Playground2";
    String[] sensores = { "alive", "ontarget", "compass", "angular", "distance", "visual", "gps" };
    // General use variables
    JsonArray perceptions;
    Status status;
    ACLMessage current;
    ArrayList<String> actions;
    boolean needsNewActionPlan;
    private boolean needsInfo;
    // Control variables
    int energy, xVisualNextPos, yVisualNextPos;
    double compassSensor;
    double distanceSensor;
    double angularSensor;
    ArrayList<ArrayList<Integer>> visualSensor;
    ArrayList<Integer> gpsSensor;
    double compassActual;
    double angularActual;
    double distanceActual;
    ArrayList<Integer> gpsActual;
    ArrayList<String> nextActions;
    JsonArray perceptionsAuxiliar;
    boolean onTarget;

    @Override
    public void setup() {
        super.setup();
        this.doCheckinPlatform();
        this.doCheckinLARVA();
        this.perceptions = new JsonArray();
        this.actions = new ArrayList<>();
        this.visualSensor = new ArrayList<ArrayList<Integer>>();
        this.gpsSensor = new ArrayList<>();
        this.gpsActual = new ArrayList<>();
        this.nextActions = new ArrayList<>();
        this.receiver = this.whoLarvaAgent();
        this.myControlPanel = new TTYControlPanel(getAID());
        this.status = Status.LOGIN;
        this.needsNewActionPlan = false;
        this.needsInfo = true;
        this.energy = 1000;
        this.onTarget = false;
        this.perceptionsAuxiliar = new JsonArray();
        this._exitRequested = false;
    }

    @Override
    public void plainExecute() {
        switch (this.status) {
            case LOGIN:
                this.current = this.makeLogin();
                this.status = Status.NEEDS_INFO;
                break;
            case NEEDS_INFO:
                this.current = this.readSensors(this.current);
                this.needsInfo = false;
                this.status = Status.PLANNING;
                break;
            case HAS_ACTIONS:
                if (this.hasActions() && !this.onTarget) {
                    this.executeAction(this.current, this.actions.get(0));
                    this.current = this.blockingReceive();
                    this.setEnergy(this.actions.get(0));
                    this.removeFirstAction();
                } else if(this.status == Status.HAS_ACTIONS) {
                    this.status = Status.PLANNING;
                }
                break;
            case PLANNING:
                // Hay que ver cuando es necesario modificar needsInfo para evitar que no
                // planifique sin información suficiente
                this.createStrategy();
                if(this.hasActions() && !this.onTarget){
                    this.status = Status.HAS_ACTIONS;
                }else if (this.status == Status.PLANNING){
                    this.status = Status.NEEDS_INFO;
                }
                break;
            case LOGOUT:
                this._exitRequested = true;
                break;
        }
        this.showInfo(this.current);
    }

    @Override
    protected void takeDown() {
        this.myControlPanel.close();
        this.logoutAgent();
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }

    protected JsonObject generateLogin(String world, String[] sensores) {
        JsonObject login_json;
        // Generate JSON body
        login_json = new JsonObject();
        login_json.add("command", "login");
        login_json.add("world", world);

        // Generating JsonArray for sensors
        this.sensors = new JsonArray();
        for (String sensor : sensores) {
            this.sensors.add(sensor);
        }

        // Adding array to JSON body
        login_json.add("attach", this.sensors);
        return login_json;
    }

    public void sendMessage(String receiver, JsonObject body) {
        ACLMessage out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        out.setContent(body.toString());
        this.send(out);
    }

    private ACLMessage retrieveLoginMessage() {
        ACLMessage in = this.blockingReceive();
        this.key = this.getStringContent(in, "key").replaceAll("\\\"", "");
        this.width = this.getIntContent(in, "width");
        this.height = this.getIntContent(in, "height");
        this.maxflight = this.getIntContent(in, "maxflight");
        this.options = this.getJsonArrayContent(in, "capabilities");

        return in;
    }

    private ACLMessage makeLogin() {
        JsonObject login_json = new JsonObject(this.generateLogin(this.world, this.sensores));
        this.sendMessage(this.receiver, login_json);
        return this.retrieveLoginMessage();
    }

    private ACLMessage readSensors(ACLMessage in) {
        JsonObject read_sensors_json = new JsonObject();
        read_sensors_json.add("command", "read");
        read_sensors_json.add("key", this.key);
        this.replyMessage(in, read_sensors_json);

        ACLMessage reply = this.blockingReceive();
        JsonObject replyObj = new JsonObject(Json.parse(reply.getContent()).asObject());
        //System.out.println("ESTE ES EL REPLY DE SENSORES -----> " +replyObj);
        if (replyObj.get("result").asString().contains("ok")) {
            this.perceptions = new JsonArray(replyObj.get("details").asObject().get("perceptions").asArray());
            this.setEnergy("readSensors");
            this.updateSensorsInfo();
        } else {
            System.out.println("[SENSORS] Error: " + reply);
        }

        return reply;
    }

    public void executeAction(ACLMessage msg, String action) {
        JsonObject execute_json = new JsonObject();
        execute_json.add("command", "execute");
        execute_json.add("action", action);
        execute_json.add("key", this.key);
        this.replyMessage(msg, execute_json);
    }

    public void replyMessage(ACLMessage in, JsonObject content) {
        // Parseo del contenido a String para añadirlo al mensaje        

        ACLMessage out = in.createReply();
        out.setContent(content.toString());
        this.sendServer(out);
    }

    public ACLMessage retrieveMessage() {
        ACLMessage in = this.blockingReceive();
        String result = getStringContent(in, "result").replaceAll("\\\"", "");
        if (result.equals("ok")) {
            return in;
        } else {
            return null;
        }
    }

    public String getStringContent(ACLMessage msg, String field) {
        JsonObject res = new JsonObject(Json.parse(msg.getContent()).asObject());
        return res.get(field).toString();
    }

    public JsonObject getJsonContent(ACLMessage msg, String field) {
        JsonObject res = new JsonObject(Json.parse(msg.getContent()).asObject());
        return new JsonObject(res.get(field).asObject());
    }

    public JsonObject getJsonContent(ACLMessage msg) {
        JsonObject res = new JsonObject(Json.parse(msg.getContent()).asObject());
        return new JsonObject(res);
    }

    public int getIntContent(ACLMessage msg, String field) {
        JsonObject res = new JsonObject(Json.parse(msg.getContent()).asObject());
        return res.get(field).asInt();
    }

    public JsonArray getJsonArrayContent(ACLMessage msg, String field) {
        JsonObject res = new JsonObject(Json.parse(msg.getContent()).asObject());
        return new JsonArray(res.get(field).asArray());
    }

    public void logoutAgent() {
        JsonObject logout_json = new JsonObject();
        logout_json.add("command", "logout");

        this.sendMessage(this.receiver, logout_json);
    }

    private void removeFirstAction() {
            if(this.actions.size() > 1){
                this.actions.remove(0);
            }
            else{
                this.actions.clear();
            }    
    }

    private void addAction(String action) {
        this.actions.add(action);
    }

    private boolean hasActions() {
        return !this.actions.isEmpty();
    }

    private void showInfo(ACLMessage in) {
        this.myControlPanel.feedData(in, width, height, maxflight); // width height maxflight obtenidos en el login
        this.myControlPanel.fancyShow();
    }

    private Object interpretSensors(String sensor) {
        switch(sensor) {
            case "alive":
                return this.perceptions.get(0).asObject().get("data").asArray().get(0).asInt() == 1;
            case "ontarget":
                return this.perceptions.get(1).asObject().get("data").asArray().get(0).asInt() == 1;
            case "compass":
                return this.perceptions.get(2).asObject().get("data").asArray().get(0).asDouble();
            case "angular":
                return this.perceptions.get(3).asObject().get("data").asArray().get(0).asDouble();
            case "distance":
                return this.perceptions.get(4).asObject().get("data").asArray().get(0).asDouble();
            case "visual":
                JsonArray visualData = this.perceptions.get(5).asObject().get("data").asArray();
                ArrayList<ArrayList<Integer>> elevations = new ArrayList<>();
                ArrayList<Integer> element = new ArrayList<>();
                
                for(int i=0; i<visualData.size(); i++) {
                    JsonArray array =  visualData.get(i).asArray();
                    element.clear();
                    for(int j=0; j<array.size(); j++) {
                        element.add(array.get(j).asInt());
                    }
                    elevations.add(element);
                }

                return elevations;
            case "gps":
                JsonArray gpsData = this.perceptions.get(5).asObject().get("data").asArray().get(0).asArray();
                ArrayList<Integer> coordenadas = new ArrayList<>();

                for(int i=0; i<gpsData.size(); i++) {
                    coordenadas.add(gpsData.get(i).asInt());
                }

                return coordenadas;
        }
        
        return null;
    }
    
    /**
     * Fn que actualiza la información local de los sensores y el agente
     */
    private void updateSensorsInfo(){
        this.onTarget = (boolean) this.interpretSensors("ontarget");
        this.compassSensor = (double)this.interpretSensors("compass");
        this.angularSensor = (double)this.interpretSensors("angular");
        this.distanceSensor = (double)this.interpretSensors("distance");
        this.visualSensor = (ArrayList<ArrayList<Integer>>)this.interpretSensors("visual");
        this.gpsSensor = (ArrayList<Integer>)this.interpretSensors("gps");
        this.compassActual = (double)this.interpretSensors("compass");
        this.angularActual = (double)this.interpretSensors("angular");
        this.distanceActual = (double)this.interpretSensors("distance");
        this.gpsActual = (ArrayList<Integer>)this.interpretSensors("gps");
        System.out.println("DESPUES de actualizarse los sensores: ");
        this.showTrackingInfo();
    }

    private ArrayList<String> orientate(double angular) {
        ArrayList<String> plan = new ArrayList<String>();
        int d = 0;

        double compass = this.compassActual;
        double diff = compass > angular ? compass-angular : angular-compass;

        while (abs(diff) >= 45) {
            diff = compass > angular ? compass-angular : angular-compass;
            compass += 45.0;
            d++;

            if (compass > 180) { 
                compass = -135;
            }
        }
        
        if (d <= 4) {
            // El plan es girar a la derecha d veces
            while (d != 0) {
                plan.add("rotateR");
                d--; 
            }  
        }
        else {
            // El plan es girar a la izquierda i veces
            int i = 8 - d;

            while (i != 0) {
                plan.add("rotateL");
                i--;
            }
        }

        for(String action: plan){
            this.updateActualInfo(action);
        }

        return plan;
    }

    /**
     * Fn que contiene toda la lógica de planificación de movimientos teniendo en cuenta el entorno
     */
    private void createStrategy() {
        // orientarse, crear secuencia de acciones y añadirlas a this.actions
        nextActions.clear();
        nextActions = this.orientate(this.angularSensor);
        
        if (!hasEnoughEnergy()) {
            nextActions = this.landAgent();
        }else if(nextActions.isEmpty() && this.hasEnoughtInfo()){
            nextActions.clear();
            if(!this.objectiveReached()){
                //Mientras pueda avanzar hacia adelante se añade al plan de acciones
                while(!this.isLookingOutOfFrontier()){
                    if(this.canExecuteNextAction("moveF")){
                        nextActions.add("moveF");
                        this.updateActualInfo("moveF");
                    }else if(!this.isLookingOutOfFrontier()){
                        int z = this.gpsActual.get(2);
                        int visualHeight = this.visualSensor.get(this.xVisualNextPos).get(this.yVisualNextPos);
                        int heightDiff = z - visualHeight;
                        int counter = 0;
                        while(counter < 3 && heightDiff <= 0 && (z + (-heightDiff)) < this.maxflight && !this.isLookingOutOfFrontier()){
                            nextActions.add("moveU");
                            this.updateActualInfo("moveU");
                            z = this.gpsActual.get(2);
                            heightDiff = z - visualHeight;
                        }
                        if(heightDiff <= 0 && z == this.maxflight){
                            nextActions.clear();
                            nextActions.add("rotateL");
                            this.updateActualInfo("rotateL");
                            if(this.canExecuteNextAction("moveF")){
                                nextActions.add("moveF");
                            }
                        }
                    }
                }
            }else if(!this.isLanded()){
                nextActions = this.landAgent();
            }else{
                this.status = Status.LOGOUT;
            }
        }
        
        if(nextActions.isEmpty()){
            this.status = Status.NEEDS_INFO;
        }

        for(String action: nextActions){
            this.addAction(action);
            System.out.println("Acción añadida: " + action);
        }
    }
    
    private ArrayList<String> landAgent() {
        ArrayList<String> nextActions = new ArrayList<>();
        while (!this.isLanded()) {
            if (this.canExecuteNextAction("touchD")) {
                nextActions.add("touchD");
                this.updateActualInfo("touchD");
                break;
            }else{
                nextActions.add("moveD");
                this.updateActualInfo("moveD");
            }
        }
        return nextActions;
    }

    private boolean objectiveReached() {
        return this.onTarget;
    }

    private boolean isLanded() {
        int xVisualPos = this.gpsSensor.get(0)-this.gpsActual.get(0) + 3;
        int yVisualPos = this.gpsSensor.get(1)-this.gpsActual.get(1) + 3;
        
        int height = this.gpsActual.get(2) - this.visualSensor.get(xVisualPos).get(yVisualPos); 

        return height == 0;
    }

    /**
     * Fn que determina si el agente está en el límite de la información del lidar
     * de frente
     * 
     * @return
     */
    private boolean isLookingOutOfFrontier(){
        int x = this.gpsActual.get(0);
        int y = this.gpsActual.get(1);
        boolean outOfFrontier = false;
        String lookingAt = whereIsLooking();
        if(((x >= (this.gpsSensor.get(0)+3) || x <= (this.gpsSensor.get(0)-3)) && !lookingAt.equals("N") && !lookingAt.equals("S")) || 
           ((y >= (this.gpsSensor.get(1)+3) || y <= (this.gpsSensor.get(1)-3)) && !lookingAt.equals("W") && !lookingAt.equals("E"))){
            outOfFrontier = true;
        }
        return outOfFrontier;
    }
    
    /**
     * Fn que devuelve en String hacia donde está encarado el agente
     * @return String
     */
    private String whereIsLooking(){
        String lookingAt = "";
        switch((int)this.compassActual){
            case 0:
                lookingAt = "N";
                break;
            case 45:
                lookingAt = "NE";
                break;
            case 90:
                lookingAt = "E";
                break;
            case 135:
                lookingAt = "SE";
                break;
            case 180:
                lookingAt = "S";
                break;
            case -45:
                lookingAt = "NW";
                break;
            case -90:
                lookingAt = "W";
                break;
            case -135:
                lookingAt = "SW";
                break;
        }
        return lookingAt;
    }

    /**
     * Fn que indica si se puede o no ejecutar la siguiente acción con las actuales condiciones e información
     * @return boolean
     */
    private boolean canExecuteNextAction(String nextAction) {
        boolean canExecute = false;
        if(nextAction == null && !this.actions.isEmpty()){
            nextAction = this.actions.get(0);
        }
        int z = this.gpsActual.get(2);
        int xVisualPos = this.gpsSensor.get(0)-this.gpsActual.get(0) + 3;
        int yVisualPos = this.gpsSensor.get(1)-this.gpsActual.get(1) + 3;
        
        switch (nextAction) {
            case "moveF":
                if(!this.isLookingOutOfFrontier()){
                    this.getNextLidarPos();
                    canExecute = z >= this.visualSensor.get(this.xVisualNextPos).get(this.yVisualNextPos);
                }
                break;
            case "rotateL":
            case "rotateR":
                canExecute = true;
                break;
            case "touchD":
                if((z - this.visualSensor.get(xVisualPos).get(yVisualPos)) <= 5){
                    canExecute = true;
                }
                break;
            case "moveUp":
                if(z < this.maxflight ){
                    canExecute = true;
                }
                break;
            case "moveDown":
                if((z - this.visualSensor.get(xVisualPos).get(yVisualPos)) >= 5)
                break;
            case "readSensors":
                canExecute = true;
                break;
        }

        //Si no se puede ejecutar la siguiente acción indicamos que hay que crear un nuevo plan de acciones
        if(!canExecute){
            this.needsNewActionPlan = true;
        }
        
        return canExecute; 
    }

    /**
     * Fn que determina si tiene suficiente información para planificar y si no setea el estado para leer sensores
     * @return 
     */
    private boolean hasEnoughtInfo() {
        // determinar si se tiene suficiente información para elaborar un plan o no
        // Si está mirando hacia un borde del lidar no tiene info suficiente.
        boolean response = this.isLookingOutOfFrontier();
        this.needsInfo = response;
        return !response; 
    }

    private boolean hasEnoughEnergy() {
        int height;
        boolean enough = true;

        int xVisualPos = this.gpsSensor.get(0)-this.gpsActual.get(0) + 3;
        int yVisualPos = this.gpsSensor.get(1)-this.gpsActual.get(1) + 3;
        
        height = this.gpsActual.get(2) - this.visualSensor.get(xVisualPos).get(yVisualPos); // Altura del dron - Altura del terreno debajo de él
        
        if (height + 10 >= this.energy) {
            enough = false;
        }

        return enough;
    }

    private void setEnergy(String action) {
        switch (action) {
            case "moveF":
            case "rotateL":
            case "rotateR":
                this.energy--;
                break;
            case "touchD":
                // Poner el índice de la altura, MODIFICAR
                this.energy -= this.perceptions.get(0).asInt();
                break;
            case "moveUp":
            case "moveDown":
                this.energy -= 5;
                break;
            case "readSensors":
                this.energy -= this.sensores.length;
                break;
        }
    }

    /**
     * Fn que actualiza cual sería la siguiente posición del lidar si avanzamos con la orientación actual
     */
    private void getNextLidarPos() {
        int xVisualPos = this.gpsSensor.get(0)-this.gpsActual.get(0) + 3;
        int yVisualPos = this.gpsSensor.get(1)-this.gpsActual.get(1) + 3;
        String lookingAt = this.whereIsLooking();
        
        switch(lookingAt){
            case "N":
                this.xVisualNextPos = xVisualPos;
                this.yVisualNextPos = yVisualPos-1;
                break;
            case "NE":
                this.xVisualNextPos = xVisualPos+1;
                this.yVisualNextPos = yVisualPos-1;
                break;
            case "E":
                this.xVisualNextPos = xVisualPos+1;
                this.yVisualNextPos = yVisualPos;
                break;
            case "SE":
                this.xVisualNextPos = xVisualPos+1;
                this.yVisualNextPos = yVisualPos+1;
                break;
            case "S":
                this.xVisualNextPos = xVisualPos;
                this.yVisualNextPos = yVisualPos+1;
                break;
            case "NW":
                this.xVisualNextPos = xVisualPos-1;
                this.yVisualNextPos = yVisualPos-1;
                break;
            case "W":
                this.xVisualNextPos = xVisualPos-1;
                this.yVisualNextPos = yVisualPos;
                break;
            case "SW":
                this.xVisualNextPos = xVisualPos-1;
                this.yVisualNextPos = yVisualPos+1;
                break;
        }
    }

    /**
     * Actualiza la información de los sensores locales para poder planificar varios movimientos
     * @param action 
     */
    private void updateActualInfo(String action) {
        /*
        Sensores locales que hay que actualizar
            compassActual
            angularActual 
            distanceActual
            gpsActual
        */
        switch(action){
            case "moveF":
                //Actualizar distancia al objetivo
                this.setActualDistance();
                //Actualizar gpsActual
                this.setActualGPS();
                break;
            case "rotateL":
                if(this.compassActual <= -135){
                    this.compassActual = 180;
                }else{
                    this.compassActual -= 45;
                }
                break;
            case "rotateR":
                if(this.compassActual >= 185){
                    this.compassActual = -135;
                }else{
                    this.compassActual += 45;
                }
                break;
            case "touchD":
                //Aterriza y está en z 0
                int xVisualPos = this.gpsSensor.get(0)-this.gpsActual.get(0) + 3;
                int yVisualPos = this.gpsSensor.get(1)-this.gpsActual.get(1) + 3;
                int actualHeight = this.visualSensor.get(xVisualPos).get(yVisualPos);
                this.gpsActual.set(2, actualHeight);
                break;
            case "moveUp":
                actualHeight = this.gpsActual.get(2);
                this.gpsActual.set(2, actualHeight+5);
                break;
            case "moveDown":
                actualHeight = this.gpsActual.get(2);
                this.gpsActual.set(2, actualHeight-5);
                break;
        }
        this.showTrackingInfo();
    }
    
    private void showTrackingInfo(){
        String gps_msg = "GPS Actual: [" + this.gpsActual.get(0) + ", " + this.gpsActual.get(1) + ", " + this.gpsActual.get(2) + "] \n" + "GPS Sensores: [" + this.gpsSensor.get(0) + ", " + this.gpsSensor.get(1) + ", " + this.gpsSensor.get(2) + "] \n";
        String visual_msg = "Visual Sensor: \n";
        for(ArrayList<Integer> row: this.visualSensor){
            visual_msg += "[ ";
            for(int value: row){
                visual_msg += value + ", ";
            }
            visual_msg += "]\n ";
        }
        visual_msg += "\n";
        String compass_msg = "Compass Actual: [" + this.compassActual + "] \n" + "Compass Sensores: [" + this.compassSensor + "] \n";
        String angular_msg = "Angular Actual: [" + this.angularActual + "] \n" + "Angular Sensores: [" + this.angularSensor + "] \n";
        String distance_msg = "Distance Actual: [" + this.distanceActual + "] \n" + "Distance Sensores: [" + this.distanceSensor + "] \n";
        
        System.out.println(gps_msg + visual_msg + compass_msg + angular_msg + distance_msg);
    }

    /**
     * Actualiza la posición en el GPS según se haya movido y estuviese mirando, se llama cuando se hace un moveF solo
     */
    private void setActualGPS() {
        String lookingAt = this.whereIsLooking();
        switch(lookingAt){
            case "N":
                this.gpsActual.set(1, this.gpsActual.get(1)-1);
                break;
            case "NE":
                this.gpsActual.set(0, this.gpsActual.get(0)+1);
                this.gpsActual.set(1, this.gpsActual.get(1)-1);
                break;
            case "E":
                this.gpsActual.set(0, this.gpsActual.get(0)+1);
                break;
            case "SE":
                this.gpsActual.set(0, this.gpsActual.get(0)+1);
                this.gpsActual.set(1, this.gpsActual.get(1)+1);
                break;
            case "S":
                this.gpsActual.set(1, this.gpsActual.get(1)+1);
                break;
            case "NW":
                this.gpsActual.set(0, this.gpsActual.get(0)-1);
                this.gpsActual.set(1, this.gpsActual.get(1)-1);
                break;
            case "W":
                this.gpsActual.set(0, this.gpsActual.get(0)-1);
                break;
            case "SW":
                this.gpsActual.set(0, this.gpsActual.get(0)-1);
                this.gpsActual.set(1, this.gpsActual.get(1)+1);
                break;
        }
    }

    /**
     * Actualiza la distancia al objetivo según se haya movido y estuviese mirando, solo se llama con moveF
     */
    private void setActualDistance() {
        if(this.angularActual == this.compassActual){
            this.distanceActual--;
        }else{
            if(this.angularActual > compassActual){
                //Si está a más de un giro, se aleja
                if(abs(this.angularActual - this.compassActual) > 45){
                    this.distanceActual++;
                }else{
                    this.distanceActual-= 0.5;
                }
            }else{
                //Si está a más de un giro, se aleja
                if(abs(this.compassActual - this.angularActual) > 45){
                    this.distanceActual++;
                }else{
                    this.distanceActual-= 0.5;
                }
            }
        }
    }
}
