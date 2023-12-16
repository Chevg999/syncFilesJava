package org.suai;

import java.io.File;
import java.io.IOException;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.methvin.watcher.DirectoryWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.suai.hashedStorage.HashedDirectory;
import org.suai.hashedStorage.HashedElement;
import org.suai.hashedStorage.HashedFile;
import org.suai.hashedStorage.HashedStorage;
import org.suai.models.ClientModel;
import org.suai.utils.HashUtil;

public class DirectorySyncer {
    Path directoryToWatch;

    HashedStorage hashedStorage;
    private static final Logger logger = LogManager.getLogger(DirectorySyncer.class);

    private DirectoryWatcher watcher;

    //    private HashedStorage hashedStorage;
//    public DirectorySyncer(HashedStorage hashedStorage) {
//        this.hashedStorage = hashedStorage;
//    }
    private static final boolean ignoreTempFiles = true;


//    public boolean checkHash(String path, String hash){
//        HashedDirectory currentDirectory = rootDirectory;
//    }

    public boolean checkFilename(String filename) {
        if (ignoreTempFiles) {
            if (
                    filename.startsWith("~$") ||
                            filename.startsWith("~WRL") ||
                            filename.startsWith("~WRD") ||
                            filename.startsWith("~DF") ||
                            filename.startsWith("~RF") ||
                            filename.startsWith("~") ||
                            filename.startsWith(".") ||
                            filename.endsWith("~") ||
                            filename.contains(" ")
            ) {
                return false;
            }
        }
        return true;
    }


    public void syncDemonWithWatcher(String path, HashedStorage userHashedStorage) {
        directoryToWatch = Paths.get(path);
        hashedStorage = userHashedStorage;

        scanDirectory(path, hashedStorage, false);

//        hashedStorage.printRootsPaths();
    }

    private void prepareWatcher() {
        try {
            this.watcher = DirectoryWatcher.builder()
                    .path(directoryToWatch) // or use paths(directoriesToWatch)
                    .listener(event -> {
                        switch (event.eventType()) {
                            case CREATE:
                                Path relative_path = directoryToWatch.relativize(event.path());
                                if (!checkFilename(relative_path.getFileName().toString())) break;
                                logger.atDebug().log("{CREATED_EVENT} Relative path: " + relative_path);
//                                System.out.println("Relative path: " + relative_path);
                                if (event.isDirectory()) {
                                    hashedStorage.CreatedOrUpdatedEvent(relative_path, true, 0, "");
                                } else {
                                    String currentHash = HashUtil.calculateFileHash(event.path().toFile());
                                    hashedStorage.CreatedOrUpdatedEvent(relative_path, false, FileTimeMillis(event.rootPath()), currentHash);
                                }
//                                hashedStorage.printRootsPaths();
//                                System.out.println("Created: " + relative_path);
//                                hashedStorage.printRootsPaths();
                                break;
                            case MODIFY:
//                                System.out.println("Modified: " + event.path());
                                relative_path = directoryToWatch.relativize(event.path());
                                if (!checkFilename(relative_path.getFileName().toString())) break;

                                logger.atDebug().log("{MODIFIED_EVENT} Relative path: " + relative_path);
//                                System.out.println("Relative path: " + relative_path);
                                if (event.isDirectory()) {
                                    hashedStorage.CreatedOrUpdatedEvent(relative_path, true, 0, "");
                                } else {
                                    String currentHash = HashUtil.calculateFileHash(event.path().toFile());
                                    hashedStorage.CreatedOrUpdatedEvent(relative_path, false, FileTimeMillis(event.rootPath()), currentHash);
                                }
                                hashedStorage.printRootsPaths();
                                break;
                            case DELETE:
//                                System.out.println("Deleted: " + event.path());
                                relative_path = directoryToWatch.relativize(event.path());
                                if (!checkFilename(relative_path.getFileName().toString())) break;
                                logger.atDebug().log("{DELETED_EVENT} Relative path: " + relative_path);
//                                System.out.println("Relative path: " + relative_path);
                                hashedStorage.DeleteEvent(relative_path, event.isDirectory());
                                hashedStorage.printRootsPaths();
                                break;
                        }
                    })
                    // .fileHashing(false) // defaults to true
                    // .logger(logger) // defaults to LoggerFactory.getLogger(DirectoryWatcher.class)
                    // .watchService(watchService) // defaults based on OS to either JVM WatchService or the JNA macOS WatchService
                    .build();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void startWatchingCycle() {
        prepareWatcher();
        this.watcher.watch();
    }

    public void stopWatching() {
        try {
            watcher.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<Void> watch() {
        // you can also use watcher.watch() to block the current thread
        prepareWatcher();
        return watcher.watchAsync();
    }

    public void scanDirectory(String path, HashedStorage hashedStorage, boolean lazy) {
        File directory = new File(path);
        HashedDirectory hashedDirectory = new HashedDirectory(directory.getName(), null, null);
        hashedStorage.setRootDirectory(hashedDirectory);
        if (directory.exists() && directory.isDirectory()) {
            try {
                processFiles(directory, hashedDirectory, lazy);
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("Invalid directory path.");
        }
    }

    private void processFiles(File directory, HashedDirectory processingDirectory, boolean lazy) throws IOException, NoSuchAlgorithmException {
        File[] files = directory.listFiles();

        if (processingDirectory == null)
            processingDirectory = new HashedDirectory(directory.getName(), null, null);
        ArrayList<HashedElement> children = new ArrayList<>();
        processingDirectory.setChildren(children);
        if (files != null) {
            for (File file : files) {
                if (file.getName().contains(" ")) continue;

                HashedElement hashedElement;
                if (file.isDirectory()) {
                    HashedDirectory hashedDirectory = new HashedDirectory(file.getName(), null, processingDirectory);
                    processFiles(file, hashedDirectory, lazy);
                    hashedElement = hashedDirectory;
                } else {
                    String filePath = file.getAbsolutePath();
                    String currentHash = lazy ? "" : HashUtil.calculateFileHash(file);

                    Path p = Paths.get(file.getAbsolutePath());


                    hashedElement = new HashedFile(file.getName(), currentHash, FileTimeMillis(p));

//                    if (!localDbHashes.containsHash(filePath) || !localDbHashes.getHash(filePath).equals(currentHash)) {
//                        System.out.println("File changed: " + filePath);
//
//                        // Perform synchronization logic here
//                        // For example, you can copy the file to another location:
//                        File destination = new File("/path/to/destination/" + file.getName());
//                        FileUtils.copyFile(file, destination);
//
//                        // Update the hash in the map
//                        localDbHashes.addHash(filePath, currentHash);
//                    }
                }
                children.add(hashedElement);
            }
        }
    }

    private static long FileTimeMillis(Path p) {
        if (p.toFile().exists()) {
            return p.toFile().lastModified();
        }
        return 0;
//        BasicFileAttributes view
//                = null;
//        try {
//            view = Files.getFileAttributeView(p, BasicFileAttributeView.class)
//            .readAttributes();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        FileTime fileTime=view.lastModifiedTime();
//        return fileTime.toMillis();
    }
}
