package org.suai.hashedStorage;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.suai.exceptions.server.NotFoundException;
import org.suai.temp.ChatClient;

import java.io.File;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HashedStorage {
    private static final Logger logger = LogManager.getLogger(ChatClient.class);

    @Setter
    @Getter
    HashedDirectory rootDirectory;

    public HashedStorage() {
        rootDirectory = new HashedDirectory("root", null, null);
    }

    public void printRootsPaths() {
        // recursive print all paths
        ArrayList<String> results = new ArrayList<>();

        processPrinting(rootDirectory, results, "");

        for (String result : results) {
            System.out.println(result);
        }
        System.out.println();
    }

    private void processPrinting(HashedDirectory directory, ArrayList<String> result, String prefix_path) {
        if (directory.getChildren() == null) {
            return;
        }

        for (HashedElement child : directory.getChildren()) {
            if (child instanceof HashedDirectory) {
                HashedDirectory hashedDirectory = (HashedDirectory) child;
                String logText = "Directory: %s, contentHash %s".formatted(prefix_path + "/" + hashedDirectory.getName(), hashedDirectory.getContentHash());

//                result.add(logText);
                processPrinting(hashedDirectory, result, prefix_path + "/" + hashedDirectory.getName());
            } else {
                HashedFile hashedFile = (HashedFile) child;
//                String logText = "File: %s, hash: %s contentHash %s".formatted(prefix_path + "/" + hashedFile.getName(), hashedFile.getHash(), hashedFile.getFileHash());
                String logText = "File: %s, contentHash %s".formatted(prefix_path + "/" + hashedFile.getName(), hashedFile.getFileHash());
                result.add(logText);
            }
        }
    }

    public void CreatedOrUpdatedEvent(Path path, boolean isDirectory, long fileTimeEdited, String fileHash) {
        HashedDirectory currentDirectory = rootDirectory;

        if (currentDirectory == null) {
            currentDirectory = new HashedDirectory("root", null, null);
            rootDirectory = currentDirectory;
        }


        for (int i = 0; i < path.getNameCount() - 1; i++) {
            String name = path.getName(i).toString();

            HashedElement child = null;
            if (currentDirectory.getChildren() != null) {
                for (HashedElement currentChild : currentDirectory.getChildren()) {
                    if (currentChild.getName().equals(name) && currentChild instanceof HashedDirectory) {
                        child = currentChild;
                        break;
                    }
                }
            }

            if (child == null) {
                HashedDirectory hashedDirectory = new HashedDirectory(name, null, currentDirectory);
                currentDirectory.addChild(hashedDirectory);
                currentDirectory = hashedDirectory;
            } else {
                currentDirectory = (HashedDirectory) child;
            }
        }

        if (isDirectory) {
            HashedDirectory existedDirectory = null;
            if (currentDirectory.getChildren() != null) {
                for (HashedElement currentChild : currentDirectory.getChildren()) {
                    if (currentChild.getName().equals(path.getName(path.getNameCount() - 1).toString()) && currentChild instanceof HashedDirectory) {
                        existedDirectory = (HashedDirectory) currentChild;
                        break;
                    }
                }
            }

            if (existedDirectory == null) {
                HashedDirectory hashedDirectory = new HashedDirectory(path.getName(path.getNameCount() - 1).toString(), null, currentDirectory);
                currentDirectory.addChild(hashedDirectory);
            }
        } else {
            HashedFile existedFile = null;
            if (currentDirectory.getChildren() != null) {
                for (HashedElement currentChild : currentDirectory.getChildren()) {
                    if (currentChild.getName().equals(path.getName(path.getNameCount() - 1).toString()) && currentChild instanceof HashedFile) {
                        existedFile = (HashedFile) currentChild;
                        break;
                    }
                }
            }

            if (existedFile == null) {
                HashedFile hashedFile = new HashedFile(path.getName(path.getNameCount() - 1).toString(), fileHash, fileTimeEdited);
                currentDirectory.addChild(hashedFile);
            } else {
                existedFile.setFileHash(fileHash);
                existedFile.setTimestamp(fileTimeEdited);
                existedFile.recalculateHash();
            }
        }

    }

    // Delete event. Remove from hash tree
    public void DeleteEvent(Path path, boolean isDirectory) {
        HashedDirectory currentDirectory = rootDirectory;

        if (currentDirectory == null) {
            return;
        }


        for (int i = 0; i < path.getNameCount() - 1; i++) {
            String name = path.getName(i).toString();

            HashedElement child = null;
            if (currentDirectory.getChildren() != null) {
                for (HashedElement currentChild : currentDirectory.getChildren()) {
                    if (currentChild.getName().equals(name) && currentChild instanceof HashedDirectory) {
                        child = currentChild;
                        break;
                    }
                }
            }

            if (child == null) {
                return;
            } else {
                currentDirectory = (HashedDirectory) child;
            }
        }

        if (isDirectory) {
            HashedDirectory existedDirectory = null;
            if (currentDirectory.getChildren() != null) {
                for (HashedElement currentChild : currentDirectory.getChildren()) {
                    if (currentChild.getName().equals(path.getName(path.getNameCount() - 1).toString()) && currentChild instanceof HashedDirectory) {
                        existedDirectory = (HashedDirectory) currentChild;
                        break;
                    }
                }
            }

            if (existedDirectory != null) {
                existedDirectory.recursiveClear();
                currentDirectory.getChildren().remove(existedDirectory);
            }
        } else {
            HashedFile existedFile = null;
            if (currentDirectory.getChildren() != null) {
                for (HashedElement currentChild : currentDirectory.getChildren()) {
                    if (currentChild.getName().equals(path.getName(path.getNameCount() - 1).toString()) && currentChild instanceof HashedFile) {
                        existedFile = (HashedFile) currentChild;
                        break;
                    }
                }
            }

            if (existedFile != null) {
                currentDirectory.getChildren().remove(existedFile);
            }
        }
    }

    public boolean checkHashDirectory(Path path, String hash) {
        HashedDirectory currentDirectory = rootDirectory;
        if (currentDirectory == null) {
            return false;
        }


        for (int i = 0; i < path.getNameCount() - 1; i++) {
            String name = path.getName(i).toString();

            HashedElement child = null;
            if (currentDirectory.getChildren() != null) {
                for (HashedElement currentChild : currentDirectory.getChildren()) {
                    if (currentChild.getName().equals(name) && currentChild instanceof HashedDirectory) {
                        child = currentChild;
                        break;
                    }
                }
            }

            if (child == null) {
                return false;
            } else {
                currentDirectory = (HashedDirectory) child;
            }
        }

        HashedDirectory existedDirectory = null;
        if (currentDirectory.getChildren() != null) {
            for (HashedElement currentChild : currentDirectory.getChildren()) {
                if (currentChild.getName().equals(path.getName(path.getNameCount() - 1).toString()) && currentChild instanceof HashedDirectory) {
                    existedDirectory = (HashedDirectory) currentChild;
                    break;
                }
            }
        }

        if (existedDirectory == null) {
            return false;
        } else {
            logger.atDebug().log("Hashes: " + existedDirectory.getHash() + " " + hash);
            return existedDirectory.getContentHash().equals(hash);
        }
    }

    public String getHashDirectory(Path path) throws NotFoundException {
        HashedDirectory currentDirectory = rootDirectory;
        if (currentDirectory == null) {
            throw new NotFoundException("Directory not found");
        }
        logger.atDebug().log("[getHashDirectory] Path: " + path.toString()+ " "+ path.getNameCount());
        if (path.getNameCount() == 1 && path.getName(0).toString().isEmpty()) {
            return currentDirectory.getContentHash();
        }


        for (int i = 0; i < path.getNameCount() - 1; i++) {
            String name = path.getName(i).toString();

            HashedElement child = null;
            if (currentDirectory.getChildren() != null) {
                for (HashedElement currentChild : currentDirectory.getChildren()) {
                    if (currentChild.getName().equals(name) && currentChild instanceof HashedDirectory) {
                        child = currentChild;
                        break;
                    }
                }
            }

            if (child == null) {
                throw new NotFoundException("Directory not found");
            } else {
                currentDirectory = (HashedDirectory) child;
            }
        }

        HashedDirectory existedDirectory = null;
        if (currentDirectory.getChildren() != null) {
            for (HashedElement currentChild : currentDirectory.getChildren()) {
                if (currentChild.getName().equals(path.getName(path.getNameCount() - 1).toString()) && currentChild instanceof HashedDirectory) {
                    existedDirectory = (HashedDirectory) currentChild;
                    break;
                }
            }
        }

        if (existedDirectory == null) {
            throw new NotFoundException("Directory not found");
        } else {
            logger.atDebug().log("Hash for directory " + path + " is " + existedDirectory.getContentHash());
            return existedDirectory.getContentHash();
        }
    }

    public void recalculateHashRecursive(){
        if (rootDirectory != null) {
            rootDirectory.calculateHashRecursive();
        }
    }

    public JSONObject getJson(Path path) throws NotFoundException {
        HashedDirectory currentDirectory = rootDirectory;
        if (currentDirectory == null) {
            throw new NotFoundException("Directory not found");
        }

        if (path.getNameCount() == 1 && path.getName(0).toString().isEmpty()) {
            return currentDirectory.toJson();
        }

        for (int i = 0; i < path.getNameCount() - 1; i++) {
            String name = path.getName(i).toString();

            HashedElement child = null;
            if (currentDirectory.getChildren() != null) {
                for (HashedElement currentChild : currentDirectory.getChildren()) {
                    if (currentChild.getName().equals(name) && currentChild instanceof HashedDirectory) {
                        child = currentChild;
                        break;
                    }
                }
            }

            if (child == null) {
                throw new NotFoundException("Directory not found");
            } else {
                currentDirectory = (HashedDirectory) child;
            }
        }

        HashedDirectory existedDirectory = null;
        if (currentDirectory.getChildren() != null) {
            for (HashedElement currentChild : currentDirectory.getChildren()) {
                if (currentChild.getName().equals(path.getName(path.getNameCount() - 1).toString()) && currentChild instanceof HashedDirectory) {
                    existedDirectory = (HashedDirectory) currentChild;
                    break;
                }
            }
        }

        if (existedDirectory == null) {
            throw new NotFoundException("Directory not found");
        } else {
            JSONObject jsonObject = existedDirectory.toJson();
            logger.atDebug().log("Hashes: " + jsonObject.toString() + " ");
            return jsonObject;
        }
    }
//    public StorageDifference getDifference(HashedStorage otherStorage) {
//        StorageDifference storageDifference = new StorageDifference();
//
//        processDifference(rootDirectory, otherStorage.rootDirectory, storageDifference);
//
//        return storageDifference;
//    }
//
//    private void processDifference(HashedDirectory directory, HashedDirectory otherDirectory, StorageDifference storageDifference) {
//        if (directory.getChildren() == null) {
//            return;
//        }
//
//        rootDirectory.calculateHashRecursive();
//
//
//        for (HashedElement child : directory.getChildren()) {
//            if (child instanceof HashedDirectory) {
//                HashedDirectory hashedDirectory = (HashedDirectory) child;
//
//                HashedDirectory otherHashedDirectory = null;
//                if (otherDirectory.getChildren() != null) {
//                    for (HashedElement otherChild : otherDirectory.getChildren()) {
//                        if (otherChild instanceof HashedDirectory) {
//                            HashedDirectory otherHashedDirectoryCandidate = (HashedDirectory) otherChild;
//                            if (otherHashedDirectoryCandidate.getName().equals(hashedDirectory.getName())) {
//                                otherHashedDirectory = otherHashedDirectoryCandidate;
//                                break;
//                            }
//                        }
//                    }
//                }
//
//                if (otherHashedDirectory == null) {
//                    storageDifference.addRemovedElement(hashedDirectory);
//                } else {
//                    processDifference(hashedDirectory, otherHashedDirectory, storageDifference);
//                }
//            } else {
//                HashedFile hashedFile = (HashedFile) child;
//
//                HashedFile otherHashedFile = null;
//                if (otherDirectory.getChildren() != null) {
//                    for (HashedElement otherChild : otherDirectory.getChildren()) {
//                        if (otherChild instanceof HashedFile) {
//                            HashedFile otherHashedFileCandidate = (HashedFile) otherChild;
//                            if (otherHashedFileCandidate.getName().equals(hashedFile.getName())) {
//                                otherHashedFile = otherHashedFileCandidate;
//                                break;
//                            }
//                        }
//                    }
//                }
//
//                if (otherHashedFile == null) {
//                    storageDifference.addRemovedElement(hashedFile);
//                } else {
//                    if (!hashedFile.getHash().equals(otherHashedFile.getHash())) {
//                        storageDifference.addRemovedElement(hashedFile);
//                        storageDifference.addAddedElement(otherHashedFile);
//                    }
//                }
//            }
//        }
//    }


}
