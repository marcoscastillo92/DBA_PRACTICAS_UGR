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
    CHECKIN_LARVA, SUBSCRIBE_WM, LISTENNING, PLANNING, CANCEL_WM, CHECKOUT_LARVA, EXIT
}

public class Listener extends IntegratedAgent{
    public String service, worldManager, conversationID, replyWith, id_problema;
    ACLMessage out, in;
    YellowPages yp;
    DBAMap map;
    Status status;
    
    @Override
    public void setup(){
        super.setup();
        id_problema = "World1";
        service = "Analytics group Almirall";
        _identitymanager = "Sphinx";
        _exitRequested = false;
        status = Status.CHECKIN_LARVA;
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
        in = this.blockingReceive(2000);
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
    
    private ACLMessage getYellowPages(ACLMessage in) {
        //Get YellowPages
        out = in.createReply();
        out.setPerformative(ACLMessage.QUERY_REF);
        out.setContent("");
        this.send(out);
        in = this.blockingReceive(2000);
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
        return in;
    }
    
    public void checkOut(){
        out = in.createReply();
        out.setProtocol("ANALYTICS");
        out.setPerformative(ACLMessage.CANCEL);
        out.setContent("");
        out.setInReplyTo(replyWith);
        out.setConversationId(conversationID);
        this.send(out);
        in = this.blockingReceive(2000);
        if(in.getPerformative() == ACLMessage.INFORM){
            //this.doExit();
            status = Status.EXIT;
        }
    }

    public ACLMessage subscribeToWorldManager() {
        JsonObject subscribe_world;
        // Generate JSON body
        subscribe_world = new JsonObject();
        subscribe_world.add("problem", id_problema);
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        out.setProtocol("ANALYTICS");
        out.setContent(subscribe_world.asString());
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        this.send(out);
        in = this.blockingReceive(2000);
        System.out.println("RESPUESTA SUSCRIPCION WorldManager: "+in);
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
                    map.fromJson(jsonMapFile.asArray());
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
                in = this.subscribeByType("LISTENER");
            }else{
                System.out.println("Error 2 no se ha obtenido el mapa en la subscripción: " + replyObj.toString());
            }
        }else{
            System.out.println("Error en la suscripción al agente");
        }
        return in;
    }
    
    public ACLMessage subscribeByType(String type){
        JsonObject subscribe_type;
        // Generate JSON body
        subscribe_type = new JsonObject();
        subscribe_type.add("type", type);
        out = in.createReply();
        out.setProtocol("REFERENCE");
        out.setPerformative(ACLMessage.QUERY_REF);
        out.setInReplyTo(replyWith);
        out.setConversationId(conversationID);
        out.setContent(subscribe_type.asString());
        this.send(out);
        in = this.blockingReceive(2000);
        System.out.println("RESPUESTA SUSCRIPCION AGENTE: "+in);
        if(in.getPerformative() == ACLMessage.AGREE){
            conversationID = in.getConversationId();
            replyWith = in.getReplyWith();
            status = Status.LISTENNING;
        }else{
            System.out.println("No se ha podido empezar partida, se deberá solicitar de nuevo");
            status = Status.CHECKOUT_LARVA;
        }
        return in;
    }
}
