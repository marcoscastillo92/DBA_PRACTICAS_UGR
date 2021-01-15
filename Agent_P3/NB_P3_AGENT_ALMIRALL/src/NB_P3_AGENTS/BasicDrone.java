package NB_P3_AGENTS;

import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.util.*;

enum Status {
    CHECKIN_LARVA, SUBSCRIBE_WM, SUBSCRIBE_TYPE, LISTENNING, PLANNING, ACTING, CANCEL_WM, CHECKOUT_LARVA, EXIT
}

public abstract class BasicDrone extends IntegratedAgent {
    public String service, worldManager, conversationID, replyWith, id_problema;
    public java.util.Map<String,String> droneNames;
    ACLMessage out, in;
    Map map;
    String name;
    Boolean subscribed, loggedInWorld, checkedInLarva, keepAliveSession;
    int ludwigsCount = 10;
    
    @Override
    public void setup(){
        super.setup();
        id_problema = "Playground1";
        service = "Analytics group Almirall";
        _identitymanager = "Sphinx";
        _exitRequested = false;
        map = new Map();
        droneNames = new HashMap<>();
        droneNames.put("listener", "ALMIRALL_LISTENER11");
        droneNames.put("seeker1", "ALMIRALL_SEEKER111");
        droneNames.put("seeker2", "ALMIRALL_SEEKER2");
        droneNames.put("seeker3", "ALMIRALL_SEEKER3");
        droneNames.put("rescuer", "ALMIRALL_RESCUER");
    }
    
    /**
     * Mensaje inicial a un agente
     * @param agent
     * @param protocol
     * @param content
     * @param performative 
     * @author Diego Garcia Aurelio
     */
    public void initMessage(String agent, String protocol, String content, int performative) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(agent, AID.ISLOCALNAME));
        out.setProtocol(protocol);
        out.setContent(content);
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(performative);
        this.send(out);
        Info(out.toString());
    }
    

    /**
     * Mensaje inicial a un agente
     * @param agent
     * @param protocol
     * @param content
     * @param performative
     * @param conversationID
     * @param replyWithR 
     */
    public void initMessage(String agent, String protocol, String content, int performative, String conversationID, String replyWithR) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(agent, AID.ISLOCALNAME));
        out.setProtocol(protocol);
        out.setContent(content);
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(performative);
        out.setInReplyTo(replyWith);
        if(!replyWithR.isEmpty()){
            out.setReplyWith(replyWithR);
        }
        out.setConversationId(conversationID);
        this.send(out);
        Info(out.toString());
    }

    /**
     * Respuesta a un mensaje
     * @param protocol
     * @param performative
     * @param content
     * @author Diego Garcia Aurelio
     */
    public void replyMessage(String protocol, int performative, String content) {
        out = in.createReply();
        out.setProtocol(protocol);
        out.setPerformative(performative);
        out.setContent(content);
        out.setInReplyTo(replyWith);
        out.setConversationId(conversationID);
        this.send(out);
        Info(out.toString());
    }

    /**
     * Respuesta a un mensaje
     * @param protocol
     * @param performative
     * @param content
     * @author Diego Garcia Aurelio
     */
    public void replyMessage(String protocol, int performative, String content, String replyWithR) {
        out = in.createReply();
        out.setProtocol(protocol);
        out.setPerformative(performative);
        out.setContent(content);
        out.setInReplyTo(replyWith);
        if(!replyWithR.isEmpty()){
            out.setReplyWith(replyWithR);
        }
        out.setConversationId(conversationID);
        this.send(out);
        Info(out.toString());
    }
}
