package org.suai.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.suai.utils.HashUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class UserService {
    private static final Logger logger = LogManager.getLogger(UserService.class);

    private static final String USERS_FILE = "users.json";
    private static final String TOKENS_FILE = "tokens.json";

    private static final String rootPassword = "rootKEY";

    private static final String passwordSalt = "salt";

    HashMap<String, String> admins = new HashMap<>(); // username -> password
    HashMap<String, String> users = new HashMap<>(); // username -> password
    HashMap<String, String> userTokens = new HashMap<>(); // token -> username
    HashMap<String, String> adminTokens = new HashMap<>(); // token -> username

    HashMap<String, ReentrantLock> transactions = new HashMap<>(); // username -> transactionObjectLock

    ArrayList<String> activeTransactions = new ArrayList<>();

    private UserService() {
    }

    private static class UserServiceHolder {
        private static final UserService instance = new UserService();
    }

    public static UserService getInstance() {
        return UserServiceHolder.instance;
    }

    public void createBasicUser(String username, String password) {
        password = HashUtil.calculateHash(password+passwordSalt);
        users.put(username, password);
    }

    public void createAdmin(String username, String password) {
        password = HashUtil.calculateHash(password+passwordSalt);
        admins.put(username, password);
    }

    public boolean checkUser(String role, String username, String password) {
        password = HashUtil.calculateHash(password+ passwordSalt);
        return switch (role) {
            case "super-admin" -> checkSuperAdmin(username, password);
            case "admin" -> checkAdmin(username, password);
            case "user" -> checkBasicUser(username, password);
            default -> throw new RuntimeException("Unknown role: " + role);
        };
    }

    private boolean checkBasicUser(String username, String password) {
        if (!users.containsKey(username)) return false;
        return users.get(username).equals(password);
    }

    private boolean checkAdmin(String username, String password) {
        if (!admins.containsKey(username)) return false;
        return admins.get(username).equals(password);
    }

    private boolean checkSuperAdmin(String username, String password) {
        if (!username.equals(rootPassword)) return false;
        return rootPassword.equals(password);
    }

    public boolean checkUsername(String role, String username) {
        switch (role) {
            case "admin" -> {
                return admins.containsKey(username);
            }
            case "user" -> {
                return users.containsKey(username);
            }
            default -> throw new RuntimeException("Unknown role: " + role);
        }
    }

    public String createToken(String role, String username) {
        String token = HashUtil.calculateHash(username+System.currentTimeMillis());
        switch (role) {
            case "super-admin" -> {
                return rootPassword;
            }
            case "admin" -> adminTokens.put(token, username);
            case "user" -> userTokens.put(token, username);
            default -> throw new RuntimeException("Unknown role: " + role);
        }
        return token;
    }

    public boolean checkToken(String role, String token) {
        logger.atDebug().log("Checking token: " + token);
        logger.atDebug().log(userTokens.toString());
        return switch (role) {
            case "super-admin" -> rootPassword.equals(token);
            case "admin" -> adminTokens.containsKey(token);
            case "user" -> userTokens.containsKey(token);
            default -> throw new RuntimeException("Unknown role: " + role);
        };
    }

    public String getUsernameByToken(String token) {
        return userTokens.get(token);
    }

    public ReentrantLock getTransactionObject(String username) {
        return transactions.get(username);
    }

    public void saveToFile() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

            // Save users to JSON
            objectMapper.writeValue(new File(USERS_FILE), users);

            // Save tokens to JSON
            objectMapper.writeValue(new File(TOKENS_FILE), userTokens);

            System.out.println("Data saved to files successfully.");
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception appropriately based on your application's requirements.
        }
    }

    public void loadFromFile() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // Load users from JSON
            users = objectMapper.readValue(new File("users.json"), HashMap.class);

            // Load tokens from JSON
            userTokens = objectMapper.readValue(new File("tokens.json"), HashMap.class);

            for (String username : users.keySet()) {
                transactions.put(username, new ReentrantLock());
            }

            System.out.println("Data loaded from files successfully.");
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception appropriately based on your application's requirements.
        }
    }

    public void clearUsers() {
        users.clear();
    }
}
