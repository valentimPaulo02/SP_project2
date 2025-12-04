package pt.unl.fct.pds;

import junit.framework.TestCase;
import pt.unl.fct.pds.model.Circuit;
import pt.unl.fct.pds.model.Node;
import pt.unl.fct.pds.utils.ConsensusParser;
import pt.unl.fct.pds.utils.CountryFinder;
import pt.unl.fct.pds.utils.GeoSelector;
import pt.unl.fct.pds.utils.PathSelector;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class AppTest extends TestCase {


    private static final int TRIALS = 100;
    private static final String CSV_FILE = "evaluation.csv";

    private static final String consensusPath = "src/main/java/pt/unl/fct/pds/data/consensus.txt";
    private static final String countryDbPath = "src/main/java/pt/unl/fct/pds/data/GeoLite2-Country.mmdb";


    public void testConsensusParsing() throws IOException {
        CountryFinder resolver = new CountryFinder(countryDbPath);
        ConsensusParser cp = new ConsensusParser(consensusPath, resolver);
        Node[] nodes = cp.parseConsensus();

        assertNotNull("Parsed nodes should not be null", nodes);
        assertTrue("There should be at least one node in consensus", nodes.length > 0);
    }


    public void testSelectorsBuildOneCircuit() throws IOException {
        CountryFinder resolver = new CountryFinder(countryDbPath);
        ConsensusParser cp = new ConsensusParser(consensusPath, resolver);
        Node[] nodes = cp.parseConsensus();

        PathSelector tor = new PathSelector(nodes);
        GeoSelector geo = new GeoSelector(nodes);

        Circuit c1 = null;
        Circuit c2 = null;
        try {
            c1 = tor.selectPath(80);
        } catch (Exception e) {
            // allow fail
        }
        try {
            c2 = geo.selectPath(80);
        } catch (Exception e) {
            // allow fail
        }

        assertNotNull("Tor selector should produce at least one circuit", c1);
        assertNotNull("Geo selector should produce at least one circuit", c2);

        validateCircuit(c1);
        validateCircuit(c2);
    }


    public void testSelectorsEvaluationAndCsv() throws IOException {
        CountryFinder resolver = new CountryFinder(countryDbPath);
        ConsensusParser cp = new ConsensusParser(consensusPath, resolver);
        Node[] nodes = cp.parseConsensus();

        assertNotNull("Parsed nodes must not be null", nodes);
        assertTrue("Need some nodes in consensus", nodes.length > 0);

        PathSelector tor = new PathSelector(nodes);
        GeoSelector geo = new GeoSelector(nodes);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(CSV_FILE))) {
            bw.write("selector,trial,role,nickname,fingerprint,ip,country,bandwidth\n");

            EvalStats torStats = runTrialsAndWriteCsv(tor, "Tor", nodes, 80, TRIALS, bw);
            EvalStats geoStats = runTrialsAndWriteCsv(geo, "Geo", nodes, 80, TRIALS, bw);

            System.out.println("=== Simple Evaluation Summary ===");
            System.out.printf("Trials per selector: %d\n\n", TRIALS);

            printSummary("Tor", torStats);
            printSummary("Geo", geoStats);

            assertTrue("Tor selector should complete at least 1 trial", torStats.completed > 0);
            assertTrue("Geo selector should complete at least 1 trial", geoStats.completed > 0);

        } catch (IOException e) {
            fail("Could not write CSV: " + e.getMessage());
        }
    }

    // --- helpers ---
    private void validateCircuit(Circuit c) {
        assertNotNull("Circuit must not be null", c);
        Node[] path = c.getNodes();
        assertNotNull("Circuit nodes must not be null", path);
        assertEquals("Circuit must have 3 nodes", 3, path.length);
        assertNotNull("Guard must not be null", path[0]);
        assertNotNull("Middle must not be null", path[1]);
        assertNotNull("Exit must not be null", path[2]);

        String f0 = path[0].getFingerprint();
        String f1 = path[1].getFingerprint();
        String f2 = path[2].getFingerprint();
        assertNotNull(f0); assertNotNull(f1); assertNotNull(f2);
        assertFalse("Guard != Middle", f0.equals(f1));
        assertFalse("Guard != Exit", f0.equals(f2));
        assertFalse("Middle != Exit", f1.equals(f2));

        boolean exitOk = false;
        try {
            String policy = path[2].getExitPolicy();
            if (policy != null && !policy.isEmpty()) {
                if (policy.contains("accept 1-65535") || policy.contains("accept") || !policy.contains("reject 1-65535")) {
                    exitOk = true;
                }
            }
        } catch (Exception ignored) {}
        assertTrue("Exit should allow (likely) port 80", exitOk);
    }

    private static class EvalStats {
        int trialsRequested;
        int completed;
        int distinctCountryCount;
        int subnetCollisions;
        long guardBwSum, middleBwSum, exitBwSum;
        Map<String,Integer> guardFreq = new HashMap<>();
        Map<String,Integer> exitFreq = new HashMap<>();
    }

    private EvalStats runTrialsAndWriteCsv(Object selector, String name, Node[] nodes, int port, int trials, BufferedWriter bw) throws IOException {
        EvalStats s = new EvalStats();
        s.trialsRequested = trials;

        for (int i = 0; i < trials; i++) {
            try {
                Circuit c;
                if (selector instanceof PathSelector) {
                    c = ((PathSelector) selector).selectPath(port);
                } else if (selector instanceof GeoSelector) {
                    c = ((GeoSelector) selector).selectPath(port);
                } else {
                    throw new IllegalArgumentException("Unknown selector");
                }

                validateCircuit(c);

                Node guard = c.getNodes()[0];
                Node middle = c.getNodes()[1];
                Node exit = c.getNodes()[2];

                s.completed++;
                s.guardBwSum += guard.getBandwidth();
                s.middleBwSum += middle.getBandwidth();
                s.exitBwSum += exit.getBandwidth();

                String gc = safe(guard.getCountry());
                String mc = safe(middle.getCountry());
                String ec = safe(exit.getCountry());
                if (!gc.equals(mc) && !gc.equals(ec) && !mc.equals(ec)) s.distinctCountryCount++;

                if (same16(guard, middle) || same16(guard, exit) || same16(middle, exit)) s.subnetCollisions++;

                s.guardFreq.merge(guard.getNickname(), 1, Integer::sum);
                s.exitFreq.merge(exit.getNickname(), 1, Integer::sum);

                writeCsvLine(bw, name, i, "guard", guard);
                writeCsvLine(bw, name, i, "middle", middle);
                writeCsvLine(bw, name, i, "exit", exit);

            } catch (Exception e) {
                System.err.println("[" + name + "] trial " + i + " failed: " + e.getMessage());
            }
        }

        return s;
    }

    private void writeCsvLine(BufferedWriter bw, String selector, int trial, String role, Node n) throws IOException {
        String line = String.format("%s,%d,%s,%s,%s,%s,%s,%d\n",
                selector,
                trial,
                role,
                sanitize(n.getNickname()),
                sanitize(n.getFingerprint()),
                sanitize(n.getIpAddress()),
                sanitize(n.getCountry()),
                n.getBandwidth());
        bw.write(line);
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace(",", "_").replace("\n", " ").replace("\r", " ");
    }

    private static String safe(String s) {
        return s == null ? "UNKNOWN" : s;
    }

    private static boolean same16(Node a, Node b) {
        if (a == null || b == null) return false;
        String ipa = a.getIpAddress();
        String ipb = b.getIpAddress();
        if (ipa == null || ipb == null) return false;
        try {
            String[] pa = ipa.split("\\.");
            String[] pb = ipb.split("\\.");
            if (pa.length < 2 || pb.length < 2) return false;
            return pa[0].equals(pb[0]) && pa[1].equals(pb[1]);
        } catch (Exception e) {
            return false;
        }
    }

    private void printSummary(String name, EvalStats s) {
        System.out.println("=== " + name + " Summary ===");
        System.out.println("Requested trials: " + s.trialsRequested);
        System.out.println("Completed circuits: " + s.completed);
        System.out.printf("Distinct-country circuits: %d (%.2f%%)\n", s.distinctCountryCount,
                s.completed == 0 ? 0.0 : 100.0 * s.distinctCountryCount / s.completed);
        System.out.printf("Subnet collisions (/16): %d (%.2f%%)\n", s.subnetCollisions,
                s.completed == 0 ? 0.0 : 100.0 * s.subnetCollisions / s.completed);
        System.out.printf("Avg guard BW: %.1f, avg middle BW: %.1f, avg exit BW: %.1f\n",
                s.completed == 0 ? 0.0 : (double) s.guardBwSum / s.completed,
                s.completed == 0 ? 0.0 : (double) s.middleBwSum / s.completed,
                s.completed == 0 ? 0.0 : (double) s.exitBwSum / s.completed);

        System.out.println("Top guards:");
        s.guardFreq.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(5).forEach(it -> System.out.println("  " + it.getKey() + " -> " + it.getValue()));

        System.out.println("Top exits:");
        s.exitFreq.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(5).forEach(it -> System.out.println("  " + it.getKey() + " -> " + it.getValue()));

        System.out.println();
    }
}
