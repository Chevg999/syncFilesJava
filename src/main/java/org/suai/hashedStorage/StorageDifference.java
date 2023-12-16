package org.suai.hashedStorage;

import java.util.ArrayList;

public class StorageDifference {
    ArrayList<HashedElement> addedElements;
    ArrayList<HashedElement> removedElements;

    public StorageDifference() {
        addedElements = new ArrayList<>();
        removedElements = new ArrayList<>();
    }

    public void addAddedElement(HashedElement element) {
        addedElements.add(element);
    }

    public void addRemovedElement(HashedElement element) {
        removedElements.add(element);
    }
}
