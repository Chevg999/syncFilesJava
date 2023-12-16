package org.suai;


import org.suai.hashedStorage.HashedStorage;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final DirectorySyncer directorySyncer = new DirectorySyncer();

    public static void main(String[] args) {
        HashedStorage hashedStorage = new HashedStorage();
        String directoryPath = "C:\\Projects\\suai\\syncFilesJava\\temp\\clientFolder";
        directorySyncer.syncDemonWithWatcher(directoryPath, hashedStorage);
        directorySyncer.watch();
        hashedStorage.printRootsPaths();
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

//        hashedStorage.CreatedOrUpdatedEvent(Paths.get("temp/dir1/dir2/file1.txt"), false, 0, "");
//        hashedStorage.CreatedOrUpdatedEvent(Paths.get("temp/dir1/dir2/dir3/file1.txt"), false, 0, "");
//        hashedStorage.CreatedOrUpdatedEvent(Paths.get("temp/dir1/dir2/dir3"), true, 0, "");
//        hashedStorage.CreatedOrUpdatedEvent(Paths.get("temp/dir1/dir2/file2.txt"), false, 0, "");
//        hashedStorage.CreatedOrUpdatedEvent(Paths.get("123"), false, 0, "");
//        hashedStorage.CreatedOrUpdatedEvent(Paths.get("1234"), true, 0, "");
//        hashedStorage.CreatedOrUpdatedEvent(Paths.get("1234/123"), false, 0, "");
//        directorySyncer.scanDirectory(directoryPath, hashedStorage, true);
//        hashedStorage.printRootsPaths();
//        hashedStorage.DeleteEvent(Paths.get("temp/dir1/dir2/file4.txt"), false);
//        hashedStorage.printRootsPaths();
//        System.out.println("Hello world!");
    }
}