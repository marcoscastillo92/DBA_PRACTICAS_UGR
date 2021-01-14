package NB_P3_AGENTS;

import jade.lang.acl.ACLMessage;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Seeker extends MoveDrone {
    private Status status;
    
    @Override
    public void setup(){
        super.setup();
        status = Status.LISTENNING;
        tiendas.add("THERMALDLX");
        tiendas.add("THEMALHQ");
        tiendas.add("THERMAL");
        tiendas.add("COMPASS");
    }
    
    @Override
    public void plainExecute() {
        switch(status) {
            case LISTENNING:

                Info("Esperando mensaje inicial del Listener");
                keepAliveSession = this.listenForMessages();
                if(keepAliveSession) {
                    status = Status.SUBSCRIBE_WM;
                } else {
                    _exitRequested = true;
                    status = Status.EXIT;
                }
                break;
                
            case SUBSCRIBE_WM:
                keepAliveSession = this.checkIn();
                keepAliveSession &= this.subscribeByType("SEEKER");
                //this.requestAction("Found");
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
                /* Enviar moneda
                this.sendCoin(droneNames.get("rescuer"));
                */
                /* Recibir moneda
                in = this.blockingReceive();
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
                if(keepAliveSession){
                    //TODO
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
