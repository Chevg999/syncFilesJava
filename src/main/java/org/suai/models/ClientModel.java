package org.suai.models;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.suai.exceptions.ClientException;
import org.suai.exceptions.CredentialsException;
import org.suai.temp.ChatServer;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

// Auth Token, hostname, port, etc.
public class ClientModel {
    private static final Logger logger = LogManager.getLogger(ClientModel.class);
    private String authToken;
    private String hostname;
    private int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private OutputStream outputStream;
    private InputStream inputStream;

    public ClientModel(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }


    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public void connect() {
        try {
            socket = new Socket(hostname, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            outputStream = socket.getOutputStream();
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            inputStream = socket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void doAuth(String role, String authToken) throws CredentialsException {
        String authMessage = String.format("@auth %s %s", role, authToken);
        out.println(authMessage);

        String response;

        try {
            response = in.readLine();
            Response parsedResponse = Response.parse(response);
            if (!parsedResponse.isSuccess()) {
                if (parsedResponse.getCode() == 2) {
                    throw new CredentialsException(parsedResponse.getMessage());
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String doLogin(String role, String login, String password) {
        String loginMessage = String.format("@login %s %s %s", role, login, password);
        out.println(loginMessage);

        String response;

        try {
            response = in.readLine();
            Response parsedResponse = Response.parse(response);
            if (!parsedResponse.isSuccess()) {
                if (parsedResponse.getCode() == 2) {
                    throw new CredentialsException(parsedResponse.getMessage());
                }

            }
            return parsedResponse.getMessage();
        } catch (IOException e) {
            throw new CredentialsException("Wrong password");
        }
    }

    public void startCommunication() {
        try {
            Scanner scanner = new Scanner(System.in);

            Thread receiveThread = new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            receiveThread.start();

            String userInput;
            while (true) {
                userInput = scanner.nextLine();
                out.println(userInput);
            }
        } finally {
            try {
                assert socket != null;
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getRemoteHash(String path) {
        String message = String.format("@getHash %s", path);
        out.println(message);

        String response;

        try {
            response = in.readLine();
            Response parsedResponse = Response.parse(response);
            if (!parsedResponse.isSuccess()) {
                if (parsedResponse.getCode() == 2) {
                    throw new CredentialsException(parsedResponse.getMessage());
                }
            }
            return parsedResponse.getMessage();
        } catch (IOException e) {
            logger.error("Failed to get hash for file: " + path);
            logger.throwing(e);
            e.printStackTrace();
        }
        return "";
    }

    public void sendFile(String path, String serverPath, long timestamp, String contentHash) {

        // Open file and get file size in bytes
        File file = new File(path);
        long fileSize = file.length();

        // Send file size to server
        String message = String.format("@sendFile %s %d %d %s", serverPath, fileSize, timestamp, contentHash);
        out.println(message);

        String response;
        Response parsedResponse;
        try {
            response = in.readLine();
            parsedResponse = Response.parse(response);
        } catch (IOException e) {
            logger.catching(e);
            throw new RuntimeException(e);
        }
        logger.info(parsedResponse.getMessage());
        logger.info(parsedResponse.getCode());
        if (!parsedResponse.isSuccess()) {
            logger.error("Failed to send file: " + parsedResponse.getMessage());
            return;
        }

        // Send file data to server
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            fileInputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void downloadFile(String path, String serverPath) {
        String message = String.format("@downloadFile %s", serverPath);
        out.println(message);

        String response;

        try {
            response = in.readLine();
            Response parsedResponse = Response.parse(response);
            if (!parsedResponse.isSuccess()) {
                if (parsedResponse.getCode() == 2) {
                    throw new CredentialsException(parsedResponse.getMessage());
                }
            }
            long fileSize = Long.parseLong(parsedResponse.getMessage());
            FileOutputStream fileOutputStream = new FileOutputStream(path);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while (fileSize > 0 && (bytesRead = inputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
                fileSize -= bytesRead;
            }
            fileOutputStream.close();
            // edit file last modified time
            File file = new File(path);
            if (!file.setLastModified(System.currentTimeMillis())) {
                logger.error("Failed to set last modified time for file: " + path);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteElement(String serverPath, boolean isDirectory) {
        String message = String.format("@deleteElement %s %s", serverPath, isDirectory);
        out.println(message);

        String response;

        try {
            response = in.readLine();
            Response parsedResponse = Response.parse(response);
            if (!parsedResponse.isSuccess()) {
                logger.error("[deleteElement] " + parsedResponse.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void createDirectory(String serverPath) {
        String message = String.format("@createDirectory %s", serverPath);
        out.println(message);

        String response;

        try {
            response = in.readLine();
            Response parsedResponse = Response.parse(response);
            if (!parsedResponse.isSuccess()) {
                logger.error("[createDirectory] " + parsedResponse.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JSONObject getFilesList(String path) {
        String message = String.format("@getListFiles %s", path);
        out.println(message);

        String response;

        try {
            response = in.readLine();
            Response parsedResponse = Response.parse(response);
            if (!parsedResponse.isSuccess()) {
                logger.error("[getFilesList] " + parsedResponse.getMessage());
            }
            JSONObject returnObject = new JSONObject(parsedResponse.getMessage());
            // validate returnObject for "dirs" and "files" keys
            if (!returnObject.has("dirs") || !returnObject.has("files")) {
                logger.error("[getFilesList] Invalid response from server");
                throw new ClientException("Invalid response from server");
            }

            return returnObject;
//            System.out.println(parsedResponse.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void startSync() {
        String message = "@startSync";
        out.println(message);

        String response;

        try {
            response = in.readLine();
            Response parsedResponse = Response.parse(response);
            if (!parsedResponse.isSuccess()) {
                logger.error("[startSync] " + parsedResponse.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopSync() {
        String message = "@stopSync";
        out.println(message);

        String response;

        try {
            response = in.readLine();
            Response parsedResponse = Response.parse(response);
            if (!parsedResponse.isSuccess()) {
                logger.error("[stopSync] " + parsedResponse.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
