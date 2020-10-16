package nb_hw_adv;

import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import ControlPanel.TTYControlPanel;


public class AgentP1 extends IntegratedAgent {
    String receiver;
    JsonObject login_json;
    JsonArray sensors;
    JsonArray options;
    JsonArray perceptions;
    String key;
    int width, height, maxflight;
    TTYControlPanel myControlPanel;
    
    @Override
    public void setup() {
        super.setup();
        this.doCheckinPlatform();
        this.doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        myControlPanel = new TTYControlPanel(getAID());
        _exitRequested = false;
    }

    @Override
    public void plainExecute() {
        //Dialogar con receiver para entrar en el mundo
        // moverse y leer los sensores
        this.generateLogin();
        this.sendMessage(receiver, login_json);
        ACLMessage in = this.retrieveLoginMessage();
        in = this.readSensors(in);
        this.showInfo(in);
        this.executeAction(in, "moveF");
        _exitRequested = true;
    }

    @Override
    protected void takeDown() {
        this.logoutAgent();
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }
    
    protected JsonObject generateLogin(){
        //Generate JSON body
        login_json = new JsonObject();
        login_json.add("command","login");
        login_json.add("world","BasePlayground");
        
        //Generating JsonArray for sensors
        sensors = new JsonArray();
        sensors.add("alive");
        sensors.add("distance");
        sensors.add("altimeter");
        
        //Adding array to JSON body
        login_json.add("attach", sensors);
        
        return login_json;
    }
    
    public void sendMessage(String receiver, JsonObject body){
        ACLMessage out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        out.setContent(body.toString());
        this.send(out);
    }
    
    public ACLMessage retrieveLoginMessage(){
        ACLMessage in = this.blockingReceive();
        String result = this.getStringContent(in, "result");
        JsonObject answer_json = this.getJsonContent(in);
        if (result.equals("ok")){
            key = this.getStringContent(in, "key");
            width = answer_json.get("width").asInt();
            height = answer_json.get("height").asInt();
            maxflight = answer_json.get("maxflight").asInt();
            options =  answer_json.get("capabilities").asArray();
        }
        
        return in;
    }
    
    public ACLMessage readSensors(ACLMessage in){
        JsonObject read_sensors_json = new JsonObject();
        read_sensors_json.add("command","read");
        read_sensors_json.add("key",key);
        this.replyMessage(in, read_sensors_json);
        
        ACLMessage reply = this.blockingReceive();        
        String result = getStringContent(reply, "result");
        if (result.equals("ok")){
            JsonObject details = getJsonContent(reply, "details");
            perceptions = details.get("perceptions").asArray();
        }else{
            System.out.println("[SENSORS] Error: " + result);
        }
            
        return reply;
    }
    
    public ACLMessage executeAction(ACLMessage msg, String action){
        JsonObject execute_json = new JsonObject();
        execute_json.add("command","execute");
        execute_json.add("action",action);
        execute_json.add("key",key);

        return this.replyMessage(msg, execute_json);
    }
    
    public ACLMessage replyMessage(ACLMessage in, Object content){
        String msg = "";
        //Parseo del contenido a String para añadirlo al mensaje
        if(content instanceof JsonObject){
            msg = ((JsonObject) content).asString();
        }else if (content instanceof String){
            msg = (String) content;
        }else{
            System.out.println("[Reply] Formato de contenido no válido.");
        }
        
        ACLMessage out = in.createReply();
        out.setContent(msg);
        this.sendServer(out);
        return out;
    }
    
    public String getStringContent(ACLMessage msg, String field){
        return Json.parse(msg.getContent()).asObject().get(field).toString();
    }
    
    public JsonObject getJsonContent(ACLMessage msg, String field){
        return Json.parse(msg.getContent()).asObject().get(field).asObject();
    }
    
    public JsonObject getJsonContent(ACLMessage msg){
        return Json.parse(msg.getContent()).asObject();
    }

    private void logoutAgent() {
        JsonObject logout_json = new JsonObject();
        logout_json.add("command","logout");
        
        this.sendMessage(receiver, logout_json);
    }

    private void showInfo(ACLMessage in) {
        myControlPanel.feedData(in, width, height); // width height obtenidos en el login
        myControlPanel.fancyShow();
    }
}
