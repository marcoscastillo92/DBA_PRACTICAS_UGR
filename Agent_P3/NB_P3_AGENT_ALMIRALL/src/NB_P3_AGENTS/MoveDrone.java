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
        if (s1.getPrice() < s2.getPrice()) {
            return 1;
        } else if (s1.getPrice() > s2.getPrice()) {
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
            JsonArray sensors = new JsonArray();

            loginJson.add("operation", "login");
            loginJson.add("attach", sensors);
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
        JsonObject sensorValues = new JsonObject();
 
        PriorityQueue<Sensor> products = new PriorityQueue<>(new sensorComparator());
        
        for (String shop : shops) {
            this.initMessage(shop, "REGULAR", "{}", ACLMessage.QUERY_REF, conversationID, "RESCUER_BUY");
            
            in = this.blockingReceive();
            if(in.getPerformative() == ACLMessage.INFORM) {
                JsonObject replyObj = Json.parse(in.getContent()).asObject();
                if(replyObj.names().contains("products")) {
                    
                    JsonArray jsonProducts = replyObj.get("products").asArray();
                    
                    for(JsonValue value: jsonProducts) {
                        JsonObject sensorObject = value.asObject();
                        String sensorName = sensorObject.get("reference").asString();
                        int sensorPrice = sensorObject.get("price").asInt();
                        products.add(new Sensor(shop, sensorName, sensorPrice));
                    }
                    
                    Info("PRODUCTOS: " + products.toString());

                }
            }
            else {
                Info("Error en la consulta de la tienda"+in.getContent());
            }

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
    
    public void buy(String shop, String service, int cost) {
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
        }
        else {
            // En otro caso, se habra realizado la compra correctamente
            System.out.println("Compra realizada");
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

    private int[] getActualPosition() {
        return new int[]{xPosition, yPosition};
    }

    private int[] getNextPosition() {
        Node nextNode = route.get(0);
        return new int[]{(int)nextNode.getX(), (int)nextNode.getY()};
    }

    private int getDroneHeight() {
        return droneHeight;
    }

    private int getEnergy() {
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
        this.initMessage(droneNames.get("listener"), "ANALYTICS", "", ACLMessage.CANCEL, "INERTN", name);
    }

    public void requestAction(String actionToPerform){
        JsonObject action = new JsonObject();
        action.add("action", actionToPerform);
        String protocol;
        int performative;

        if ("Found".equals(actionToPerform)) {
            protocol = "INFORM";
            performative = ACLMessage.INFORM;
            action.add("xPositionLudwig", objectiveFounded.getX());
            action.add("yPositionLudwig", objectiveFounded.getY());
            action.add("ludwigHeight", objectiveFounded.getHeight());
        } else {
            protocol = "REGULAR";
            performative = ACLMessage.PROPOSE;
        }

        this.initMessage(droneNames.get("listener"), protocol, action.toString(), performative, "INERTN", name);

        MessageTemplate t = MessageTemplate.MatchInReplyTo(name);
        in = this.blockingReceive(t);

        if(in.getPerformative() == ACLMessage.CONFIRM){
            //TODO Send action to WorldManager if it's executable action
        }
        // TODO If it's executable action and is not "recharge" wait else send coins to ...
    }

    public boolean listenForMessages(){
        ACLMessage aux = this.blockingReceive(5000);
        if(aux != null){
            Info("Mensaje interno recibido: " + aux.toString());
            if(aux.getPerformative() == ACLMessage.CANCEL && aux.getConversationId().equals("INTERN")){
                //this.initMessage(droneNames.get("listener"), "ANALYTICS", "", ACLMessage.CONFIRM, "INERTN", "INTERN");
                return false;
            }else{
                return this.listenInit(aux);
            }
        }
        return true;
    }
}
