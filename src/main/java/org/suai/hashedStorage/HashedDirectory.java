package org.suai.hashedStorage;

import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;
import org.suai.utils.HashUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HashedDirectory implements HashedElement {
    @Getter
    String name;
    @Setter
    @Getter
    ArrayList<HashedElement> children;

    HashedDirectory parent;

    private String calculatedHash;
    @Getter
    private String contentHash;

    public String getHash() {
        return calculatedHash;
    }

    public void addChild(HashedElement child) {
        if (children == null) {
            children = new java.util.ArrayList<>();
        }
        children.add(child);
    }

    public void calculateHashRecursive() {
        StringBuilder sb = new StringBuilder();

        if (children != null) {
            // Sorted by name
            children.sort(Comparator.comparing(HashedElement::getName));

            for (HashedElement child : children) {
                if (child instanceof HashedDirectory hashedDirectory) {
                    hashedDirectory.calculateHashRecursive();
                }
//                else {
//                    HashedFile hashedFile = (HashedFile) child;
//                    hashedFile.recalculateHash();
//                }
                sb.append(child.getHash());
            }
        }

        contentHash = HashUtil.calculateHash(sb.toString());
        sb.append(name);
        calculatedHash = HashUtil.calculateHash(sb.toString());
    }

    public void recursiveClear() {
        if (children != null) {
            for (HashedElement child : children) {
                if (child instanceof HashedDirectory hashedDirectory) {
                    hashedDirectory.recursiveClear();
                }
            }
        }
        children = null;
        parent = null;
    }

    public HashedDirectory(String name, ArrayList<HashedElement> children, HashedDirectory parent) {
        this.name = name;
        this.parent = parent;
        calculateHashRecursive();
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        List<JSONObject> dirs = new ArrayList<>();
        List<JSONObject> files = new ArrayList<>();

        if (children != null) {
            for (HashedElement child : children) {
                JSONObject childJson = new JSONObject();
                childJson.put("name", child.getName());
//                childJson.put("hash", child.getHash());

                if (child instanceof HashedFile hashedFile) {
                    childJson.put("lastModified", hashedFile.getTimestamp());
                    childJson.put("hash", hashedFile.getFileHash());
                    files.add(childJson);
                } else {
                    childJson.put("hash", ((HashedDirectory) child).getContentHash());
                    dirs.add(childJson);
                }
            }
        }

        jsonObject.put("dirs", dirs);
        jsonObject.put("files", files);

        return jsonObject;
    }
}
