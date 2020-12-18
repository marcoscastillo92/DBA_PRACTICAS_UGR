package NB_P3_AGENTS;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

public abstract class MoveDrone extends BasicDrone {
    @Override
    public void setup(){
        super.setup();
    }
    
    /**
     * Escucha inicial del listener
     * @return true si ha conseguido el mapa
     * @author Diego Garcia Aurelio
     */
    boolean listenInit() {
        in = this.blockingReceive();
        String content = in.getContent();
        
        if(content.contains("map")) {
            Info("Recibido mapa del listening!");
            JsonObject contentObject = new JsonObject(Json.parse(in.getContent()).asObject());
            map.loadMap(contentObject.get("map").asObject());
            worldManager = contentObject.get("WorldManager").toString();
            conversationID = contentObject.get("ConversationID").toString();
            replyWith = contentObject.get("ReplyWith").toString();
            return true;
        }
        
        return false;
    }
}
