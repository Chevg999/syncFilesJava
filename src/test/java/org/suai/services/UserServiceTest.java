package org.suai.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {
    UserService userService = UserService.getInstance();

    @Test
    void createUserAndTokens() {
        userService.createBasicUser("user1", "pass1");
        userService.createBasicUser("user2", "pass2");

        userService.createToken("user", "user1");
        userService.createToken("user", "user2");
        userService.createToken("user", "user2");
        userService.saveToFile();
    }

    @Test
    void checkUser() {
//        userService.createUser("user1", "pass1");
//        userService.createUser("user2", "pass2");

        assertTrue(userService.checkUser("user", "user1", "pass1"));
        assertTrue(userService.checkUser("user", "user2", "pass2"));
        assertFalse(userService.checkUser("user", "user1", "pass2"));
        assertFalse(userService.checkUser("user", "user2", "pass1"));
    }

    @Test
    void clearUsers() {
        userService.clearUsers();
        assertFalse(userService.checkUser("user", "user1", "pass1"));
    }

    @Test
    void checkLoadedUsers() {
        userService.loadFromFile();
        assertTrue(userService.checkUser("user", "user1", "pass1"));
        assertTrue(userService.checkUser("user", "user2", "pass2"));
        assertFalse(userService.checkUser("user", "user1", "pass2"));
        assertFalse(userService.checkUser("user", "user2", "pass1"));
    }
}