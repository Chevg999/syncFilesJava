package org.suai.hashedStorage;

import lombok.Getter;

import lombok.Setter;
import org.suai.utils.HashUtil;

public class HashedFile implements HashedElement {
    @Getter
    String name;

    @Getter
    String hash;

    @Getter
    @Setter
    String fileHash;

    @Setter
    @Getter
    long timestamp;

    public HashedFile(String name, String fileHash, long timestamp) {
        this.name = name;
        this.fileHash = fileHash;
        this.timestamp = timestamp;
        recalculateHash();
    }

    public void recalculateHash() {
//        hash = HashUtil.calculateHash(name + fileHash + timestamp);
        hash = HashUtil.calculateHash(name + fileHash);
    }
}
