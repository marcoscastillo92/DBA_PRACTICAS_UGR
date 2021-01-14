package NB_P3_AGENTS;

import jade.lang.acl.ACLMessage;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Seeker extends MoveDrone {
    private Status status;
    private int sensorSize;
    private Stack<Node> visitar;
    private int maxHeight;
    private int minHeight;
    
    @Override
    public void setup(){
        super.setup();
        status = Status.LISTENNING;
        tiendas.add("THERMALDLX");
        tiendas.add("THEMALHQ");
        tiendas.add("THERMAL");
        tiendas.add("COMPASS");
        sensorSize = 0;
        visitar = new Stack<>();
        
        if (this.getAID().toString().indexOf('1') > -1) {
            minHeight = 0;
            maxHeight = (int) map.getHeight() / 3;
        }
        else if (this.getAID().toString().indexOf('2') > -1) {
            minHeight = ((int) map.getHeight() / 3) + 1;
            maxHeight = ((int) map.getHeight() / 3) * 2;
        }
        else {
            minHeight = (((int) map.getHeight() / 3) * 2) + 1;
            maxHeight = map.getHeight();
        }
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
                    this.getSensors();
                    this.setupCurrentState();
                    //status = Status.PLANNING;
                    status = Status.EXIT;
                }else{
                    _exitRequested = true;
                    status = Status.EXIT;
                }

                break;
                
            case PLANNING:
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
                
                keepAliveSession = this.listenForMessages();
                
                try{
                    Thread.sleep(10000);
                }catch (Exception e){
                    Info("Exception sleep: " + e);
                }
                
                if(keepAliveSession){
                    // Si tiene energia suficiente
                    if (hasEnoughtEnergy()) {
                        // Mete los nodos vecinos en la pila
                        this.floodFill();

                        // Si la pila no esta vacia
                        if (!visitar.empty()) {
                            Node from = graphMap.getNode(this.getActualPosition()[0] + "-" + this.getActualPosition()[1]);
                            Node to = visitar.pop();
                            // Puede que ya haya pasado por ese nodo cuando ha visitado otros
                            if (!to.isVisited()) { 
                                // Va desde donde se encuentra al nodo del tope de la pila
                                this.routeFinder.findRoute(from, to);
                                status = Status.ACTING;
                            }
                        }
                        // Si la pila esta vacia
                        else {
                            // Busca si aun queda en la zona algo por explorar
                            boolean continuar = this.findNewNode();
                            // Si no encuentra nada, significa que ha acabado y termina
                            if (!continuar) {
                                status = Status.EXIT;
                            }
                            // Si encuentra, empieza a moverse hacia ahi
                            else {
                                status = Status.ACTING;
                            }
                        }
                    }
                    // Si no tiene energia recarga
                    else {
                        this.land();
                        status = Status.ACTING;
                    }
                }
                else {
                    _exitRequested = true;
                    status = Status.EXIT;
                }
                status = Status.EXIT;
                break;

            case ACTING:
                requestAction(this.actions.get(0));
                
                if (actions.size() > 1) {
                    actions.remove(0);
                }
                else {
                    actions.clear();
                }
                
                if (actions.isEmpty()) {
                    status = Status.PLANNING;
                }
                break;
                
            case EXIT:
                Info("Se cierra el agente");
                this.exitRequestedToListener();
                break;
                
        }
    }
    
    /*
    Guarda en la pila los nodos adyacentes que no haya visitado
    */
    private void floodFill() {
        int[] actualPosition = this.getActualPosition();
        int[] tempPosition = new int[2];
        Node temp;
        boolean fin = false;

        // Arriba derecha
        for (int i = 0; i < (int) sensorSize/2 && !fin; i++) {
            tempPosition[0] = actualPosition[0] + sensorSize - i;
            tempPosition[1] = actualPosition[1] - sensorSize + i;
            if (tempPosition[1] > minHeight && tempPosition[1] < maxHeight) {
                temp = graphMap.getNode(tempPosition[0] + "-" + tempPosition[1]);
                if (!temp.isVisited() && temp.getHeight() > 0) {
                    visitar.push(temp);
                    fin = true;
                }
            }
        }
        
        fin = false;
        
        // Arriba izquierda
        for (int i = 0; i < (int) sensorSize/2 && !fin; i++) {
            tempPosition[0] = actualPosition[0] - sensorSize + i;
            tempPosition[1] = actualPosition[1] - sensorSize + i;
            if (tempPosition[1] > minHeight && tempPosition[1] < maxHeight) {
                temp = graphMap.getNode(tempPosition[0] + "-" + tempPosition[1]);
                if (!temp.isVisited() && temp.getHeight() > 0) {
                    visitar.push(temp);
                    fin = true;
                }
            }
        }

        fin = false;
        
        // Abajo izquierda
        for (int i = 0; i < (int) sensorSize/2 && !fin; i++) {
            tempPosition[0] = actualPosition[0] - sensorSize + i;
            tempPosition[1] = actualPosition[1] + sensorSize - i;
            if (tempPosition[1] > minHeight && tempPosition[1] < maxHeight) {
                temp = graphMap.getNode(tempPosition[0] + "-" + tempPosition[1]);
                if (!temp.isVisited() && temp.getHeight() > 0) {
                    visitar.push(temp);
                    fin = true;
                }
            }
        }
        
        fin = false;
        
        // Abajo derecha
        for (int i = 0; i < (int) sensorSize/2 && !fin; i++) {
            tempPosition[0] = actualPosition[0] + sensorSize - i;
            tempPosition[1] = actualPosition[1] + sensorSize - i;
            if (tempPosition[1] > minHeight && tempPosition[1] < maxHeight) {
                temp = graphMap.getNode(tempPosition[0] + "-" + tempPosition[1]);
                if (!temp.isVisited() && temp.getHeight() > 0) {
                    visitar.push(temp);
                    fin = true;
                }
            }
        }
        
        fin = false;
        
        // Arriba
        for (int i = 0; i < (int) sensorSize/2 && !fin; i++) {
            tempPosition[0] = actualPosition[0];
            tempPosition[1] = actualPosition[1] - sensorSize + i;
            if (tempPosition[1] > minHeight && tempPosition[1] < maxHeight) {
                temp = graphMap.getNode(tempPosition[0] + "-" + tempPosition[1]);
                if (!temp.isVisited() && temp.getHeight() > 0) {
                    visitar.push(temp);
                    fin = true;
                }
            }
        }
        
        fin = false;
        
        // Izquierda
        for (int i = 0; i < (int) sensorSize/2 && !fin; i++) {
            tempPosition[0] = actualPosition[0] - sensorSize + i;
            tempPosition[1] = actualPosition[1];
            if (tempPosition[1] > minHeight && tempPosition[1] < maxHeight) {
                temp = graphMap.getNode(tempPosition[0] + "-" + tempPosition[1]);
                if (!temp.isVisited() && temp.getHeight() > 0) {
                    visitar.push(temp);
                    fin = true;
                }
            }
        }
        
        fin = false;
        
        // Abajo
        for (int i = 0; i < (int) sensorSize/2 && !fin; i++) {
            tempPosition[0] = actualPosition[0];
            tempPosition[1] = actualPosition[1] + sensorSize - i;
            if (tempPosition[1] > minHeight && tempPosition[1] < maxHeight) {
                temp = graphMap.getNode(tempPosition[0] + "-" + tempPosition[1]);
                if (!temp.isVisited() && temp.getHeight() > 0) {
                    visitar.push(temp);
                    fin = true;
                }
            }
        }
        
        fin = false;
        
        // Derecha
        for (int i = 0; i < (int) sensorSize/2 && !fin; i++) {
            tempPosition[0] = actualPosition[0] + sensorSize - i;
            tempPosition[1] = actualPosition[1];
            if (tempPosition[1] > minHeight && tempPosition[1] < maxHeight) {
                temp = graphMap.getNode(tempPosition[0] + "-" + tempPosition[1]);
                if (!temp.isVisited() && temp.getHeight() > 0) {
                    visitar.push(temp);
                    fin = true;
                }
            }
        }
    }
    
    /*
    Cuando el dron no tenga mas nodos cerca que pueda visitar, busca si queda alguno sin visitar en la zona
    Si lo encuentra, lo inserta en la pila y devuelve true
    Si no, devuelve false yse supone que ya ha recorrido toda su zona y su trabajo ha acabado
    */
    private boolean findNewNode() {
        Node temp;
        boolean fin = false;
        
        for (int i = 0; i < map.getWidth() && !fin; i++) {
            for (int j = minHeight; j <= maxHeight && !fin; j++) {
                temp = graphMap.getNode(i + "-" + j);
                if (!temp.isVisited() && temp.getHeight() > 0) {
                    visitar.push(temp);
                    fin = true;
                }
            }
        }
        
        return fin;
    }
    
    // Va completando el mapa conforme el sensor detecta las casillas
    private void paint() {
        int[] actualPosition = this.getActualPosition();
        int distance = (int) sensorSize / 2;
        
        for (int i = actualPosition[0] - distance; i <= actualPosition[0] + distance; i++) {
            if (i >= 0 && i < map.getWidth()) {
                for (int j = actualPosition[1] - distance; j <= actualPosition[1] + distance; j++) {
                    if (j >= 0 && j < map.getHeight()) {
                        graphMap.getNode(i + "-" + j).setVisited(true);
                    }
                }
            }
        }
        
        // LEER SENSOR THERMAL
        // if Thermal = 0 then informLudwigPositionToRescuer(Node position)
    }
}
