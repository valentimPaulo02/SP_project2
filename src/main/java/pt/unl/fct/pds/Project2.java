package pt.unl.fct.pds;

import pt.unl.fct.pds.model.Node;

import java.util.Arrays;

import pt.unl.fct.pds.model.Circuit;
import pt.unl.fct.pds.utils.ConsensusParser;
import pt.unl.fct.pds.utils.CountryFinder;
import pt.unl.fct.pds.utils.GeoSelector;
import pt.unl.fct.pds.utils.PathSelector;


/**
 * Application for Tor Path Selection alternatives.
 *
 */
public class Project2 
{
    public static void main( String[] args ) throws Exception
    {
        String consensusPath = "src/main/java/pt/unl/fct/pds/data/consensus.txt";
        String countryDbPath = "src/main/java/pt/unl/fct/pds/data/GeoLite2-Country.mmdb";

        CountryFinder resolver = new CountryFinder(countryDbPath);
        ConsensusParser parser = new ConsensusParser(consensusPath, resolver);

        Node[] nodes = parser.parseConsensus();
        resolver.close();

        PathSelector selector = new PathSelector(nodes);
        Circuit c1 = selector.selectPath(80);

        GeoSelector geo = new GeoSelector(nodes);
        Circuit c2 = geo.selectPath(80);

    
        // VIEWING RESULTS 
        if (nodes == null || nodes.length == 0) {
            System.out.println("No nodes parsed. Check file path / parser errors.");
            return;
        }

        System.out.println("Parsed total nodes: " + nodes.length);
        System.out.println("Printing up to first 5 nodes:\n");

        int toPrint = Math.min(5, nodes.length);
        for (int i = 0; i < toPrint; i++) {
            printNode("Node " + (i+1), nodes[i]);
        }

        System.out.println("================================================");
        System.out.println("Printing nodes in the circuit from path.\n");

        printNode("Guard Node",  c1.getNodes()[0]);
        printNode("Middle Node", c1.getNodes()[1]);
        printNode("Exit Node",   c1.getNodes()[2]);

        System.out.println("================================================");
        System.out.println("Printing nodes in the circuit from geo.\n");

        printNode("Guard Node",  c2.getNodes()[0]);
        printNode("Middle Node", c2.getNodes()[1]);
        printNode("Exit Node",   c2.getNodes()[2]);
    }

    private static void printNode(String title, Node n) {
        System.out.println("=== " + title + " ===");
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
