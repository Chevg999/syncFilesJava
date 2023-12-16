package org.suai.temp;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.commons.cli.*;
import org.suai.DirectorySyncer;
import org.suai.exceptions.CredentialsException;
import org.suai.hashedStorage.HashedStorage;
import org.suai.models.ClientModel;

public class ChatClient {
    private static final Logger logger = LogManager.getLogger(ChatClient.class);

    private static HashedStorage hashedStorage;
    private static final DirectorySyncer directorySyncer = new DirectorySyncer();
    static String token;
    static String hostname;

    static ClientModel clientModel;

    static String syncRule;

    static int port;

    public static void main(String[] args) {
        Options options = new Options();


        Option loginOption = new Option("l", "login", true, "Login credentials in the format 'login:password'");
        loginOption.setRequired(false);
        options.addOption(loginOption);

        Option serverOption = new Option("s", "server", true, "Server details in the format 'hostname:port'");
        serverOption.setRequired(false);
        options.addOption(serverOption);

        Option ruleOption = new Option("r", "rule", true, "Specify sync rule: Newest, Manual, etc.");
        ruleOption.setRequired(false);
        options.addOption(ruleOption);

        Option serverPathOption = new Option("sp", "server_path", true, "Specify server path");
        serverPathOption.setRequired(false);
        options.addOption(serverPathOption);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("AuthProgram", options);
            return;
        }

        String localSyncPath = cmd.getArgs()[0];

        String login = cmd.getOptionValue("login");
        String server = cmd.getOptionValue("server");
        syncRule = cmd.getOptionValue("rule", "Manual");
        String serverPath = cmd.getOptionValue("server_path");


        if (localSyncPath == null) {
            logger.error("Specify local sync path");
            return;
        }

        logger.info("Chat Client is running...");



        if (login != null && server != null) {
            logger.info("Login mode");
            // Обработка аргументов --login и --server
            String[] loginInfo = login.split(":");
            String[] serverInfo = server.split(":");

            if (loginInfo.length != 2 || serverInfo.length != 2) {
//                System.out.println("Invalid login or server format");
                logger.error("Invalid login or server format");
                return;
            }

            String username = loginInfo[0];
            String password = loginInfo[1];
            String hostname = serverInfo[0];
            int port = Integer.parseInt(serverInfo[1]);

            String token;
            try {
                clientModel = new ClientModel(hostname, port);
                clientModel.connect();
                token = clientModel.doLogin("user", username, password);
            } catch (CredentialsException e) {
                logger.error("Wrong auth data!");
                return;
            }

            saveDataToJson("authData.json", token, hostname, port);
            return;
        } else {
            try {
                readDataFromJson("authData.json");
                clientModel = new ClientModel(hostname, port);
                clientModel.connect();
            } catch (Exception e) {
//                System.out.println("Error reading data from authData.json");
                logger.error("Error reading data from authData.json; That pls run with --login and --server options");
                return;
            }

        }

//        if (syncRule == null) {
//            logger.error("Specify sync rule");
//            return;
//        }


        try {
            clientModel.doAuth("user", token);
        } catch (Exception e) {
//            System.out.println("Error authenticating");
            logger.error("Error authenticating. Token is invalid or expired");
            return;
        }


//        firstSync(localSyncPath);
//        directorySyncer.watch();

        String lastSyncHash = "initalHash";


        hashedStorage = new HashedStorage();
        directorySyncer.syncDemonWithWatcher(localSyncPath, hashedStorage);
        hashedStorage.recalculateHashRecursive();
        directorySyncer.watch();

        hashedStorage.printRootsPaths();
        while (true) {
            directorySyncer.stopWatching();
            hashedStorage.recalculateHashRecursive();
            clientModel.startSync();
            String currentHash = hashedStorage.getHashDirectory(Path.of(""));
            String serverHash = clientModel.getRemoteHash("");

            if (lastSyncHash.equals(currentHash) && !serverHash.equals(currentHash)) {
                logger.atDebug().log("клиентПолучаетСервернуюВерсию.");
                clientDirection(Path.of(""), hashedStorage, Path.of(localSyncPath));
            } else if (!lastSyncHash.equals(currentHash) && serverHash.equals(lastSyncHash)) {
                logger.atDebug().log("серверПолучаетНашуКлиентскуюВерсию.");
                serverDirection(Path.of(""), hashedStorage, Path.of(localSyncPath));
            } else if (!serverHash.equals(currentHash)) {
                logger.atDebug().log("двуСторонняяСинхронизация.");
//                firstSync(localSyncPath);
                Path currentFolder = Path.of("");
                biDirectionSync(currentFolder, hashedStorage, Path.of(localSyncPath));
            }

            hashedStorage.recalculateHashRecursive();
            currentHash = hashedStorage.getHashDirectory(Path.of(""));
            serverHash = clientModel.getRemoteHash("");
            if (serverHash.equals(currentHash)) {
                logger.atDebug().log("СинхронизацияЗавершена.");
                lastSyncHash = currentHash;
            } else {
                logger.error("Произошла ошибка при синхронизации. Остановка программы.");
                clientModel.stopSync();
                return;
            }

            logger.info("All files are synced. Waiting for changes... 30 sec");

            clientModel.stopSync();
            directorySyncer.watch();

            try {
                Thread.sleep(8000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


    // download all files from server that does not exist locally and upload all files that does not exist remotely
    private static void biDirectionSync(Path localSyncPath, HashedStorage hashedStorage, Path rootPath) {
        JSONObject remoteFolder = clientModel.getFilesList(localSyncPath.toString());
        JSONObject localFolderFiles = hashedStorage.getJson(localSyncPath);

        // Json object contains 2 keys dirs and files
        // dirs contains array of objects with keys name and hash
        // files contains array of objects with keys name and hash


        for (int i = 0; i < remoteFolder.getJSONArray("files").length(); i++) {
            JSONObject currentRemoteFile = remoteFolder.getJSONArray("files").getJSONObject(i);
            String currentRemoteFileName = currentRemoteFile.getString("name");
            String currentRemoteFileHash = currentRemoteFile.getString("hash");
            long currentRemoteFileTimestamp = currentRemoteFile.getLong("lastModified");

            // check if remote file not exists locally - then download it
            JSONObject fileExistsLocally = null;
            for (int j = 0; j < localFolderFiles.getJSONArray("files").length(); j++) {
                JSONObject localFile = localFolderFiles.getJSONArray("files").getJSONObject(j);
                String localFileName = localFile.getString("name");
                String localFileHash = localFile.getString("hash");
                if (localFileName.equals(currentRemoteFileName)) {
                    fileExistsLocally = localFile;
                    // remove file from localFolderFiles
                    localFolderFiles.getJSONArray("files").remove(j);
                    break;
                }
            }
            if (fileExistsLocally == null) {
//                clientModel.downloadFile(Path.of(localSyncPath.toString(), currentFileName).toString(), Path.of(localSyncPath.toString(), currentFileName).toString());
                Path relativeDownloadPath = Path.of(localSyncPath.toString(), currentRemoteFileName);
                clientModel.downloadFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentRemoteFileName).toString(), relativeDownloadPath.toString());
                hashedStorage.CreatedOrUpdatedEvent(relativeDownloadPath, false, currentRemoteFileTimestamp, currentRemoteFileHash);
                logger.atDebug().log("File %s not exists locally. Should download it".formatted(currentRemoteFileName));
            } else {
                String currentLocalFileHash = fileExistsLocally.getString("hash");
                long currentLocalFileTimestamp = fileExistsLocally.getLong("lastModified");

                // check if local file hash is different from remote file hash - then update it
                if (!fileExistsLocally.getString("hash").equals(currentRemoteFileHash)) {
                    Path relativePath = Path.of(localSyncPath.toString(), currentRemoteFileName);
                    // Merge conflict.
                    switch (syncRule) {
                        case "Newest" -> {
                            logger.info("Merge conflict was automatically resolved. Newest version was chosen. File: %s".formatted(Path.of(localSyncPath.toString(), currentRemoteFileName).toString()));

                            if (fileExistsLocally.getLong("lastModified") > currentRemoteFileTimestamp) {
                                // local file is newer
                                clientModel.sendFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentRemoteFileName).toString(), relativePath.toString(), currentLocalFileTimestamp, currentLocalFileHash);
//                                logger.atDebug().log("File %s exists remotely but hashes are different. Should update it".formatted(currentFileName));
                            } else {
                                // remote file is newer
                                clientModel.downloadFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentRemoteFileName).toString(), relativePath.toString());
                                hashedStorage.CreatedOrUpdatedEvent(relativePath, false, currentRemoteFileTimestamp, currentRemoteFileHash);
//                                logger.atDebug().log("File %s exists remotely but hashes are different. Should update it".formatted(currentFileName));
                            }
                        }
                        case "Manual" -> {
                            // Ask user which version is better
                            // Remote file date in pretty format
                            String remoteFileDate = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new java.util.Date(currentRemoteFileTimestamp));
                            String localFileDate = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new java.util.Date(fileExistsLocally.getLong("lastModified")));


                            logger.info(("Merge conflict!\n" +
                                    "File: %s\n" +
                                    "1. Remote file date: %s\n" +
                                    "2. Local file date: %s\n" +
                                    "Enter 1 or 2")
                                    .formatted(
                                            Path.of(localSyncPath.toString(),
                                                    currentRemoteFileName).toString(),
                                            remoteFileDate,
                                            localFileDate));

                            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                            String input = null;
                            while (true) {
                                try {
                                    input = reader.readLine();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                if (input.equals("1")) {
                                    // remote file is newer
                                    clientModel.downloadFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentRemoteFileName).toString(), relativePath.toString());
                                    hashedStorage.CreatedOrUpdatedEvent(relativePath, false, currentRemoteFileTimestamp, currentRemoteFileHash);
//                                    logger.atDebug().log("File %s exists remotely but hashes are different. Should update it".formatted(currentFileName));
                                    break;
                                } else if (input.equals("2")) {
                                    // local file is newer
                                    clientModel.sendFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentRemoteFileName).toString(), relativePath.toString(), fileExistsLocally.getLong("lastModified"), fileExistsLocally.getString("hash"));
//                                    logger.atDebug().log("File %s exists remotely but hashes are different. Should update it".formatted(currentFileName));
                                    break;
                                } else {
                                    logger.info("Invalid input. Try again");
                                }
                            }

                        }

                        case "CopyPrefix" -> {
                            logger.info("Merge conflict was automatically resolved. Copy with suffix was chosen. File: %s".formatted(Path.of(localSyncPath.toString(), currentRemoteFileName).toString()));

                            // rename local file
                            String newFileName = "copy_" + currentRemoteFileName;
                            File file = new File(Path.of(rootPath.toString(), localSyncPath.toString(), currentRemoteFileName).toString());
                            File newFile = new File(Path.of(rootPath.toString(), localSyncPath.toString(), newFileName).toString());
                            if (!file.renameTo(newFile)) {
                                throw new RuntimeException("Failed to rename file %s".formatted(currentRemoteFileName));
                            }
                            newFile.setLastModified(currentLocalFileTimestamp);
                            Path relativeNewPath = Path.of(localSyncPath.toString(), newFileName);

                            hashedStorage.CreatedOrUpdatedEvent(relativeNewPath, false, currentLocalFileTimestamp, currentLocalFileHash);
                            clientModel.sendFile(Path.of(rootPath.toString(), relativeNewPath.toString()).toString(), relativeNewPath.toString(), currentLocalFileTimestamp, currentLocalFileHash);

                            clientModel.downloadFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentRemoteFileName).toString(), relativePath.toString());
                            hashedStorage.CreatedOrUpdatedEvent(relativePath, false, currentRemoteFileTimestamp, currentRemoteFileHash);
                        }
                    }
                }
            }
        }

        for (int i = 0; i < localFolderFiles.getJSONArray("files").length(); i++) {
            JSONObject currentFile = localFolderFiles.getJSONArray("files").getJSONObject(i);
            String currentFileName = currentFile.getString("name");
            String currentFileHash = currentFile.getString("hash");
            long currentFileTimestamp = currentFile.getLong("lastModified");

            // check if local file not exists remotely - then upload it
            JSONObject fileExistsRemotely = null;
            for (int j = 0; j < remoteFolder.getJSONArray("files").length(); j++) {
                JSONObject remoteFile = remoteFolder.getJSONArray("files").getJSONObject(j);
                String remoteFileName = remoteFile.getString("name");
                String remoteFileHash = remoteFile.getString("hash");
                if (remoteFileName.equals(currentFileName)) {
                    fileExistsRemotely = remoteFile;
                    // remove file from remoteFolder
                    remoteFolder.getJSONArray("files").remove(j);
                    break;
                }
            }
            if (fileExistsRemotely == null) {
                Path relativeUploadPath = Path.of(localSyncPath.toString(), currentFileName);
                clientModel.sendFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentFileName).toString(), relativeUploadPath.toString(), currentFileTimestamp, currentFileHash);
//                hashedStorage.CreatedOrUpdatedEvent(relativeUploadPath, false, currentFileTimestamp, currentFileHash);
                logger.atDebug().log("File %s not exists remotely. Should upload it".formatted(currentFileName));
            }
        }

        for (int i = 0; i < remoteFolder.getJSONArray("dirs").length(); i++) {
            JSONObject currentDir = remoteFolder.getJSONArray("dirs").getJSONObject(i);
            String currentDirName = currentDir.getString("name");
            String currentDirHash = currentDir.getString("hash");
            Path currentDirPath = Path.of(localSyncPath.toString(), currentDirName);

            JSONObject dirExistsLocally = null;
            for (int j = 0; j < localFolderFiles.getJSONArray("dirs").length(); j++) {
                JSONObject localDir = localFolderFiles.getJSONArray("dirs").getJSONObject(j);
                String localDirName = localDir.getString("name");
                String localDirHash = localDir.getString("hash");
                if (localDirName.equals(currentDirName)) {
                    dirExistsLocally = localDir;
                    // remove dir from localFolderFiles
                    localFolderFiles.getJSONArray("dirs").remove(j);
                    break;
                }
            }
            if (dirExistsLocally == null) {
                Path folderToCreate = Path.of(rootPath.toString(), localSyncPath.toString(), currentDirName);
                hashedStorage.CreatedOrUpdatedEvent(currentDirPath, true, 0, "");
                // Create folder
                logger.atDebug().log("folderToCreate: " + folderToCreate);
                if (!Files.exists(folderToCreate)) {
                    try {
                        Files.createDirectory(folderToCreate);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            biDirectionSync(Path.of(localSyncPath.toString(), currentDirName), hashedStorage, rootPath);
        }

        // check if local dir not exists remotely - then create it
        for (int i = 0; i < localFolderFiles.getJSONArray("dirs").length(); i++) {
            JSONObject currentDir = localFolderFiles.getJSONArray("dirs").getJSONObject(i);
            String currentDirName = currentDir.getString("name");
            String currentDirHash = currentDir.getString("hash");
            Path currentDirPath = Path.of(localSyncPath.toString(), currentDirName);

            JSONObject dirExistsRemotely = null;
            for (int j = 0; j < remoteFolder.getJSONArray("dirs").length(); j++) {
                JSONObject remoteDir = remoteFolder.getJSONArray("dirs").getJSONObject(j);
                String remoteDirName = remoteDir.getString("name");
                String remoteDirHash = remoteDir.getString("hash");
                if (remoteDirName.equals(currentDirName)) {
                    dirExistsRemotely = remoteDir;
                    // remove dir from remoteFolder
                    remoteFolder.getJSONArray("dirs").remove(j);
                    break;
                }
            }
            if (dirExistsRemotely == null) {
                Path folderToCreate = Path.of(rootPath.toString(), localSyncPath.toString(), currentDirName);
                hashedStorage.CreatedOrUpdatedEvent(currentDirPath, true, 0, "");
                // Create folder
                logger.atDebug().log("folder not exists remotely. Should create it %s".formatted(folderToCreate));
                clientModel.createDirectory(currentDirPath.toString());
            }
        }
    }

    // upload all files from local to server. Remove, update and create files on server
    // VERSION ON SERVER WILL BE SAME AS VERSION ON CLIENT
    private static void serverDirection(Path localSyncPath, HashedStorage hashedStorage, Path rootPath) {
        JSONObject remoteFolder = clientModel.getFilesList(localSyncPath.toString());
        JSONObject localFolderFiles = hashedStorage.getJson(localSyncPath);

        // Json object contains 2 keys dirs and files
        // dirs contains array of objects with keys name and hash
        // files contains array of objects with keys name and hash

        // цель: достичь на сервере такого же состояния, как и на клиенте 1 в1

        // 1. Пройтись по всем локальным файлам.
        // 1.1 Попытаться найти данный файл в списке файлов на сервере.
        // 1.1.0 Если файл найден. Сохранить его в переменную. И удалить из временного списка файлов на сервере.
        // 1.2.1 Если файл не найден, то загрузить его на сервер.
        // 1.2.2 Если файл найден, то проверить хэш. Если хэш разный, то загрузить файл на сервер.

        // 2.0 Описание: Все файлы, которые остались в списке файлов на сервере, но не были найдены на клиенте, будут удалены.
        // 2.1 Пройтись по всем файлам, которые остались в списке файлов на сервере.
        // 2.1.1 Удалить файл на сервере.


        for (int i = 0; i < localFolderFiles.getJSONArray("files").length(); i++) {
            JSONObject currentFile = localFolderFiles.getJSONArray("files").getJSONObject(i);
            String currentFileName = currentFile.getString("name");
            String currentFileHash = currentFile.getString("hash");
            long currentFileTimestamp = currentFile.getLong("lastModified");

            // check if local file not exists remotely - then upload it
            JSONObject fileExistsRemotely = null;
            for (int j = 0; j < remoteFolder.getJSONArray("files").length(); j++) {
                JSONObject remoteFile = remoteFolder.getJSONArray("files").getJSONObject(j);
                String remoteFileName = remoteFile.getString("name");
                String remoteFileHash = remoteFile.getString("hash");
                if (remoteFileName.equals(currentFileName)) {
                    fileExistsRemotely = remoteFile;
                    // remove file from remoteFolder
                    remoteFolder.getJSONArray("files").remove(j);
                    break;
                }
            }
            if (fileExistsRemotely == null) {
                Path relativeUploadPath = Path.of(localSyncPath.toString(), currentFileName);
                clientModel.sendFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentFileName).toString(), relativeUploadPath.toString(), currentFileTimestamp, currentFileHash);
//                hashedStorage.CreatedOrUpdatedEvent(relativeUploadPath, false, currentFileTimestamp, currentFileHash);
                logger.atDebug().log("File %s not exists remotely. Should upload it".formatted(currentFileName));
            } else {
                // check if remote file hash is different from local file hash - then update it
                if (!fileExistsRemotely.getString("hash").equals(currentFileHash)) {
                    Path relativeUploadPath = Path.of(localSyncPath.toString(), currentFileName);
                    clientModel.sendFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentFileName).toString(), relativeUploadPath.toString(), currentFileTimestamp, currentFileHash);
                    logger.atDebug().log("File %s exists remotely but hashes are different. Should update it".formatted(currentFileName));
                }
            }
        }

        // now in remote folder array only files that does not exist locally
        // remove all files on remoteFolder that does not exist locally
        for (int i = 0; i < remoteFolder.getJSONArray("files").length(); i++) {
            JSONObject currentFile = remoteFolder.getJSONArray("files").getJSONObject(i);
            String currentFileName = currentFile.getString("name");
            clientModel.deleteElement(Path.of(localSyncPath.toString(), currentFileName).toString(), false);

            logger.atDebug().log("File %s exists remotely but not exists locally. Should delete it".formatted(currentFileName));
        }

        for (int i = 0; i < localFolderFiles.getJSONArray("dirs").length(); i++) {
            JSONObject currentDir = localFolderFiles.getJSONArray("dirs").getJSONObject(i);
            String currentDirName = currentDir.getString("name");
            String currentDirHash = currentDir.getString("hash");
            Path currentDirPath = Path.of(localSyncPath.toString(), currentDirName);

            JSONObject dirExistsRemotely = null;
            for (int j = 0; j < remoteFolder.getJSONArray("dirs").length(); j++) {
                JSONObject remoteDir = remoteFolder.getJSONArray("dirs").getJSONObject(j);
                String remoteDirName = remoteDir.getString("name");
                String remoteDirHash = remoteDir.getString("hash");
                if (remoteDirName.equals(currentDirName)) {
                    dirExistsRemotely = remoteDir;
                    // remove dir from remoteFolder
                    remoteFolder.getJSONArray("dirs").remove(j);
                    break;
                }
            }
            if (dirExistsRemotely == null) {
                Path folderToCreate = Path.of(rootPath.toString(), localSyncPath.toString(), currentDirName);
//                hashedStorage.CreatedOrUpdatedEvent(currentDirPath, true, 0, "");
                // Create folder
                logger.atDebug().log("folder not exists remotely. Should create it %s".formatted(folderToCreate));
                clientModel.createDirectory(currentDirPath.toString());
                serverDirection(Path.of(localSyncPath.toString(), currentDirName), hashedStorage, rootPath);
            } else {
                // recursively call if hash is different
                if (!dirExistsRemotely.getString("hash").equals(currentDirHash)) {
                    serverDirection(Path.of(localSyncPath.toString(), currentDirName), hashedStorage, rootPath);
                }
            }
        }

        for (int i = 0; i < remoteFolder.getJSONArray("dirs").length(); i++) {
            JSONObject currentDir = remoteFolder.getJSONArray("dirs").getJSONObject(i);
            String currentDirName = currentDir.getString("name");

            clientModel.deleteElement(Path.of(localSyncPath.toString(), currentDirName).toString(), true);
            logger.atDebug().log("folder not exists locally, but exist in server. Should delete it %s".formatted(currentDirName));
        }
    }

    private static void clientDirection(Path localSyncPath, HashedStorage hashedStorage, Path rootPath) {
        JSONObject remoteFolder = clientModel.getFilesList(localSyncPath.toString());
        JSONObject localFolderFiles = hashedStorage.getJson(localSyncPath);

        // Json object contains 2 keys dirs and files
        // dirs contains array of objects with keys name and hash
        // files contains array of objects with keys name and hash

        // цель: достичь на клиенте такого же состояния, как и на сервере 1 в1

        for (int i = 0; i < remoteFolder.getJSONArray("files").length(); i++) {
            JSONObject currentFile = remoteFolder.getJSONArray("files").getJSONObject(i);
            String currentFileName = currentFile.getString("name");
            String currentFileHash = currentFile.getString("hash");
            long currentFileTimestamp = currentFile.getLong("lastModified");

            // check if remote file not exists locally - then download it
            JSONObject fileExistsLocally = null;
            for (int j = 0; j < localFolderFiles.getJSONArray("files").length(); j++) {
                JSONObject localFile = localFolderFiles.getJSONArray("files").getJSONObject(j);
                String localFileName = localFile.getString("name");
                String localFileHash = localFile.getString("hash");
                if (localFileName.equals(currentFileName)) {
                    fileExistsLocally = localFile;
                    // remove file from localFolderFiles
                    localFolderFiles.getJSONArray("files").remove(j);
                    break;
                }
            }
            if (fileExistsLocally == null) {
//                clientModel.downloadFile(Path.of(localSyncPath.toString(), currentFileName).toString(), Path.of(localSyncPath.toString(), currentFileName).toString());
                Path relativeDownloadPath = Path.of(localSyncPath.toString(), currentFileName);
                clientModel.downloadFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentFileName).toString(), relativeDownloadPath.toString());
                hashedStorage.CreatedOrUpdatedEvent(relativeDownloadPath, false, currentFileTimestamp, currentFileHash);
                logger.atDebug().log("File %s not exists locally. Should download it".formatted(currentFileName));
            } else {
                // check if local file hash is different from remote file hash - then update it
                if (!fileExistsLocally.getString("hash").equals(currentFileHash)) {
                    Path relativeDownloadPath = Path.of(localSyncPath.toString(), currentFileName);
                    clientModel.downloadFile(Path.of(rootPath.toString(), localSyncPath.toString(), currentFileName).toString(), relativeDownloadPath.toString());
                    hashedStorage.CreatedOrUpdatedEvent(relativeDownloadPath, false, currentFileTimestamp, currentFileHash);
                    logger.atDebug().log("File %s exists locally but hashes are different. Should update it".formatted(currentFileName));
                }
            }
        }

        // now in local folder array only files that does not exist remotely
        // remove all files on localFolderFiles that does not exist remotely
        for (int i = 0; i < localFolderFiles.getJSONArray("files").length(); i++) {
            JSONObject currentFile = localFolderFiles.getJSONArray("files").getJSONObject(i);
            String currentFileName = currentFile.getString("name");
            Path relativeDeletePath = Path.of(localSyncPath.toString(), currentFileName);
            String fullPath = Path.of(rootPath.toString(), localSyncPath.toString(), currentFileName).toString();

            File file = new File(fullPath);
            if (!file.exists()) {
                hashedStorage.DeleteEvent(relativeDeletePath, false);
                logger.error("Trying to delete file %s, but it does not exist locally".formatted(currentFileName));
                continue;
            }

            if (!file.delete()) {
                logger.error("Failed to delete file %s".formatted(currentFileName));
                throw new RuntimeException("Failed to delete file %s".formatted(currentFileName));
            }

            hashedStorage.DeleteEvent(relativeDeletePath, false);
            logger.atDebug().log("File %s exists locally but not exists remotely. Should delete it".formatted(currentFileName));
        }

        for (int i = 0; i < remoteFolder.getJSONArray("dirs").length(); i++) {
            JSONObject currentDir = remoteFolder.getJSONArray("dirs").getJSONObject(i);
            String currentDirName = currentDir.getString("name");
            String currentDirHash = currentDir.getString("hash");
            Path currentDirPath = Path.of(localSyncPath.toString(), currentDirName);

            JSONObject dirExistsLocally = null;
            for (int j = 0; j < localFolderFiles.getJSONArray("dirs").length(); j++) {
                JSONObject localDir = localFolderFiles.getJSONArray("dirs").getJSONObject(j);
                String localDirName = localDir.getString("name");
                String localDirHash = localDir.getString("hash");
                if (localDirName.equals(currentDirName)) {
                    dirExistsLocally = localDir;
                    // remove dir from localFolderFiles
                    localFolderFiles.getJSONArray("dirs").remove(j);
                    break;
                }
            }
            if (dirExistsLocally == null) {
                Path folderToCreate = Path.of(rootPath.toString(), localSyncPath.toString(), currentDirName);
                hashedStorage.CreatedOrUpdatedEvent(currentDirPath, true, 0, "");
                // Create folder
                logger.atDebug().log("folder not exists locally. Should create it %s".formatted(folderToCreate));
                if (!Files.exists(folderToCreate)) {
                    try {
                        Files.createDirectory(folderToCreate);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                clientDirection(Path.of(localSyncPath.toString(), currentDirName), hashedStorage, rootPath);
            } else {
                // recursively call if hash is different
                if (!dirExistsLocally.getString("hash").equals(currentDirHash)) {
                    clientDirection(Path.of(localSyncPath.toString(), currentDirName), hashedStorage, rootPath);
                }
            }
        }

        for (int i = 0; i < localFolderFiles.getJSONArray("dirs").length(); i++) {
            JSONObject currentDir = localFolderFiles.getJSONArray("dirs").getJSONObject(i);
            String currentDirName = currentDir.getString("name");

            Path relativeDeletePath = Path.of(localSyncPath.toString(), currentDirName);
            String fullPath = Path.of(rootPath.toString(), localSyncPath.toString(), currentDirName).toString();

            File file = new File(fullPath);
            if (!file.exists()) {
                hashedStorage.DeleteEvent(relativeDeletePath, true);
                logger.error("Trying to delete folder %s, but it does not exist locally".formatted(currentDirName));
                continue;
            }

//            if (!file.delete()) {
//                logger.error("Failed to delete folder %s".formatted(currentDirName));
////                throw new RuntimeException("Failed to delete folder %s".formatted(currentDirName));
//            }

            while (file.exists()) {
                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    logger.error("Failed to delete folder %s".formatted(currentDirName));
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            hashedStorage.DeleteEvent(relativeDeletePath, true);
            logger.atDebug().log("folder not exists locally, but exist in server. Should delete it %s".formatted(currentDirName));
        }
    }


    private static void saveDataToJson(String fileName, String token, String hostname, int port) {
        // Создаем JSON объект
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("token", token);
        jsonObject.put("hostname", hostname);
        jsonObject.put("port", port);

        // Сохраняем JSON в файл
        try (FileWriter fileWriter = new FileWriter(fileName)) {
            fileWriter.write(jsonObject.toString());
            System.out.println("Authorization data saved to " + fileName);
            logger.info("Authorization data saved to " + fileName);
        } catch (IOException e) {
            System.out.println("Error saving data to " + fileName);
            e.printStackTrace();
        }
    }

    private static void readDataFromJson(String fileName) throws IOException {
        // Читаем данные из JSON файла
        try {
            FileReader fileReader = new FileReader(fileName);
            StringBuilder content = new StringBuilder();
            int character;
            while ((character = fileReader.read()) != -1) {
                content.append((char) character);
            }

            JSONObject jsonObject = new JSONObject(content.toString());
            token = jsonObject.getString("token");

            hostname = jsonObject.getString("hostname");

            port = jsonObject.getInt("port");

            // Здесь можно использовать прочитанные данные
            System.out.println("Token: " + token);
            System.out.println("Hostname: " + hostname);
            System.out.println("Port: " + port);

        } catch (IOException | JSONException e) {
            System.out.println("Error reading data from " + fileName);
            throw e;
//            e.printStackTrace();
        }
    }

}