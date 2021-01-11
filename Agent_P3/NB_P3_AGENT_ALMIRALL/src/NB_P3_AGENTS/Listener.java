package NB_P3_AGENTS;

import com.eclipsesource.json.*;
import jade.lang.acl.ACLMessage;
import YellowPages.YellowPages;
import jade.lang.acl.MessageTemplate;

import java.util.Set;

public class Listener extends BasicDrone {
    //public String service, worldManager, conversationID, replyWith, id_problema;
    //ACLMessage out, in;
    YellowPages yp;
    Status status;
    int tries, cancelRequested;
    JsonObject contentMessage;
    Set<String> shops;
    
    @Override
    public void setup(){
        super.setup();
        status = Status.CHECKIN_LARVA;
        tries = 0;
        cancelRequested = 0;
        contentMessage = new JsonObject();
        name = "ALMIRALL_LISTENER";
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
                listenForMessages();
                break;
            case PLANNING:
                //status = Status.CANCEL_WM;
                status = Status.LISTENNING;
                break;
            case CANCEL_WM:
                status = Status.CHECKOUT_LARVA;
                break;
            case CHECKOUT_LARVA:
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
            if(in.getPerformative() == ACLMessage.CANCEL){
                cancelRequested++;
                if(cancelRequested == 3){
                    status = Status.CANCEL_WM;
                }
            }
        }
    }

    /**
     * Función que hace el checkin en Larva al Identity Manager y llama a obtener las YellowPages
     * @author Marcos Castillo
     * @return ACLMessage respuesta
     */
    public ACLMessage checkIn(){
        System.out.println("Intenta hacer el checkin en Larva");
        this.initMessage(_identitymanager, "ANALYTICS", "", ACLMessage.SUBSCRIBE);
        
        in = this.blockingReceive(10000);
        System.out.println("RESPUESTA CHECKIN: "+in);
        if(in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM){
            Info("Checkin confirmed in the platform");
            return this.getYellowPages(in);
        }else{
            System.out.println("No se ha podido ejecutar el login correctamente, saliendo de la ejecución.");
            //this.abortSession();
            status = Status.EXIT;
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
        if(in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM){
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
            System.out.println("No se ha podido obtener correctamente las YellowPages.");
            status = Status.CHECKOUT_LARVA;
        }
        return in;
    }
    
    /**
     * Función que hace el checkout de Larva
     * @author Marcos Castillo
     */
    public void checkOut(){
        this.replyMessage("ANALYTICS", ACLMessage.CANCEL, "");
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
                }
            }else{
                System.out.println("Error en la suscripción al agente");
            }
        }else{
            System.out.println("El agente no ha contestado la petición");
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
        this.replyMessage("REGULAR", ACLMessage.SUBSCRIBE, subscribe_type);
        
        ACLMessage reply = this.blockingReceive();
        System.out.println("RESPUESTA SUSCRIPCION AGENTE: "+reply);
        if(reply != null){
            in = reply;
            if(in.getPerformative() == ACLMessage.INFORM){
                conversationID = in.getConversationId();
                replyWith = in.getReplyWith();
                this.sendInitMessage();
                status = Status.LISTENNING;
            }else{
                System.out.println("No se ha podido empezar partida, se deberá solicitar de nuevo");
                status = Status.CHECKOUT_LARVA;
            }
        }
        if(tries == 3){
            status = Status.CHECKOUT_LARVA;
        }else{
            tries++;
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
     * Función que manda el mensaje inicial al resto de DRONES
     * @author Diego Garcia Aureli
     */
    private void sendInitMessage(){
        contentMessage.add("WorldManager", worldManager);
        contentMessage.add("ConversationID", conversationID);
        contentMessage.add("ReplyWith", replyWith);
        System.out.println("REPLYWITH " + replyWith);
        contentMessage.add("Shops", shops.toString());

        contentMessage.add("xPosition", 1);
        contentMessage.add("yPosition", 1);
        contentMessage.add("name", "ALMIRALL_SEEKER1");
        this.initMessage("ALMIRALL_SEEKER1", "REGULAR", contentMessage.toString(), ACLMessage.INFORM);
        contentMessage.set("xPosition", 3);
        contentMessage.set("yPosition", 3);
        contentMessage.set("name", "ALMIRALL_SEEKER2");
        this.initMessage("ALMIRALL_SEEKER2", "REGULAR", contentMessage.toString(), ACLMessage.INFORM);
        contentMessage.set("xPosition", 6);
        contentMessage.set("yPosition", 6);
        contentMessage.set("name", "ALMIRALL_SEEKER3");
        this.initMessage("ALMIRALL_SEEKER3", "REGULAR", contentMessage.toString(), ACLMessage.INFORM);
        contentMessage.set("xPosition", 10);
        contentMessage.set("yPosition", 10);
        contentMessage.set("name", "ALMIRALL_RESCUER");
        this.initMessage("ALMIRALL_RESCUER", "REGULAR", contentMessage.toString(), ACLMessage.INFORM);
    }
    
}
