package NB_P3_AGENTS;

import AppBoot.ConsoleBoot;

public class AgentNBP3 {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("P3", args);
        app.selectConnection();
        
        app.launchAgent("ALMIRALL_LISTENER", Listener.class);
        app.launchAgent("ALMIRALL_SEEKER1", Seeker.class);
        app.launchAgent("ALMIRALL_SEEKER2", Seeker.class);
        app.launchAgent("ALMIRALL_RESCUER", Seeker.class);
        app.shutDown();        
    }
    
}
