package org.suai.temp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.logging.log4j.ThreadContext;
import org.json.JSONObject;
import org.suai.DirectorySyncer;
import org.suai.exceptions.server.NotFoundException;
import org.suai.hashedStorage.HashedStorage;
import org.suai.models.Response;
import org.suai.services.UserService;

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class ChatServer {
    private static Set<PrintWriter> clientWriters = new HashSet<>();

    private static final UserService userService = UserService.getInstance();

    private static final int SERVER_PORT = 12345;

    private static final String serverPath = "C://Progects//FileServ";

    private static HashedStorage hashedStorage;
    private static final DirectorySyncer directorySyncer = new DirectorySyncer();

    private static final Logger logger = LogManager.getLogger(ChatServer.class);

    private static String checkHash(String username, String path) throws NotFoundException {
        // Path is username + path
        Path path1 = Path.of(username, path);
        logger.atDebug().log("[ChatServer] Checking hash for file: " + path1);
        return hashedStorage.getHashDirectory(path1);
    }

    private static JSONObject getFilesList(String username, String path) throws NotFoundException {
        // Path is username + path
        Path path1 = Path.of(username, path);
        logger.atDebug().log("[ChatServer] Getting files list for path: " + path1);


        return hashedStorage.getJson(path1);
    }

    public static void main(String[] args) {
        userService.loadFromFile();
        hashedStorage = new HashedStorage();
        directorySyncer.syncDemonWithWatcher(serverPath, hashedStorage);
        hashedStorage.recalculateHashRecursive();
//        directorySyncer.watch();

//        System.out.println("Server is running...");
        logger.info("Server is running on port " + SERVER_PORT + "...");
        ServerSocket serverSocket = null;

        try {
            serverSocket = new ServerSocket(SERVER_PORT);

            while (true) {
                logger.info("Waiting for a client...");
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        private InputStream inputStream;

        private OutputStream outputStream;

        String role;
        String username;
        String token;

        ReentrantLock transactionLocker;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void ManagerConnection() throws IOException {
            String message;

            while ((message = in.readLine()) != null) {
                if (message.startsWith("@createUser")) {
                    String[] parts = message.split(" ");
                    if (parts.length < 4) {
                        answer(1, "PROTOCOL ERROR");
                        return;
                    }
                    String role = parts[1];
                    String username = parts[2];
                    String password = parts[3];

                    if (userService.checkUser(role, username, password)) {
                        answer(3, "USER ALREADY EXISTS");
                        return;
                    }

                    switch (role) {
                        case "user" -> userService.createBasicUser(username, password);
                        case "admin" -> userService.createAdmin(username, password);
                        default -> {
                            answer(1, "PROTOCOL ERROR");
                            return;
                        }
                    }
                    answer(0, "OK");
                }
            }
        }

        public void ClientConnection() throws IOException, SocketException {
            String message;

            while ((message = in.readLine()) != null) {
                ThreadContext.clearStack();
                ThreadContext.push(UUID.randomUUID().toString());
                logger.atDebug().log("[ClientHandler] Message from client: " + message);
                if (message.startsWith("@startSync")) {
                    transactionLocker.lock();
                    logger.atDebug().log("[ClientHandler] Starting sync");
                    answer(0, "OK");
                } else if (message.startsWith("@stopSync")) {
                    transactionLocker.unlock();
                    logger.atDebug().log("[ClientHandler] Stopping sync");
                    answer(0, "OK");
                } else if (message.startsWith("@getHash")) {
                    String[] parts = message.split(" ");
                    if (parts.length < 2) {
//                        answer(1, "PROTOCOL ERROR");
                        String[] parts_new = new String[2];
                        parts_new[0] = parts[0];
                        parts_new[1] = "";
                        parts = parts_new;
                    }
                    String filename = parts[1];
                    logger.atDebug().log("[ClientHandler] Checking hash for file: " + filename);
                   try {
                       String hash = checkHash(username, filename);
                       answer(0, hash);
                   } catch (NotFoundException e) {
                       answer(6, "FILE NOT FOUND");
                   }
                } else if (message.startsWith("@sendFile")) {
                    String[] parts = message.split(" ");
                    if (parts.length < 5) {
                        answer(1, "PROTOCOL ERROR");
                        return;
                    }
                    String filename = parts[1];
                    long fileSize = Long.parseLong(parts[2]);
                    long timestamp = Long.parseLong(parts[3]);
                    String contentHash = parts[4];
                    logger.info("[ClientHandler] Receiving file: " + filename + " " + fileSize);
                    String path = Path.of(serverPath, username, filename).toString();


                    File file = new File(path);
                    // Check folder for existence
                    if (!file.getParentFile().exists()) {
                        answer(7, "INTERNAL ERROR");
                        continue;
                    }
                    answer(0, "OK");

                    // Check if path to folder exists and create if not
                    File folder = new File(path).getParentFile();
                    if (!folder.exists()) {
                        folder.mkdirs();
                    }

                    FileOutputStream fileOutputStream = new FileOutputStream(path);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while (fileSize > 0 && (bytesRead = inputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead);
                        fileSize -= bytesRead;
                    }
                    fileOutputStream.close();
                    hashedStorage.CreatedOrUpdatedEvent(Path.of(username, filename), false, timestamp, contentHash);
                    hashedStorage.recalculateHashRecursive();
                    // set timestamp
                    if (!file.setLastModified(timestamp))
                        logger.atError().log("Failed to set timestamp for file: " + filename);
                } else if (message.startsWith("@downloadFile")) {
                    String[] parts = message.split(" ");
                    if (parts.length < 2) {
                        answer(1, "PROTOCOL ERROR");
                        return;
                    }
                    String filename = parts[1];
                    String path = Path.of(serverPath, username, filename).toString();
                    File file = new File(path);
                    if (!file.exists()) {
                        answer(6, "FILE NOT FOUND");
                        return;
                    }
                    answer(0, String.valueOf(file.length()));
                    FileInputStream fileInputStream = new FileInputStream(file);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    fileInputStream.close();
                } else if (message.startsWith("@deleteElement")) {
                    String[] parts = message.split(" ");
                    if (parts.length < 3) {
                        answer(1, "PROTOCOL ERROR");
                        return;
                    }
                    String filename = parts[1];
                    boolean isDirectory = Boolean.parseBoolean(parts[2]);

                    String path = Path.of(serverPath, username, filename).toString();
                    File file = new File(path);
                    if (!file.exists()) {
                        answer(6, "FILE NOT FOUND");
                        return;
                    }
                    hashedStorage.DeleteEvent(Path.of(username, filename), isDirectory);
                    hashedStorage.recalculateHashRecursive();
                    if (file.delete())
                        answer(0, "OK");
                    else
                        answer(7, "INTERNAL ERROR");
                } else if (message.startsWith("@createDirectory ")) {
                    String[] parts = message.split(" ");
                    if (parts.length < 2) {
                        answer(1, "PROTOCOL ERROR");
                        return;
                    }
                    String filename = parts[1];
                    String path = Path.of(serverPath, username, filename).toString();
                    File file = new File(path);
                    if (file.exists()) {
                        answer(5, "DIRECTORY ALREADY EXISTS");
                        return;
                    }
                    if (file.mkdirs()) {
                        hashedStorage.CreatedOrUpdatedEvent(Path.of(username, filename), true, 0, "");
                        answer(0, "OK");
                    } else {
                        answer(7, "INTERNAL ERROR");
                    }
                    hashedStorage.recalculateHashRecursive();
                }else if (message.startsWith("@getListFiles ")) {
                    String[] parts = message.split(" ");
                    if (parts.length < 2) {
                        String[] parts_new = new String[2];
                        parts_new[0] = parts[0];
                        parts_new[1] = "";
                        parts = parts_new;
//                        answer(1, "PROTOCOL ERROR");
                    }
                    try {
                        JSONObject jsonObject = getFilesList(username, parts[1]);
                        answer(0, jsonObject.toString());
                    } catch (NotFoundException e) {
                        answer(6, "FILE NOT FOUND");
                    }
                }
            }

        }

        public void run() {
            try {
                ThreadContext.put("ip", socket.getInetAddress().toString());
                logger.atDebug().log("[ClientHandler] Client connected: " + socket.getInetAddress() + ":" + socket.getPort());
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                clientWriters.add(out);

                String message;
                message = in.readLine();
                logger.atDebug().log("[ClientHandler] Message from client: " + message);
                // message can be @login or @auth

                // @login - specially for getting auth token and closing connection
                // @auth - for using auth token and working with server

                if (message.startsWith("@login")) {
                    String[] parts = message.split(" ");

                    if (parts.length < 4) {
                        answer(1, "PROTOCOL ERROR");
                        return;
                    }
                    String role = parts[1];
                    String username = parts[2];
                    String password = parts[3];
                    ThreadContext.put("username", username);
                    ThreadContext.put("role", role);
                    if (!userService.checkUser(role, username, password)) {
                        logger.info("Wrong credentials");
                        answer(2, "WRONG CREDENTIALS");
                        return;
                    }
                    String token = userService.createToken(role, username);
                    userService.saveToFile();
                    ThreadContext.put("token", token);
                    logger.info("Successful login");
                    answer(0, token);
                    return;

                } else if (message.startsWith("@auth")) {
                    String[] parts = message.split(" ");

                    if (parts.length < 3) {
                        answer(1, "PROTOCOL ERROR");
                        return;
                    }
                    role = parts[1];
                    token = parts[2];
                    ThreadContext.put("role", role);
                    ThreadContext.put("token", token);
                    if (!userService.checkToken(role, token)) {
                        logger.atError().log("Wrong credentials for token: " + token);

                        answer(2, "WRONG CREDENTIALS");
                        return;
                    }
                    username = userService.getUsernameByToken(token);
                    ThreadContext.put("username", username);
                    logger.info("Successful auth");

                    transactionLocker = userService.getTransactionObject(username);

                    answer(0, username);

                } else {
                    answer(1, "PROTOCOL ERROR");
                    return;
                }

                File folder = new File(Path.of(serverPath, username).toString());
                if (!folder.exists()) {
                    folder.mkdirs();
                }

                if (role.equals("user")) {
                    ClientConnection();
                } else if (role.equals("admin") || role.equals("super-admin")) {
                    ManagerConnection();
                } else {
                    answer(3, "INTERNAL ERROR"); // Внутренняя ошибка сервера
                }

            } catch (SocketException e) {
                logger.atDebug().log("[ClientHandler] Connection reset: " + socket.getInetAddress() + ":" + socket.getPort());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
//                transactionLocker.unlock();
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                clientWriters.remove(out);
                logger.atDebug().log("[ClientHandler] Client disconnected: " + socket.getInetAddress() + ":" + socket.getPort());
            }
        }

        private void broadcast(String message) {
            for (PrintWriter writer : clientWriters) {
                writer.println(message);
            }
        }

        public void answer(int code, String message) {
//            System.out.println("Answer: " + code + " " + message);
            logger.atDebug().log("[ClientHandler] Answer: " + code + " " + message);
            out.println(new Response(code, message));
        }

    }

}
