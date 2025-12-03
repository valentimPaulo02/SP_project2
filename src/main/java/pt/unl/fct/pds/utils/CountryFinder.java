package pt.unl.fct.pds.utils;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CountryResponse;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CountryFinder implements AutoCloseable {
    private final DatabaseReader reader;
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();


    public CountryFinder(String dbFilePath) throws IOException {
        File database = new File(dbFilePath);
        if (!database.exists()) {
            throw new IOException("GeoIP DB not found at: " + dbFilePath);
        }
        this.reader = new DatabaseReader.Builder(database).build();
    }

    public String lookupCountryForIp(String ipWithOptionalPort) {
        if (ipWithOptionalPort == null || ipWithOptionalPort.trim().isEmpty()) return "UNKNOWN";

        String cached = cache.get(ipWithOptionalPort);
        if (cached != null) return cached;

        String ip = stripPort(ipWithOptionalPort.trim());

        try {
            InetAddress addr = InetAddress.getByName(ip);
            CountryResponse response = reader.country(addr);
            if (response != null && response.getCountry() != null && response.getCountry().getName() != null) {
                String country = response.getCountry().getName();
                cache.put(ipWithOptionalPort, country);
                return country;
            }
        } catch (AddressNotFoundException e) {
            // in not in db
        } catch (Exception e) {
            // other probs
        }

        cache.put(ipWithOptionalPort, "UNKNOWN");
        return "UNKNOWN";
    }

    private static String stripPort(String ip) {
        if (ip.startsWith("[") && ip.contains("]")) {
            int idx = ip.indexOf(']');
            return ip.substring(1, idx);
        }
        
        int colonPos = ip.lastIndexOf(':');
        if (colonPos > 0 && ip.indexOf('.') >= 0) {
            return ip.substring(0, colonPos);
        }
        
        return ip;
    }

    @Override
    public void close() throws Exception {
        if (reader != null) {
            reader.close();
        }
    }
}
