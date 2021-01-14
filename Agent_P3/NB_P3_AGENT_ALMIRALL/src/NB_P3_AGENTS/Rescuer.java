package NB_P3_AGENTS;

import YellowPages.YellowPages;
import com.eclipsesource.json.*;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.util.Comparator;
import java.util.PriorityQueue;

class LudwigComparator implements Comparator<Node> {
    public int compare(Node n1, Node n2) {
        return (int)n1.getDistanceToRescuer() - (int)n2.getDistanceToRescuer();
    }
}

public class Rescuer extends MoveDrone {

    @Override
    public void setup(){
        super.setup();
        status = Status.LISTENNING;
        keepAliveSession = true;
        ludwigs = new PriorityQueue<Node>(new LudwigComparator());
    }
    
    @Override
    public void plainExecute() {
        switch(status) {
            case LISTENNING:
                Info("Esperando esperando mensaje del Listener");
                keepAliveSession = this.listenForMessages();
                if(keepAliveSession) {
                    status = Status.SUBSCRIBE_WM;
                }
                else {
                    _exitRequested = true;
                    status = Status.EXIT;
                }
                break;
                
            case SUBSCRIBE_WM:
                keepAliveSession = this.checkIn();
                keepAliveSession &= this.subscribeByType("RESCUER");
                keepAliveSession &= this.loginWorld();
                if(keepAliveSession){
                    this.getProducts();
                    this.setupCurrentState();
                    //status = Status.PLANNING;
                    status = Status.EXIT;
                }else{
                    _exitRequested = true;
                    status = Status.EXIT;
                }

                break;
            
            case PLANNING:
                keepAliveSession = this.listenForMessages();
                /*in = this.blockingReceive();
                if (in.getContent().contains("COIN")) {
                    System.out.println("Mensaje recibido");
                    System.out.println("Guardando coin " + in.getContent());
                    this.wallet.add(in.getContent());
                }
                */
                try{
                    Thread.sleep(10000);
                }catch (Exception e){
                    Info("Exception sleep: " + e);
                }
                if(keepAliveSession) {
                    if (!shops.isEmpty()) {
                        getProducts();
                    }
                }else{
                    _exitRequested = true;
                    status = Status.EXIT;
                }
                status = Status.EXIT;
                break;
                
            case EXIT:
                Info("Se cierra el agente");
                this.exitRequestedToListener();
                break;
                
        }
    }
}
