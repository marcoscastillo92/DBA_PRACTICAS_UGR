package NB_P3_AGENTS;

import DBAMap.DBAMap;
import com.eclipsesource.json.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Map {
    private DBAMap dbaMap;
    private int height;
    private int width;
    private java.util.Map<String,Set<String>> connections;
    private Set<Node> mapSet;
    
    public Map() {
        dbaMap = new DBAMap();
        connections = new HashMap<>();
        mapSet = new HashSet<Node>();
    }
    
    /**
     * Función que carga el Mapa de un agente
     * @param jsonMapFile jsonMapFile
     * @author Marcos Castillo
     */
    public void loadMap(JsonObject jsonMapFile) {
        try{
            String mapfilename = jsonMapFile.getString("filename", "nonamefound");
            System.out.println("Se ha encontrado el mapa: "+mapfilename);
            
            dbaMap.fromJson(jsonMapFile.get("filedata").asArray());
            if(dbaMap.hasMap()){
                height = dbaMap.getHeight();
                width = dbaMap.getWidth();
                fillInfoAStarMap();
                System.out.println("MAP "+mapfilename+" ("+width+" cols x "+height+" rows) saved on project and ready in memory");
            }
            else {
                System.out.println("Error 1 no se ha obtenido el mapa en la subscripción.");
            }
        }catch(IOException e){
            System.out.println("Excepción al cargar mapa: "+e);
        }
    }
    
    /**
     * Método para convertir el mapa en un Array y obtener las conexiones de los nodos
     * @author Marcos Castillo
     */
    public void fillInfoAStarMap() {
        for(int i = 0; i < height; i++){ //Y value
            for(int j = 0; j < width; j++){ //X value
                String key = i+""+j;
                mapSet.add(new Node(key, i, j, dbaMap.getLevel(j, i)));
                String connectionsToNode = getConnectionsFromNodePosition(i,j);
                connections.put(key, Stream.of(connectionsToNode).collect(Collectors.toSet()));
            }
        }
    }
    
    /**
     * Método que obtiene las conexiones de un nodo teniendo en cuenta los límites del mapa.
     * @param x int
     * @param y int
     * @return String 
     * @author Marcos Castillo
     */
    public String getConnectionsFromNodePosition(int x, int y){
        String[][] connectionsToNode = new String[3][3];
        ArrayList<String> response = new ArrayList<String>();
        
        // Ids from nearest Nodes (id format: yx)
        connectionsToNode[0][0] = (y-1)+""+(x-1); //leftTop
        connectionsToNode[0][1] = (y+1)+""+x; //top
        connectionsToNode[0][2] = (y-1)+""+(x+1); //rightTop
        connectionsToNode[1][0] = y+""+(x-1); //left
        connectionsToNode[1][2] = y+""+(x+1); //right
        connectionsToNode[2][0] = (y+1)+""+(x-1); //leftBottom
        connectionsToNode[2][1] = (y-1)+""+x; //bottom
        connectionsToNode[2][2] = (y+1)+""+(x+1); //rightBottom
                
        if(x-1 >= 0){
            response.add(connectionsToNode[1][0]); //left
            if(y-1 >= 0){
                response.add(connectionsToNode[0][0]); //leftTop
            }
            if(y+1 <= height){
                response.add(connectionsToNode[2][0]); //leftBottom
            } 
        }
        if(x+1 <= width){
            response.add(connectionsToNode[1][2]); //right
            if(y-1 >= 0){
                response.add(connectionsToNode[0][2]); //rightTop
            }
            if(y+1 <= height){
                response.add(connectionsToNode[2][2]); //rightBottom
            }
        }
        if(y-1 >= 0){
            response.add(connectionsToNode[0][1]); //top
        }
        if(y+1 <= height){
            response.add(connectionsToNode[2][1]); //bottom
        }
        
        return response.toString().replace("[","").replace("]","").replace(" ","");
    }
    
    /**
     * Método que devuelve las conexiones de los nodos en HashMap
     * @return HashMap
     * @author Marcos Castillo
     */
    public java.util.Map<String,Set<String>> getConnections(){
        return connections;
    }
    
    /**
     * Método que devuelve el mapa en Set
     * @return Set<Node>
     * @author Marcos Castillo
     */
    public Set<Node> asSet(){
        return mapSet;
    }
}
