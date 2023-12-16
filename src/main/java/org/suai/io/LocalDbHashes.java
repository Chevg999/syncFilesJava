package org.suai.io;

import java.util.HashMap;
import java.util.Map;

public class LocalDbHashes {
    private static final Map<String, String> fileHashes = new HashMap<>();

    public void addHash(String filePath, String hash) {
        fileHashes.put(filePath, hash);
    }

    public String getHash(String filePath) {
        return fileHashes.get(filePath);
    }

    public boolean containsHash(String filePath) {
        return fileHashes.containsKey(filePath);
    }

    public void removeHash(String filePath) {
        fileHashes.remove(filePath);
    }
}
