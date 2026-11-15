package com.securedocumentexchange.test;

import org.mindrot.jbcrypt.BCrypt;

public class GeneratePasswordHash {

    public static void main(String[] args) {

        String password = "admin123";

        String hash = BCrypt.hashpw(
                password,
                BCrypt.gensalt(12)
        );

        System.out.println("Hash BCrypt :");
        System.out.println(hash);
    }
}