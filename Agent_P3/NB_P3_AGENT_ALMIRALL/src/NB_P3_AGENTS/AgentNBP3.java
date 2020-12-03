/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package larva_hackathon;

import AppBoot.ConsoleBoot;

public class AgentNBP3 {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("HACKATHON", args);
        app.selectConnection();
        
        app.launchAgent("MarcosCastillo", Agent.class);
        app.shutDown();        
    }
    
}
