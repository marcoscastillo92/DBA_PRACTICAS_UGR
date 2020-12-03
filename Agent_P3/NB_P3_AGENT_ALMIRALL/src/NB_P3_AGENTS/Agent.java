package larva_hackathon;

import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import com.eclipsesource.json.*;
import jade.lang.acl.ACLMessage;
import YellowPages.YellowPages;
import java.util.ArrayList;

public class Agent extends IntegratedAgent{
    public int minValue, maxValue, middleValue;
    public boolean endSession, logged, playing, susbscribed;
    public String service, worldManager, conversationID, replyWith;
    ACLMessage out, in;
    YellowPages yp;
    
    @Override
    public void setup(){
        super.setup();
        service = "Group Almirall";
        playing = false;
        susbscribed = false;
        endSession = false;
        _identitymanager = "Sphinx";
        logged = this.checkIn();
    }
    
    @Override
    public void plainExecute() {
        while(!endSession){
            if(logged){
                if(!playing){
                    if(susbscribed){
                        this.subscribeByType("LISTENER");
                    }else{
                        this.subscribeToWorldManager();
                    }
                }else{
                    //Operaciones siguientes
                }
            }
        }
        this.checkOut();
    }
    
    public boolean checkIn(){
        System.out.println("Intenta loguearse");
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(_identitymanager, AID.ISLOCALNAME));
        out.setProtocol("ANALYTICS");
        out.setContent("");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        this.send(out);
        in = this.blockingReceive(2000);
        System.out.println("RESPUESTA LOGIN: "+in);
        if(in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM){
            Info("Checkin confirmed in the platform");
            //Get YellowPages
            out = in.createReply();
            out.setPerformative(ACLMessage.QUERY_REF);
            out.setContent("");
            this.send(out);
            in = this.blockingReceive(2000);
            yp = new YellowPages();
            yp.updateYellowPages(in);
            System.out.println("YellowPages: "+yp.prettyPrint());
            ArrayList<String> players = new ArrayList(yp.queryProvidersofService(service));
            worldManager = players.get(0);
            if(worldManager.equals("")){
                //No hay servicio en las YellowPages asociado, se aborta
                this.abortSession();
            }
            System.out.println("Proveedor "+worldManager);
            return true;
        }
        System.out.println("No se ha podido ejecutar el login correctamente, saliendo de la ejecución.");
        this.abortSession();
        return false;
    }
    
    public void checkOut(){
        out = in.createReply();
        out.setPerformative(ACLMessage.CANCEL);
        out.setContent("");
        this.send(out);
        in = this.blockingReceive(2000);
        this.doExit();
    }

    public boolean subscribeToWorldManager() {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        out.setProtocol("ANALYTICS");
        out.setContent("");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        this.send(out);
        in = this.blockingReceive(2000);
        System.out.println("RESPUESTA SUSCRIPCION AGENTE: "+in);
        if(in.getPerformative() == ACLMessage.CONFIRM || in.getPerformative() == ACLMessage.INFORM){
            conversationID = in.getConversationId();
            replyWith = in.getReplyWith();
            susbscribed = true;
            return true;
        }else{
            System.out.println("Error en la suscripción al agente");
            return false;
        }
    }
    
    public boolean subscribeByType(String type){
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
            playing = true;
            return true;
        }
        System.out.println("No se ha podido empezar partida, se solicita de nuevo");
        return false;
    }
}
