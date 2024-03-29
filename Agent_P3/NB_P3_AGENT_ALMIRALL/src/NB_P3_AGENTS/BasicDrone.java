package NB_P3_AGENTS;

import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

enum Status {
    CHECKIN_LARVA, SUBSCRIBE_WM, SUBSCRIBE_TYPE, LISTENNING, PLANNING, CANCEL_WM, CHECKOUT_LARVA, EXIT
}

public abstract class BasicDrone extends IntegratedAgent {
    public String service, worldManager, conversationID, replyWith, id_problema;
    ACLMessage out, in;
    Map map;
    
    @Override
    public void setup(){
        super.setup();
        id_problema = "Playground1";
        service = "Analytics group Almirall";
        _identitymanager = "Sphinx";
        _exitRequested = false;
        map = new Map();
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
    }
}
