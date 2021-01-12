package NB_P3_AGENTS;

import YellowPages.YellowPages;
import com.eclipsesource.json.*;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

public class Rescuer extends MoveDrone {

    @Override
    public void setup(){
        super.setup();
        status = Status.LISTENNING;
    }
    
    @Override
    public void plainExecute() {
        switch(status) {
            case LISTENNING:
                Info("Esperando esperando mensaje del Listener");
                if(this.listenInit()) {
                    status = Status.SUBSCRIBE_WM;
                }
                else {
                    status = Status.EXIT;
                }
                break;
                
            case SUBSCRIBE_WM:
                this.checkIn();
                this.subscribeByType("RESCUER");
                status = Status.PLANNING;
                break;
            
            case PLANNING:
                /*in = this.blockingReceive();
                if (in.getContent().contains("COIN")) {
                    System.out.println("Mensaje recibido");
                    System.out.println("Guardando coin " + in.getContent());
                    this.wallet.add(in.getContent());
                }
                */
                status = Status.EXIT;
                break;
                
            case EXIT:
                Info("Se cierra el agente");
                this.exitRequestedToListener();
                //this.replyMessage("ANALYTICS", ACLMessage.CANCEL, "");
                _exitRequested = true;
                break;
                
        }
    }
}
