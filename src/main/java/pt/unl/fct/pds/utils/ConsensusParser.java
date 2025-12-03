package pt.unl.fct.pds.utils;

import pt.unl.fct.pds.model.Node;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConsensusParser {
    String filename;
    private final CountryFinder geoIpResolver;

    public ConsensusParser(String filename, CountryFinder resolver) {
        this.filename = filename;
        this.geoIpResolver = resolver;
    }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public Node[] parseConsensus() {
        if (filename == null) return new Node[0];

        List<Node> nodes = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean inRelay = false;

            
            String nickname = null;
            String fingerprint = null;
            LocalDateTime published = null;
            String ipAddress = null;
            int orPort = 0;
            int dirPort = 0;
            String[] flags = new String[0];
            String version = null;
            int bandwidth = 0;
            String exitPolicy = null;


            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            while ((line = br.readLine()) != null) {

                // --- r LINE ---
                if (line.startsWith("r ")) {

                    // finalize previous node
                    if (inRelay) {
                        nodes.add(new Node(
                                nickname,
                                fingerprint,
                                published,
                                ipAddress,
                                orPort,
                                dirPort,
                                flags,
                                version,
                                bandwidth,
                                lookupCountryForIp(ipAddress),
                                exitPolicy
                        ));
                    }
                    inRelay = true;
                    String[] toks = line.split("\\s+");
                    
                    if (toks.length >= 9) {
                        nickname = toks[1];
                        fingerprint = toks[2];

                        String date = toks[4];
                        String time = toks[5];
                        try {
                            published = LocalDateTime.parse(date + " " + time, dtf);
                        } catch (DateTimeParseException e) {
                            published = null;
                        }

                        ipAddress = toks[6];

                        try { orPort = Integer.parseInt(toks[7]); }
                        catch (NumberFormatException e) { orPort = 0; }

                        try { dirPort = Integer.parseInt(toks[8]); }
                        catch (NumberFormatException e) { dirPort = 0; }

                    } else {
                        nickname = fingerprint = ipAddress = exitPolicy = null;
                        flags = new String[0];
                        version = null;
                        bandwidth = orPort = dirPort = 0;
                    }
                }

                // --- s LINE ---
                else if (line.startsWith("s ") && inRelay) {
                    flags = line.substring(2).trim().split("\\s+");
                }

                // --- v LINE ---
                else if (line.startsWith("v ") && inRelay) {
                    version = line.substring(2).trim();
                }

                // --- w LINE ---
                else if (line.startsWith("w ") && inRelay) {
                    String[] parts = line.substring(2).trim().split("\\s+");
                    for (String p : parts) {
                        if (p.startsWith("Bandwidth=")) {
                            try { bandwidth = Integer.parseInt(p.substring("Bandwidth=".length())); }
                            catch (NumberFormatException ignored) {}
                        }
                    }
                }

                // --- p LINE ---
                else if (line.startsWith("p ") && inRelay) {
                    exitPolicy = line.substring(2).trim();
                }
            }

            // finalize last relay
            if (inRelay) {
                nodes.add(new Node(
                        nickname,
                        fingerprint,
                        published,
                        ipAddress,
                        orPort,
                        dirPort,
                        flags,
                        version,
                        bandwidth,
                        lookupCountryForIp(ipAddress),
                        exitPolicy
                ));
            }

        } catch (IOException e) {
            System.err.println("Error reading consensus: " + e.getMessage());
        }

        return nodes.toArray(new Node[0]);
    }

    // --- country finder ---
    private String lookupCountryForIp(String ip) {
        if (geoIpResolver == null) return "UNKNOWN";
        return geoIpResolver.lookupCountryForIp(ip);
    }
}