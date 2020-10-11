package nb_hw_adv;

import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;

public class AgentP1 extends IntegratedAgent {
    String receiver;
    JsonObject login_json;
    JsonArray sensors;
    JsonArray options;
    JsonObject perceptions;
    String key;
    int width, height, maxflight;
    
    @Override
    public void setup() {
        super.setup();
        this.doCheckinPlatform();
        this.doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        _exitRequested = false;
    }

    @Override
    public void plainExecute() {
        //Dialogar con receiver para entrar en el mundo
        // moverse y leer los sensores
        this.generateLogin();
        this.sendMessage(receiver, login_json);
        ACLMessage in = this.retrieveLoginMessage();
        JsonObject sensors_json = this.readSensors(in);
        this.executeAction("moveF");
        _exitRequested = true;
    }

    @Override
    protected void takeDown() {
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
        String answer = in.getContent();
        JsonObject answer_json = Json.parse(answer).asObject();
        String result = answer_json.get("result").asString();
        if (result.equals("ok")){
            key = answer_json.get("key").asString();
            width = answer_json.get("width").asInt();
            height = answer_json.get("height").asInt();
            maxflight = answer_json.get("maxflight").asInt();
            options =  answer_json.get("capabilities").asArray();
        }
        
        return in;
    }
    
    public JsonObject readSensors(ACLMessage in){
        ACLMessage out = in.createReply();
        JsonObject read_sensors_json = new JsonObject();
        read_sensors_json.add("command","read");
        read_sensors_json.add("key",key);
        out.setContent(read_sensors_json.toString());
        this.sendServer(out);
        
        ACLMessage reply = this.blockingReceive();
        JsonObject sensors_json = Json.parse(reply.getContent()).asObject();
        
        String result = sensors_json.get("result").asString();
        if (result.equals("ok")){
            JsonObject details = sensors_json.get("details").asObject();
            perceptions = details.get("perceptions").asObject();
        }
            
        return sensors_json;
    }
    
    public boolean executeAction(String action){
        String commands_availables = options.toString();
        if(commands_availables.contains(action)){
            JsonObject execute_json = new JsonObject();
            execute_json.add("command","execute");
            execute_json.add("aciton",action);
            execute_json.add("key",key);

            this.sendMessage(receiver, execute_json);
            ACLMessage in = this.blockingReceive();
            String result = Json.parse(in.getContent()).asObject().get("result").toString();
            return result.equals("ok");
        }else{
            return false;
        }
    }
}
