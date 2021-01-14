package NB_P3_AGENTS;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;
import java.util.stream.Collectors;

class sensorComparator implements Comparator<Sensor> {
    @Override
    public int compare(Sensor s1, Sensor s2) 
    { 
        if (s1.getPrice() > s2.getPrice()) {
            return 1;
        } else if (s1.getPrice() < s2.getPrice()) {
            return -1;
        } else {
            return 0;
        }
    } 
}

public abstract class MoveDrone extends BasicDrone {
    protected Status status;
    protected ArrayList<String> wallet;
    private int xPosition, yPosition, droneHeight, energy;
    List<String> shops;
    Graph<Node> graphMap;
    RouteFinder<Node> routeFinder;
    List<Node> route;
    JsonObject currentState;
    Node objectiveFounded;
    Node nextNode;
    PriorityQueue<Node> ludwigs;
    ArrayList<String> actions;
    PriorityQueue<Sensor> products;
    ArrayList<String> tiendas;
    HashMap<String, Sensor> sensors;
    
    @Override
    public void setup(){
        super.setup();
        this.wallet = new ArrayList<>();
        currentState = new JsonObject();
        energy = 10;
        subscribed = false;
        loggedInWorld = false;
        checkedInLarva = false;
        keepAliveSession = true;
        objectiveFounded = new Node("11-1", 1, 11, 239); //PARA MOCKUP se ha de hacer bien cuando se encuentre un Ludwig
        tiendas = new ArrayList<>();
        sensors = new HashMap<String, Sensor>();
    }
    
    /**
     * Escucha inicial del listener
     * @return true si ha conseguido el mapa
     * @author Diego Garcia Aurelio
     */
    boolean listenInit(ACLMessage in) {
        if(in != null) {
            String content = in.getContent();

            if (content.contains("map")) {
                Info("Recibido mapa del listening!");
                JsonObject contentObject = new JsonObject(Json.parse(in.getContent()).asObject());
                map.loadMap(contentObject.get("map").asObject());
                setUpAStarPathfinding();
                name = contentObject.get("name").toString();
                name = name.replace("\"", "");
                worldManager = contentObject.get("WorldManager").toString();
                conversationID = contentObject.get("ConversationID").toString();
                conversationID = conversationID.replace("\"", "");
                replyWith = contentObject.get("ReplyWith").toString();
                replyWith = replyWith.replace("\"", "");

                String tiendas = contentObject.get("Shops").toString();
                tiendas = tiendas.replace("\"", "");
                tiendas = tiendas.replace("[", "");
                tiendas = tiendas.replace("]", "");
                tiendas = tiendas.replace(" ", "");
                String[] stores = tiendas.split(",");
                shops = Arrays.asList(stores);

                xPosition = contentObject.get("xPosition").asInt();
                yPosition = contentObject.get("yPosition").asInt();

                return true;
            }
        }
        return false;
    }

    public boolean loginWorld() {
        if(!loggedInWorld) {
            JsonObject loginJson = new JsonObject();
            JsonArray sensorsJson = new JsonArray();

            for(Sensor sensor : sensors.values()){
                sensorsJson.add(sensor.getName());
            }
            loginJson.add("operation", "login");
            loginJson.add("attach", sensorsJson);
            loginJson.add("posx", xPosition);
            loginJson.add("posy", yPosition);

            this.replyMessage("REGULAR", ACLMessage.REQUEST, loginJson.toString(), name);
            //this.initMessage(worldManager, "REGULAR", loginJson.toString(), ACLMessage.REQUEST, conversationID, replyWith);
            MessageTemplate t = MessageTemplate.MatchInReplyTo(name);
            in = this.blockingReceive(t);
            if (in.getPerformative() != ACLMessage.REFUSE && in.getPerformative() != ACLMessage.FAILURE) {
                Info("Login en mundo correcto, iniciado en la posición [" + xPosition + ", " + yPosition + "]");
                loggedInWorld = true;
                conversationID = in.getConversationId();
                replyWith = in.getReplyWith();
                droneHeight = graphMap.getNode(yPosition + "-" + xPosition).getHeight();
                return true;
            }
            Info("Fallo en login en el mundo. Mensaje: " + in.getContent());
            status = Status.EXIT;
        }else{
            Info("Ya logueado en el mundo e intenta volver a hacerlo: " + in.toString());
        }

        return false;
    }

    /**
     * Método para suscribirse por tipo al WM
     * @param type
     * @return ACLMessage
     * @author Marcos Castillo
     * @modifiedBy Juan Pablo
     */
    public boolean subscribeByType(String type){
        int tries = 0;
        String content;
        if(!subscribed) {
            String subscribe_type = "{\"type\":\"" + type + "\"}";
            out = new ACLMessage();
            out.setProtocol("REGULAR");
            out.setSender(getAID());
            out.addReceiver(new AID("Almirall", AID.ISLOCALNAME));
            out.setPerformative(ACLMessage.SUBSCRIBE);
            out.setContent(subscribe_type);
            out.setInReplyTo(this.replyWith);
            out.setReplyWith(name);
            out.setConversationId(this.conversationID);
            this.send(out);

            MessageTemplate t = MessageTemplate.MatchInReplyTo(name);
            ACLMessage reply = this.blockingReceive(t);
            System.out.println("RESPUESTA SUSCRIPCION AGENTE " + type + ": " + reply);
            if (reply != null) {
                in = reply;
                if (in.getPerformative() == ACLMessage.INFORM) {
                    content = in.getContent();

                    System.out.println(content);

                    JsonObject replyObj = new JsonObject(Json.parse(in.getContent()).asObject());
                    if (replyObj.names().contains("coins")) {
                        JsonArray monedas = replyObj.get("coins").asArray();

                        for (int i = 0; i < monedas.size(); i++) {
                            this.wallet.add(monedas.get(i).asString());
                        }
                    }

                    if (replyObj.names().contains("energy")) {
                        energy = replyObj.get("energy").asInt();
                    }
                    subscribed = true;
                    return true;
                    //status = Status.EXIT;
                } else {
                    System.out.println("No se ha podido empezar partida, se deberá solicitar de nuevo: " + in.toString());
                    status = Status.EXIT;
                }
            }
            if (tries == 3) {
                status = Status.EXIT;
            } else {
                tries++;
            }
        }
        return false;
    }
    
    public JsonObject getProducts(){
        JsonObject shopValues = new JsonObject();

        products = new PriorityQueue<>(new sensorComparator());
        if(!shops.isEmpty()) {
            for (String shop : shops) {
                this.initMessage(shop, "REGULAR", "{}", ACLMessage.QUERY_REF, conversationID, "RESCUER_BUY");

                in = this.blockingReceive();
                if (in.getPerformative() == ACLMessage.INFORM) {
                    JsonObject replyObj = Json.parse(in.getContent()).asObject();
                    if (replyObj.names().contains("products")) {

                        JsonArray jsonProducts = replyObj.get("products").asArray();

                        for (JsonValue value : jsonProducts) {
                            JsonObject sensorObject = value.asObject();
                            String sensorName = sensorObject.get("reference").asString();
                            int sensorPrice = sensorObject.get("price").asInt();
                            products.add(new Sensor(shop, sensorName, sensorPrice));
                        }

                        Info("PRODUCTOS: " + products.toString());

                    }
                } else {
                    Info("Error en la consulta de la tienda" + in.getContent());
                }

            }
        } else {
            Info("Se ha intentado consultar la tienda sin tener el id de la tienda: " + in.toString());
            status = Status.EXIT;
        }
        return shopValues;
    }
    
    /**
     * Método para hacer el checkIn en Larva
     * @return ACLMessage
     * @author Marcos Castillo
     */
    public boolean checkIn(){
        System.out.println("Intenta hacer el checkin en Larva");
        if (!checkedInLarva) {
            this.initMessage(_identitymanager, "ANALYTICS", "", ACLMessage.SUBSCRIBE, conversationID, name);

            MessageTemplate t = MessageTemplate.MatchInReplyTo(name);
            in = this.blockingReceive(t);
            System.out.println("RESPUESTA CHECKIN: " + in);
            if (in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM) {
                Info("Checkin confirmed in the platform");
                return true;
            } else {
                System.out.println("No se ha podido ejecutar el login correctamente, saliendo de la ejecución.");
                //this.abortSession();
                status = Status.EXIT;
            }
        } else {
            Info("Está logueado en LARVA e intenta volver a loguearse: " + in.toString());
        }
        return false;
    }
    
    public void sendCoin(String receiver) {
        String coin = this.wallet.get(0);
        
        if (!coin.isEmpty()) {
            out = new ACLMessage();
            out.setProtocol("REGULAR");
            out.setSender(getAID());
            out.addReceiver(new AID(receiver, AID.ISLOCALNAME));
            out.setPerformative(ACLMessage.INFORM);
            out.setContent(coin);
            System.out.println("Enviando " + coin + " de " + getAID() + " a " + receiver);
            this.send(out);
            this.wallet.remove(0);
        }
        else {
            System.out.println("Este dron no tiene monedas!");
        }
    }
    
    public boolean buy(String shop, String service, int cost) {
        JsonObject compra = new JsonObject();
        JsonArray payment = new JsonArray();
        
        // Coge las monedas de la cartera necesarias para pagar
        for (int i = 0; i < cost; i++) {
            payment.add(wallet.get(0));
            wallet.remove(0);
        }
        
        // En el contenido del mensaje indica que queremos comprar y el pago
        compra.add("operation", "buy");
        compra.add("reference", service);
        compra.add("payment", payment);
        
        // Crea el mensaje y lo envia
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(shop, AID.ISLOCALNAME));
        out.setPerformative(ACLMessage.REQUEST);
        out.setContent(compra.toString());
        this.send(out);
        
        // Espera la respuesta
        in = this.blockingReceive();
        if (in.getPerformative() == ACLMessage.FAILURE || in.getPerformative() == ACLMessage.REFUSE) {
            // Si recibimos FAILURE o REFUSE, no se ha realizado la compra
            System.out.println("Error en la compra");
            
            // Volvemos a meter las monedas en la cartera
            for (int i = 0; i < payment.size(); i++) {
                this.wallet.add(payment.get(i).asString());
            }
            
            return false;
        }
        else {
            // En otro caso, se habra realizado la compra correctamente
            System.out.println("Compra realizada");
            
            return true;
        }
    }
    
    public void getSensors(){
        boolean thermalbought = false;
        
        for (String sensor:tiendas){
            for (Sensor s:products){
                if(!s.getName().contains("THERMAL") || !thermalbought){
                    if(this.buy(s.getShop(), s.getName(), s.getPrice())){
                        if(s.getName().contains(sensor) && s.getName().contains("THERMAL")){
                            thermalbought = true;
                        }
                        sensors.put(s.getName(), s);
                    }
                }
            }
        }
    }
    
    /**
     * Método para inicializar los datos del algoritmo A*
     * @author Marcos Castillo
     */
    public void setUpAStarPathfinding() {      
        graphMap = new Graph<Node>(map.asSet(),map.getConnections());
        routeFinder = new RouteFinder<Node>(graphMap, new Scorer(), new Scorer());
    }
    
    /**
     * Método para buscar una ruta con el algoritmo A*
     * @param xFrom posición X de origen
     * @param yFrom posición Y de origen
     * @param xTo posición X de destino
     * @param yTo posición Y de destino
     * @author Marcos Castillo
     */
    public void findRoute(int xFrom, int yFrom, int xTo, int yTo) {
        String idFrom = yFrom+"-"+xFrom;
        String idTo = yTo+"-"+xTo;
        
        route = routeFinder.findRoute(graphMap.getNode(idFrom), graphMap.getNode(idTo));
        System.out.println("RUTA: \n"+route.stream().map(Node::getId).collect(Collectors.toList()));
    }

    public int[] getActualPosition() {
        return new int[]{xPosition, yPosition};
    }

    public int[] getNextPosition() {
        Node nextNode = route.get(0);
        return new int[]{(int)nextNode.getX(), (int)nextNode.getY()};
    }

    public int getDroneHeight() {
        return droneHeight;
    }
    
    public void land(){
        int altura = this.getDroneHeight();
        
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
        this.actions = landActions;
    }

    public int getEnergy() {
        return energy;
    }

    public void setupCurrentState(){
        int[] position = getActualPosition();
        int[] nextPosition = getActualPosition(); //No tiene objetivo aún al iniciarse
        currentState.add("actualXPos",position[0]);
        currentState.add("actualYPos",position[1]);
        currentState.add("nextXPos",nextPosition[0]);
        currentState.add("nextYPos",nextPosition[1]);
        currentState.add("droneHeight",getDroneHeight());
        currentState.add("energy",getEnergy());
        currentState.add("carryingLudwigs",nextPosition[0]);
    }

    public void updateCurrentState(){
        int[] position = getActualPosition();
        int[] nextPosition = getNextPosition();
        currentState.set("actualXPos",position[0]);
        currentState.set("actualYPos",position[1]);
        currentState.set("nextXPos",nextPosition[0]);
        currentState.set("nextYPos",nextPosition[1]);
        currentState.set("droneHeight",getDroneHeight());
        currentState.set("energy",getEnergy());
        currentState.set("carryingLudwigs",nextPosition[0]);
    }

    public void exitRequestedToListener(){
        this.initMessage(droneNames.get("listener"), "ANALYTICS", "", ACLMessage.CANCEL, "INTERN", name);
        in = this.blockingReceive();
        if(in.getPerformative() == ACLMessage.CONFIRM){
            this.initMessage( _identitymanager, "ANALYTICS", "", ACLMessage.CANCEL, conversationID, replyWith);
            _exitRequested = true;
        }
    }

    public boolean requestAction(String actionToPerform){
        JsonObject action = new JsonObject();
        action.add("operation", actionToPerform);

        this.initMessage(droneNames.get("listener"), "REGULAR", action.toString(), ACLMessage.PROPOSE, "INTERN", name);

        MessageTemplate t = MessageTemplate.MatchSender(new AID(droneNames.get("listener")));
        in = this.blockingReceive(t);

        if(in.getPerformative() == ACLMessage.CONFIRM){
            //TODO Send action to WorldManager if it's executable action
            this.initMessage(worldManager, "REGULAR", action.toString(), ACLMessage.REQUEST, conversationID, "");
            MessageTemplate template = MessageTemplate.MatchSender(new AID(worldManager));
            in = this.blockingReceive(template);
            if( in.getPerformative() == ACLMessage.CONFIRM){
                Info("Va a ejecutar acción: " + actionToPerform);
                replyWith = in.getReplyWith();
                conversationID = in.getConversationId();
            }else{
                Info("World manager no permite realizar esta acción: " +actionToPerform);
                // TODO: ¿QUÉ HACER? EXIT o INFORMAR A LISTENER Y DESPUES DE "X" VECES EXIT
            }
        } else {
            //Si es acción ejecutable que no sea recargar sleep y devolvemos falso para volver a intentarlo más tarde
          if(!actionToPerform.equals("recharge")){
              try{
                  Thread.sleep(2000);
              }catch(Exception e){
                  Info("Excepción al dormir en acción: " + e);
              }

          }else{
              //Mandamos coins al rescuer
              sendCoin(droneNames.get("rescuer"));
              return true;
          }
        }
        return false;
    }

    public void informLudwigPositionToRescuer(Node node){
        JsonObject position = new JsonObject();
        position.add("xPositionLudwig", objectiveFounded.getX());
        position.add("yPositionLudwig", objectiveFounded.getY());
        position.add("ludwigHeight", objectiveFounded.getHeight());

        this.initMessage(droneNames.get("rescuer"), "INFORM", position.toString(), ACLMessage.INFORM, "INTERN", name);
    }

    public boolean listenForMessages(){
        ACLMessage aux = this.blockingReceive();
        if(aux != null){
            Info("Mensaje interno recibido: " + aux.toString());
            if(aux.getPerformative() == ACLMessage.CANCEL && aux.getConversationId().equals("INTERN")){
                //this.initMessage(droneNames.get("listener"), "ANALYTICS", "", ACLMessage.CONFIRM, "INTERN", "INTERN");
                return false;
            } else {
                return this.listenInit(aux);
            }
        }
        return true;
    }

    public double calculateDistance(int xPositionLudwig, int yPositionLudwig, int ludwigHeight) {
        int[] positionRescuer = getActualPosition();
        int dX = xPositionLudwig - positionRescuer[0];
        int dY = yPositionLudwig - positionRescuer[1];
        int dH = ludwigHeight - getDroneHeight();

        return (Math.sqrt(Math.pow(dX, 2)+Math.pow(dY, 2)) + dH);
    }

    public void updateEnergy(String action){
        switch (action) {
            case "moveF":
            case "rotateL":
            case "rotateR":
                energy = name.matches(".*SEEKER.*") ? energy-- : energy - 4;
                break;
            case "touchD":
                int[] dronePosition = getActualPosition();
                energy -= getDroneHeight() - graphMap.getNode(dronePosition[1]+"-"+dronePosition[0]).getHeight();
                break;
            case "moveUP":
            case "moveD":
                energy -= 5;
                break;
            case "readSensors":
                int cost = 0;
                for(Sensor sensor : sensors.values()){
                    if(sensor.getName().matches(".*HQ")){
                        cost += 4;
                    }else if(sensor.getName().matches(".*DLX")){
                        cost += 8;
                    }else{
                        cost++;
                    }
                }
                energy -= cost;
                break;
            case "recharge":
                energy = 1000;
                break;
        }
    }

    public boolean hasEnoughtEnergy(){
        return energy < 1000/2.5;
    }
    
    public void moveToNode(Node from, Node to, double compass) {
        // Cuanto se tiene que mover en el eje x y cuanto en el y
        nextNode = to;
        int diffX = (int) (to.getX() - from.getX());
        int diffY = (int) (to.getY() - from.getY());
        int d = 0;
        double aux;
        
        int diffHeight = (to.getHeight()+1) - this.droneHeight;
        
        while (diffHeight > 0) {
            actions.add("moveUP");
            diffHeight =- 5;
        }
        
        // Borra los decimales que pueda tener compass
        if (compass % 45 != 0) {
            aux = compass / 45;
            aux = Math.round(aux);
            compass = aux * 45;
        }
        
        // Si tiene que moverse arriba a la derecha
        if (diffX == 1 && diffY == -1) {
            while (compass != 45) {
            compass += 45.0;
            d++;

                if (compass > 180) { 
                    compass = -135;
                }
            }
        }
        // Si tiene que moverse arriba
        else if (diffX == 0 && diffY == -1) {
            while (compass != 0) {
            compass += 45.0;
            d++;

                if (compass > 180) { 
                    compass = -135;
                }
            }
        }
        // Si tiene que moverse arriba a la izquierda
        else if (diffX == -1 && diffY == -1) {
            while (compass != -45) {
            compass += 45.0;
            d++;

                if (compass > 180) { 
                    compass = -135;
                }
            }
        }
        // Si tiene que moverse a la derecha
        else if (diffX == 1 && diffY == 0) {
            while (compass != 90) {
            compass += 45.0;
            d++;

                if (compass > 180) { 
                    compass = -135;
                }
            }
        }
        // Si tiene que moverse a la izquierda
        else if (diffX == -1 && diffY == 0) {
            while (compass != -90) {
            compass += 45.0;
            d++;

                if (compass > 180) { 
                    compass = -135;
                }
            }
        }
        // Si tiene que moverse abajo a la derecha
        else if (diffX == 1 && diffY == 1) {
            while (compass != 135) {
            compass += 45.0;
            d++;

                if (compass > 180) { 
                    compass = -135;
                }
            }
        }
        // Si tiene que moverse abajo
        else if (diffX == 0 && diffY == 1) {
            while (compass != 180) {
            compass += 45.0;
            d++;

                if (compass > 180) { 
                    compass = -135;
                }
            }
        }
        // Si tiene que moverse abajo a la izquierda
        else if (diffX == -1 && diffY == 1) {
            while (compass != -135) {
            compass += 45.0;
            d++;

                if (compass > 180) { 
                    compass = -135;
                }
            }
        }
        
        if (d <= 4) {
            // El plan es girar a la derecha d veces
            while (d != 0) {
                actions.add("rotateR");
                d--; 
            }
            
            actions.add("moveF");
        }
        else {
            // El plan es girar a la izquierda i veces
            int i = 8 - d;

            while (i != 0) {
                actions.add("rotateL");
                i--;
            }
            
            actions.add("moveF");
        }
    }
    
    public boolean isLanded(){
        boolean is = false;
        int[] position = getActualPosition();
        int h = getDroneHeight();
        int g = graphMap.getNode(position[1]+"-"+position[0]).getHeight();
        
        if(h==g){
            is = true;
        }
            
        return is;
    }

    public boolean canExecuteNextAction(String nextAction) {
        boolean canExecute = false;
        if(nextAction == null && !actions.isEmpty()){
            nextAction = actions.get(0);
        }
        int z = getDroneHeight();
        int [] dronePosition = getActualPosition();
        Node actualNode = graphMap.getNode(dronePosition[1]+"-"+dronePosition[0]);
        Node nextNode = getNextNode("actiooooooon");

        switch(nextAction) {
            case "moveF":
                //Si no estoy mirando a la forntera y el siguiente nodo tiene altura menor o igual
                if(!this.isLookingOutOfFrontier()){
                    //puedo ejecutar accion si mi altura es mayor o igual que la altura de la siguiente casilla
                    canExecute = z >= nextNode.getHeight();
                }
                break;
            case "touchD":
                if((z - actualNode.getHeight()) <= 5){
                    canExecute = true;
                }
                break;
            case "moveUP":
                if(z < actualNode.MAX_HEIGHT ){
                    canExecute = true;
                }
                break;
            case "moveD":
                if((z - actualNode.getHeight()) >= 5)
                    break;
            case "rotateL":
            case "rotateR":
            case "readSensors":
            case "rescue":
                canExecute = true;
                break;
        }

        return canExecute;
    }

    public Node getNextNode(String action) {
        return nextNode;
    }

    public boolean isLookingOutOfFrontier() {
        int [] dronePosition = getActualPosition();
        int x = dronePosition[0];
        int y = dronePosition[1];
        boolean outOfFrontier = false;
        String lookingAt = whereIsLooking();
        if(((x >= map.getWidth() - 1 || x <= 0) && !lookingAt.equals("N") && !lookingAt.equals("S")) ||
                ((y >= map.getHeight() - 1 || y <= 0) && !lookingAt.equals("W") && !lookingAt.equals("E"))){
            outOfFrontier = true;
        }
        return outOfFrontier;
    }

    /**
     * Devuelve en String hacia donde está encarado el agente
     * @author Marcos
     * @return dirección a la que mira el agente
     */
    public String whereIsLooking(){
        String lookingAt = "";
        switch((int)sensors.get("compass").getValue()){
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

    public void updateActualInfo(String action){
        this.updateEnergy(action);
        switch(action){
            case "moveF":
                //Actualizar gpsActual
                xPosition = nextNode.getX();
                yPosition = nextNode.getY();
                break;
            case "rotateL":
                if(sensors.get("compass").getValue() <= -135){
                    sensors.get("compass").setValue(180);
                }else{
                    sensors.get("compass").setValue(-45);
                }
                break;
            case "rotateR":
                if(sensors.get("compass").getValue() >= 185){
                    sensors.get("compass").setValue(-135);
                }else{
                    sensors.get("compass").setValue(45);
                }
                break;
            case "touchD":
                //Aterriza y está en z 0
                int[] dronePosition = getActualPosition();
                droneHeight = graphMap.getNode(dronePosition[1]+"-"+dronePosition[0]).getHeight();
                break;
            case "moveUP":
                droneHeight += 5;
                break;
            case "moveD":
                droneHeight -= 5;
                break;
        }
        //this.showTrackingInfo();
    }

    public void showTrackingInfo() {
        //TODO
    }
}