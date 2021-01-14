package NB_P3_AGENTS;

import YellowPages.YellowPages;
import com.eclipsesource.json.*;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.PriorityQueue;

import java.util.PriorityQueue;

public class Rescuer extends MoveDrone {

    @Override
    public void setup(){
        super.setup();
        status = Status.LISTENNING;
        keepAliveSession = true;
        ludwigs = new PriorityQueue<Node>(new LudwigComparator());
        tiendas.add("COMPASS");
        tiendas.add("ANGULAR");
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
    
    public boolean onTarget(){
        boolean on = false;
        int x = getActualPosition()[0];
        int y = getActualPosition()[1];
        
        for(Node ludwig : ludwigs){
            if(ludwig.getX()==x && ludwig.getY()==y){
                on = true;
            }
        }
        return on;
    }
    
    // Se ejecuta solo si esta encima de el
    public boolean takeLudwig(int targetHeight){
        JsonObject rescate = new JsonObject();
        
        if(this.isLanded()){
            rescate.add("operation", "rescue");
            
            this.initMessage(worldManager, "REGULAR", rescate.toString(), ACLMessage.REQUEST, conversationID, "");
        }
        else{
            Info("Se baja a por el Ludwig");
            
            this.land();
            
            if(this.isLanded()==true){
                this.initMessage(worldManager, "REGULAR", rescate.toString(), ACLMessage.REQUEST, conversationID, "");
            }
        }
        
        in = this.blockingReceive();
        
        if(in.getPerformative() == ACLMessage.CONFIRM){
            Info("Se ha recogido el Ludwig");
            return true;
        }
        else{
            Info("No se ha podido recoger al Ludwig");
            return false;
        }
    }
    
    /**
     * Método para actualizar la cola con prioridad en base a la nueva distancia al rescuer
     * y recalcular ruta si hay alguno más cercano
     * @author Marcos Castillo
     */
    public void updateDistanceToLudwigsInQueue(){
        if(!ludwigs.isEmpty()){
            int[] positionRescuer = getActualPosition();
            Node oldObjective = ludwigs.peek();
            for(Node ludwig : ludwigs){
                int dX = ludwig.getX() - positionRescuer[0];
                int dY = ludwig.getY() - positionRescuer[1];
                int dH = ludwig.getHeight() - getDroneHeight();
                ludwig.setDistanceToRescuer(Math.sqrt(Math.pow(dX, 2)+Math.pow(dY, 2)) + dH);
            }
            Node newObjective = ludwigs.peek();
            if(newObjective != oldObjective){
                this.findRoute(positionRescuer[0], positionRescuer[1], newObjective.getX(), newObjective.getY());
            }
        }
    }

    @Override
    public boolean listenForMessages(){
        ACLMessage aux = this.blockingReceive();
        if(name.equals(droneNames.get("rescuer")) && aux.getPerformative() == ACLMessage.INFORM_REF){
            JsonObject response = new JsonObject(Json.parse(in.getContent()).asObject());
            //Ludwig founded
            int xPositionLudwig = response.get("xPositionLudwig").asInt();
            int yPositionLudwig = response.get("yPositionLudwig").asInt();
            int ludwigHeight = response.get("ludwigHeight").asInt();
            double distanceToRescuer = calculateDistance(xPositionLudwig, yPositionLudwig, ludwigHeight);

            Node node = new Node(yPositionLudwig+"-"+xPositionLudwig, xPositionLudwig, yPositionLudwig, ludwigHeight, distanceToRescuer);
            ludwigs.add(node);

            updateDistanceToLudwigsInQueue();
            return true;
        }
        return false;
    }
}
