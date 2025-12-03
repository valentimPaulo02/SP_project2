package pt.unl.fct.pds;

import pt.unl.fct.pds.model.Node;

import java.util.Arrays;

import pt.unl.fct.pds.model.Circuit;
import pt.unl.fct.pds.utils.ConsensusParser;


/**
 * Application for Tor Path Selection alternatives.
 *
 */
public class Project2 
{
    public static void main( String[] args )
    {
        // Here we write our logic to choose circuits!
        System.out.println("Welcome to the Circuit Simulator!");


        // TESTING ----

        // Path to your consensus file (update if your file is elsewhere)
        String consensusPath = "src/main/java/pt/unl/fct/pds/consensus.txt";

        ConsensusParser parser = new ConsensusParser(consensusPath);
        Node[] nodes = parser.parseConsensus();

        if (nodes == null || nodes.length == 0) {
            System.out.println("No nodes parsed. Check file path / parser errors.");
            return;
        }

        System.out.println("Parsed total nodes: " + nodes.length);
        System.out.println("Printing up to first 5 nodes:\n");

        int toPrint = Math.min(5, nodes.length);
        for (int i = 0; i < toPrint; i++) {
            Node n = nodes[i];
            System.out.println("=== Node " + (i+1) + " ===");
            System.out.println("Nickname   : " + n.getNickname());
            System.out.println("Fingerprint: " + n.getFingerprint());
            System.out.println("Published  : " + n.getTimePublished());
            System.out.println("IP Address : " + n.getIpAddress());
            System.out.println("OR Port    : " + n.getOrPort());
            System.out.println("DIR Port   : " + n.getDirPort());
            System.out.println("Flags      : " + Arrays.toString(n.getFlags()));
            System.out.println("Version    : " + n.getVersion());
            System.out.println("Bandwidth  : " + n.getBandwidth());
            System.out.println("Country    : " + n.getCountry());
            System.out.println("ExitPolicy : " + n.getExitPolicy());
            System.out.println();
        }
    }
}
