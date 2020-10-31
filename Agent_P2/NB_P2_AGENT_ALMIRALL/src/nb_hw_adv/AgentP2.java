package nb_hw_adv;

import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import ControlPanel.TTYControlPanel;
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
    String world = "BasePlayground";
    String[] sensores = { "alive", "ontarget", "compass", "angular", "distance", "visual", "gps" };
    // General use variables
    JsonArray perceptions;
    Status status;
    ACLMessage current;
    ArrayList<String> actions;
    boolean needsNewActionPlan;
    private boolean needsInfo;
    // Control variables
    int energy, xLidarNextPos, yLidarNextPos;
    double compassSensor;
    double distanceSensor;
    double angularSensor;
    ArrayList<ArrayList<Integer>> visualSensor;
    ArrayList<Integer> gpsSensor;
    double compassActual;
    double angularActual;
    double distanceActual;
    ArrayList<ArrayList<Integer>> visualActual;
    ArrayList<Integer> gpsActual;

    @Override
    public void setup() {
        super.setup();
        this.doCheckinPlatform();
        this.doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        myControlPanel = new TTYControlPanel(getAID());
        status = Status.LOGIN;
        needsNewActionPlan = false;
        needsInfo = true;
        energy = 1000;
        _exitRequested = false;
    }

    @Override
    public void plainExecute() {
        switch (status) {
            case LOGIN:
                current = this.makeLogin();
                status = Status.NEEDS_INFO;
                break;
            case NEEDS_INFO:
                current = this.readSensors(current);
                this.needsInfo = false;
                if (!this.hasActions() || this.needsNewActionPlan || !this.canExecuteNextAction()) {
                    status = Status.PLANNING;
                }
                break;
            case HAS_ACTIONS:
                if (this.hasActions() && this.canExecuteNextAction() && !this.needsNewActionPlan) {
                    this.executeAction(current, this.actions.get(0));
                    current = this.retrieveMessage();
                    if (current != null) {
                        this.setEnergy(this.actions.get(0));
                        this.removeFirstAction();
                    }
                } else {
                    status = Status.PLANNING;
                }
                break;
            case PLANNING:
                // Hay que ver cuando es necesario modificar needsInfo para evitar que no
                // planifique sin información suficiente
                if (this.needsInfo || !this.hasEnoughtInfo()) {
                    status = Status.NEEDS_INFO;
                } else {
                    this.createStrategy();
                    status = Status.HAS_ACTIONS;
                }
                break;
            case LOGOUT:
                _exitRequested = true;
                break;
        }
        this.showInfo(current);
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
        sensors = new JsonArray();
        for (String sensor : sensores) {
            sensors.add(sensor);
        }

        // Adding array to JSON body
        login_json.add("attach", sensors);
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
        String result = this.getStringContent(in, "result");
        if (result.equals("ok")) {
            key = this.getStringContent(in, "key");
            width = this.getIntContent(in, "width");
            height = this.getIntContent(in, "height");
            maxflight = this.getIntContent(in, "maxflight");
            options = this.getJsonArrayContent(in, "capabilities");
        }

        return in;
    }

    private ACLMessage makeLogin() {
        JsonObject login_json = this.generateLogin(this.world, this.sensores);
        this.sendMessage(receiver, login_json);
        return this.retrieveLoginMessage();
    }

    private ACLMessage readSensors(ACLMessage in) {
        JsonObject read_sensors_json = new JsonObject();
        read_sensors_json.add("command", "read");
        read_sensors_json.add("key", key);
        this.replyMessage(in, read_sensors_json);

        ACLMessage reply = this.blockingReceive();
        String result = getStringContent(reply, "result");
        if (result.equals("ok")) {
            JsonObject details = getJsonContent(reply, "details");
            perceptions = details.get("perceptions").asArray();
            this.setEnergy("readSensors");
        } else {
            System.out.println("[SENSORS] Error: " + result);
        }

        this.updateSensorsInfo();
        return reply;
    }

    public void executeAction(ACLMessage msg, String action) {
        JsonObject execute_json = new JsonObject();
        execute_json.add("command", "execute");
        execute_json.add("action", action);
        execute_json.add("key", key);

        this.replyMessage(msg, execute_json);
    }

    public void replyMessage(ACLMessage in, Object content) {
        String msg = "";
        // Parseo del contenido a String para añadirlo al mensaje
        if (content instanceof JsonObject) {
            msg = ((JsonObject) content).asString();
        } else if (content instanceof String) {
            msg = (String) content;
        } else {
            System.out.println("[Reply] Formato de contenido no válido.");
        }

        ACLMessage out = in.createReply();
        out.setContent(msg);
        this.sendServer(out);
    }

    public ACLMessage retrieveMessage() {
        ACLMessage in = this.blockingReceive();
        String result = getStringContent(in, "result");
        if (result.equals("ok")) {
            return in;
        } else {
            return null;
        }
    }

    public String getStringContent(ACLMessage msg, String field) {
        return Json.parse(msg.getContent()).asObject().get(field).toString();
    }

    public JsonObject getJsonContent(ACLMessage msg, String field) {
        return Json.parse(msg.getContent()).asObject().get(field).asObject();
    }

    public JsonObject getJsonContent(ACLMessage msg) {
        return Json.parse(msg.getContent()).asObject();
    }

    public int getIntContent(ACLMessage msg, String field) {
        return Json.parse(msg.getContent()).asObject().get(field).asInt();
    }

    public JsonArray getJsonArrayContent(ACLMessage msg, String field) {
        return Json.parse(msg.getContent()).asObject().get(field).asArray();
    }

    public void logoutAgent() {
        JsonObject logout_json = new JsonObject();
        logout_json.add("command", "logout");

        this.sendMessage(receiver, logout_json);
    }

    private void removeFirstAction() {
        this.actions.remove(0);
    }

    private void addAction(String action) {
        this.actions.add(action);
    }

    private boolean hasActions() {
        return this.actions.size() > 0;
    }

    private void showInfo(ACLMessage in) {
        myControlPanel.feedData(in, width, height, maxflight); // width height maxflight obtenidos en el login
        myControlPanel.fancyShow();
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
        
                for(int i=0; i<visualData.size(); ++i) {
                    JsonArray array =  visualData.get(i).asArray();
                    ArrayList<Integer> element = new ArrayList<>();
                    for(int j=0; j<array.size(); ++j) {
                        element.add(array.get(j).asInt());
                    }
                    elevations.set(i, element);
                }

                return elevations;
            case "gps":
                JsonArray gpsData = this.perceptions.get(5).asObject().get("data").asArray().get(0).asArray();
                ArrayList<Integer> coordenadas = new ArrayList<>();

                for(int i=0; i<gpsData.size(); ++i) {
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
        compassSensor = (double)this.interpretSensors("compass");
        angularSensor = (double)this.interpretSensors("compass");
        distanceSensor = (double)this.interpretSensors("compass");
        visualSensor = (ArrayList<ArrayList<Integer>>)this.interpretSensors("compass");
        gpsSensor = (ArrayList<Integer>)this.interpretSensors("compass");
        compassActual = (double)this.interpretSensors("compass");
        angularActual = (double)this.interpretSensors("compass");
        distanceActual = (double)this.interpretSensors("compass");
        visualActual = (ArrayList<ArrayList<Integer>>)this.interpretSensors("compass");
        gpsActual = (ArrayList<Integer>)this.interpretSensors("compass");
    }

    private ArrayList<String> orientate() {
        ArrayList<String> plan = new ArrayList<>();

        double compass = (double)this.interpretSensors("compass");
        double angular = (double)this.interpretSensors("angular");

        if (compass != angular) {
            
            double ax = 0.0;

            while (compass != angular) {
                if(compass > angular){
                    compass -= 45.0;
                    double resultado = compass / 45;
                    
                    if(resultado > ax){

                    }else{
                        plan.add("rotateL");
                    }
                    //añadir accion rotar L
                }else{
                    compass += 45.0;
                    plan.add("rotateR");
                    //
                }
            }
            
        }

        return plan;
    }

    private void createStrategy() {
        // orientarse, crear secuencia de acciones y añadirlas a
        // this.actions
    }
    
    private boolean isLookingOutOfFrontier(){
        int x = gpsActual.get(0);
        int y = gpsActual.get(1);
        boolean outOfFrontier = false;
        String lookingAt = whereIsLooking();
        if(((x >= (gpsSensor.get(0)+3) || x <= (gpsSensor.get(0)-3)) && !lookingAt.equals("N") && !lookingAt.equals("S")) || 
           ((y >= (gpsSensor.get(1)+3) || y <= (gpsSensor.get(1)-3)) && !lookingAt.equals("W") && !lookingAt.equals("E"))){
            this.needsInfo = true;
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
        switch((int)compassActual){
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
    private boolean canExecuteNextAction() {
        ArrayList<ArrayList<Integer>> visual = (ArrayList<ArrayList<Integer>>)this.interpretSensors("visual");
        ArrayList<Integer> gps = (ArrayList<Integer>)this.interpretSensors("gps");
        String nextAction = actions.get(0);
        int z = gpsActual.get(2);
        int xLidarPos = gpsSensor.get(0)-gpsActual.get(0) + 3;
        int yLidarPos = gpsSensor.get(1)-gpsActual.get(1) + 3;
        boolean canExecute = false;
        
        switch (nextAction) {
            case "moveF":
                if(!this.isLookingOutOfFrontier()){
                    this.getNextLidarPos();
                    canExecute = z > visual.get(xLidarNextPos).get(yLidarNextPos);
                }
                break;
            case "rotateL":
            case "rotateR":
                canExecute = true;
                break;
            case "touchD":
                if((z - visual.get(xLidarPos).get(yLidarPos)) <= 5){
                    canExecute = true;
                }
                break;
            case "moveUp":
                if(z < this.maxflight ){
                    canExecute = true;
                }
                break;
            case "moveDown":
                if((z - visual.get(xLidarPos).get(yLidarPos)) >= 5)
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

    private boolean hasEnoughtInfo() {
        // determinar si se tiene suficiente información para elaborar un plan o no
        return true; // provisional
    }

    private boolean hasEnoughEnergy() {
        ArrayList<ArrayList<Integer>> visual = (ArrayList<ArrayList<Integer>>)this.interpretSensors("visual");
        ArrayList<Integer> gps = (ArrayList<Integer>)this.interpretSensors("gps");
        int height, rest, energyNeeded;

        energy -= 2; // Lectura de sensores
        
        height = gps.get(2) - visual.get(3).get(3); // Altura del dron - Altura del terreno debajo de él
        rest = height % 5; // Los metros que haría touch down
        energyNeeded = ((height-rest)*5) + rest; // Energía necesitada para aterrizar, 5 por cada "move down" y 1 por cada metro de altura que queda (rest) para "touch down"
        
        if (energyNeeded == energy) {
            
        }

        return true; // provisional
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
        int xLidarPos = gpsSensor.get(0)-gpsActual.get(0) + 3;
        int yLidarPos = gpsSensor.get(1)-gpsActual.get(1) + 3;
        String lookingAt = this.whereIsLooking();
        
        switch(lookingAt){
            case "N":
                xLidarNextPos = xLidarPos;
                yLidarNextPos = yLidarPos-1;
                break;
            case "NE":
                xLidarNextPos = xLidarPos+1;
                yLidarNextPos = yLidarPos-1;
                break;
            case "E":
                xLidarNextPos = xLidarPos+1;
                yLidarNextPos = yLidarPos;
                break;
            case "SE":
                xLidarNextPos = xLidarPos+1;
                yLidarNextPos = yLidarPos+1;
                break;
            case "S":
                xLidarNextPos = xLidarPos;
                yLidarNextPos = yLidarPos+1;
                break;
            case "NW":
                xLidarNextPos = xLidarPos-1;
                yLidarNextPos = yLidarPos-1;
                break;
            case "W":
                xLidarNextPos = xLidarPos-1;
                yLidarNextPos = yLidarPos;
                break;
            case "SW":
                xLidarNextPos = xLidarPos-1;
                yLidarNextPos = yLidarPos+1;
                break;
        }
    }
}
