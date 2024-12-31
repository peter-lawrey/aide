package build.chronicle.aide.eg;

import net.openhft.chronicle.map.ChronicleMap;

import java.io.File;
import java.io.IOException;

public class KeyValueStore implements AutoCloseable {

    private final ChronicleMap<String, String> map;

    public KeyValueStore(String filePath) throws IOException {
        // Create or load a persisted Chronicle Map
        map = ChronicleMap
                .of(String.class, String.class)
                .averageKey("exampleKey")
                .averageValue("exampleValue")
                .entries(1_000_000) // Number of expected entries
                .createPersistedTo(new File(filePath));
    }

    public void put(String key, String value) {
        map.put(key, value);
    }

    public String get(String key) {
        return map.get(key);
    }

    public void remove(String key) {
        map.remove(key);
    }

    public void close() {
        map.close();
    }

    public static void main(String[] args) throws IOException {
        String filePath = "key-value-store.dat";

        try (KeyValueStore store = new KeyValueStore(filePath)) {
            // Insert key-value pairs
            store.put("user:1", "Alice");
            store.put("user:2", "Bob");

            // Retrieve and print values
            System.out.println("user:1 -> " + store.get("user:1"));
            System.out.println("user:2 -> " + store.get("user:2"));

            // Remove a key
            store.remove("user:1");
            System.out.println("user:1 -> " + store.get("user:1")); // Should print null
        }
    }
}