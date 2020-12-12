package NB_P3_AGENTS;

import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import com.eclipsesource.json.*;
import jade.lang.acl.ACLMessage;
import YellowPages.YellowPages;
import java.util.ArrayList;
import DBAMap.DBAMap;
import java.io.IOException;

enum Status {
    CHECKIN_LARVA, SUBSCRIBE_WM, SUBSCRIBE_TYPE, LISTENNING, PLANNING, CANCEL_WM, CHECKOUT_LARVA, EXIT
}

public class Listener extends IntegratedAgent{
    public String service, worldManager, conversationID, replyWith, id_problema;
    ACLMessage out, in;
    YellowPages yp;
    DBAMap map;
    Status status;
    int tries;
    
    @Override
    public void setup(){
        super.setup();
        id_problema = "World1";
        service = "Analytics group Almirall";
        _identitymanager = "Sphinx";
        _exitRequested = false;
        status = Status.CHECKIN_LARVA;
        tries = 0;
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
                status = Status.PLANNING;
                break;
            case PLANNING:
                status = Status.CANCEL_WM;
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
    
    /*
    * Función que hace el checkin en Larva al Identity Manager y llama a obtener las YellowPages
    * @author Marcos Castillo
    * @return ACLMessage respuesta
    */
    public ACLMessage checkIn(){
        System.out.println("Intenta hacer el checkin en Larva");
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(_identitymanager, AID.ISLOCALNAME));
        out.setProtocol("ANALYTICS");
        out.setContent("");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        this.send(out);
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
    
    /*
    * Función que obtiene las YellowPages por primera vez para encontrar el proveedor de servicio
    * @author Marcos Castillo
    * @params ACLMessage in Mensaje para el hilo de la conversación
    * @return ACLMessage respuesta
    */
    private ACLMessage getYellowPages(ACLMessage in) {
        //Get YellowPages
        out = in.createReply();
        out.setPerformative(ACLMessage.QUERY_REF);
        out.setContent("");
        this.send(out);
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
    
    /*
    * Función que hace el checkout de Larva
    * @author Marcos Castillo
    */
    public void checkOut(){
        out = in.createReply();
        out.setProtocol("ANALYTICS");
        out.setPerformative(ACLMessage.CANCEL);
        out.setContent("");
        out.setInReplyTo(replyWith);
        out.setConversationId(conversationID);
        this.send(out);
        in = this.blockingReceive(10000);
        if(in.getPerformative() == ACLMessage.INFORM){
            //this.doExit();
            status = Status.EXIT;
        }
    }

    /*
    * Función que se suscribe por primera vez al WM para iniciar la conversación con el y obtiene el Mapa
    * @author Marcos Castillo
    * @return ACLMessage respuesta
    */
    public ACLMessage subscribeToWorldManager() {
        String subscribe_world = "{\"problem\":\""+id_problema+"\"}";
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        out.setProtocol("ANALYTICS");
        out.setContent(subscribe_world);
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        this.send(out);
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
                    String mapfilename = jsonMapFile.getString("filename", "nonamefound");
                    System.out.println("Se ha encontrado el mapa: "+mapfilename);
                    map = new DBAMap();
                    try{
                        map.fromJson(jsonMapFile.get("filedata").asArray());
                        if(map.hasMap()){
                            System.out.println("MAP "+mapfilename+" ("+map.getWidth()+" cols x "+map.getHeight()+" rows) saved on project and ready in memory");
                        }else{
                            System.out.println("Error 1 no se ha obtenido el mapa en la subscripción: " + replyObj.toString());
                        }
                    }catch(IOException e){
                        System.out.println("Excepción al cargar mapa: "+e);
                        status = Status.CHECKOUT_LARVA;
                        return null;
                    }
                    this.refreshYellowPages();
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
    
    /*
    * Función que se suscribe al WM por tipo de DRONE.
    * @author Marcos Castillo
    * @param String type Tipo de DRONE "LISTENER", "RESCUER" o "SEEKER"
    * @return ACLMessage respuesta
    */
    public ACLMessage subscribeByType(String type){
        String subscribe_type = "{\"type\":\""+type+"\"}";
        out = in.createReply();
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.SUBSCRIBE);
        out.setInReplyTo(replyWith);
        out.setConversationId(conversationID);
        out.setContent(subscribe_type);
        this.send(out);
        ACLMessage reply = this.blockingReceive(10000);
        System.out.println("RESPUESTA SUSCRIPCION AGENTE: "+reply);
        if(reply != null){
            in = reply;
            if(in.getPerformative() == ACLMessage.INFORM){
                conversationID = in.getConversationId();
                replyWith = in.getReplyWith();
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

    /*
    * Función que refresca las YellowPages después de la suscripción al WM para obtener las tiendas.
    * @author Marcos Castillo
    */
    private void refreshYellowPages() {
        //Refresh YellowPages with shops
        ACLMessage out_aux = new ACLMessage();
        out_aux.setSender(getAID());
        out_aux.addReceiver(new AID(_identitymanager, AID.ISLOCALNAME));
        out_aux.setProtocol("ANALYTICS");
        out_aux.setEncoding(_myCardID.getCardID());
        out_aux.setPerformative(ACLMessage.QUERY_REF);
        out_aux.setContent("");
        this.send(out_aux);
        ACLMessage in_aux = this.blockingReceive(10000);
        if(in_aux.getPerformative() == ACLMessage.CONFIRM || in_aux.getPerformative() == ACLMessage.INFORM){
            yp.updateYellowPages(in_aux);
            System.out.println("YellowPages actualizada (shopping): "+yp.prettyPrint());
        }else{
            System.out.println("No se ha podido actualizar correctamente las YellowPages.");
        }
    }
    
    /*
    * Función que manda el mensaje inicial al resto de DRONES
    * @author Marcos Castillo
    */
    private void sendInitMessage(){
        //TODO
    }
    
}
