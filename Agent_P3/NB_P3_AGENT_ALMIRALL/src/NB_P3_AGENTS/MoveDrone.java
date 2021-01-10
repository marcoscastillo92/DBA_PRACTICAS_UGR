package NB_P3_AGENTS;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonArray;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.*;
import java.util.stream.Collectors;

public abstract class MoveDrone extends BasicDrone {
    protected Status status;
    protected ArrayList<String> wallet;
    String[] shops;
    Graph<Node> graphMap;
    RouteFinder<Node> routeFinder;
    List<Node> route;
    
    @Override
    public void setup(){
        super.setup();
        this.wallet = new ArrayList<>();
    }
    
    /**
     * Escucha inicial del listener
     * @return true si ha conseguido el mapa
     * @author Diego Garcia Aurelio
     */
    boolean listenInit() {
        in = this.blockingReceive();
        String content = in.getContent();
        
        if(content.contains("map")) {
            Info("Recibido mapa del listening!");
            JsonObject contentObject = new JsonObject(Json.parse(in.getContent()).asObject());
            map.loadMap(contentObject.get("map").asObject());
            setUpAStarPathfinding();
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
            shops = tiendas.split(",");
            
            return true;
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
    public ACLMessage subscribeByType(String type){
        int tries = 0;
        String content;
        
        String subscribe_type = "{\"type\":\""+type+"\"}";
        out = new ACLMessage();
        out.setProtocol("REGULAR");
        out.setSender(getAID());
        out.addReceiver(new AID("Almirall", AID.ISLOCALNAME));
        out.setPerformative(ACLMessage.SUBSCRIBE);
        out.setContent(subscribe_type);
        out.setInReplyTo(this.replyWith);
        out.setConversationId(this.conversationID);
        this.send(out);
        
        ACLMessage reply = this.blockingReceive();
        System.out.println("RESPUESTA SUSCRIPCION AGENTE " + type + ": "+reply);
        if(reply != null){
            in = reply;
            if(in.getPerformative() == ACLMessage.INFORM){
                content = in.getContent();
                
                System.out.println(content);
                
                JsonObject replyObj = new JsonObject(Json.parse(in.getContent()).asObject());
                if (replyObj.names().contains("coins")) {
                    JsonArray monedas = replyObj.get("coins").asArray();
                    
                    for (int i = 0; i < monedas.size(); i++) {
                        this.wallet.add(monedas.get(i).asString());
                    }
                }
                
                status = Status.EXIT;
            }else{
                System.out.println("No se ha podido empezar partida, se deberá solicitar de nuevo");
                status = Status.EXIT;
            }
        }
        if(tries == 3){
            status = Status.EXIT;
        }else{
            tries++;
        }
        return in;
    }
    
    /**
     * Método para hacer el checkIn en Larva
     * @return ACLMessage
     * @author Marcos Castillo
     */
    public ACLMessage checkIn(){
        System.out.println("Intenta hacer el checkin en Larva");
        this.initMessage(_identitymanager, "ANALYTICS", "", ACLMessage.SUBSCRIBE);
        
        in = this.blockingReceive(10000);
        System.out.println("RESPUESTA CHECKIN: "+in);
        if(in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM){
            Info("Checkin confirmed in the platform");
        }else{
            System.out.println("No se ha podido ejecutar el login correctamente, saliendo de la ejecución.");
            //this.abortSession();
            status = Status.EXIT;
        }
        return in;
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
        String idFrom = yFrom+""+xFrom;
        String idTo = yTo+""+xTo;
        
        route = routeFinder.findRoute(graphMap.getNode(idFrom), graphMap.getNode(idTo));
        System.out.println("RUTA: \n"+route.stream().map(Node::getId).collect(Collectors.toList()));
    }
}
