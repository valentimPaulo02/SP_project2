package pt.unl.fct.pds.utils;

import pt.unl.fct.pds.model.Circuit;
import pt.unl.fct.pds.model.Node;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


public class GeoSelector {

    private final Node[] allNodes;
    private final Random rng;

    public GeoSelector(Node[] allNodes) {
        this(allNodes, new Random());
    }

    public GeoSelector(Node[] allNodes, Random rng) {
        this.allNodes = allNodes;
        this.rng = rng;
    }

    public Circuit selectPath(int destPort) {
        Node exit = selectExit(destPort);
        if (exit == null) throw new IllegalStateException("No valid exit node found");

        Node guard = selectGuardPreferDifferentCountry(exit);
        if (guard == null) throw new IllegalStateException("No valid guard node found");

        Node middle = selectMiddlePreferDifferentCountries(guard, exit);
        if (middle == null) throw new IllegalStateException("No valid middle node found");

        return new Circuit(
                rng.nextInt(Integer.MAX_VALUE),
                new Node[]{ guard, middle, exit },
                Math.min(Math.min(guard.getBandwidth(), middle.getBandwidth()), exit.getBandwidth())
        );
    }

    // --- exit NODE ---
    private Node selectExit(int destPort) {
        List<Node> exits = Arrays.stream(allNodes)
                .filter(n -> hasFlag(n, "Exit"))
                .filter(n -> hasFlag(n, "Fast"))
                .filter(n -> hasFlag(n, "Running") && hasFlag(n, "Valid"))
                .filter(n -> n.getBandwidth() > 0)
                .filter(n -> exitPolicyAllows(n, destPort))
                .collect(Collectors.toList());

        return weightedSample(exits);
    }

    // --- guard NODE ---
    private Node selectGuardPreferDifferentCountry(Node exit) {
        String exitCountry = exit.getCountry();

        List<Node> preferred = Arrays.stream(allNodes)
                .filter(n -> hasFlag(n, "Guard"))
                .filter(n -> hasFlag(n, "Running") && hasFlag(n, "Valid"))
                .filter(n -> n.getBandwidth() > 0)
                .filter(n -> !same16(n, exit))
                .filter(n -> !sameFingerprint(n, exit))
                .filter(n -> !Objects.equals(n.getCountry(), exitCountry))
                .collect(Collectors.toList());

        if (!preferred.isEmpty()) return weightedSample(preferred);

        List<Node> fallback = Arrays.stream(allNodes)
                .filter(n -> hasFlag(n, "Guard"))
                .filter(n -> hasFlag(n, "Running") && hasFlag(n, "Valid"))
                .filter(n -> n.getBandwidth() > 0)
                .filter(n -> !same16(n, exit))
                .filter(n -> !sameFingerprint(n, exit))
                .collect(Collectors.toList());

        if (!fallback.isEmpty()) return weightedSample(fallback);

        List<Node> last = Arrays.stream(allNodes)
                .filter(n -> hasFlag(n, "Guard"))
                .filter(n -> hasFlag(n, "Running") && hasFlag(n, "Valid"))
                .filter(n -> n.getBandwidth() > 0)
                .filter(n -> !sameFingerprint(n, exit))
                .collect(Collectors.toList());

        return weightedSample(last);
    }

    // --- middle NODE ---
    private Node selectMiddlePreferDifferentCountries(Node guard, Node exit) {
        Set<String> forbiddenCountries = new HashSet<>();
        if (guard.getCountry() != null) forbiddenCountries.add(guard.getCountry());
        if (exit.getCountry() != null) forbiddenCountries.add(exit.getCountry());

        List<Node> preferred = Arrays.stream(allNodes)
                .filter(n -> hasFlag(n, "Fast"))
                .filter(n -> hasFlag(n, "Running") && hasFlag(n, "Valid"))
                .filter(n -> n.getBandwidth() > 0)
                .filter(n -> !sameFingerprint(n, guard) && !sameFingerprint(n, exit))
                .filter(n -> !same16(n, guard) && !same16(n, exit))
                .filter(n -> !forbiddenCountries.contains(n.getCountry()))
                .collect(Collectors.toList());

        if (!preferred.isEmpty()) return weightedSample(preferred);

        List<Node> fallback = Arrays.stream(allNodes)
                .filter(n -> hasFlag(n, "Fast"))
                .filter(n -> hasFlag(n, "Running") && hasFlag(n, "Valid"))
                .filter(n -> n.getBandwidth() > 0)
                .filter(n -> !sameFingerprint(n, guard) && !sameFingerprint(n, exit))
                .filter(n -> !same16(n, guard) && !same16(n, exit))
                .collect(Collectors.toList());

        if (!fallback.isEmpty()) return weightedSample(fallback);

        List<Node> last = Arrays.stream(allNodes)
                .filter(n -> hasFlag(n, "Fast"))
                .filter(n -> hasFlag(n, "Running") && hasFlag(n, "Valid"))
                .filter(n -> n.getBandwidth() > 0)
                .filter(n -> !sameFingerprint(n, guard) && !sameFingerprint(n, exit))
                .collect(Collectors.toList());

        return weightedSample(last);
    }

    // --- helpers ---
    private boolean exitPolicyAllows(Node node, int destPort) {
        String policy = node.getExitPolicy();
        if (policy == null || policy.isEmpty())
            return false;

        if (policy.contains("reject 1-65535"))
            return false;

        String[] lines = policy.split("\n");
        boolean allowed = false;

        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2) continue;

            String action = parts[0];
            String range = parts[1];

            if (range.contains("-")) {
                String[] ports = range.split("-");
                try {
                    int low = Integer.parseInt(ports[0]);
                    int high = Integer.parseInt(ports[1]);
                    if (destPort >= low && destPort <= high) {
                        if (action.equalsIgnoreCase("accept")) allowed = true;
                        if (action.equalsIgnoreCase("reject")) allowed = false;
                    }
                } catch (Exception ignored) {}
            } else {
                try {
                    int port = Integer.parseInt(range);
                    if (port == destPort) {
                        if (action.equalsIgnoreCase("accept")) allowed = true;
                        if (action.equalsIgnoreCase("reject")) allowed = false;
                    }
                } catch (Exception ignored) {}
            }
        }

        return allowed;
    }

    private boolean hasFlag(Node n, String flag) {
        return Arrays.asList(n.getFlags()).contains(flag);
    }

    private boolean sameFingerprint(Node a, Node b) {
        return a.getFingerprint() != null && a.getFingerprint().equals(b.getFingerprint());
    }

    private boolean same16(Node a, Node b) {
        try {
            String[] pa = a.getIpAddress().split("\\.");
            String[] pb = b.getIpAddress().split("\\.");
            return pa[0].equals(pb[0]) && pa[1].equals(pb[1]);
        } catch (Exception e) {
            return false;
        }
    }

    private Node weightedSample(List<Node> list) {
        if (list.isEmpty()) return null;
        if (list.size() == 1) return list.get(0);

        double total = 0;
        for (Node n : list) {
            total += n.getBandwidth();
        }

        double r = rng.nextDouble() * total;

        for (Node n : list) {
            r -= n.getBandwidth();
            if (r <= 0)
                return n;
        }

        return list.get(list.size() - 1);
    }
}
