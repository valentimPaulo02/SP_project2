package pt.unl.fct.pds.utils;

import pt.unl.fct.pds.model.Circuit;
import pt.unl.fct.pds.model.Node;

import java.util.*;
import java.util.stream.Collectors;

public class PathSelector {

    private final Node[] allNodes;
    private final Random rng;

    public PathSelector(Node[] allNodes) {
        this(allNodes, new Random());
    }

    public PathSelector(Node[] allNodes, Random rng) {
        this.allNodes = allNodes;
        this.rng = rng;
    }

    public Circuit selectPath(int destPort) {

        Node exit = selectExit(destPort);
        if (exit == null)
            throw new IllegalStateException("No valid exit node found!");

        Node guard = selectGuard(exit);
        if (guard == null)
            throw new IllegalStateException("No valid guard node found!");

        Node middle = selectMiddle(guard, exit);
        if (middle == null)
            throw new IllegalStateException("No valid middle node found!");

        return new Circuit(
                rng.nextInt(1_000_000),
                new Node[]{ guard, middle, exit },
                Math.min(guard.getBandwidth(), Math.min(middle.getBandwidth(), exit.getBandwidth()))
        );
    }

    // --- exit NODE ---
    private Node selectExit(int destPort) {
        List<Node> exits = Arrays.stream(allNodes)
                .filter(n -> hasFlag(n, "Exit"))
                .filter(n -> hasFlag(n, "Fast"))
                .filter(n -> hasFlag(n, "Running") && hasFlag(n, "Valid"))
                .filter(n -> exitPolicyAllows(n, destPort))
                .filter(n -> n.getBandwidth() > 0)
                .collect(Collectors.toList());

        return weightedSample(exits);
    }

    // --- guard NODE ---
    private Node selectGuard(Node exit) {
        List<Node> guards = Arrays.stream(allNodes)
                .filter(n -> hasFlag(n, "Guard"))
                .filter(n -> hasFlag(n, "Running") && hasFlag(n, "Valid"))
                .filter(n -> n.getBandwidth() > 0)
                .filter(n -> !same16(n, exit))
                .collect(Collectors.toList());

        return weightedSample(guards);
    }

    // --- middle NODE ---
    private Node selectMiddle(Node guard, Node exit) {
        List<Node> candidates = Arrays.stream(allNodes)
                .filter(n -> hasFlag(n, "Fast"))
                .filter(n -> hasFlag(n, "Running") && hasFlag(n, "Valid"))
                .filter(n -> n.getBandwidth() > 0)
                .filter(n -> !sameNode(n, guard) && !sameNode(n, exit))
                .filter(n -> !same16(n, guard) && !same16(n, exit))
                .collect(Collectors.toList());

        return weightedSample(candidates);
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

    private boolean sameNode(Node a, Node b) {
        return a.getFingerprint().equals(b.getFingerprint());
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
