package NB_P3_AGENTS;

import DBAMap.DBAMap;
import com.eclipsesource.json.JsonObject;
import java.io.IOException;

public class Map {
    private DBAMap dbaMap;
    
    public Map() {
        dbaMap = new DBAMap();
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
                System.out.println("MAP "+mapfilename+" ("+dbaMap.getWidth()+" cols x "+dbaMap.getHeight()+" rows) saved on project and ready in memory");
            }
            else {
                System.out.println("Error 1 no se ha obtenido el mapa en la subscripción.");
            }
        }catch(IOException e){
            System.out.println("Excepción al cargar mapa: "+e);
        }
    }
}
