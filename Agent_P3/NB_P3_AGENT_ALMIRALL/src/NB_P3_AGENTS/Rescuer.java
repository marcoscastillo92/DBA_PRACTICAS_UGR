package NB_P3_AGENTS;

public class Rescuer extends MoveDrone {
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
                Info("Esperando esperando mensaje del Listener");
                if(this.listenInit()) {
                    status = Status.SUBSCRIBE_WM;
                }
                else {
                    status = Status.EXIT;
                }
                break;
                
            case SUBSCRIBE_WM:
                status = Status.EXIT;
                break;
                
            case EXIT:
                Info("Se cierra el agente");
                _exitRequested = true;
                break;
        }
    }
}
