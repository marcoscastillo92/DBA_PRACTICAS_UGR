package NB_P3_AGENTS;

import com.eclipsesource.json.*;
import jade.lang.acl.ACLMessage;
import YellowPages.YellowPages;
import jade.lang.acl.MessageTemplate;

import java.util.*;

class LudwigComparator implements Comparator<Node> {
    public int compare(Node n1, Node n2) {
        return (int)n1.getDistanceToRescuer() - (int)n2.getDistanceToRescuer();
    }
}

public class Listener extends BasicDrone {
    //public String service, worldManager, conversationID, replyWith, id_problema;
    //ACLMessage out, in;
    YellowPages yp;
    Status status;
    int tries, cancelRequested;
    JsonObject contentMessage;
    Set<String> shops;
    List<DroneInfo> drones;
    PriorityQueue<Node> ludwigs;
    
    @Override
    public void setup(){
        super.setup();
        status = Status.CHECKIN_LARVA;
        tries = 0;
        cancelRequested = 0;
        contentMessage = new JsonObject();
        name = "ALMIRALL_LISTENER";
        drones = new ArrayList<>();
        drones.add(new DroneInfo("ALMIRALL_SEEKER1"));
        drones.add(new DroneInfo("ALMIRALL_SEEKER2"));
        drones.add(new DroneInfo("ALMIRALL_SEEKER3"));
        drones.add(new DroneInfo("ALMIRALL_RESCUER"));
        ludwigs = new PriorityQueue<Node>(new LudwigComparator());
        worldManager = "";
        conversationID = "";
        replyWith = "";
        subscribed = false;
        loggedInWorld = false;
        checkedInLarva = false;
        shops = new HashSet<String>();
    }
    
    @Override
    public void plainExecute() {
        switch(status){
            case CHECKIN_LARVA:
                this.checkIn();
                break;
            case SUBSCRIBE_WM:
                this.subscribeToWorldManager();
                break;
            case SUBSCRIBE_TYPE:
                this.subscribeByType("LISTENER");
                break;
            case LISTENNING:
                //status = Status.PLANNING;
                sendInitMessage();
                listenForMessages();
                break;
            case PLANNING:
                //status = Status.CANCEL_WM;
                status = Status.LISTENNING;
                break;
            case CANCEL_WM:
                status = Status.CHECKOUT_LARVA;
                if(cancelRequested < 1) {
                    MessageTemplate t = MessageTemplate.MatchConversationId("INTERN");
                    this.initMessage("ALMIRALL_SEEKER1", "ANALYTICS", contentMessage.toString(), ACLMessage.CANCEL, "INTERN", "");
                    in = this.blockingReceive(t);
                    if (in != null && in.getPerformative() == ACLMessage.CANCEL) {
                        this.initMessage(worldManager, "ANALYTICS", "", ACLMessage.CANCEL, conversationID, replyWith);
                        status = Status.CHECKOUT_LARVA;
                    } else {
                        Info("No se ha podido hacer el checkout de WM: " + in.toString());
                    }
                }else{
                    this.initMessage("ALMIRALL_SEEKER1", "ANALYTICS", contentMessage.toString(), ACLMessage.CANCEL, "INTERN", "");
                }
                break;
            case CHECKOUT_LARVA:
                Info("Mandamos mensaje de CHECKOUT LARVA a todos los DRONES");
                this.initMessage("ALMIRALL_SEEKER1", "ANALYTICS", contentMessage.toString(), ACLMessage.CANCEL, "INTERN", "");
                MessageTemplate ta = MessageTemplate.MatchReplyWith("INTERN");
                in = this.blockingReceive(ta);
                this.checkOut();
                break;
            case EXIT:
                System.out.println("Se cierra el agente");
                _exitRequested = true;
                break;
        }
    }

    private void listenForMessages() {
        MessageTemplate t = MessageTemplate.MatchConversationId("INTERN");
        in = this.blockingReceive(t);

        if(in != null){
            JsonObject response = new JsonObject(Json.parse(in.getContent()).asObject());
            if(in.getPerformative() == ACLMessage.CANCEL){
                cancelRequested++;
                if(cancelRequested == 3){
                    status = Status.CANCEL_WM;
                }
            }else if(in.getPerformative() == ACLMessage.INFORM){
                //Ludwig founded
                // TODO save position of Ludwig and queue by priority
                int xPositionLudwig = response.get("xPositionLudwig").asInt();
                int yPositionLudwig = response.get("yPositionLudwig").asInt();
                int ludwigHeight = response.get("ludwigHeight").asInt();
                double distanceToRescuer = calculateDistance(xPositionLudwig, yPositionLudwig, ludwigHeight);

                Node node = new Node(yPositionLudwig+"-"+xPositionLudwig, xPositionLudwig, yPositionLudwig, ludwigHeight, distanceToRescuer);
                ludwigs.add(node);

                this.replyMessage("INFORM", ACLMessage.CONFIRM, "");

            }else if(in.getPerformative() == ACLMessage.PROPOSE){
                // TODO check if it's possible execute that action
                boolean canExecute = true; //TODO Evaluate by funtion
                if(canExecute) {
                    this.replyMessage("INFORM", ACLMessage.CONFIRM, "");
                    //Actualizar droneInfo
                }else{
                    this.replyMessage("INFORM", ACLMessage.REJECT_PROPOSAL, "");
                }
            } else {
                Info("Error en Listener, mensaje no contemplado: " + in.toString());
            }
        }
    }

    private double calculateDistance(int xPositionLudwig, int yPositionLudwig, int ludwigHeight) {
        int xPositionRescuer = drones.get(3).getxPosition();
        int yPositionRescuer = drones.get(3).getyPosition();
        int heightRescuer = drones.get(3).getDroneHeight();
        int dX = xPositionLudwig - xPositionRescuer;
        int dY = yPositionLudwig - yPositionRescuer;
        int dH = ludwigHeight - heightRescuer;

        return (Math.sqrt(Math.pow(dX, 2)+Math.pow(dY, 2)) + dH);
    }

    /**
     * Función que hace el checkin en Larva al Identity Manager y llama a obtener las YellowPages
     * @author Marcos Castillo
     * @return ACLMessage respuesta
     */
    public ACLMessage checkIn(){
        System.out.println("Intenta hacer el checkin en Larva");
        if (!checkedInLarva) {
            this.initMessage(_identitymanager, "ANALYTICS", "", ACLMessage.SUBSCRIBE);

            in = this.blockingReceive(10000);
            System.out.println("RESPUESTA CHECKIN: "+in);
            if(in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM){
                Info("Checkin confirmed in the platform");
                checkedInLarva = true;
                return this.getYellowPages(in);
            }else{
                System.out.println("No se ha podido ejecutar el login correctamente, saliendo de la ejecución.");
                //this.abortSession();
                status = Status.EXIT;
            }
        } else {
            Info("Está logueado en LARVA e intenta volver a loguearse: " + in.toString());
            status = Status.CHECKOUT_LARVA;
        }
        return in;
    }
    
    /**
     * Función que obtiene las YellowPages por primera vez para encontrar el proveedor de servicio
     * @author Marcos Castillo
     * @params ACLMessage in Mensaje para el hilo de la conversación
     * @return ACLMessage respuesta
     */
    private ACLMessage getYellowPages(ACLMessage in) {
        this.replyMessage("ANALYTICS", ACLMessage.QUERY_REF, "");
        
        in = this.blockingReceive(10000);
        if(in != null && (in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM)){
            yp = new YellowPages();
            yp.updateYellowPages(in);
            System.out.println("YellowPages: "+yp.prettyPrint());
            worldManager = yp.queryProvidersofService(service).iterator().next();
            if(worldManager.equals("")){
                //No hay servicio en las YellowPages asociado, se aborta
                //this.abortSession();
                status = Status.CHECKOUT_LARVA;
            }else{
                System.out.println("Proveedor "+worldManager);
                status = Status.SUBSCRIBE_WM;
            }
        }else{
            String message = in != null ? in.toString() : " NULO ";
            System.out.println("No se ha podido obtener correctamente las YellowPages. " + message);
            //status = Status.LISTENNING;
            status = Status.CHECKOUT_LARVA;
        }
        return in;
    }
    
    /**
     * Función que hace el checkout de Larva
     * @author Marcos Castillo
     */
    public void checkOut(){
        this.initMessage(_identitymanager, "ANALYTICS", "", ACLMessage.CANCEL, conversationID, replyWith);
        /*
        in = this.blockingReceive(10000);
        if(in.getPerformative() == ACLMessage.INFORM){
        */
            //this.doExit();
            status = Status.EXIT;
        //}
    }

    /**
     * Función que se suscribe por primera vez al WM para iniciar la conversación con el y obtiene el Mapa
     * @author Marcos Castillo
     * @return ACLMessage respuesta
     */
    public ACLMessage subscribeToWorldManager() {
        if(!loggedInWorld){
            String subscribe_world = "{\"problem\":\""+id_problema+"\"}";
            this.initMessage(worldManager, "ANALYTICS", subscribe_world, ACLMessage.SUBSCRIBE);

            in = this.blockingReceive(10000);
            System.out.println("RESPUESTA SUSCRIPCION WorldManager: "+in);
            if(in != null){
                if(in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM){
                    conversationID = in.getConversationId();
                    replyWith = in.getReplyWith();

                    //Obtener el mapa en el mensaje y asignarlo al DBAMap de la clase
                    JsonObject replyObj = new JsonObject(Json.parse(in.getContent()).asObject());
                    if(replyObj.names().contains("map")){
                        JsonObject jsonMapFile = replyObj.get("map").asObject();
                        contentMessage.add("map", jsonMapFile);
                        map.loadMap(jsonMapFile);
                        this.refreshYellowPages();

                        shops = yp.queryProvidersofService("shop@"+conversationID);
                        if (!shops.isEmpty()) {
                            System.out.println("TIENDAS: " + shops);
                        }

                        status = Status.SUBSCRIBE_TYPE;
                    }else{
                        System.out.println("Error 2 no se ha obtenido el mapa en la subscripción: " + replyObj.toString());
                        status = Status.CHECKOUT_LARVA;
                    }
                }else{
                    System.out.println("Error en la suscripción al agente: " +in.toString());
                    status = Status.CHECKOUT_LARVA;
                }
            }else{
                System.out.println("El agente no ha contestado la petición: " + in.toString());
                status = Status.CHECKOUT_LARVA;
            }
        }else{
            Info("Intenta volver a loguearse en el WM :" + in.toString());
            status = Status.CANCEL_WM;
        }
        return in;
    }
    
    /**
     * Función que se suscribe al WM por tipo de DRONE.
     * @author Marcos Castillo
     * @param type String Tipo de DRONE "LISTENER", "RESCUER" o "SEEKER"
     * @return ACLMessage respuesta
     */
    public ACLMessage subscribeByType(String type){
        String subscribe_type = "{\"type\":\""+type+"\"}";

        if(!subscribed) {
            this.replyMessage("REGULAR", ACLMessage.SUBSCRIBE, subscribe_type);

            ACLMessage reply = this.blockingReceive();
            System.out.println("RESPUESTA SUSCRIPCION AGENTE: "+reply);
            if(reply != null){
                in = reply;
                if(in.getPerformative() == ACLMessage.INFORM){
                    conversationID = in.getConversationId();
                    replyWith = in.getReplyWith();
                    subscribed = true;
                    this.sendInitMessage();
                    status = Status.LISTENNING;
                }else{
                    System.out.println("No se ha podido empezar partida, se deberá solicitar de nuevo");
                    status = Status.CHECKOUT_LARVA;
                }
            }else if(tries == 3){
                status = Status.CHECKOUT_LARVA;
            }else{
                tries++;
            }
        }else{
            Info("Suscrito por tipo y vuelve a intentarlo: " + in.toString());
            status = Status.CANCEL_WM;
        }
        return in;
    }

    /**
     * Función que refresca las YellowPages después de la suscripción al WM para obtener las tiendas.
     * @author Marcos Castillo
     */
    private void refreshYellowPages() {
        //Refresh YellowPages with shops
        this.initMessage(_identitymanager, "ANALYTICS", "", ACLMessage.QUERY_REF);
        
        ACLMessage in_aux = this.blockingReceive(10000);
        if(in_aux.getPerformative() == ACLMessage.CONFIRM || in_aux.getPerformative() == ACLMessage.INFORM){
            yp.updateYellowPages(in_aux);
            System.out.println(in.toString());
            System.out.println("YellowPages actualizada (shopping): "+yp.prettyPrint());
        }else{
            System.out.println("No se ha podido actualizar correctamente las YellowPages.");
        }
    }
    
    /**
     * Función que modifica la posición de un drone
     * @param name nombre del drone
     * @param x posición coordenada x
     * @param y posición coordenada y
     * @author Diego García
     */
    private void setDronePosition(String name, int x, int y) {
        switch(name) {
            case "ALMIRALL_SEEKER1":
                drones.get(0).setxPosition(x);
                drones.get(0).setyPosition(y);
                break;
            case "ALMIRALL_SEEKER2":
                drones.get(1).setxPosition(x);
                drones.get(1).setyPosition(y);
                break;
            case "ALMIRALL_SEEKER3":
                drones.get(2).setxPosition(x);
                drones.get(2).setyPosition(y);
                break;
            case "ALMIRALL_RESCUER":
                drones.get(3).setxPosition(x);
                drones.get(3).setyPosition(y);
                break;
        }
    }
    
    /**
     * Función que manda el mensaje inicial al resto de DRONES
     * @author Diego Garcia
     */
    private void sendInitMessage(){
        contentMessage.add("WorldManager", worldManager);
        contentMessage.add("ConversationID", conversationID);
        contentMessage.add("ReplyWith", replyWith);
        System.out.println("REPLYWITH " + replyWith);
        contentMessage.add("Shops", shops.toString());

        setDronePosition("ALMIRALL_SEEKER1", 1, 1);
        setDronePosition("ALMIRALL_SEEKER2", 3, 3);
        setDronePosition("ALMIRALL_SEEKER3", 6, 6);
        setDronePosition("ALMIRALL_RESCUER", 10, 10);

        this.initMessage("ALMIRALL_AWACS", "REGULAR", "", ACLMessage.QUERY_IF, conversationID, replyWith);

        try{
            Thread.sleep(5000);
        }catch (Exception e){
            System.out.println("Error al dormir listener : "+e);
        }

        for(DroneInfo drone : drones) {
            contentMessage.add("xPosition", drone.getxPosition());
            contentMessage.add("yPosition", drone.getyPosition());
            contentMessage.add("name", drone.getName());
            this.initMessage(drone.getName(), "REGULAR", contentMessage.toString(), ACLMessage.INFORM);
        }
    }
    
}
