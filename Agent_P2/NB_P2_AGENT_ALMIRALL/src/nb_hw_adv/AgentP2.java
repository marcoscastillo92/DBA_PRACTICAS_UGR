package nb_hw_adv;

import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import ControlPanel.TTYControlPanel;

enum Status {LOGIN, NEEDS_INFO, HAS_ACTIONS, LOGOUT}

public class AgentP2 extends IntegratedAgent {
    String receiver;
    TTYControlPanel myControlPanel;
    //Login variables
    String key;
    int width, height, maxflight;
    JsonArray options;
    JsonArray sensors;
    String world = "BasePlayground";
    String [] sensores = {"alive", "distance", "altimeter"};
    //General use variables
    JsonArray perceptions;
    Status status;
    ACLMessage current;
    
    
    @Override
    public void setup() {
        super.setup();
        this.doCheckinPlatform();
        this.doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        myControlPanel = new TTYControlPanel(getAID());
        status = Status.LOGIN;
        _exitRequested = false;
    }

    @Override
    public void plainExecute() {
        //Dialogar con receiver para entrar en el mundo
        // moverse y leer los sensores
        switch(status){
            case LOGIN:
                current = this.makeLogin();
                break;
            case NEEDS_INFO:
                current = this.readSensors(current);
                break;
            case HAS_ACTIONS:
                this.executeAction(current, "moveF");
                break;
            case LOGOUT:
                _exitRequested = true;
                break;
        }
        this.showInfo(current);
    }

    @Override
    protected void takeDown() {
        this.logoutAgent();
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }
    
    protected JsonObject generateLogin(String world, String[] sensores){
        JsonObject login_json;
        //Generate JSON body
        login_json = new JsonObject();
        login_json.add("command","login");
        login_json.add("world",world);
        
        //Generating JsonArray for sensors
        sensors = new JsonArray();
        for(String sensor : sensores){
            sensors.add(sensor);
        }
        
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
        if (result.equals("ok")){
            key = this.getStringContent(in, "key");
            width = this.getIntContent(in, "width");
            height = this.getIntContent(in, "height");
            maxflight = this.getIntContent(in, "maxflight");
            options =  this.getJsonArrayContent(in, "capabilities");
        }
        
        return in;
    }
    
    public ACLMessage makeLogin(){
        JsonObject login_json = this.generateLogin(this.world, this.sensores);
        this.sendMessage(receiver, login_json);
        return this.retrieveLoginMessage();
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
    
    public void executeAction(ACLMessage msg, String action){
        JsonObject execute_json = new JsonObject();
        execute_json.add("command","execute");
        execute_json.add("action",action);
        execute_json.add("key",key);

        this.replyMessage(msg, execute_json);
    }
    
    public void replyMessage(ACLMessage in, Object content){
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
    
    public int getIntContent(ACLMessage msg, String field){
        return Json.parse(msg.getContent()).asObject().get(field).asInt();
    }
    
    public JsonArray getJsonArrayContent(ACLMessage msg, String field){
        return Json.parse(msg.getContent()).asObject().get(field).asArray();
    }

    private void logoutAgent() {
        JsonObject logout_json = new JsonObject();
        logout_json.add("command","logout");
        
        this.sendMessage(receiver, logout_json);
    }

    private void showInfo(ACLMessage in) {
        myControlPanel.feedData(in, width, height, maxflight); // width height obtenidos en el login
        myControlPanel.fancyShow();
    }
}
