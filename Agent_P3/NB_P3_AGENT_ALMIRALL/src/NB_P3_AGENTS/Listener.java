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
    YellowPages yp;
    Status status;
    int tries, cancelRequested;
    JsonObject contentMessage;
    Set<String> shops;
    HashMap<String, DroneInfo> drones;
    PriorityQueue<Node> ludwigs;
    
    @Override
    public void setup(){
        super.setup();
        status = Status.CHECKIN_LARVA;
        tries = 0;
        cancelRequested = 0;
        contentMessage = new JsonObject();
        name = droneNames.get("listener");
        drones = new HashMap<String, DroneInfo>();
        drones.put("seeker1", new DroneInfo(droneNames.get("seeker1")));
//        drones.put("seeker2", new DroneInfo(droneNames.get("seeker2")));
//        drones.put("seeker3", new DroneInfo(droneNames.get("seeker3")));
//        drones.put("rescuer", new DroneInfo(droneNames.get("rescuer")));
        ludwigs = new PriorityQueue<Node>(new LudwigComparator());
        subscribed = false;
        loggedInWorld = false;
        checkedInLarva = false;
        keepAliveSession = true;
        shops = new HashSet<String>();
    }
    
    @Override
    public void plainExecute() {
        switch(status){
            case CHECKIN_LARVA:
                keepAliveSession &= this.checkIn();
                if(!keepAliveSession)
                    status = Status.EXIT;
                break;
            case SUBSCRIBE_WM:
                keepAliveSession &= this.subscribeToWorldManager();
                if(!keepAliveSession)
                    status = Status.CHECKOUT_LARVA;
                break;
            case SUBSCRIBE_TYPE:
                keepAliveSession &= this.subscribeByType("LISTENER");
                if(!keepAliveSession)
                    status = Status.CANCEL_WM;
                break;
            case LISTENNING:
                //status = Status.PLANNING;
                listenForMessages();
                break;
            case PLANNING:
                status = Status.LISTENNING;
                break;
            case CANCEL_WM:
                status = Status.CHECKOUT_LARVA;
                this.initMessage(worldManager, "ANALYTICS", "", ACLMessage.CANCEL, conversationID, replyWith);
                status = Status.CHECKOUT_LARVA;
                break;
            case CHECKOUT_LARVA:
                Info("Mandamos mensaje de CHECKOUT LARVA a todos los DRONES");
                this.checkOut();
                break;
            case EXIT:
                System.out.println("Se cierra el agente");
                _exitRequested = true;
                break;
        }
    }

    /**
     * Método para evaluar si un drone puede realizar la acción o no
     * @param agent
     * @param message
     * @return
     * @author Marcos Castillo
     */
    private boolean canExecuteMove(String agent, JsonObject message) {
        boolean canExecute = true;
        
        for(DroneInfo drone : drones.values()) {
            if(agent.equals(drone.getName())) {
                int nextXMove = message.get("xPosition").asInt();
                int nextYMove = message.get("yPosition").asInt();
                int nextHeight = message.get("droneHeight").asInt();
                int energyLeft = message.get("energy").asInt();
                switch(message.get("operation").toString()){
                    case "moveF":
                        for(DroneInfo dron : drones.values()){
                            if(dron.getxPosition() == nextXMove && dron.getyPosition() == nextYMove){
                                canExecute = false;
                            }
                        }
                        if(canExecute){
                        }
                        break;
                    case "touchD":
                    case "moveUP":
                    case "moveD":
                        for(DroneInfo dron : drones.values()){
                            if(dron.getxPosition() == nextXMove && dron.getyPosition() == nextYMove && dron.getDroneHeight() == nextHeight){
                                canExecute = false;
                            }
                        }
                        break;
                    case "recharge":
                        if((drones.get("rescuer").getEnergy() <= 100/2.5) && !agent.equals(drones.get("rescuer").getName())){
                            canExecute = false;
                        }
                        break;
                    default:
                        canExecute = true;
                        break;
                }
                if(canExecute){
                    drone.setDroneHeight(nextHeight);
                    drone.setxPosition(nextXMove);
                    drone.setyPosition(nextYMove);
                    drone.setEnergy(energyLeft);
                }
            }
        }

        return canExecute;
    }

    /**
     * Método de escucha de mensajes internos
     * @author Marcos Castillo
     */
    private void listenForMessages() {
        MessageTemplate t = MessageTemplate.MatchConversationId("INTERN");
        in = this.blockingReceive(t);

        if(in != null){
            JsonObject response = new JsonObject();
            if(!in.getContent().isEmpty()){
                response = new JsonObject(Json.parse(in.getContent()).asObject());
            }

            if(in.getPerformative() == ACLMessage.CANCEL){
                this.replyMessage("ANALITYCS", ACLMessage.CONFIRM, "");
                cancelRequested++;
                if(cancelRequested > 3){
                    status = Status.CANCEL_WM;
                }
            }else if(in.getPerformative() == ACLMessage.PROPOSE){
                if(!in.getContent().isEmpty()) {
                    if (canExecuteMove(in.getSender().getName(), response)) {
                        this.replyMessage("INFORM", ACLMessage.CONFIRM, "");
                        //Actualizar droneInfo
                    } else {
                        this.replyMessage("INFORM", ACLMessage.REJECT_PROPOSAL, "");
                    }
                }else{
                    Info("Mensaje con performative PROPOSE y contenido vacío: " +in.toString());
                }
            } else {
                Info("Error en Listener, mensaje no contemplado: " + in.toString());
            }
        }
    }

    /**
     * Función que hace el checkin en Larva al Identity Manager y llama a obtener las YellowPages
     * @author Marcos Castillo
     * @return boolean respuesta
     */
    public boolean checkIn(){
        Info("Intenta hacer el checkin en Larva");
        if (!checkedInLarva) {
            this.initMessage(_identitymanager, "ANALYTICS", "", ACLMessage.SUBSCRIBE);

            in = this.blockingReceive();
            System.out.println("RESPUESTA CHECKIN: "+in);
            if(in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM){
                Info("Checkin confirmed in the platform");
                checkedInLarva = true;
                return this.getYellowPages();
            }else{
                System.out.println("No se ha podido ejecutar el login correctamente, saliendo de la ejecución.");
                //this.abortSession();
                status = Status.EXIT;
            }
        } else {
            Info("Está logueado en LARVA e intenta volver a loguearse: " + in.toString());
            status = Status.CHECKOUT_LARVA;
        }
        return false;
    }
    
    /**
     * Función que obtiene las YellowPages por primera vez para encontrar el proveedor de servicio
     * @author Marcos Castillo
     * @params ACLMessage in Mensaje para el hilo de la conversación
     * @return boolean respuesta
     */
    private boolean getYellowPages() {
        this.replyMessage("ANALYTICS", ACLMessage.QUERY_REF, "");
        
        in = this.blockingReceive();
        if(in != null && (in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM)){
            yp = new YellowPages();
            yp.updateYellowPages(in);
            System.out.println("YellowPages: "+yp.prettyPrint());
            worldManager = yp.queryProvidersofService(service).iterator().next();
            if(worldManager.equals("")){
                //No hay servicio en las YellowPages asociado, se cierra
                status = Status.CHECKOUT_LARVA;
            }else{
                System.out.println("Proveedor "+worldManager);
                status = Status.SUBSCRIBE_WM;
                return true;
            }
        }else{
            String message = in != null ? in.toString() : " NULO ";
            System.out.println("No se ha podido obtener correctamente las YellowPages. " + message);
            status = Status.CHECKOUT_LARVA;
        }
        return false;
    }
    
    /**
     * Función que hace el checkout de Larva
     * @author Marcos Castillo
     */
    public void checkOut(){
        this.initMessage(_identitymanager, "ANALYTICS", "", ACLMessage.CANCEL, conversationID, replyWith);
        /*
        in = this.blockingReceive();
        if(in.getPerformative() == ACLMessage.INFORM){
        */
            //this.doExit();
            status = Status.EXIT;
        //}
    }

    /**
     * Función que se suscribe por primera vez al WM para iniciar la conversación con el y obtiene el Mapa
     * @author Marcos Castillo
     * @return boolean respuesta
     */
    public boolean subscribeToWorldManager() {
        if(!loggedInWorld){
            String subscribe_world = "{\"problem\":\""+id_problema+"\"}";
            this.initMessage(worldManager, "ANALYTICS", subscribe_world, ACLMessage.SUBSCRIBE);

            in = this.blockingReceive();
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
                            Info("########################## TIENDAS CON SHOPS@: " + shops);
                        }else{
                            shops = yp.queryProvidersofService(conversationID);
                            if(!shops.isEmpty()){
                                Info("########################## TIENDAS SIN SHOPS@: " + shops);
                            }else{
                                Info("Shops vacías: " +shops);
                                return false;
                            }
                        }

                        status = Status.SUBSCRIBE_TYPE;
                        return true;
                    }else{
                        System.out.println("Error 2 no se ha obtenido el mapa en la subscripción: " + replyObj.toString());
                        status = Status.CANCEL_WM;
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
        return false;
    }
    
    /**
     * Función que se suscribe al WM por tipo de DRONE.
     * @author Marcos Castillo
     * @param type String Tipo de DRONE "LISTENER", "RESCUER" o "SEEKER"
     * @return boolean respuesta
     */
    public boolean subscribeByType(String type){
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
                    return true;
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
        return false;
    }

    /**
     * Función que refresca las YellowPages después de la suscripción al WM para obtener las tiendas.
     * @author Marcos Castillo
     */
    private void refreshYellowPages() {
        //Refresh YellowPages with shops
        this.initMessage(_identitymanager, "ANALYTICS", "", ACLMessage.QUERY_REF);
        
        ACLMessage in_aux = this.blockingReceive();
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
            case "ALMIRALL_SEEKER11111":
                drones.get("seeker1").setxPosition(x);
                drones.get("seeker1").setyPosition(y);
                break;
            case "ALMIRALL_SEEKER2":
                drones.get("seeker2").setxPosition(x);
                drones.get("seeker2").setyPosition(y);
                break;
            case "ALMIRALL_SEEKER3":
                drones.get("seeker3").setxPosition(x);
                drones.get("seeker3").setyPosition(y);
                break;
            case "ALMIRALL_RESCUER":
                drones.get("rescuer").setxPosition(x);
                drones.get("rescuer").setyPosition(y);
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

        setDronePosition("ALMIRALL_SEEKER11111", 3, 3);
//        setDronePosition("ALMIRALL_SEEKER2", 3, 3);
//        setDronePosition("ALMIRALL_SEEKER3", 6, 6);
//        setDronePosition("ALMIRALL_RESCUER", 10, 10);

        this.initMessage("ALMIRALL_AWACS111", "REGULAR", "", ACLMessage.QUERY_IF, conversationID, replyWith);

        for(DroneInfo drone : drones.values()) {
            try{
                Thread.sleep(5000);
            }catch (Exception e){
                System.out.println("Error al dormir listener : "+e);
            }
            
            contentMessage.add("xPosition", drone.getxPosition());
            contentMessage.add("yPosition", drone.getyPosition());
            contentMessage.add("name", drone.getName());
            this.initMessage(drone.getName(), "REGULAR", contentMessage.toString(), ACLMessage.INFORM);
        }
    }
    
}
