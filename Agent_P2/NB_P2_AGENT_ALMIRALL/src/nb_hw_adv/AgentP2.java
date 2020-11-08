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
    String world = "World6";
    String[] sensores = { "alive", "ontarget", "compass", "angular", "distance", "visual", "gps" };
    // General use variables
    JsonArray perceptions;
    Status status;
    ACLMessage current;
    ArrayList<String> actions;
    boolean needsNewActionPlan;
    private boolean needsInfo;
    // Control variables
    int energy, xVisualAuxActualPos, yVisualAuxActualPos, xVisualPosActual, yVisualPosActual;
    boolean aliveSensor;
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
    boolean esquivando;
    private boolean borderOfMap;
    boolean pathMap[][];

    /**
     * Inicializa los sensores del agente, el panel de control, la conexión con LARVA y variables necesarias para su funcionamiento
     * @author 
     */
    @Override
    public void setup() {
        super.setup();
        this.doCheckinPlatform();
        this.doCheckinLARVA();
        this.perceptions = new JsonArray();
        this.actions = new ArrayList<>();
        this.visualSensor = new ArrayList<>();
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
        this.esquivando = false;
        this.perceptionsAuxiliar = new JsonArray();
        this._exitRequested = false;        
    }

    /**
     * Decide la siguiente acción del agente según su estado.
     * Estado LOGIN - Estado inicial. Conecta el agente con el servidor y pasa a estado NEEDS_INFO.
     * Estado NEEDS_INFO - Lee los sensores. Si ha llegado al objetivo, pasa a estado LOGOUT, si no pasa a estado PLANNING.
     * Estado HAS_ACTIONS - Si tiene un plan de acciones, las ejecuta. Si se queda sin plan, pasa a estado PLANNING.
     * Estado PLANNING - Se crea un plan de acciones para el agente. Si el plan de acciones está vacío o el agente está en el objetivo pasa a estado NEEDS_INFO, si no pasa a HAS_ACTIONS.
     * Estado LOGOUT - El agente manda un mensaje de LOGOUT al servidor y se desconecta.
     * @author 
     */
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
                if(this.objectiveReached() && this.isLanded()){
                    this.status = Status.LOGOUT;
                }else{
                    this.status = Status.PLANNING;            
                }
                this.showInfo(this.current);
                break;
            case HAS_ACTIONS:
                this.executeAction(this.current, this.actions.get(0));
                this.current = this.blockingReceive();
                this.removeFirstAction();
                if(this.actions.isEmpty()){
                    this.status = Status.PLANNING;
                }
                break;
            case PLANNING:
                // Hay que ver cuando es necesario modificar needsInfo para evitar que no
                // planifique sin información suficiente
                if(this.aliveSensor) {
                    this.createStrategy();
                    if(!this.actions.isEmpty() && !this.onTarget){
                        this.status = Status.HAS_ACTIONS;
                    }
                    else{
                        this.status = Status.NEEDS_INFO;
                    }
                }
                else {
                    this.status = Status.LOGOUT;
                }
                break;
            case LOGOUT:
                this._exitRequested = true;
                break;
            default:
                System.out.println("Chungo!");
                break;
        }
    }

    /**
     * Manda un mensaje de log out al servidor LARVA
     * @author 
     */
    @Override
    protected void takeDown() {
        //this.myControlPanel.close();
        this.logoutAgent();
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }

    /**
     * Genera el mensaje de login al servidor, el mundo que va a explorar el agente y los sensores que va a utilizar.
     * @author 
     * @param world nombre del mundo que va a explorar el agente.
     * @param sensores lista de sensores que va a usar el agente.
     * @return el mensaje completo para el servidor.
     */
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

    /**
     * Manda un mensaje a receiver con el contenido de body.
     * @author 
     * @param receiver nombre de quien recibe el mensaje
     * @param body contenido del mensaje
     */
    public void sendMessage(String receiver, JsonObject body) {
        ACLMessage out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        out.setContent(body.toString());
        this.send(out);
    }

    /**
     * Recibe el mensaje de login y lo parsea, obteniendo la clave, la anchura y altura del mapa, la altura máxima a la que puede volar el agente y las acciones que puede realizar.
     * @author 
     * @return el mensaje completo de login
     */
    private ACLMessage retrieveLoginMessage() {
        ACLMessage in = this.blockingReceive();
        //System.out.println("LOGIN MENSAJE ------------------ " +in);
        this.key = this.getStringContent(in, "key").replaceAll("\\\"", "");
        this.width = this.getIntContent(in, "width");
        this.height = this.getIntContent(in, "height");
        this.maxflight = this.getIntContent(in, "maxflight");
        System.out.println("ALTURA MAXIMA "+this.maxflight);
        this.options = this.getJsonArrayContent(in, "capabilities");
        this.pathMap = new boolean[width][height];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                this.pathMap[i][j] = false;
            }
        }

        return in;
    }

    /**
     * Manda el mensaje de login y recibe la respuesta (contiene toda la información de {@link #retrieveLoginMessage})
     * @author 
     * @return mensaje de respuesta al login
     */
    private ACLMessage makeLogin() {
        JsonObject login_json = new JsonObject(this.generateLogin(this.world, this.sensores));
        this.sendMessage(this.receiver, login_json);
        return this.retrieveLoginMessage();
    }

    /**
     * Hace una petición para leer los sensores
     * @author 
     * @param in cabecera del mensaje
     * @return información de los sensores
     */
    private ACLMessage readSensors(ACLMessage in) {
        JsonObject read_sensors_json = new JsonObject();
        read_sensors_json.add("command", "read");
        read_sensors_json.add("key", this.key);
        this.replyMessage(in, read_sensors_json);

        ACLMessage reply = this.blockingReceive();
        JsonObject replyObj = new JsonObject(Json.parse(reply.getContent()).asObject());
        if (replyObj.get("result").asString().contains("ok")) {
            this.perceptions = new JsonArray(replyObj.get("details").asObject().get("perceptions").asArray());
            this.setEnergy("readSensors");
            //System.out.println("Se actualiza sensores");
            this.updateSensorsInfo();
        } else {
            //System.out.println("[SENSORS] Error: " + reply);
        }

        return reply;
    }

    /**
     * Manda un mensaje con la siguiente acción a realizar
     * @author
     * @param msg cabecera del mensaje
     * @param action acción a realizar
     */
    public void executeAction(ACLMessage msg, String action) {
        JsonObject execute_json = new JsonObject();
        execute_json.add("command", "execute");
        execute_json.add("action", action);
        execute_json.add("key", this.key);
        this.replyMessage(msg, execute_json);
    }

    /**
     * Manda un mensaje de respuesta al servidor
     * @author
     * @param in cabecera del mensaje
     * @param content contenido del mensaje
     */
    public void replyMessage(ACLMessage in, JsonObject content) {
        // Parseo del contenido a String para añadirlo al mensaje        

        ACLMessage out = in.createReply();
        out.setContent(content.toString());
        this.sendServer(out);
    }

    /**
     * Recibe el mensaje de respuesta del servidor
     * @author
     * @return devuelve la respuesta si result == ok, si no devuelve un null
     */
    public ACLMessage retrieveMessage() {
        ACLMessage in = this.blockingReceive();
        String result = getStringContent(in, "result").replaceAll("\\\"", "");
        if (result.equals("ok")) {
            return in;
        } else {
            return null;
        }
    }

    /**
     * Obtiene la información del campo elegido del mensaje como String
     * @author
     * @param msg mensaje a parsear
     * @param field campo buscado
     * @return contenido del campo como String
     */
    public String getStringContent(ACLMessage msg, String field) {
        JsonObject res = new JsonObject(Json.parse(msg.getContent()).asObject());
        return res.get(field).toString();
    }

    /**
     * Obtiene la información del campo elegido del mensaje como JsonObject
     * @author
     * @param msg mensaje a parsear
     * @param field campo buscado
     * @return contenido del campo como JsonObject
     */
    public JsonObject getJsonContent(ACLMessage msg, String field) {
        JsonObject res = new JsonObject(Json.parse(msg.getContent()).asObject());
        return new JsonObject(res.get(field).asObject());
    }

    /**
     * Obtiene la información del mensaje como JsonObject
     * @author
     * @param msg mensaje a parsear
     * @return contenido del mensaje como JsonObject
     */
    public JsonObject getJsonContent(ACLMessage msg) {
        JsonObject res = new JsonObject(Json.parse(msg.getContent()).asObject());
        return new JsonObject(res);
    }

    /**
     * Obtiene la información del campo elegido del mensaje como int
     * @author
     * @param msg mensaje a parsear
     * @param field campo buscado
     * @return contenido del campo como int
     */
    public int getIntContent(ACLMessage msg, String field) {
        JsonObject res = new JsonObject(Json.parse(msg.getContent()).asObject());
        return res.get(field).asInt();
    }

    /**
     * Obtiene la información del campo elegido del mensaje como array
     * @author
     * @param msg mensaje a parsear
     * @param field campo buscado
     * @return contenido del campo como array
     */
    public JsonArray getJsonArrayContent(ACLMessage msg, String field) {
        JsonObject res = new JsonObject(Json.parse(msg.getContent()).asObject());
        return new JsonArray(res.get(field).asArray());
    }

    /**
     * Manda un mensaje de logout al servidor
     * @author
     */
    public void logoutAgent() {
        JsonObject logout_json = new JsonObject();
        logout_json.add("command", "logout");

        this.sendMessage(this.receiver, logout_json);
    }

    /**
     * Elimina la primera acción del plan de acciones
     * @author
     */
    private void removeFirstAction() {
            if(this.actions.size() > 1){
                this.actions.remove(0);
            }
            else{
                this.actions.clear();
            }    
    }

    /**
     * Añade una acción al plan de acciones
     * @author
     * @param action acción a añadir
     */
    private void addAction(String action) {
        this.actions.add(action);
    }

    /**
     * Comprueba si el plan de acciones está vacío o no
     * @author
     * @return true si tiene acciones, false si está vacío
     */
    private boolean hasActions() {
        return !this.actions.isEmpty();
    }

    /**
     * Muestra la información en un panel de control
     * @author
     * @param in mensaje con la información de los sensores
     */
    private void showInfo(ACLMessage in) {
        //Index 0 out of bounds for length 0
        this.myControlPanel.feedData(in, width, height, maxflight); // width height maxflight obtenidos en el login
        this.myControlPanel.fancyShow();
    }

    /**
     * Obtiene la informacion de un sensor
     * @author Diego
     * @param  sensor sensor a consultar
     * @return objeto con la informacion del sensor indicado
     */
    private Object interpretSensors(String sensor) {
        if(!this.perceptions.isEmpty()) {
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

                    for(JsonValue value: visualData) {
                        JsonArray array = new JsonArray(value.asArray());
                        ArrayList<Integer> element = new ArrayList<>();
                        for(int j=0; j<array.size(); j++) {
                            element.add(array.get(j).asInt());
                        }
                        elevations.add(element);
                    }

                    return elevations;
                case "gps":
                    JsonArray gpsData = this.perceptions.get(6).asObject().get("data").asArray().get(0).asArray();
                    ArrayList<Integer> coordenadas = new ArrayList<>();

                    for(int i=0; i<gpsData.size(); i++) {
                        coordenadas.add(gpsData.get(i).asInt());
                    }

                    return coordenadas;
            }
        }
        
        return null;
    }
    
    /**
     * Actualiza la información local de los sensores y el agente
     * @author
     */
    private void updateSensorsInfo(){
        this.xVisualPosActual = 3;
        this.yVisualPosActual = 3;
        this.aliveSensor = (boolean) this.interpretSensors("alive");
        this.onTarget = (boolean) this.interpretSensors("ontarget");
        this.compassSensor = (double)this.interpretSensors("compass");
        this.angularSensor = (double)this.interpretSensors("angular");
        this.distanceSensor = (double)this.interpretSensors("distance");
        this.visualSensor = (ArrayList<ArrayList<Integer>>)this.interpretSensors("visual");
        this.gpsSensor = (ArrayList<Integer>)this.interpretSensors("gps");
        this.compassActual = this.compassSensor;
        this.angularActual = this.angularSensor;
        this.distanceActual = this.distanceSensor;
        this.gpsActual = this.gpsSensor;
    }

    /**
     * Orienta el agente hasta que el compass apunte en la misma dirección que el angular
     * @author Luis Escobar Reche
     * @param angular sensor angular
     * @return plan de acciones para orientar al agente
     */
    private ArrayList<String> orientate(double angular) {
        ArrayList<String> plan = new ArrayList<>();
        int d = 0;

        double compass = this.compassActual;
        double aux;
        
        //System.out.println("ANTES DEL RECALCULO: angular: "+angular+" compass: "+compass);
        
        if(angular % 45 != 0){
            aux = angular / 45;
            aux = Math.round(aux);
            angular = aux * 45;
            
            if(angular == -180)
                angular = 180;
        }
        
        if(compass % 45 != 0){
            aux = compass / 45;
            aux = Math.round(aux);
            compass = aux * 45;
        }

        while (compass != angular) {
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

        //System.out.println("------------- GIROS: sensores: "+angular+" Compass Actual: "+compass);
        for(String action: plan){
            this.updateActualInfo(action);
        }

        return plan;
    }

    /**
     * Contiene toda la lógica de planificación de movimientos teniendo en cuenta el entorno
     * @author
     */
    private void createStrategy() {
        // orientarse, crear secuencia de acciones y añadirlas a this.actions
        nextActions.clear();
        int count = 0;
        //Si no tengo suficiente energia voy al suelo y recargo
        if(!this.hasEnoughEnergy()) {
            nextActions = this.landAgent();
            nextActions.add("recharge");
            System.out.println("VA A BAJAR CON "+nextActions.size()+" MOVIMIENTOS");
            this.updateActualInfo("recharge");
        }
        //Si tengo suficiente energia
        else {
            //Si esta esquivando
            if(this.esquivando) {
                System.out.println("ESQUIVAR");
                boolean canRotateR = true;
                int [] visualPosR = this.getNextVisualPos(this.xVisualPosActual, this.yVisualPosActual, this.whereIsGoingToLook("rotateR"));
                
                if(visualPosR[0] < 7 && visualPosR[0] >= 0 && visualPosR[1] < 7 && visualPosR[1] >= 0) {
                    int [] nextPos = this.getNextPos(this.gpsActual.get(0), this.gpsActual.get(1), this.whereIsGoingToLook("rotateR"));
                    if(this.visualSensor.get(visualPosR[1]).get(visualPosR[0]) >= this.maxflight && this.pathMap[nextPos[0]][nextPos[1]]) {
                        canRotateR = false;
                    }
                }
                
                boolean canRotateL = true;
                int [] visualPosL = this.getNextVisualPos(this.xVisualPosActual, this.yVisualPosActual, this.whereIsGoingToLook("rotateL"));
                
                if(visualPosL[0] < 7 && visualPosL[0] >= 0 && visualPosL[1] < 7 && visualPosL[1] >= 0) {
                    int [] nextPos = this.getNextPos(this.gpsActual.get(0), this.gpsActual.get(1), this.whereIsGoingToLook("rotateL"));
                    if((this.visualSensor.get(visualPosL[1]).get(visualPosL[0]) >= this.maxflight) && this.pathMap[nextPos[0]][nextPos[1]]) {
                        canRotateL = false;
                    }
                }
                
                //Comprobar a donde gira
                if (canRotateR && canRotateL) {
                    nextActions.add("rotateR");
                    nextActions.add("moveF");

                    this.esquivando = false;
                }
                else{
                    if (canRotateL && !canRotateR){
                        nextActions.add("rotateL");
                        nextActions.add("moveF");

                        this.esquivando = false;
                    }
                    if (canRotateR && !canRotateL){
                        nextActions.add("rotateR");
                        nextActions.add("moveF");

                        this.esquivando = false;
                    }
                    if (!canRotateL && !canRotateR){
                        nextActions.add("rotateR");
                    }
                }

                for(String action: nextActions){
                    this.updateActualInfo(action);
                }
            }
            else {
                //Si no he encontrado el objetivo
                if(!this.objectiveReached()){
                    nextActions = this.orientate(this.angularSensor);
                    int [] visualNextPos = this.getNextVisualPos(this.xVisualPosActual, this.yVisualPosActual, this.whereIsLooking());
                    //Si la siguiente casilla esta dentro de la malla de visual
                    if(visualNextPos[0] < 7 && visualNextPos[0] >= 0 && visualNextPos[1] < 7 && visualNextPos[1] >= 0){
                        //Si la siguiente casilla no se sale del mapa
                        if(this.visualSensor.get(visualNextPos[1]).get(visualNextPos[0]) >= 0){
                            int z = this.gpsActual.get(2);
                            
                            if((z+5) >= this.maxflight && !this.canExecuteNextAction("moveF")) {
                                System.out.println("Voy a esquivar");
                                this.esquivando = true;
                            }
                            //Si la altura del dron es menor que la siguiente casilla
                            else if(z < this.visualSensor.get(visualNextPos[1]).get(visualNextPos[0])) {
                                //Mientras no pueda avanzar y mi altura+5 sea menor que la maxima, asciendo
                                while(!this.canExecuteNextAction("moveF") && (z+5) < this.maxflight && count < 3) {
                                    System.out.println("QUIERE SUBIR : Altura->"+ z);
                                    nextActions.add("moveUP");
                                    this.updateActualInfo("moveUP");
                                    count++;
                                }
                            }
                            //Si la altura de la siguiente casilla es igual o menor, avanzo
                            else {
                                while(count < 3) {
                                    count++;
                                    // Si puedo avanzar y la distancia con el objetivo es mayor o igual que 1
                                    if(this.canExecuteNextAction("moveF") && this.distanceActual >= 1) {
                                        nextActions.add("moveF");
                                        this.updateActualInfo("moveF");
                                        this.pathMap[this.gpsActual.get(0)][this.gpsActual.get(1)] = true;
                                    }
                                }
                            }
                            count = 0;
                        }
                    }
                }
                //Si he encontrado el objetivo, bajo
                else if(!this.isLanded()){
                    nextActions = this.landAgent();
                }
                
                //Si no tengo acciones en el plan, necesito informacion
                if(nextActions.isEmpty()){
                    this.status = Status.NEEDS_INFO;
                }
            } 
        }

        for(String action: nextActions){
            this.addAction(action);
            //System.out.println("Acción añadida: " + action);
        }
    }
    
    /**
     * Aterrizaje del agente
     * @author
     * @return plan de acciones para aterrizar al agente
     */
    private ArrayList<String> landAgent() {
        ArrayList<String> landActions = new ArrayList<>();
        while (!this.isLanded()) {
            if (this.canExecuteNextAction("touchD")) {
                landActions.add("touchD");
                this.updateActualInfo("touchD");
                break;
            }else{
                landActions.add("moveD");
                this.updateActualInfo("moveD");
            }
        }
        return landActions;
    }

    /**
     * Comprueba si se ha llegado al objetivo
     * @author
     * @return true si se encuentra en el objetivo o está a menos de 1 de distancia, false en otro caso
     */
    private boolean objectiveReached() {
        return this.onTarget || this.distanceActual < 1;
    }

    /**
     * Comprueba si el agente se encuentra en tierra
     * @author
     * @return true si la altura entre el agente y el suelo es menor o igual a 0, false en otro caso
     */
    private boolean isLanded() {
        return (this.gpsActual.get(2) - this.visualSensor.get(this.xVisualPosActual).get(this.yVisualPosActual)) <= 0;
    }

    /**
     * Determina si el agente está en el límite de la información del lidar
     * de frente
     * @author
     * @return true si el gps detecta out of bounds en el lidar, false si no
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
     * Devuelve en String hacia donde está encarado el agente
     * @author
     * @return dirección a la que mira el agente
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
     * Devuelve la dirección a la que va a mirar el agente después de una rotación
     * @author
     * @param direction sentido en el que tiene que rotar el agente
     * @return dirección a la que acaba mirando
     */
    private String whereIsGoingToLook(String direction){
        String lookingAt = "";
        switch(direction) {
            case "rotateR":
                switch((int)this.compassActual){
                    case 0:
                        lookingAt = "NE";
                        break;
                    case 45:
                        lookingAt = "E";
                        break;
                    case 90:
                        lookingAt = "SE";
                        break;
                    case 135:
                        lookingAt = "S";
                        break;
                    case 180:
                        lookingAt = "SW";
                        break;
                    case -45:
                        lookingAt = "W";
                        break;
                    case -90:
                        lookingAt = "NW";
                        break;
                    case -135:
                        lookingAt = "N";
                        break;
                }
                break;
            case "rotateL":
                switch((int)this.compassActual){
                    case 0:
                        lookingAt = "NW";
                        break;
                    case 45:
                        lookingAt = "W";
                        break;
                    case 90:
                        lookingAt = "SW";
                        break;
                    case 135:
                        lookingAt = "S";
                        break;
                    case 180:
                        lookingAt = "SE";
                        break;
                    case -45:
                        lookingAt = "E";
                        break;
                    case -90:
                        lookingAt = "NE";
                        break;
                    case -135:
                        lookingAt = "N";
                        break;
                }
            break;
        }
        
        return lookingAt;
    }

    /**
     * Indica si se puede o no ejecutar la siguiente acción con las actuales condiciones e información
     * @author
     * @param nextAction siguiente acción del plan
     * @return true si puede realizar la acción, false en otro caso
     */
    private boolean canExecuteNextAction(String nextAction) {
        boolean canExecute = false;
        if(nextAction == null && !this.actions.isEmpty()){
            nextAction = this.actions.get(0);
        }
        int z = this.gpsActual.get(2);
        
        switch(nextAction) {
            case "moveF":
                //Si no estoy mirando a la forntera
                if(!this.isLookingOutOfFrontier()){
                    int [] visualNextPos = this.getNextVisualPos(this.xVisualPosActual, this.yVisualPosActual, this.whereIsLooking());
                    //Si la siguiente casilla esta dentro de la malla del visual
                    if(visualNextPos[0] < 7 && visualNextPos[0] >= 0 && visualNextPos[1] < 7 && visualNextPos[1] >= 0){
                        //Si la siguiente casilla no esta fuera del mapa
                        if(this.visualSensor.get(visualNextPos[1]).get(visualNextPos[0]) >= 0){
                            //puedo ejecutar accion si mi altura es mayor o igual que la altura de la siguiente casilla
                            canExecute = z >= this.visualSensor.get(visualNextPos[1]).get(visualNextPos[0]);
                            if(canExecute){
                                this.xVisualPosActual = this.xVisualAuxActualPos;
                                this.yVisualPosActual = this.yVisualAuxActualPos;
                            }
                        }else{
                            canExecute = false;
                        }
                    }else{
                        canExecute = false;
                    }
                }
                break;
            case "rotateL":
            case "rotateR":
                canExecute = true;
                break;
            case "touchD":
                if((z - this.visualSensor.get(this.xVisualPosActual).get(this.yVisualPosActual)) <= 5){
                    canExecute = true;
                }
                break;
            case "moveUP":
                if(z < this.maxflight ){
                    canExecute = true;
                }
                break;
            case "moveD":
                if((z - this.visualSensor.get(this.xVisualPosActual).get(this.yVisualPosActual)) >= 5)
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
     * Determina si tiene suficiente información para planificar y si no setea el estado para leer sensores
     * @return true si no le hace falta más información, false si sí
     */
    private boolean hasEnoughtInfo() {
        // determinar si se tiene suficiente información para elaborar un plan o no
        // Si está mirando hacia un borde del lidar no tiene info suficiente.
        boolean response = this.isLookingOutOfFrontier();
        this.needsInfo = response;
        return !response; 
    }

    /**
     * Comprueba que el dron tiene suficiente energía para continuar o debe aterrizar para recargar
     * @author
     * @return true si no hace falta que recarge, false si sí
     */
    private boolean hasEnoughEnergy() {
        int distance;
        boolean enough = true;
        
        distance = this.gpsActual.get(2) - this.visualSensor.get(this.xVisualPosActual).get(this.yVisualPosActual); // Altura del dron - Altura del terreno debajo de él
        if(distance > 0){
            if ((double)(this.energy/distance) < 2.0) {
                System.out.println("NO TIENE SUFICIENTE ENERGÍA: height: "+this.gpsActual.get(2)+"  visual: "+this.visualSensor.get(this.yVisualPosActual).get(this.xVisualPosActual));
                enough = false;
            }
        }

        return enough;
    }

    /**
     * Actualiza la energía restante del dron
     * @author
     * @param action acción que gasta/recarga energía
     */
    private void setEnergy(String action) {
        switch (action) {
            case "moveF":
            case "rotateL":
            case "rotateR":
                this.energy--;
                break;
            case "touchD":
                this.energy -= this.gpsActual.get(2) - this.visualSensor.get(this.xVisualPosActual).get(this.yVisualPosActual);
                break;
            case "moveUP":
            case "moveD":
                this.energy -= 5;
                break;
            case "readSensors":
                this.energy -= this.sensores.length;
                break;
            case "recharge":
                this.energy = 1000;
                break;
        }
    }

    /**
     * Actualiza cual sería la siguiente posición del lidar si avanzamos con la orientación actual
     * @author
     * @param posX coordenada x actual del agente
     * @param posY coordenada y actual del agente
     * @param lookingAt dirección en la que está mirando el agente
     * @return par de coordenadas donde acaba después del movimiento
     */
    private int[] getNextVisualPos(int posX, int posY, String lookingAt) {
        int[] nextPos = this.getNextPos(posX, posY, lookingAt);
        //System.out.println("Antes de obtener siguiente posicion x: "+this.yVisualAuxActualPos+"; y: "+this.xVisualAuxActualPos+" y mira hacia el "+lookingAt);
        this.xVisualAuxActualPos = nextPos[0];
        this.yVisualAuxActualPos = nextPos[1];
        
        //if(this.yVisualAuxActualPos < 7 && this.xVisualAuxActualPos < 7)
            //System.out.println("Obtiene el siguiente visual position que es x: "+this.yVisualAuxActualPos+"; y: "+this.xVisualAuxActualPos+" y el visual es: "+this.visualSensor.get(this.yVisualAuxActualPos).get(this.xVisualAuxActualPos));
        return nextPos;
    }

    private int[] getNextPos(int posX, int posY, String lookingAt) {
        int x = 0;
        int y = 0;

        switch(lookingAt){
            case "N":
                x = posX;
                y = --posY;
                break;
            case "NE":
                x = ++posX;
                y = --posY;
                break;
            case "E":
                x = ++posX;
                y = posY;
                break;
            case "SE":
                x = ++posX;
                y = ++posY;
                break;
            case "S":
                x = posX;
                y = ++posY;
                break;
            case "NW":
                x = --posX;
                y = --posY;
                break;
            case "W":
                x = --posX;
                y = posY;
                break;
            case "SW":
                x = --posX;
                y = ++posY;
                break;
        }

        return new int[]{x,y};
    }

    /**
     * Actualiza la información de los sensores locales para poder planificar varios movimientos
     * @param action última acción realizada
     */
    private void updateActualInfo(String action) {
        this.setEnergy(action);
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
                int actualHeight = this.visualSensor.get(this.xVisualPosActual).get(this.yVisualPosActual);
                this.gpsActual.set(2, actualHeight);
                break;
            case "moveUP":
                actualHeight = this.gpsActual.get(2);
                this.gpsActual.set(2, actualHeight+5);
                break;
            case "moveD":
                actualHeight = this.gpsActual.get(2);
                this.gpsActual.set(2, actualHeight-5);
                break;
        }
        this.showTrackingInfo();
    }
    
    /**
     * Muestra información sobre el agente por consola
     * @author
     */
    private void showTrackingInfo(){
        String gps_msg = "GPS Actual: [" + this.gpsActual.get(0) + ", " + this.gpsActual.get(1) + ", " + this.gpsActual.get(2) + "] \n" + "GPS Sensores: [" + this.gpsSensor.get(0) + ", " + this.gpsSensor.get(1) + ", " + this.gpsSensor.get(2) + "] \n";
        String visual_msg = "Visual Sensor: \n";
        for(int i = 0; i < this.visualSensor.size(); i++){
            visual_msg += "[ ";
            ArrayList<Integer> array = this.visualSensor.get(i);
            for (int j = 0; j < array.size(); j++) {
                int value = array.get(j);
                visual_msg += value + ", ";
            }
            visual_msg += "]\n ";
        }
        visual_msg += "\n";
        String compass_msg = "Compass Actual: [" + this.compassActual + "] \n" + "Compass Sensores: [" + this.compassSensor + "] \n";
        String angular_msg = "Angular Actual: [" + this.angularActual + "] \n" + "Angular Sensores: [" + this.angularSensor + "] \n";
        String distance_msg = "Distance Actual: [" + this.distanceActual + "] \n" + "Distance Sensores: [" + this.distanceSensor + "] \n";
        
        System.out.println("ENERGY: "+this.energy+"\n"+gps_msg + visual_msg + compass_msg + angular_msg + distance_msg);
    }

    /**
     * Actualiza la posición en el GPS según se haya movido y estuviese mirando, se llama cuando se hace un moveF solo
     * @author
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
     * @author
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
                    this.distanceActual--;
                }
            }else{
                //Si está a más de un giro, se aleja
                if(abs(this.compassActual - this.angularActual) > 45){
                    this.distanceActual++;
                }else{
                    this.distanceActual--;
                }
            }
        }
    }
}