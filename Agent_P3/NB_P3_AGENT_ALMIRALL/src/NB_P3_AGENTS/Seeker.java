package NB_P3_AGENTS;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Seeker extends MoveDrone {
    private Status status;
    
    @Override
    public void setup(){
        super.setup();
        status = Status.LISTENNING;
    }
    
    @Override
    public void plainExecute() {
        switch(status) {
            case LISTENNING:
                Info("Esperando mensaje inicial del Listener");
                if(this.listenInit()) {
                    status = Status.SUBSCRIBE_WM;
                }
                else {
                    status = Status.EXIT;
                }
                break;
                
            case SUBSCRIBE_WM:
                this.checkIn();
                this.subscribeByType("SEEKER");
                status = Status.PLANNING;
                break;
                
            case PLANNING:
                /* Enviar moneda
                this.sendCoin("ALMIRALL_RESCUER");
                */
                /* Recibir moneda
                in = this.blockingReceive();
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
                _exitRequested = true;
                break;
        }
    }
}
