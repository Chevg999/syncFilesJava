package org.suai.utils;

import org.suai.services.UserService;

public class UserCreate {
    public static void main(String[] args) {
        UserService userService = UserService.getInstance();
        userService.loadFromFile();
        if (!userService.checkUsername("user", "SEMEN")){
            System.out.println("User does not exist");
            userService.createBasicUser("SEMEN","123");
            userService.saveToFile();
        } else {
            System.out.println("User exist");
        }

        userService.createToken("user", "SEMEN");
        userService.saveToFile();
    }
}
