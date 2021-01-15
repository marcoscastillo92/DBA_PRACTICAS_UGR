package NB_P3_AGENTS;

import AppBoot.ConsoleBoot;

public class AgentNBP3 {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("PRACTICA 3", args);
        app.selectConnection();

        app.launchAgent("ALMIRALL_AWACS11", Awacs.class);
        app.launchAgent("ALMIRALL_LISTENER11", Listener.class);
        app.launchAgent("ALMIRALL_SEEKER111", Seeker.class);
//        app.launchAgent("ALMIRALL_SEEKER2", Seeker.class);
//        app.launchAgent("ALMIRALL_SEEKER3", Seeker.class);
//        app.launchAgent("ALMIRALL_RESCUER", Rescuer.class);
        app.shutDown();        
    }
    
}
