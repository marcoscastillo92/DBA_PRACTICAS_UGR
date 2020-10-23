
package nb_hw_adv;

import AppBoot.ConsoleBoot;


public class NB_P2_AGENT_ALMIRALL {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("AGENT-P1", args);
        app.selectConnection();
        app.launchAgent("ALMIRALL", AgentP2.class);
        app.shutDown();
    }

}















////        app.selectConnection("isg2.ugr.es", 1099);
//        app.selectConnection("localhost", 1099);
////        app.selectConnection();
//        app.launchAgent("Smith", AgentHW.class);
////        app.launchAgent("Neo", AgentHW.class);
////        app.launchAgent("Trinity", AgentHW.class);
//        app.shutDown();
